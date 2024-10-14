package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.HashMap;
import java.util.Map;

abstract public class MapLayerBase extends ReactContextBaseJavaModule {

    abstract public String getName();

	protected Map<Integer, Layer> layers = new HashMap<>();

    MapLayerBase(ReactApplicationContext context) {
        super(context);
    }

	@ReactMethod
	abstract public void createLayer(
		int reactTag,
		int reactTreeIndex,
		Promise promise
	);

    @ReactMethod
    public void removeLayer(int reactTag, int hash, Promise promise) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }

			Layer layer = layers.get( hash );

			if ( null == layer )  {
				promise.resolve( false );
				return;
			}

			// Remove layer from map.
			int layerIndex = -1;
			for ( int i = 0; i < mapView.map().layers().size(); i++ ) {
				if ( layer == mapView.map().layers().get( i ) ) {
					layerIndex = i;
				}
			}
			if ( layerIndex != -1 ) {
				mapView.map().layers().remove( layerIndex );
			}

			// Remove layer from layers.
			layers.remove( hash );

			// Trigger map update.
			mapView.map().updateMap();

			// Resolve hash
			promise.resolve( hash );
        } catch(Exception e) {
            promise.reject("Remove Layer Error", e);
        }
    }

}
