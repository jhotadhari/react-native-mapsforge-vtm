package com.jhotadhari.reactnative.mapsforge.vtm;

/**
 * Read-only square elevation grid of one SRTM HGT tile (row 0 = north edge).
 *
 * <p>Pure Java (no Android dependencies) so implementations and the
 * interpolation can be tested on the host JVM. Implementations must be
 * thread-safe for concurrent reads.</p>
 */
public interface HgtGrid {

	/** Number of rows == number of columns (1201 for SRTM3, 3601 for SRTM1). */
	int size();

	/** Raw value at (row, col), {@link Short#MIN_VALUE} for void cells. */
	short get(int row, int col);

	/**
	 * Approximate number of Java-heap bytes this grid retains. Memory-mapped
	 * grids return a small constant (the data lives in the page cache, not on
	 * the Java heap); array-backed grids return their real array size.
	 */
	long heapBytes();
}
