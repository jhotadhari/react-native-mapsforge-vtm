package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.ReactApplicationContext;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerBase;

import org.oscim.layers.GroupLayer;
import org.oscim.layers.Layer;

public class HandleGroupLayerZoomBounds extends HandleLayerZoomBounds {

	public HandleGroupLayerZoomBounds( MapLayerBase module, ReactApplicationContext reactApplicationContext ) {
		super( module, reactApplicationContext );
	}

	public void updateEnabled( Layer layer, int zoomMin, int zoomMax, int zoomLevel ) {
		updateEnabledGroup( (GroupLayer) layer, zoomMin, zoomMax, zoomLevel );
		super.updateEnabled( (Layer) layer, zoomMin, zoomMax, zoomLevel );
	}

	protected void updateEnabledGroup( GroupLayer groupLayer, int zoomMin, int zoomMax, int zoomLevel ) {
		for ( int i = 0; i < groupLayer.layers.size(); i++ ) {
			if ( zoomLevel <= zoomMax && zoomLevel >= zoomMin ) {
				groupLayer.layers.get( i ).setEnabled( true );
			} else {
				groupLayer.layers.get( i ).setEnabled( false );
			}
		}
	}

}
