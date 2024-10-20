package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.tileSource.HillshadingTileSource;

import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.ITileCache;

import java.util.UUID;

public class MapLayerHillshadingModule extends MapLayerBase {

    public String getName() {
        return "MapLayerHillshadingModule";
    }

    public MapLayerHillshadingModule(ReactApplicationContext context) {
        super(context);
    }

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
            int reactTag,
			String hgtDirPath,
			int zoomMin,
			int zoomMax,
			String shadingAlgorithmKey,
			ReadableMap shadingAlgorithmOptions,
			int magnitude,
			int cacheSize,
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

			double linearity = shadingAlgorithmOptions.getDouble( "linearity" );
			double scale = shadingAlgorithmOptions.getDouble( "scale" );
			Double heightAngle = (Double) shadingAlgorithmOptions.getDouble( "heightAngle" );

			String dbname = "hillshading_" + shadingAlgorithmKey + "_" + String.valueOf( magnitude );
			ShadingAlgorithm shadingAlgorithm;
			switch ( shadingAlgorithmKey ) {
				case "DiffuseLightShadingAlgorithm":
					shadingAlgorithm = new DiffuseLightShadingAlgorithm( heightAngle.floatValue() );
					dbname += "_" + String.valueOf( heightAngle );
					break;
				default: {
					shadingAlgorithm = new SimpleShadingAlgorithm( linearity, scale );
					dbname += "_" + String.valueOf( linearity ) + "_" + String.valueOf( scale );
				}
			}

			HillshadingTileSource tileSource = new HillshadingTileSource(
				hgtDirPath,
				zoomMin,
				zoomMax,
				shadingAlgorithm,
				(short) magnitude
			);

			if ( cacheSize > 0 ) {
				ITileCache mCache = new TileCache(
					getCurrentActivity(),
					getReactApplicationContext().getExternalCacheDir().getAbsolutePath(),
					dbname
				);
				mCache.setCacheSize( (long) cacheSize * ( 1 << 10 ) );
				tileSource.setCache( mCache );
			}

			BitmapTileLayer layer = new BitmapTileLayer( mapView.map(), tileSource );

			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				layer
			);

			mapView.map().updateMap( true );

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, layer );

			// Resolve layer uuid
            promise.resolve( uuid );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

    @ReactMethod
    public void removeLayer(int reactTag, String uuid, Promise promise) {
		super.removeLayer( reactTag, uuid, promise );
	}

}
