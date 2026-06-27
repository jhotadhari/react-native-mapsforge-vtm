package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Handler;
import android.os.Looper;

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
					if (existingPos != null && existingPos >= add.positionIndex) {
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

		// Single updateMap for the entire batch.
		mapView.map().updateMap();

		// Notify all mutations.
		for (Mutation mut : batch) {
			mut.afterFlush(mapView);
		}

		// If more mutations arrived while we were flushing, schedule another
		// flush to process them.  Using post() (not postDelayed) ensures
		// the UI thread gets a chance to breathe between batches.
		// Explicit GC hint helps ART keep up with the CopyOnWriteArrayList
		// array churn from the incremental insertions.
		if (!pending.isEmpty()) {
			System.gc();
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
			}
		}
	}

	// ------------------------------------------------------------------
	// Accessors for other native code
	// ------------------------------------------------------------------

	/**
	 * Returns the map of all JS-managed layers currently on this map (uuid → Layer).
	 * Used by {@code LayerHelper.getLayer} / {@code getLayers} for uuid-based lookups
	 * and by {@code MapContainer.reorderLayers} for order resolution.
	 */
	public Map<String, Layer> getKnownLayers() {
		return knownLayers;
	}
}
