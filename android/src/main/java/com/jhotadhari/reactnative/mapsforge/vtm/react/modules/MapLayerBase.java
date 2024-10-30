package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.HashMap;
import java.util.Map;

abstract public class MapLayerBase extends ReactContextBaseJavaModule {

    abstract public String getName();

	protected Map<String, Layer> layers = new HashMap<>();

    public MapLayerBase(ReactApplicationContext context) {
        super(context);
    }

	@ReactMethod
	abstract public void createLayer(
		int reactTag,
		int reactTreeIndex,
		Promise promise
	);

	protected int getLayerIndexInMapLayers(
		int reactTag,
		String uuid
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
		if ( null == mapView ) {
			return -1;
		}

		Layer layer = layers.get( uuid );
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


    @ReactMethod
    public void removeLayer( int reactTag, String uuid, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" );
            }

			// Remove layer from map.
			int layerIndex = getLayerIndexInMapLayers( reactTag, uuid );
			if ( layerIndex != -1 ) {
				mapView.map().layers().remove( layerIndex );
			}

			// Remove layer from layers.
			layers.remove( uuid );

			// Trigger map update.
			mapView.map().updateMap( true );

			// Resolve uuid
			promise.resolve( uuid );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

}
