package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;

import java.util.UUID;

public class MapLayerScalebarModule extends MapLayerBase {

    public String getName() {
        return "MapLayerScalebarModule";
    }

	public MapLayerScalebarModule(ReactApplicationContext context) {
        super(context);
    }

    @ReactMethod
    public void createLayer(
            int nativeNodeHandle,
            int reactTreeIndex,
            Promise promise
    ) {
        try {
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );

            if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
            }

			// The promise response
			WritableMap responseParams = new WritableNativeMap();

			// Create scaleBar and add to map.
			DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar( mapView.map() );
			mapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
			mapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
			mapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
			MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer( mapView.map(), mapScaleBar );
			mapScaleBarLayer.getRenderer().setPosition( GLViewport.Position.BOTTOM_LEFT );
			mapScaleBarLayer.getRenderer().setOffset(5 * CanvasAdapter.getScale(), 0 );

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				mapScaleBarLayer
			);

			// Update map.
			mapView.map().clearMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, mapScaleBarLayer );

			// Resolve uuid
			responseParams.putString( "uuid", uuid );
            promise.resolve( responseParams );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

    @ReactMethod
    public void removeLayer(int nativeNodeHandle, String uuid, Promise promise) {
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

}
