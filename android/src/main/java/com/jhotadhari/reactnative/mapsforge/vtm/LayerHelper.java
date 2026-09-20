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

		// The absolute target order (bottom → top) the JS side sends with the
		// create — applied atomically with the add so the layer lands at its
		// final position in the same flush.
		java.util.List<String> desiredOrder = Utils.rMapGetStringList(params, "layerUuids");

		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);
		CompletableFuture<String> future = queue.enqueueAddLayer(layer, resolvedUuid, desiredOrder);

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

	/**
	 * Async removal with promise plumbing: resolves with the removed uuid,
	 * rejects with the failure message.
	 */
	public void removeLayerResolving( ReadableMap params, Promise promise ) {
		try {
			String uuid = params.getString( "uuid" );
			removeLayerAsync( params )
				.thenRun( () -> promise.resolve( uuid ) )
				.exceptionally( t -> {
					Utils.promiseReject( promise, t.getMessage() );
					return null;
				} );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}
}
