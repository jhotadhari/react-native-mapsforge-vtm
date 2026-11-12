package com.jhotadhari.reactnative.mapsforge.vtm;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link HgtGrid} backed by one flat {@code short[size*size]} (row-major).
 * Used when a DEM file cannot be memory-mapped.
 */
public final class ArrayHgtGrid implements HgtGrid {

	private static final int SRTM3_SIZE = 1201;
	private static final int SRTM1_SIZE = 3601;
	private static final int CHUNK_BYTES = 64 * 1024;

	private final short[] data;
	private final int size;

	/** @param data row-major samples; must hold at least {@code size*size} entries. */
	public ArrayHgtGrid(short[] data, int size) {
		this.data = data;
		this.size = size;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public short get(int row, int col) {
		return data[row * size + col];
	}

	@Override
	public long heapBytes() {
		return (long) data.length * 2L;
	}

	/**
	 * Reads big-endian int16 samples from {@code in} straight into one flat
	 * short array (64 KiB scratch buffer, no doubling ladder / trimmed copy).
	 *
	 * @param expectedBytes the file length when known ({@code <= 0}: unknown;
	 *                      the array then starts at SRTM3 size and grows once to
	 *                      SRTM1 size if the stream turns out to be longer).
	 */
	public static ArrayHgtGrid read(InputStream in, long expectedBytes) throws IOException {
		boolean known = expectedBytes > 0;
		int initial;
		if (known) {
			int s = HgtInterpolator.sizeFromByteLength(expectedBytes);
			initial = s * s;
		} else {
			initial = SRTM3_SIZE * SRTM3_SIZE;
		}
		short[] data = new short[initial];
		int count = 0;
		int carry = -1; // pending high byte of a sample split across reads
		byte[] buf = new byte[CHUNK_BYTES];
		boolean full = false;
		int n;
		while (!full && (n = in.read(buf)) != -1) {
			for (int i = 0; i < n; i++) {
				int b = buf[i] & 0xff;
				if (carry < 0) {
					carry = b;
					continue;
				}
				if (count == data.length) {
					if (known || data.length >= SRTM1_SIZE * SRTM1_SIZE) {
						full = true; // ignore trailing bytes beyond the grid
						break;
					}
					short[] bigger = new short[SRTM1_SIZE * SRTM1_SIZE];
					System.arraycopy(data, 0, bigger, 0, count);
					data = bigger;
				}
				data[count++] = (short) ((carry << 8) | b);
				carry = -1;
			}
		}
		int size = (int) Math.sqrt(count);
		if (size < 1 || (known && count < data.length)) {
			throw new IOException("HGT stream too short: " + count + " samples");
		}
		return new ArrayHgtGrid(data, size);
	}
}
