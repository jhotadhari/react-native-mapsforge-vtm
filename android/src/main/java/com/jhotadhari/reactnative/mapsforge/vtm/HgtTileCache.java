package com.jhotadhari.reactnative.mapsforge.vtm;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache of {@link HgtGrid}s with two bounds, both enforced after every put:
 *
 * <ul>
 *   <li><b>tile count</b> ({@link #resize}) — bounds mapped address space /
 *       page-cache footprint;</li>
 *   <li><b>Java-heap bytes</b> ({@code heapBudgetBytes}, summing
 *       {@link HgtGrid#heapBytes()}) — memory-mapped tiles are charged a tiny
 *       constant, array-backed fallback tiles their real size.</li>
 * </ul>
 *
 * The most recently inserted entry is never evicted by the byte budget (a
 * single oversized tile is still cached). All methods are synchronized.
 * Pure Java.
 */
public final class HgtTileCache {

	private final LinkedHashMap<String, HgtGrid> map = new LinkedHashMap<>(16, 0.75f, true);
	private int maxTiles;
	private final long heapBudgetBytes;
	private long heapBytes = 0;

	public HgtTileCache(int maxTiles, long heapBudgetBytes) {
		this.maxTiles = Math.max(1, maxTiles);
		this.heapBudgetBytes = heapBudgetBytes;
	}

	/** Returns the grid (marking it most-recently-used) or {@code null}. */
	public synchronized HgtGrid get(String key) {
		return map.get(key);
	}

	public synchronized void put(String key, HgtGrid grid) {
		HgtGrid old = map.put(key, grid);
		if (old != null) {
			heapBytes -= old.heapBytes();
		}
		heapBytes += grid.heapBytes();
		trim();
	}

	/** Sets the maximum tile count (clamped to ≥ 1) and evicts LRU entries to fit. */
	public synchronized void resize(int maxTiles) {
		this.maxTiles = Math.max(1, maxTiles);
		trim();
	}

	/** Evicts up to {@code n} least-recently-used entries. */
	public synchronized void evictOldest(int n) {
		Iterator<Map.Entry<String, HgtGrid>> it = map.entrySet().iterator();
		while (n-- > 0 && it.hasNext()) {
			heapBytes -= it.next().getValue().heapBytes();
			it.remove();
		}
	}

	public synchronized void evictAll() {
		map.clear();
		heapBytes = 0;
	}

	public synchronized int size() {
		return map.size();
	}

	public synchronized long heapBytes() {
		return heapBytes;
	}

	private void trim() {
		Iterator<Map.Entry<String, HgtGrid>> it = map.entrySet().iterator();
		while (it.hasNext() && map.size() > 1
				&& (map.size() > maxTiles || heapBytes > heapBudgetBytes)) {
			heapBytes -= it.next().getValue().heapBytes();
			it.remove();
		}
	}
}
