package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.tileSource.HillshadingTileSource;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;

public class MapLayerHillshadingModule extends MapLayerBase {

    public String getName() {
        return "MapLayerHillshadingModule";
    }

    public MapLayerHillshadingModule(ReactApplicationContext context) {
        super(context);
    }

    @ReactMethod
    public void createLayer(
            int reactTag,
            int reactTreeIndex,
            Promise promise
    ) {
        try {
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), reactTag );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );

            if ( mapFragment == null || null == mapView ) {
                promise.resolve( false );
                return;
            }


			// ??? react props
			String hgtDirPath = "/storage/emulated/0/Documents/orux/dem";
			HillshadingTileSource tileSource = new HillshadingTileSource( hgtDirPath );

			// ??? add int zoomMin, int zoomMax, int overZoom
			// add react props for new DiffuseLightShadingAlgorithm( 30f ),
			// add react props for new SimpleShadingAlgorithm( -1d, 1d ),h
			// add react props for magnitude  ??? 


			BitmapTileLayer layer = new BitmapTileLayer( mapView.map(), tileSource );


			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				layer
			);

			mapView.map().updateMap( true );

			// Store layer
            int hash = layer.hashCode();
			layers.put( hash, layer );

			// Resolve layer hash
            promise.resolve( hash );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

    @ReactMethod
    public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
