package com.jhotadhari.reactnative.mapsforge.vtm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * {@link HgtGrid} backed by a (typically memory-mapped, read-only) buffer of
 * big-endian shorts. Reads use absolute indexing and never touch the buffer
 * position, so concurrent reads are safe.
 */
public final class MappedHgtGrid implements HgtGrid {

	/** Heap bytes charged per mapped tile (wrapper objects only). */
	public static final long HEAP_BYTES = 1024;

	private final ShortBuffer shorts;
	private final int size;

	/**
	 * @param bytes raw HGT file contents (big-endian int16 samples). Its
	 *              position/limit define the visible region; its byte order is
	 *              ignored and replaced by BIG_ENDIAN on a duplicate.
	 */
	public MappedHgtGrid(ByteBuffer bytes) {
		ByteBuffer dup = bytes.duplicate();
		dup.order(ByteOrder.BIG_ENDIAN);
		this.shorts = dup.asShortBuffer();
		this.size = HgtInterpolator.sizeFromByteLength(dup.remaining());
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public short get(int row, int col) {
		return shorts.get(row * size + col);
	}

	@Override
	public long heapBytes() {
		return HEAP_BYTES;
	}
}
