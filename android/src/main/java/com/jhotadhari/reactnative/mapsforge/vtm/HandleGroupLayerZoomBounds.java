package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.ReactApplicationContext;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerBase;

import org.oscim.layers.GroupLayer;
import org.oscim.layers.Layer;

public class HandleGroupLayerZoomBounds extends HandleLayerZoomBounds {

	public HandleGroupLayerZoomBounds( MapLayerBase module, ReactApplicationContext reactApplicationContext ) {
		super( module, reactApplicationContext );
	}

	public void updateEnabled( Layer layer, int enabledZoomMin, int enabledZoomMax, int zoomLevel ) {
		updateEnabledGroup( (GroupLayer) layer, enabledZoomMin, enabledZoomMax, zoomLevel );
		super.updateEnabled( (Layer) layer, enabledZoomMin, enabledZoomMax, zoomLevel );
	}

	protected void updateEnabledGroup( GroupLayer groupLayer, int enabledZoomMin, int enabledZoomMax, int zoomLevel ) {
		for ( int i = 0; i < groupLayer.layers.size(); i++ ) {
			if ( zoomLevel <= enabledZoomMax && zoomLevel >= enabledZoomMin ) {
				groupLayer.layers.get( i ).setEnabled( true );
			} else {
				groupLayer.layers.get( i ).setEnabled( false );
			}
		}
	}

}
