package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;

public class MapLayerScalebarModule extends MapLayerBase {

    public String getName() {
        return "MapLayerScalebarModule";
    }

	public MapLayerScalebarModule(ReactApplicationContext context) {
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

			// Create scalebar and add to map.
			DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar( mapView.map() );
			mapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
			mapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
			mapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
			MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer( mapView.map(), mapScaleBar );
			mapScaleBarLayer.getRenderer().setPosition( GLViewport.Position.BOTTOM_LEFT );
			mapScaleBarLayer.getRenderer().setOffset(5 * CanvasAdapter.getScale(), 0 );
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				mapScaleBarLayer
			);

			// ??? bug, somehow need to trigger update map to show the scalebar.

			// Store layer
            int hash = mapScaleBarLayer.hashCode();
			layers.put( hash, mapScaleBarLayer );

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
