package com.jhotadhari.reactnative.mapsforge.vtm;

/**
 * Bilinear interpolation within a single HGT tile grid. Pure Java.
 */
public final class HgtInterpolator {

	/**
	 * SRTM void / no-data sentinel. Grid cells with this value represent ocean or
	 * missing data and must never be treated as a real elevation.
	 */
	public static final short SRTM_VOID = Short.MIN_VALUE; // -32768

	private HgtInterpolator() {}

	/** Grid edge length for a raw HGT file of {@code byteLength} bytes. */
	public static int sizeFromByteLength(long byteLength) {
		return (int) Math.sqrt(byteLength / 2.0);
	}

	/**
	 * @param data the tile elevation grid (row 0 = north edge, last row = south edge).
	 * @param lat  latitude in degrees.
	 * @param lng  longitude in degrees.
	 * @return interpolated elevation in metres, or {@code null} if any corner is void.
	 */
	public static Short interpolate(HgtGrid data, double lat, double lng) {
		int tileLength = data.size();
		if (tileLength == 0) {
			return null;
		}

		// Degenerate / 1×1 tile.
		if (tileLength <= 1) {
			short val = data.get(0, 0);
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
			short val = data.get(rowNorth, colWest);
			return val == SRTM_VOID ? null : val;
		}

		// Degenerate case: one dimension collapsed (point on a grid line).
		if (rowNorth == rowSouth) {
			short w = data.get(rowNorth, colWest);
			short e = data.get(rowNorth, colEast);
			if (w == SRTM_VOID || e == SRTM_VOID) return null;
			return (short) Math.round(w * (1.0 - dx) + e * dx);
		}
		if (colWest == colEast) {
			short n = data.get(rowNorth, colWest);
			short s = data.get(rowSouth, colWest);
			if (n == SRTM_VOID || s == SRTM_VOID) return null;
			return (short) Math.round(n * (1.0 - dy) + s * dy);
		}

		// Four corner values.
		short fNW = data.get(rowNorth, colWest);
		short fNE = data.get(rowNorth, colEast);
		short fSW = data.get(rowSouth, colWest);
		short fSE = data.get(rowSouth, colEast);

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
}
