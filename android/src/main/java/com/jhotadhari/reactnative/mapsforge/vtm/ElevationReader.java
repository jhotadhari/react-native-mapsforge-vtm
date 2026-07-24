package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.LruCache;

import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads SRTM HGT files to provide point-elevation queries.
 *
 * <p>Much thinner than the old {@code HgtReader} (which was a ~530-line copy of
 * mapsforge's {@code HgtCache} + JOSM's {@code SRTMTile} + JOSM's {@code HgtReader}).
 *
 * <p>{@link #getElevation} is safe to call on the render thread at frame rate:
 * on cache hit it does a sub-millisecond array lookup + bilinear interpolation;
 * on cache miss it kicks off a background load and returns {@code null} — the
 * next call (typically 40ms later from the next map event) hits the cache.
 */
public class ElevationReader {

    /** Number of rows/columns in an SRTM3 tile (3 arc-second resolution). */
    private static final int SRTM3_TILE_LENGTH = 1201;

    /** Maximum number of loaded tile data arrays kept in memory (~2.9 MB each, ~29 MB total). */
    private static final int CACHE_MAX_TILES = 10;

    /**
     * SRTM void / no-data sentinel. Grid cells with this value represent ocean or
     * missing data and must never be treated as a real elevation.
     */
    private static final short SRTM_VOID = Short.MIN_VALUE; // -32768

    /**
     * Fixed thread pool for background tile loads.
     *
     * <p>4 threads saturate typical mobile flash I/O without excessive
     * memory pressure — each concurrent read allocates a ~5.8 MB
     * short[][] (1201×1201×2 bytes × 2 for the working copy in
     * readHgtFile).  The LruCache and inFlightPreloads accesses are
     * already synchronized, so the elevation data stays consistent.</p>
     */
    private static final AtomicInteger preloadThreadNum = new AtomicInteger(0);
    private static final Executor PRELOAD_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ElevationReader-preload-" + preloadThreadNum.incrementAndGet());
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
    private final LruCache<String, short[][]> dataCache = new LruCache<String, short[][]>(CACHE_MAX_TILES) {
        @Override
        protected int sizeOf(String key, short[][] value) {
            // Count each tile as 1 entry; max 10 tiles.
            return 1;
        }
    };

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
        synchronized (dataCache) {
            return dataCache.get(filename) != null;
        }
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

            short[][] data;
            synchronized (dataCache) {
                data = dataCache.get(filename);
            }
            if (data == null) {
                // Cache miss — start a (possibly delayed) background load.
                if (preloadDelayMs > 0) {
                    schedulePreload(filename, file, preloadDelayMs);
                } else {
                    preload(filename, file);
                }
                return null;
            }

            return interpolate(data, lat, lng);
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
        synchronized (dataCache) {
            dataCache.evictAll();
        }
        synchronized (fileIndex) {
            fileIndex.clear();
        }
    }

    /**
     * Resizes the LRU cache to hold at most {@code maxTiles} elevation grids.
     * Use to temporarily raise capacity during batch elevation enrichment and
     * restore to the compile-time default of {@value #CACHE_MAX_TILES} afterwards.
     *
     * <p>Values ≤ 0 are clamped to 1.  The resize is cheap — it only adjusts
     * the capacity field; existing cache entries are unaffected unless the new
     * capacity is smaller than the current entry count, in which case the
     * least-recently-used entries are evicted until the count fits.</p>
     */
    public void setCacheCapacity(int maxTiles) {
        synchronized (dataCache) {
            dataCache.resize(Math.max(1, maxTiles));
        }
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
                synchronized (dataCache) {
                    if (dataCache.get(filename) != null) {
                        return;
                    }
                }
                short[][] data = readHgtFile(file);
                if (data != null) {
                    synchronized (dataCache) {
                        dataCache.put(filename, data);
                    }
                }
            } catch (IOException e) {
                // Best-effort — tile just won't be cached.
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
     * Bilinear interpolation within a single HGT tile grid.
     *
     * @param data the tile elevation grid (row 0 = north edge, last row = south edge).
     * @param lat  latitude in degrees.
     * @param lng  longitude in degrees.
     * @return interpolated elevation in metres, or {@code null} if any corner is void.
     */
    private Short interpolate(short[][] data, double lat, double lng) {
        int tileLength = data.length;
        if (tileLength == 0) {
            return null;
        }

        // Degenerate / 1×1 tile.
        if (tileLength <= 1) {
            short val = data[0][0];
            return val == SRTM_VOID ? null : val;
        }

        int latFloor = (int) Math.floor(lat);
        int lngFloor = (int) Math.floor(lng);

        // Fractional position within the tile (0..1).
        double latFrac = lat - latFloor;
        double lngFrac = lng - lngFloor;

        // Continuous row/column index within the grid.
        // Row 0 = northernmost edge of tile, row (tileLength-1) = southernmost.
        double latRowD = (1.0 - latFrac) * (tileLength - 1);
        double lngColD = lngFrac * (tileLength - 1);

        // Floor and ceil give the two surrounding grid lines.
        int rowNorth = (int) Math.floor(latRowD);
        int rowSouth = rowNorth + 1;
        int colWest = (int) Math.floor(lngColD);
        int colEast = colWest + 1;

        // Clamp to valid range.
        rowNorth = Math.max(0, Math.min(tileLength - 1, rowNorth));
        rowSouth = Math.max(0, Math.min(tileLength - 1, rowSouth));
        colWest = Math.max(0, Math.min(tileLength - 1, colWest));
        colEast = Math.max(0, Math.min(tileLength - 1, colEast));

        // Fractional offsets within the cell (0..1).
        double dx = lngColD - colWest;
        double dy = latRowD - rowNorth;

        // Degenerate case: query point landed exactly on a grid intersection.
        if (rowNorth == rowSouth && colWest == colEast) {
            short val = data[rowNorth][colWest];
            return val == SRTM_VOID ? null : val;
        }

        // Degenerate case: one dimension collapsed (point on a grid line).
        if (rowNorth == rowSouth) {
            short w = data[rowNorth][colWest];
            short e = data[rowNorth][colEast];
            if (w == SRTM_VOID || e == SRTM_VOID) return null;
            return (short) Math.round(w * (1.0 - dx) + e * dx);
        }
        if (colWest == colEast) {
            short n = data[rowNorth][colWest];
            short s = data[rowSouth][colWest];
            if (n == SRTM_VOID || s == SRTM_VOID) return null;
            return (short) Math.round(n * (1.0 - dy) + s * dy);
        }

        // Four corner values.
        short fNW = data[rowNorth][colWest];
        short fNE = data[rowNorth][colEast];
        short fSW = data[rowSouth][colWest];
        short fSE = data[rowSouth][colEast];

        // Refuse to interpolate across void cells.
        if (fNW == SRTM_VOID || fNE == SRTM_VOID || fSW == SRTM_VOID || fSE == SRTM_VOID) {
            return null;
        }

        // Standard bilinear interpolation on a uniform grid.
        double result = fNW * (1.0 - dx) * (1.0 - dy)
                      + fNE * dx * (1.0 - dy)
                      + fSW * (1.0 - dx) * dy
                      + fSE * dx * dy;

        return (short) Math.round(result);
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

    /**
     * Reads a single .hgt file into a {@code short[][]}.
     */
    private static short[][] readHgtFile(DemFile file) throws IOException {
        InputStream fis = file.asStream();
        if (fis == null) {
            throw new IOException("DemFile.asStream() returned null for " + file.getName());
        }
        byte[] bytes;
        try {
            bytes = readAllBytes(fis);
        } finally {
            fis.close();
        }

        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        int size = (int) Math.sqrt(bytes.length / 2.0);
        short[][] data = new short[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                data[row][col] = bb.getShort();
            }
        }
        return data;
    }

    /** Reads all bytes from an InputStream (Java 8 compatible). */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int capacity = 8192;
        int total = 0;
        byte[] data = new byte[capacity];
        int nRead;
        while ((nRead = in.read(buf)) != -1) {
            while (total + nRead > capacity) {
                capacity = capacity * 2;
            }
            byte[] newData = new byte[capacity];
            System.arraycopy(data, 0, newData, 0, total);
            data = newData;
            System.arraycopy(buf, 0, data, total, nRead);
            total += nRead;
        }
        if (total == capacity) {
            return data;
        }
        byte[] trimmed = new byte[total];
        System.arraycopy(data, 0, trimmed, 0, total);
        return trimmed;
    }
}
