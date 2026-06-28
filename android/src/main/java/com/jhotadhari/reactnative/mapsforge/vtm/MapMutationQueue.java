package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Serializes all map-layer mutations onto the UI thread, with automatic batching:
 * mutations enqueued within a single frame are flushed together, and
 * {@code mapView.map().updateMap()} is called exactly once per batch.
 *
 * <p>One instance per {@code nativeNodeHandle} (i.e. per map view). Obtain via
 * {@link #get(int, MapView)} and tear down with {@link #remove(int)} (called from
 * {@code MapFragment.onDestroy}).
 *
 * <p>Thread safety: {@code enqueue*} methods are safe to call from any thread.
 * Flush always runs on the UI thread, serialized with vtm's own rendering and
 * with {@code animateTo}/{@code getPosition} (which also dispatch to the UI thread).
 *
 * <p>Position-aware insertion: {@code AddLayer} carries a {@code positionIndex}
 * (0 = first among JS-managed layers). The flush algorithm inserts each new layer
 * directly at its correct position, eliminating the need for a separate
 * {@code reorderLayers} pass after creation. When multiple layers are added in a
 * single batch, they are sorted by positionIndex before insertion so earlier
 * indices don't shift later ones.
 *
 * <h3>Threading model</h3>
 * <pre>
 *  Native Modules Thread (TurboModule)          UI Thread (Main Looper)
 *  ================================             =======================
 *
 *  createLayer (async)  ──enqueue──>  ┌─────────────────────────────┐
 *  removeLayer (async)  ──enqueue──>  │  MapMutationQueue.flush()    │
 *  reorderLayers        ──enqueue──>  │  ─────────────────────────  │
 *                                     │  1. Remove stale layers      │
 *  animateTo()          ──dispatch──> │  2. Add new layers           │
 *  getPosition()        ──dispatch──> │  3. Reorder (LIS algorithm)  │
 *                                     │  4. updateMap() once         │
 *  scheduleUpdate()     ──post─────>  │  updateMap() coalesced       │
 *  (LayerManager +       (CAS+Handler)│  (per-entry geometry changes)│
 *   MarkerLayerManager)               └─────────────────────────────┘
 * </pre>
 *
 * <p>This class is the <b>only</b> place that may call
 * {@code mapView.map().layers().add/remove} and the batch-level
 * {@code updateMap()}. Every other class must route through
 * {@link #enqueueAddLayer}, {@link #enqueueRemoveLayer},
 * {@link #enqueueReorderLayers}, or (during teardown only)
 * {@link #removeLayerSync}.
 */
public class MapMutationQueue {

	// Maximum number of mutations processed in a single flush. Capping this
	// avoids overwhelming vtm's MapRenderer with thousands of new layers in one
	// updateMap() call (which would cause OutOfMemoryError on the GL thread).
	// When more mutations are pending, another flush is posted after this one,
	// so the total work is the same but peak memory stays bounded.
	private static final int MAX_BATCH_SIZE = 25;

	private static final Map<Integer, MapMutationQueue> instances = new ConcurrentHashMap<>();

	private final int nativeNodeHandle;
	private final MapView mapView;
	private final Handler uiHandler;

	// All JS-managed layers currently on this map (uuid -> Layer). Updated only during
	// flush, after the map manipulation is complete, so it always reflects what is
	// actually on the map. Used to distinguish JS-managed from vtm-internal layers.
	private final Map<String, Layer> knownLayers = new ConcurrentHashMap<>();

	// Per-layer positionIndex, populated on add and used during incremental insertion
	// so we can find where each new layer belongs relative to existing JS-managed layers.
	private final Map<String, Integer> positionByUuid = new ConcurrentHashMap<>();

	// Tracks which uuids were part of the last reorder, so a layer that's genuinely new
	// to the tracked set can receive CLEAR_EVENT (to (re-)schedule its own tile jobs)
	// without broadcasting a map-wide clear that would flash already-loaded tile layers.
	private Set<String> previouslyReorderedUuids = new HashSet<>();

	// Pending mutations, drained by flush() on the UI thread.
	private final ConcurrentLinkedQueue<Mutation> pending = new ConcurrentLinkedQueue<>();
	private volatile boolean flushScheduled = false;

	// ------------------------------------------------------------------
	// Mutation types
	// ------------------------------------------------------------------

	private interface Mutation {
		/** Called after the batch has been fully applied to the map. */
		void afterFlush(MapView mapView);
	}

	private static final class AddLayer implements Mutation {
		final Layer layer;
		final String uuid;
		final int positionIndex; // Among JS-managed layers, 0 = first
		final CompletableFuture<String> future;

		AddLayer(Layer layer, String uuid, int positionIndex, CompletableFuture<String> future) {
			this.layer = layer;
			this.uuid = uuid;
			this.positionIndex = positionIndex;
			this.future = future;
		}

		@Override
		public void afterFlush(MapView mapView) {
			future.complete(uuid);
		}
	}

	private static final class RemoveLayer implements Mutation {
		final String uuid;
		final CompletableFuture<Void> future;

		RemoveLayer(String uuid, CompletableFuture<Void> future) {
			this.uuid = uuid;
			this.future = future;
		}

		@Override
		public void afterFlush(MapView mapView) {
			future.complete(null);
		}
	}

	private static final class ReorderLayers implements Mutation {
		final List<String> orderedLayerUuids;
		final CompletableFuture<Void> future;

		ReorderLayers(List<String> orderedLayerUuids, CompletableFuture<Void> future) {
			this.orderedLayerUuids = orderedLayerUuids;
			this.future = future;
		}

		@Override
		public void afterFlush(MapView mapView) {
			future.complete(null);
		}
	}

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

	private MapMutationQueue(int nativeNodeHandle, MapView mapView) {
		this.nativeNodeHandle = nativeNodeHandle;
		this.mapView = mapView;
		this.uiHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Returns the singleton queue for {@code nativeNodeHandle}, creating one if needed.
	 */
	public static MapMutationQueue get(int nativeNodeHandle, MapView mapView) {
		return instances.computeIfAbsent(nativeNodeHandle, k -> new MapMutationQueue(nativeNodeHandle, mapView));
	}

	/**
	 * Returns the existing queue for {@code nativeNodeHandle}, or null if it doesn't
	 * exist yet. Used for layer lookups where we don't have a MapView reference handy.
	 */
	public static MapMutationQueue getInstance(int nativeNodeHandle) {
		return instances.get(nativeNodeHandle);
	}

	/**
	 * Tears down the queue for {@code nativeNodeHandle}. Rejects all pending futures.
	 * Called from {@code MapFragment.onDestroy}.
	 */
	public static void remove(int nativeNodeHandle) {
		MapMutationQueue queue = instances.remove(nativeNodeHandle);
		if (queue != null) {
			queue.destroy();
		}
	}

	private void destroy() {
		RuntimeException e = new RuntimeException("MapMutationQueue destroyed (map fragment torn down)");
		for (Mutation m : pending) {
			if (m instanceof AddLayer) {
				((AddLayer) m).future.completeExceptionally(e);
			} else if (m instanceof RemoveLayer) {
				((RemoveLayer) m).future.completeExceptionally(e);
			}
		}
		pending.clear();
		flushScheduled = false;
		knownLayers.clear();
		previouslyReorderedUuids.clear();
	}

	// ------------------------------------------------------------------
	// Public enqueue API – safe to call from any thread
	// ------------------------------------------------------------------

	public CompletableFuture<String> enqueueAddLayer(Layer layer, String uuid, int positionIndex) {
		CompletableFuture<String> future = new CompletableFuture<>();
		pending.add(new AddLayer(layer, uuid, positionIndex, future));
		scheduleFlush();
		return future;
	}

	public CompletableFuture<Void> enqueueRemoveLayer(String uuid) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		pending.add(new RemoveLayer(uuid, future));
		scheduleFlush();
		return future;
	}

	public CompletableFuture<Void> enqueueReorderLayers(List<String> orderedLayerUuids) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		pending.add(new ReorderLayers(orderedLayerUuids, future));
		scheduleFlush();
		return future;
	}

	// ------------------------------------------------------------------
	// Flush scheduling
	// ------------------------------------------------------------------

	private void scheduleFlush() {
		if (flushScheduled) {
			return;
		}
		flushScheduled = true;
		uiHandler.post(this::flush);
	}

	// ------------------------------------------------------------------
	// Flush – always runs on UI thread
	// ------------------------------------------------------------------

	private void flush() {
		flushScheduled = false;

		// Drain pending into a batch, capping at MAX_BATCH_SIZE so vtm's
		// MapRenderer isn't overwhelmed by thousands of layers in a single
		// updateMap() call.  If more remain, another flush is posted below.
		List<Mutation> batch = new ArrayList<>();
		Mutation m;
		while ((m = pending.poll()) != null && batch.size() < MAX_BATCH_SIZE) {
			batch.add(m);
		}
		if (batch.isEmpty()) {
			return;
		}

		// Verify the mapView is still alive.
		if (mapView == null || mapView.map() == null) {
			rejectAll(batch, new RuntimeException("MapView is null or destroyed"));
			return;
		}

		// Separate into adds and removals.
		List<AddLayer> adds = new ArrayList<>();
		Set<String> removeUuids = new HashSet<>();
		for (Mutation mut : batch) {
			if (mut instanceof AddLayer) {
				adds.add((AddLayer) mut);
			} else if (mut instanceof RemoveLayer) {
				removeUuids.add(((RemoveLayer) mut).uuid);
			}
		}

		// --- Step 1: remove layers that are being torn down ---
		if (!removeUuids.isEmpty()) {
			Map<Layer, String> layerToUuid = new HashMap<>();
			for (Map.Entry<String, Layer> entry : knownLayers.entrySet()) {
				layerToUuid.put(entry.getValue(), entry.getKey());
			}
			int mapSize = mapView.map().layers().size();
			for (int i = mapSize - 1; i >= 0; i--) {
				Layer l = mapView.map().layers().get(i);
				String uuid = layerToUuid.get(l);
				if (uuid != null && removeUuids.contains(uuid)) {
					mapView.map().layers().remove(i);
				}
			}
			for (String uuid : removeUuids) {
				knownLayers.remove(uuid);
				positionByUuid.remove(uuid);
			}
		}

		// --- Step 2: incremental insertion (not "remove-all re-add-all") ---
		// Existing JS layers stay on the map; new layers are inserted at their
		// positionIndex among JS-managed layers.  We sort ASCENDING so earlier
		// positions don't shift later ones, and maintain a running layerToUuid
		// / knownSet that includes layers added earlier in this batch.
		adds.sort(Comparator.comparingInt(a -> a.positionIndex));

		Map<Layer, String> layerToUuid = new HashMap<>();
		Set<Layer> knownSet = new HashSet<>();
		for (Map.Entry<String, Layer> entry : knownLayers.entrySet()) {
			layerToUuid.put(entry.getValue(), entry.getKey());
			knownSet.add(entry.getValue());
		}

		int scanFrom = 0;
		for (AddLayer add : adds) {
			int insertAt = mapView.map().layers().size(); // default: append
			int mapLen = mapView.map().layers().size();
			for (int i = scanFrom; i < mapLen; i++) {
				Layer l = mapView.map().layers().get(i);
				String existingUuid = layerToUuid.get(l);
				if (existingUuid != null) {
					Integer existingPos = positionByUuid.get(existingUuid);
					// positionByUuid may not yet have entries for layers added in
					// the very first flush for this instance; treat missing as -1
					// so the scan doesn't stop prematurely.
					if (existingPos != null && existingPos > add.positionIndex) {
						insertAt = i;
						break;
					}
				}
			}
			mapView.map().layers().add(insertAt, add.layer);
			knownLayers.put(add.uuid, add.layer);
			positionByUuid.put(add.uuid, add.positionIndex);
			layerToUuid.put(add.layer, add.uuid);
			knownSet.add(add.layer);
			scanFrom = insertAt; // next add goes at or after this point
		}

		// --- Step 3: reorder layers ---
		// Run after adds/removals so the reorder sees the correct post-mutation state.
		// Multiple ReorderLayers in a single batch are all applied; the last one wins,
		// which matches JS-side semantics (debounced scheduleSync sends the latest order).
		for (Mutation mut : batch) {
			if (mut instanceof ReorderLayers) {
				ReorderLayers reorder = (ReorderLayers) mut;
				List<Layer> orderedLayers = new ArrayList<>();
				for (String uuid : reorder.orderedLayerUuids) {
					Layer layer = knownLayers.get(uuid);
					if (layer != null && mapView.map().layers().contains(layer)) {
						orderedLayers.add(layer);
					}
				}
				if (!orderedLayers.isEmpty()) {
					// Send CLEAR_EVENT to layers new to this ordered set.
					for (Layer layer : orderedLayers) {
						String layerUuid = getLayerUuidForLayer(layer);
						if (layerUuid != null
							&& !previouslyReorderedUuids.contains(layerUuid)
							&& layer instanceof org.oscim.map.Map.UpdateListener) {
							((org.oscim.map.Map.UpdateListener) layer)
								.onMapEvent(org.oscim.map.Map.CLEAR_EVENT,
									mapView.map().getMapPosition());
						}
					}
					previouslyReorderedUuids.clear();
					previouslyReorderedUuids.addAll(reorder.orderedLayerUuids);

					reorderMinimalMoves(mapView, orderedLayers);
				}
			}
		}

		// Single updateMap for the entire batch.
		mapView.map().updateMap();

		// Notify all mutations.
		for (Mutation mut : batch) {
			mut.afterFlush(mapView);
		}

		// If more mutations arrived while we were flushing, schedule another
		// flush to process them.  Using post() (not postDelayed) ensures
		// the UI thread gets a chance to breathe between batches.
		if (!pending.isEmpty()) {
			flushScheduled = true;
			uiHandler.post(this::flush);
		}
	}

	private static void rejectAll(List<Mutation> batch, RuntimeException e) {
		for (Mutation mut : batch) {
			if (mut instanceof AddLayer) {
				((AddLayer) mut).future.completeExceptionally(e);
			} else if (mut instanceof RemoveLayer) {
				((RemoveLayer) mut).future.completeExceptionally(e);
			} else if (mut instanceof ReorderLayers) {
				((ReorderLayers) mut).future.completeExceptionally(e);
			}
		}
	}

	// ------------------------------------------------------------------
	// Accessors for other native code
	// ------------------------------------------------------------------

	/**
	 * Returns the uuid for a given Layer by reverse-searching {@link #knownLayers}.
	 */
	@Nullable
	private String getLayerUuidForLayer(Layer layer) {
		for (Map.Entry<String, Layer> entry : knownLayers.entrySet()) {
			if (entry.getValue() == layer) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Reorders mapView's layers to match orderedLayers using the minimum number of
	 * remove+add moves. mapView.map().layers() is backed by a CopyOnWriteArrayList
	 * where every add/remove/contains is O(n) — touching only out-of-place layers
	 * keeps this O(n) instead of O(n²) per reorder call.
	 */
	private static void reorderMinimalMoves(MapView mapView, List<Layer> orderedLayers) {
		int n = orderedLayers.size();
		if (n == 0) {
			return;
		}

		// Snapshot which of the map's current layers are part of the target set.
		Set<Layer> orderedSet = new HashSet<>(orderedLayers);
		List<Layer> trackedCurrent = new ArrayList<>(n);
		int currentSize = mapView.map().layers().size();
		for (int i = 0; i < currentSize; i++) {
			Layer layer = mapView.map().layers().get(i);
			if (orderedSet.contains(layer)) {
				trackedCurrent.add(layer);
			}
		}

		Map<Layer, Integer> posInTrackedCurrent = new HashMap<>();
		for (int i = 0; i < trackedCurrent.size(); i++) {
			posInTrackedCurrent.put(trackedCurrent.get(i), i);
		}

		// values[i] = where orderedLayers.get(i) currently sits within trackedCurrent.
		// Longest increasing run = layers that don't need to move.
		int[] values = new int[n];
		for (int i = 0; i < n; i++) {
			values[i] = posInTrackedCurrent.get(orderedLayers.get(i));
		}

		boolean[] keep = longestIncreasingSubsequenceMask(values);

		Layer afterLayer = null;
		for (int i = 0; i < n; i++) {
			Layer layer = orderedLayers.get(i);
			if (keep[i]) {
				afterLayer = layer;
				continue;
			}
			mapView.map().layers().remove(layer);
			int index = null == afterLayer ? 0 : mapView.map().layers().indexOf(afterLayer) + 1;
			mapView.map().layers().add(index, layer);
			afterLayer = layer;
		}
	}

	/**
	 * Standard O(n log n) patience-sorting longest increasing subsequence, returning
	 * which indices of {@code values} belong to one such strictly increasing subsequence.
	 */
	private static boolean[] longestIncreasingSubsequenceMask(int[] values) {
		int n = values.length;
		int[] tails = new int[n];
		int[] predecessors = new int[n];
		int len = 0;
		for (int i = 0; i < n; i++) {
			int lo = 0, hi = len;
			while (lo < hi) {
				int mid = (lo + hi) / 2;
				if (values[tails[mid]] < values[i]) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			predecessors[i] = lo > 0 ? tails[lo - 1] : -1;
			tails[lo] = i;
			if (lo == len) {
				len++;
			}
		}
		boolean[] keep = new boolean[n];
		int k = len == 0 ? -1 : tails[len - 1];
		while (k >= 0) {
			keep[k] = true;
			k = predecessors[k];
		}
		return keep;
	}

	/**
	 * Synchronously removes a layer from the map, bypassing the async queue.
	 * Must only be called from the UI thread (e.g., during Fragment.onDestroy)
	 * when the async flush may never run because the map is being torn down.
	 * Updates knownLayers and positionByUuid to match.
	 */
	public void removeLayerSync(String uuid) {
		Layer layer = knownLayers.remove(uuid);
		if (layer != null && mapView.map() != null) {
			mapView.map().layers().remove(layer);
		}
		positionByUuid.remove(uuid);
	}

	/**
	 * Returns the map of all JS-managed layers currently on this map (uuid → Layer).
	 * Used by {@code LayerHelper.getLayer} / {@code getLayers} for uuid-based lookups
	 * and by {@code MapContainer.reorderLayers} for order resolution.
	 */
	public Map<String, Layer> getKnownLayers() {
		return knownLayers;
	}
}
