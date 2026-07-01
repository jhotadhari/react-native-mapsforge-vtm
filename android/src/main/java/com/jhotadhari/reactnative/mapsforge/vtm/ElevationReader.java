package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.LruCache;

import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads SRTM HGT files to provide point-elevation queries.
 *
 * Much thinner than the old {@code HgtReader} (which was a ~530-line copy of mapsforge's
 * {@code HgtCache} + JOSM's {@code SRTMTile} + JOSM's {@code HgtReader}):
 * <ul>
 *   <li>No thread pool — construction-time directory scan is fast enough for typical
 *       SRTM directories (runs off the UI thread).</li>
 *   <li>No rate limiter — queries are explicit user actions or per-frame cache hits,
 *       not automated file I/O.</li>
 *   <li>No manual purge — Android's {@link LruCache} handles memory.</li>
 * </ul>
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
     * Returns the elevation in metres at the given coordinate, or {@code null} if no
     * data covers that position or the position falls in a void (ocean) area.
     *
     * <p>This method is thread-safe: the LruCache and fileIndex accesses are
     * synchronized, and the DemFile reads are single-threaded per tile.</p>
     */
    public Short getElevation(double lng, double lat) {
        String filename = tileFilename(lat, lng);
        DemFile file;
        synchronized (fileIndex) {
            file = fileIndex.get(filename);
        }
        if (file == null) {
            return null;
        }

        short[][] data;
        // Only hold the lock during cache access, not during file I/O.
        synchronized (dataCache) {
            data = dataCache.get(filename);
        }
        if (data == null) {
            try {
                data = readHgtFile(file);
            } catch (IOException e) {
                return null;
            }
            if (data != null) {
                synchronized (dataCache) {
                    dataCache.put(filename, data);
                }
            }
        }

        if (data == null || data.length == 0) {
            return null;
        }

        int tileLength = data.length;
        // Guard against degenerate / 1×1 tiles.
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

    /** Releases cached tile data and clears the file index. */
    public void close() {
        synchronized (dataCache) {
            dataCache.evictAll();
        }
        synchronized (fileIndex) {
            fileIndex.clear();
        }
    }

    // ---- internal helpers -------------------------------------------------------

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
     * Preserves the same parsing logic as the old {@code HgtFileInfo.readHgtFile()}.
     */
    private static short[][] readHgtFile(DemFile file) throws IOException {
        byte[] bytes;
        try (InputStream fis = file.asStream()) {
            bytes = readAllBytes(fis);
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
