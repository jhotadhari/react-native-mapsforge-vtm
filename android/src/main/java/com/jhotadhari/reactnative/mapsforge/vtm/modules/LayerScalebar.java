package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerScalebarSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;

@ReactModule( name = LayerScalebar.NAME )
public class LayerScalebar extends NativeLayerScalebarSpec {

	public static final String NAME = "LayerScalebar";

	private final LayerHelper layerHelper;

	public LayerScalebar( ReactApplicationContext reactContext ) {
		super( reactContext );
		layerHelper = new LayerHelper( this, this.getReactApplicationContext() );
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}

			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			// Create scaleBar and add to map.
			DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar( mapView.map() );
			mapScaleBar.setScaleBarMode( DefaultMapScaleBar.ScaleBarMode.BOTH );
			mapScaleBar.setDistanceUnitAdapter( MetricUnitAdapter.INSTANCE );
			mapScaleBar.setSecondaryDistanceUnitAdapter( ImperialUnitAdapter.INSTANCE );
			MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer( mapView.map(), mapScaleBar );
			mapScaleBarLayer.getRenderer().setPosition( GLViewport.Position.BOTTOM_LEFT );
			mapScaleBarLayer.getRenderer().setOffset( 5 * CanvasAdapter.getScale(), 0 );

			// Store layer
			String uuid = layerHelper.addLayer( mapScaleBarLayer, params );
			if ( null == uuid ) {
				Utils.promiseReject( promise, "Unable to add layer" ); return;
			}
			promise.resolve( uuid );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		layerHelper.removeLayer( params, promise );
	}

}
