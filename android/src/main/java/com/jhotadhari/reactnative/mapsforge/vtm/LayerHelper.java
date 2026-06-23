package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReadableMap;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LayerHelper {

	protected final ReactContextBaseJavaModule module;

	protected final ReactApplicationContext reactContext;

	// Shared nativeNodeHandle -> uuid -> Layer registry across all layer-type modules
	// (LayerMarker, LayerPath, LayerBitmapTile), each of which owns its own LayerHelper
	// instance. Shared because reordering (MapContainer.reorderLayers) is a whole-map
	// concern that has to resolve uuids regardless of which module created the layer, since
	// all layer types share one flat native layer list.
	private static final Map<Integer, Map<String, Layer>> layersByHandle = new ConcurrentHashMap<>();

	public LayerHelper( ReactContextBaseJavaModule module, ReactApplicationContext reactContext ) {
		this.module = module;
		this.reactContext = reactContext;
	}

	public static Layer getLayer( int nativeNodeHandle, String uuid ) {
		Map<String, Layer> layers = layersByHandle.get( nativeNodeHandle );
		return null == layers ? null : layers.get( uuid );
	}

	public Map<String, Layer> getLayers( int nativeNodeHandle ) {
		return layersByHandle.computeIfAbsent( nativeNodeHandle, k -> new HashMap<>() );
	}

	public String addLayer( Layer layer, ReadableMap params, String uuid ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) { return null; }
		int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
		MapView mapView = Utils.getMapView( reactContext, nativeNodeHandle );
		if ( null == mapView ) { return null; }

		// Append layer to map. Final position among JS-managed layers is established
		// separately and continuously by MapContainer.reorderLayers, not at creation time.
		mapView.map().layers().add( layer );

		// Trigger update map.
		mapView.map().updateMap();
		getLayers( nativeNodeHandle ).put( uuid, layer );

		return uuid;
	}

	public String addLayer( Layer layer, ReadableMap params ) {
		String uuid = UUID.randomUUID().toString();
		return addLayer( layer, params, uuid );
	}

	public void removeLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "uuid" ) || ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined uuid or nativeNodeHandle" ); return;
			}

			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			String uuid = params.getString( "uuid" );


			MapView mapView = Utils.getMapView( reactContext, nativeNodeHandle );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			// Remove layer from map.
			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( layerIndex != -1 ) {
				mapView.map().layers().remove( layerIndex );
			}

			// Remove layer from layers.
			getLayers( nativeNodeHandle ).remove( uuid );

			// Trigger map update.
			mapView.map().updateMap();

			// Resolve uuid
			promise.resolve( uuid );
		} catch( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	protected int getLayerIndexInMapLayers(
		int nativeNodeHandle,
		String uuid
	) {
		MapView mapView = Utils.getMapView( reactContext, nativeNodeHandle );
		if ( null == mapView ) {
			return -1;
		}

		Layer layer = getLayer( nativeNodeHandle, uuid );
		if ( null == layer ) {
			return -1;
		}

		int layerIndex = -1;
		int i = 0;
		while ( layerIndex == -1 || i < mapView.map().layers().size() ) {
			if ( layer == mapView.map().layers().get( i ) ) {
				layerIndex = i;
			}
			i++;
		}
		return layerIndex;
	}

}
