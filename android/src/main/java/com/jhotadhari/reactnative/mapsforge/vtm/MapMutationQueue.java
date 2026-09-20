package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.ArrayList;
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
 * <p>New layers are appended; their final position comes from the absolute
 * plan applied in the same flush ({@link LayerStackController#applyPlan}).
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
 *  animateTo()          ──dispatch──> │  2. Add new layers (append)  │
 *  getPosition()        ──dispatch──> │  3. Apply absolute plan (LIS)│
 *                                     │  4. updateMap() once         │
 *  scheduleUpdate()     ──post─────>  │  updateMap() coalesced       │
 *  (LayerManager +       (CAS+Handler)│  (per-entry geometry changes)│
 *   MarkerLayerManager)               └─────────────────────────────┘
 * </pre>
 *
 * <p>This class is the <b>only</b> place that may call
 * {@code mapView.map().layers().add/remove} and the batch-level
 * {@code updateMap()} — the layer-stack state and plan application live in
 * {@link LayerStackController}, driven exclusively from {@link #flush()}.
 * Every other class must route through
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

	/** Owns the JS-managed layer-stack state and applies absolute plans. */
	private final LayerStackController controller;

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
		final CompletableFuture<String> future;

		AddLayer(Layer layer, String uuid, CompletableFuture<String> future) {
			this.layer = layer;
			this.uuid = uuid;
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
		this.controller = new LayerStackController(mapView);
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
			} else if (m instanceof ReorderLayers) {
				((ReorderLayers) m).future.completeExceptionally(e);
			}
		}
		pending.clear();
		flushScheduled = false;
		controller.clear();
	}

	// ------------------------------------------------------------------
	// Public enqueue API – safe to call from any thread
	// ------------------------------------------------------------------

	public CompletableFuture<String> enqueueAddLayer(Layer layer, String uuid) {
		CompletableFuture<String> future = new CompletableFuture<>();
		pending.add(new AddLayer(layer, uuid, future));
		scheduleFlush();
		return future;
	}

	/**
	 * Enqueues removal of a layer from the map.
	 *
	 * <p>Called from {@link LayerHelper#removeLayerAsync} which serves two paths:
	 * <ol>
	 *   <li>Teardown of dedicated layers (e.g. {@code LayerPathJts}) — one native
	 *       layer per JS component, not shared-layer managed.</li>
	 *   <li>The deprecated synchronous {@code LayerHelper.removeLayer} (now
	 *       delegates to this async path).</li>
	 * </ol>
	 *
	 * <p>Shared-layer managers ({@code LayerManager} subclasses) do NOT use this
	 * method for per-entry removal; they call {@link #removeLayerSync} only during
	 * full manager teardown.
	 */
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
			controller.removeAll(removeUuids);
		}

		// --- Step 2: add new layers (append) ---
		// Order is the plan's responsibility — the flush applies the
		// absolute plan right after the adds, so new layers never render
		// at a wrong position beyond this single batch.
		for (AddLayer add : adds) {
			mapView.map().layers().add(add.layer);
			controller.register(add.layer, add.uuid);
		}

		// --- Step 3: apply the absolute plan ---
		// Run after adds/removals so the plan sees the correct post-mutation
		// state. Multiple plans in a single batch are all applied; the last
		// one wins, which matches JS-side semantics (debounced scheduleSync
		// sends the latest order).
		for (Mutation mut : batch) {
			if (mut instanceof ReorderLayers) {
				controller.applyPlan(((ReorderLayers) mut).orderedLayerUuids);
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
	 * Synchronously removes a layer from the map, bypassing the async queue.
	 * Must only be called from the UI thread (e.g., during Fragment.onDestroy)
	 * when the async flush may never run because the map is being torn down.
	 */
	public void removeLayerSync(String uuid) {
		controller.removeLayerSync(uuid);
	}

	/**
	 * Returns the map of all JS-managed layers currently on this map (uuid → Layer).
	 * Used by {@code LayerHelper.getLayer} / {@code getLayers} for uuid-based lookups
	 * and by {@code MapContainer.reorderLayers} for order resolution.
	 */
	public Map<String, Layer> getKnownLayers() {
		return controller.getKnownLayers();
	}

	/**
	 * Result of the last plan self-check, or null when no plan was applied yet.
	 */
	@Nullable
	public LayerStackController.VerifyResult getLastVerifyResult() {
		return controller.getLastVerifyResult();
	}

	/**
	 * Returns the number of mutations currently pending in the queue.
	 * Used by {@code MapContainer.getDebugLayerDump} for diagnostic reporting.
	 */
	public int getPendingCount() {
		return pending.size();
	}
}
