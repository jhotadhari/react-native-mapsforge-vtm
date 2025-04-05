package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.ReactApplicationContext;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerBase;

import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;

public class HandleLayerZoomBounds {

	protected Map.UpdateListener updateListener;

	protected final MapLayerBase module;

	protected final ReactApplicationContext reactApplicationContext;

	public HandleLayerZoomBounds( MapLayerBase module,  ReactApplicationContext reactApplicationContext ) {
		this.module = module;
		this.reactApplicationContext = reactApplicationContext;
	}

	public void updateEnabled(
		Layer layer,
		int enabledZoomMin,
		int enabledZoomMax,
		int zoomLevel
	) {
		if ( zoomLevel <= enabledZoomMax && zoomLevel >= enabledZoomMin ) {
			layer.setEnabled( true );
		} else {
			layer.setEnabled( false );
		}
	}

	public String removeUpdateListener( int nativeNodeHandle ) {
		MapView mapView = (MapView) Utils.getMapView( reactApplicationContext, nativeNodeHandle );
		if ( null == mapView ) {
			return "Unable to find mapView";
		}
		if ( updateListener != null ) {
			mapView.map().events.unbind( updateListener );
			updateListener = null;
		}
		return null;
	}

	public String updateUpdateListener(
		int nativeNodeHandle,
		String uuid,
		int enabledZoomMin,
		int enabledZoomMax
	) {
		MapView mapView = (MapView) Utils.getMapView( reactApplicationContext, nativeNodeHandle );
		if ( null == mapView ) {
			return "Unable to find mapView";
		}
		String errorMsg = removeUpdateListener( nativeNodeHandle );
		if ( null != errorMsg ) {
			return errorMsg;
		}
		Layer layer = module.getLayers().get( uuid );
		if ( layer == null ) {
			return "Unable to find layer";
		}
		updateListener = new Map.UpdateListener() {
			@Override
			public void onMapEvent( Event e, MapPosition mapPosition ) {
				updateEnabled( layer, enabledZoomMin, enabledZoomMax, mapPosition.getZoomLevel() );
			}
		};
		mapView.map().events.bind( updateListener );
		updateEnabled( layer, enabledZoomMin, enabledZoomMax, mapView.map().viewport().getMaxZoomLevel() );
		mapView.map().clearMap();
		return null;
	}

}
