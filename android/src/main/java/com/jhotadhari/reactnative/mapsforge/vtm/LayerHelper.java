package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReadableMap;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LayerHelper {

	protected final ReactContextBaseJavaModule module;
	protected final ReactApplicationContext reactContext;

	// Shared registry across all layer-type modules (LayerMarker, LayerPath,
	// LayerBitmapTile, etc.), each of which owns its own LayerHelper instance.
	// Maps nativeNodeHandle -> uuid -> Layer for uuid-based lookups.
	// Populated by MapMutationQueue after successful add/remove; reads are
	// delegated to MapMutationQueue.getKnownLayers().
	public LayerHelper( ReactContextBaseJavaModule module, ReactApplicationContext reactContext ) {
		this.module = module;
		this.reactContext = reactContext;
	}

	// ------------------------------------------------------------------
	// Layer lookup (still static — shared across all helper instances)
	// ------------------------------------------------------------------

	public static Layer getLayer( int nativeNodeHandle, String uuid ) {
		MapMutationQueue queue = MapMutationQueue.getInstance(nativeNodeHandle);
		if (null == queue) {
			return null;
		}
		return queue.getKnownLayers().get(uuid);
	}

	public Map<String, Layer> getLayers( int nativeNodeHandle ) {
		MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
		if (null == mapView) {
			return null;
		}
		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);
		return queue.getKnownLayers();
	}

	// ------------------------------------------------------------------
	// Async add/remove via MapMutationQueue
	// ------------------------------------------------------------------

	/**
	 * Enqueues a layer for addition at the end of the JS-managed layer block.
	 * The returned future resolves with the uuid once the layer is on the map.
	 *
	 * <p>The caller is responsible for generating the uuid (or passing null to
	 * have one generated). All geometry setup should be done before this call
	 * or chained on the returned future — the layer only needs to be on the map
	 * by the time the JS promise resolves.
	 */
	public CompletableFuture<String> addLayerAsync(Layer layer, ReadableMap params, String uuid) {
		if (!Utils.rMapHasKey(params, "nativeNodeHandle")) {
			CompletableFuture<String> f = new CompletableFuture<>();
			f.completeExceptionally(new IllegalArgumentException("Missing nativeNodeHandle"));
			return f;
		}
		int nativeNodeHandle = params.getInt("nativeNodeHandle");
		MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
		if (null == mapView) {
			CompletableFuture<String> f = new CompletableFuture<>();
			f.completeExceptionally(new RuntimeException("Unable to find mapView"));
			return f;
		}

		String resolvedUuid = uuid != null ? uuid : UUID.randomUUID().toString();

		// Position: if the caller provides a positionIndex, use it; otherwise append.
		int positionIndex = Utils.rMapHasKey(params, "positionIndex")
			? params.getInt("positionIndex")
			: Integer.MAX_VALUE; // append

		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);
		CompletableFuture<String> future = queue.enqueueAddLayer(layer, resolvedUuid, positionIndex);

		return future;
	}

	public CompletableFuture<String> addLayerAsync(Layer layer, ReadableMap params) {
		return addLayerAsync(layer, params, UUID.randomUUID().toString());
	}

	/**
	 * Enqueues a layer for removal. The returned future resolves once the layer
	 * is off the map.
	 */
	public CompletableFuture<Void> removeLayerAsync(ReadableMap params) {
		if (!Utils.rMapHasKey(params, "uuid") || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
			CompletableFuture<Void> f = new CompletableFuture<>();
			f.completeExceptionally(new IllegalArgumentException("Missing uuid or nativeNodeHandle"));
			return f;
		}

		int nativeNodeHandle = params.getInt("nativeNodeHandle");
		String uuid = params.getString("uuid");
		MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
		if (null == mapView) {
			CompletableFuture<Void> f = new CompletableFuture<>();
			f.completeExceptionally(new RuntimeException("Unable to find mapView"));
			return f;
		}

		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);
		return queue.enqueueRemoveLayer(uuid);
	}

	// ------------------------------------------------------------------
	// Legacy synchronous API (backward compat during migration)
	// ------------------------------------------------------------------

	/**
	 * @deprecated Use {@link #addLayerAsync} instead. This method still directly
	 *             mutates the map on the calling thread for backward compatibility
	 *             with callers that haven't been migrated to the async API yet.
	 */
	@Deprecated
	public String addLayer(Layer layer, ReadableMap params, String uuid) {
		if (!Utils.rMapHasKey(params, "nativeNodeHandle")) { return null; }
		int nativeNodeHandle = params.getInt("nativeNodeHandle");
		MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
		if (null == mapView) { return null; }

		String resolvedUuid = uuid != null ? uuid : UUID.randomUUID().toString();

		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);

		// Position-aware insertion: if the caller provides a positionIndex, insert
		// the layer at that position among JS-managed layers instead of appending.
		// This eliminates the need for a follow-up reorderLayers call to fix the
		// order — the layer lands in its correct position from the start.
		int positionIndex = Utils.rMapHasKey(params, "positionIndex")
			? params.getInt("positionIndex")
			: -1;

		if (positionIndex >= 0) {
			// Count JS-managed layers among current map layers to find the absolute
			// insertion point corresponding to positionIndex.
			// Build a HashSet once so the inner loop uses O(1) contains(),
			// avoiding the O(n) ConcurrentHashMap.containsValue() scan per
			// iteration (which makes this loop O(n²) per add → O(n³) overall).
			java.util.Set<org.oscim.layers.Layer> knownSet =
				new java.util.HashSet<>(queue.getKnownLayers().values());
			int mapSize = mapView.map().layers().size();
			int jsCount = 0;
			int insertAt = mapSize; // default: append
			for (int i = 0; i < mapSize; i++) {
				org.oscim.layers.Layer l = mapView.map().layers().get(i);
				if (knownSet.contains(l)) {
					if (jsCount == positionIndex) {
						insertAt = i;
						break;
					}
					jsCount++;
				}
			}
			mapView.map().layers().add(insertAt, layer);
		} else {
			// Legacy behavior: unconditional append.
			mapView.map().layers().add(layer);
		}

		mapView.map().updateMap();
		queue.getKnownLayers().put(resolvedUuid, layer);

		return resolvedUuid;
	}

	/**
	 * @deprecated Use {@link #addLayerAsync} instead.
	 */
	@Deprecated
	public String addLayer(Layer layer, ReadableMap params) {
		String uuid = UUID.randomUUID().toString();
		return addLayer(layer, params, uuid);
	}

	/**
	 * @deprecated Use {@link #removeLayerAsync} instead.
	 */
	@Deprecated
	public void removeLayer(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "uuid") || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
				Utils.promiseReject(promise, "Undefined uuid or nativeNodeHandle"); return;
			}

			int nativeNodeHandle = params.getInt("nativeNodeHandle");
			String uuid = params.getString("uuid");

			MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
			if (null == mapView) {
				Utils.promiseReject(promise, "Unable to find mapView"); return;
			}

			MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);

			// Remove layer from map.
			int layerIndex = getLayerIndexInMapLayers(nativeNodeHandle, uuid);
			if (layerIndex != -1) {
				mapView.map().layers().remove(layerIndex);
			}

			// Remove from known layers.
			queue.getKnownLayers().remove(uuid);

			// Trigger map update.
			mapView.map().updateMap();

			promise.resolve(uuid);
		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	protected int getLayerIndexInMapLayers(int nativeNodeHandle, String uuid) {
		MapView mapView = Utils.getMapView(reactContext, nativeNodeHandle);
		if (null == mapView) {
			return -1;
		}

		Layer layer = getLayer(nativeNodeHandle, uuid);
		if (null == layer) {
			return -1;
		}

		return mapView.map().layers().indexOf(layer);
	}
}
