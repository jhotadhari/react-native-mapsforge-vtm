package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.mapsforge.map.android.hills.DemFileAndroidContent;
import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFileFS;
import org.mapsforge.map.layer.hills.DemFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reads SRTM HGT files to provide point-elevation queries.
 *
 * <p>Much thinner than the old {@code HgtReader} (which was a ~530-line copy of
 * mapsforge's {@code HgtCache} + JOSM's {@code SRTMTile} + JOSM's {@code HgtReader}).
 *
 * <p>{@link #getElevation} is safe to call on the render thread at frame rate:
 * on cache hit it does a sub-millisecond grid lookup + bilinear interpolation;
 * on cache miss it kicks off a background load and returns {@code null} — the
 * next call (typically 40ms later from the next map event) hits the cache.
 *
 * <p><b>Tile storage.</b> File-backed tiles ({@code DemFileFS} and
 * {@code content://} documents backed by a seekable file descriptor) are
 * memory-mapped read-only ({@link MappedHgtGrid}) and pre-faulted with
 * {@code MappedByteBuffer.load()} on the preload thread, so a tile retains
 * ~0 Java heap. Anything that can't be mapped falls back to one exact-size
 * read into a flat {@code short[]} ({@link ArrayHgtGrid}).
 *
 * <p><b>Cache accounting</b> ({@link HgtTileCache}): the tile <em>count</em>
 * is capped at {@link #CACHE_MAX_TILES} (raisable via {@link #setCacheCapacity});
 * independently, the Java heap held by array-backed tiles is capped at
 * {@link #ARRAY_HEAP_BUDGET_BYTES}. Mapped tiles are charged a constant
 * {@value MappedHgtGrid#HEAP_BYTES} bytes. The newest tile is never evicted
 * by the byte budget.
 */
public class ElevationReader {

    /** Number of rows/columns in an SRTM3 tile (3 arc-second resolution). */
    private static final int SRTM3_TILE_LENGTH = 1201;

    private static final String TAG = "ElevationReader";

    /**
     * Default maximum number of tiles kept in the cache. Tiles are memory-mapped
     * (~2.9 MB of page cache each, ~0 Java heap), so this bounds address space and
     * resident page-cache rather than heap.
     */
    private static final int CACHE_MAX_TILES = 10;

    /**
     * Java-heap budget for array-backed (non-mappable) tiles: ~4 SRTM3 tiles
     * (2.9 MB each). Mapped tiles do not count against it.
     */
    private static final long ARRAY_HEAP_BUDGET_BYTES = 12L * 1024 * 1024;

    /**
     * Single thread for background tile loads, shared by all readers. Mapping +
     * {@code load()} is cheap, and one thread bounds concurrent transient memory
     * (relevant for the array fallback) and page-fault I/O.
     */
    private static final Executor PRELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ElevationReader-preload");
        t.setDaemon(true);
        return t;
    });

    /** Single-thread scheduled executor for delayed (debounced) preloads. */
    private static final ScheduledExecutorService DELAYED_PRELOAD_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ElevationReader-delayed-preload");
                t.setDaemon(true);
                return t;
            });

    /**
     * At most one delayed preload is pending at a time — each new tile
     * cancels the previous one so only the tile the user lingers on is
     * loaded.  Calls for the <em>same</em> tile leave the existing timer
     * running so that continuous panning within a tile doesn't perpetually
     * reset the countdown.
     */
    private ScheduledFuture<?> pendingDelayedPreload;
    private String pendingDelayedPreloadFilename;
    private final Object pendingDelayedPreloadLock = new Object();

    /** Filenames currently being read by {@link #PRELOAD_EXECUTOR}. */
    private final Set<String> inFlightPreloads = new HashSet<>();

    private final DemFolder demFolder;

    /** Filename (lowercase) → DemFile. Built once on construction, read-only thereafter. */
    private final Map<String, DemFile> fileIndex = new HashMap<>();

    /** LRU cache of loaded elevation grids, keyed by lowercase filename. */
    private final HgtTileCache dataCache = new HgtTileCache(CACHE_MAX_TILES, ARRAY_HEAP_BUDGET_BYTES);

    /**
     * @param demFolder the folder containing .hgt files.
     */
    public ElevationReader(DemFolder demFolder) {
        this.demFolder = demFolder;
        indexFiles(demFolder);
    }

    /**
     * Returns {@code true} if an HGT file exists that covers the given
     * coordinate, regardless of whether the tile data is currently cached.
     * A {@code false} return means no amount of waiting will produce an
     * elevation — callers can skip retry loops.
     *
     * <p>This method only checks the filename index (built once at
     * construction time) — it does no I/O and is safe to call from any
     * thread, including the render thread at frame rate.</p>
     */
    public boolean hasDataFor(double lng, double lat) {
        String filename = tileFilename(lat, lng);
        synchronized (fileIndex) {
            return fileIndex.containsKey(filename);
        }
    }

    /**
     * Returns {@code true} if the HGT tile covering (lng, lat) is currently
     * loaded in the LRU cache.  Unlike {@link #getElevation} this never
     * triggers a preload — it only checks the cache.  Use for fence polling
     * where you need to distinguish "tile not loaded yet" from "tile loaded
     * but this pixel is void (ocean / data gap)".
     */
    public boolean isTileCached(double lng, double lat) {
        String filename = tileFilename(lat, lng);
        synchronized (fileIndex) {
            if (!fileIndex.containsKey(filename)) {
                return false;
            }
        }
        return dataCache.get(filename) != null;
    }

    /**
     * Returns the elevation in metres at the given coordinate, or {@code null}.
     * Equivalent to {@code getElevation(lng, lat, 0)} — on cache miss the
     * preload starts immediately.
     */
    public Short getElevation(double lng, double lat) {
        return getElevation(lng, lat, 0);
    }

    /**
     * Returns the elevation in metres at the given coordinate, or {@code null}
     * if no data covers that position, the position falls in a void (ocean)
     * area, or the tile is not yet cached.
     *
     * <p>On cache miss a background load is started after {@code preloadDelayMs}
     * milliseconds.  When {@code preloadDelayMs > 0} and this method is called
     * again for the same tile before the delay expires, the timer resets — this
     * lets callers on the render hot-path (e.g. {@code getResponseBase}) debounce
     * preloads so that tiles the user quickly pans over are never loaded, while
     * the explicit JS API ({@code getAltitudeAtPosition}) passes 0 for immediate
     * loading.
     *
     * <p>This method is thread-safe.</p>
     */
    public Short getElevation(double lng, double lat, int preloadDelayMs) {
        try {
            String filename = tileFilename(lat, lng);
            DemFile file;
            synchronized (fileIndex) {
                file = fileIndex.get(filename);
            }
            if (file == null) {
                return null;
            }

            HgtGrid data = dataCache.get(filename);
            if (data == null) {
                // Cache miss — start a (possibly delayed) background load.
                if (preloadDelayMs > 0) {
                    schedulePreload(filename, file, preloadDelayMs);
                } else {
                    preload(filename, file);
                }
                return null;
            }

            return HgtInterpolator.interpolate(data, lat, lng);
        } catch (Exception e) {
            // Best-effort — never let bad data crash the caller.
            return null;
        }
    }

    /** Releases cached tile data and clears the file index. */
    public void close() {
        // Cancel any pending delayed preload so a stale task doesn't fire
        // after the reader is closed and waste I/O on a ~2.9 MB HGT file
        // whose data would be immediately evicted.
        synchronized (pendingDelayedPreloadLock) {
            if (pendingDelayedPreload != null) {
                pendingDelayedPreload.cancel(false);
                pendingDelayedPreload = null;
                pendingDelayedPreloadFilename = null;
            }
        }
        // Clear in-flight tracking so any already-enqueued preload tasks
        // see the empty cache below and return without redundant disk I/O.
        synchronized (inFlightPreloads) {
            inFlightPreloads.clear();
        }
        dataCache.evictAll();
        synchronized (fileIndex) {
            fileIndex.clear();
        }
    }

    /**
     * Resizes the LRU cache to hold at most {@code maxTiles} elevation grids
     * (the array-fallback heap budget is unaffected).
     * Use to temporarily raise capacity during batch elevation enrichment and
     * restore to the compile-time default of {@value #CACHE_MAX_TILES} afterwards.
     *
     * <p>Values ≤ 0 are clamped to 1.  The resize is cheap — it only adjusts
     * the capacity field; existing cache entries are unaffected unless the new
     * capacity is smaller than the current entry count, in which case the
     * least-recently-used entries are evicted until the count fits.</p>
     */
    public void setCacheCapacity(int maxTiles) {
        dataCache.resize(Math.max(1, maxTiles));
    }

    // ---- internal helpers -------------------------------------------------------

    /**
     * Submits a background load of {@code file} into the cache under
     * {@code filename}, unless already cached or already loading.
     */
    private void preload(String filename, DemFile file) {
        // Skip if this filename is already being loaded or already cached.
        synchronized (inFlightPreloads) {
            if (inFlightPreloads.contains(filename)) {
                return;
            }
            inFlightPreloads.add(filename);
        }
        PRELOAD_EXECUTOR.execute(() -> {
            try {
                // Double-check — a previous preload may have finished while
                // this task was enqueued.
                if (dataCache.get(filename) != null) {
                    return;
                }
                HgtGrid data = loadGrid(file);
                if (data != null) {
                    dataCache.put(filename, data);
                }
            } catch (IOException e) {
                // Best-effort — tile just won't be cached.
            } catch (OutOfMemoryError oom) {
                // Optional preload must never crash the app: treat as a cache
                // miss and free memory by dropping the older half of the cache.
                Log.w(TAG, "OOM loading " + filename + "; evicting cached tiles", oom);
                try {
                    dataCache.evictOldest(Math.max(1, dataCache.size() / 2));
                } catch (Throwable ignored) {
                    // Nothing more to do.
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to load " + filename, e);
            } finally {
                synchronized (inFlightPreloads) {
                    inFlightPreloads.remove(filename);
                }
            }
        });
    }

    /**
     * Schedules a debounced preload: waits {@code delayMs}, then calls
     * {@link #preload}.  At most one delayed preload is pending at a time —
     * each call cancels the previous one so that during rapid panning only
     * the tile the user finally stops on is ever loaded.  Tiles that the user
     * quickly pans past are never submitted to the executor at all.
     */
    private void schedulePreload(
            String filename, DemFile file, int delayMs) {
        synchronized (pendingDelayedPreloadLock) {
            // Same tile — leave the existing timer running.  Without this
            // guard, every frame during continuous panning would reset the
            // countdown and the timer would never fire.
            if (filename.equals(pendingDelayedPreloadFilename)) {
                return;
            }

            // Different tile — cancel the previous one.
            if (pendingDelayedPreload != null) {
                pendingDelayedPreload.cancel(false);
                pendingDelayedPreload = null;
                pendingDelayedPreloadFilename = null;
            }

            pendingDelayedPreloadFilename = filename;
            pendingDelayedPreload = DELAYED_PRELOAD_EXECUTOR.schedule(() -> {
                synchronized (pendingDelayedPreloadLock) {
                    pendingDelayedPreload = null;
                    pendingDelayedPreloadFilename = null;
                }
                preload(filename, file);
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Builds a lowercase-filename → DemFile index by scanning the DemFolder tree
     * synchronously once.
     */
    private void indexFiles(DemFolder folder) {
        for (DemFile file : folder.files()) {
            fileIndex.put(file.getName().toLowerCase(Locale.ROOT), file);
        }
        for (DemFolder sub : folder.subs()) {
            indexFiles(sub);
        }
    }

    /** Computes the SRTM filename for the 1°×1° tile containing (lat, lng). */
    static String tileFilename(double lat, double lng) {
        int latFloor = (int) Math.floor(lat);
        int lngFloor = (int) Math.floor(lng);
        return String.format(Locale.ROOT, "%s%02d%s%03d.hgt",
                latFloor >= 0 ? "n" : "s",
                Math.abs(latFloor),
                lngFloor >= 0 ? "e" : "w",
                Math.abs(lngFloor)
        );
    }

    // ---- file I/O ---------------------------------------------------------------

    /** Cached reflective handles (null when unavailable, e.g. obfuscated). */
    private static final Field FS_FILE_FIELD = findField(DemFileFS.class, "file");
    private static final Field CONTENT_ENTRY_FIELD = findField(DemFileAndroidContent.class, "entry");
    private static final Field CONTENT_RESOLVER_FIELD = findField(DemFileAndroidContent.class, "contentResolver");

    private static Field findField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "No reflective access to " + cls.getSimpleName() + "." + name
                    + "; falling back to stream reads");
            return null;
        }
    }

    /**
     * Loads a tile: memory-mapped when the file is mappable, else one exact-size
     * read into a flat short[].
     */
    private static HgtGrid loadGrid(DemFile file) throws IOException {
        HgtGrid mapped = null;
        try {
            mapped = mapFile(file);
        } catch (IOException | RuntimeException | LinkageError e) {
            Log.d(TAG, "Mapping failed for " + file.getName() + ", using array fallback: " + e);
        }
        if (mapped != null) {
            return mapped;
        }
        InputStream in = file.asStream();
        if (in == null) {
            throw new IOException("DemFile.asStream() returned null for " + file.getName());
        }
        try {
            return ArrayHgtGrid.read(in, file.getSize());
        } finally {
            in.close();
        }
    }

    /**
     * Maps a plain file ({@link DemFileFS}) or a {@code content://} document
     * ({@link DemFileAndroidContent}, via {@code openFileDescriptor} — works for
     * providers handing out a seekable fd, e.g. external-storage documents)
     * read-only and pre-faults it. Returns {@code null} when not mappable.
     */
    private static HgtGrid mapFile(DemFile file) throws IOException {
        try {
            if (file instanceof DemFileFS && FS_FILE_FIELD != null) {
                File f = (File) FS_FILE_FIELD.get(file);
                try (FileInputStream fis = new FileInputStream(f)) {
                    return mapChannel(fis.getChannel());
                }
            }
            if (file instanceof DemFileAndroidContent
                    && CONTENT_ENTRY_FIELD != null && CONTENT_RESOLVER_FIELD != null) {
                Object entry = CONTENT_ENTRY_FIELD.get(file);
                ContentResolver resolver = (ContentResolver) CONTENT_RESOLVER_FIELD.get(file);
                Field uriField = entry.getClass().getDeclaredField("uri");
                uriField.setAccessible(true);
                Uri uri = (Uri) uriField.get(entry);
                try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
                    if (pfd == null) {
                        return null;
                    }
                    // Not closed on purpose: the stream does not own the fd (the pfd
                    // does), and closing its channel would close the fd twice.
                    FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                    return mapChannel(fis.getChannel());
                }
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return null;
    }

    private static HgtGrid mapChannel(FileChannel channel) throws IOException {
        long length = channel.size();
        if (length < 2 || length > Integer.MAX_VALUE) {
            return null;
        }
        // The mapping stays valid after the channel / fd are closed.
        MappedByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, length);
        buf.load(); // pre-fault so "cached" still means resident.
        return new MappedHgtGrid(buf);
    }
}
