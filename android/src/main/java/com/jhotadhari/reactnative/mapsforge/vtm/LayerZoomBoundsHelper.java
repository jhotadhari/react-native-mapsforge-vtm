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
	public String addLayer( Layer layer, ReadableMap params ) {
		String uuid = super.addLayer( layer, params );

		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) { return null; }
		MapView mapView = Utils.getMapView( reactContext, params.getInt( "nativeNodeHandle" ) );
		if ( null == mapView ) { return null; }

		// Get params, assign defaults.
		int enabledZoomMin = Utils.rMapHasKey( params, "enabledZoomMin" ) ? (int) params.getDouble( "enabledZoomMin" ) : (int) module.getConstants().get( "enabledZoomMin" );
		int enabledZoomMax = Utils.rMapHasKey( params, "enabledZoomMax" ) ? (int) params.getDouble( "enabledZoomMax" ) : (int) module.getConstants().get( "enabledZoomMax" );

		updateEnabled(
			layer,
			enabledZoomMin,
			enabledZoomMax,
			mapView.map().getMapPosition().getZoomLevel()
		);

		updateUpdateListener(
			params.getInt( "nativeNodeHandle" ),
			uuid,
			enabledZoomMin,
			enabledZoomMax
		);

		return uuid;
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

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
			Utils.promiseReject( promise, "Undefined nativeNodeHandle" );  return;
		}
		removeUpdateListener( params.getInt( "nativeNodeHandle" ) );
		super.removeLayer( params, promise );
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
