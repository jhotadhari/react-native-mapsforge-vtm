package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReadableMap;

import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;

public class LayerZoomBoundsHelper extends LayerHelper {

	protected Map.UpdateListener updateListener;

	public LayerZoomBoundsHelper( ReactContextBaseJavaModule module, ReactApplicationContext reactContext ) {
		super( module, reactContext );
	}

	@Override
	public java.util.concurrent.CompletableFuture<Void> removeLayerAsync( ReadableMap params ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
			return super.removeLayerAsync( params );
		}
		removeUpdateListener( params.getInt( "nativeNodeHandle" ) );
		return super.removeLayerAsync( params );
	}

	public void updateEnabledZoomMinMax( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "uuid" ) || ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined uuid or nativeNodeHandle" ); return;
			}

			// Get params, assign defaults.
			int enabledZoomMin = Utils.rMapHasKey( params, "enabledZoomMin" ) ? (int) params.getDouble( "enabledZoomMin" ) : (int) module.getConstants().get( "enabledZoomMin"  );
			int enabledZoomMax = Utils.rMapHasKey( params, "enabledZoomMax" ) ? (int) params.getDouble( "enabledZoomMax" ) : (int) module.getConstants().get( "enabledZoomMax"  );

			updateUpdateListener(
				params.getInt( "nativeNodeHandle" ),
				params.getString( "uuid" ),
				enabledZoomMin,
				enabledZoomMax
			);

			// Resolve uuid
			promise.resolve( params.getString( "uuid" ) );
		} catch( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise,e.getMessage() );
		}
	}

	public void updateEnabled(
		Layer layer,
		int enabledZoomMin,
		int enabledZoomMax,
		int zoomLevel
	) {
		layer.setEnabled( zoomLevel <= enabledZoomMax && zoomLevel >= enabledZoomMin );
	}

	public void removeUpdateListener( int nativeNodeHandle ) {
		MapView mapView = Utils.getMapView( reactContext, nativeNodeHandle );
		if ( null != mapView && updateListener != null ) {
			mapView.map().events.unbind( updateListener );
			updateListener = null;
		}
	}

	public void updateUpdateListener(
		int nativeNodeHandle,
		String uuid,
		int enabledZoomMin,
		int enabledZoomMax
	) {
		MapView mapView = Utils.getMapView( reactContext, nativeNodeHandle );
		if ( null == mapView ) { return; }

		removeUpdateListener( nativeNodeHandle );
		Layer layer = getLayers( nativeNodeHandle ).get( uuid );
		if ( null == layer ) { return; }

		updateListener = new Map.UpdateListener() {
			@Override
			public void onMapEvent( Event e, MapPosition mapPosition ) {
				updateEnabled( layer, enabledZoomMin, enabledZoomMax, mapPosition.getZoomLevel() );
			}
		};
		mapView.map().events.bind( updateListener );
		updateEnabled( layer, enabledZoomMin, enabledZoomMax, mapView.map().viewport().getMaxZoomLevel() );
		mapView.map().updateMap();
	}

}
