package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.UUID;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class MapLayerBitmapTileModule extends MapLayerBase {

    public String getName() {
        return "MapLayerBitmapTileModule";
    }

    public MapLayerBitmapTileModule(ReactApplicationContext context) {
        super(context);
    }

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
    public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
            int reactTag,
			String url,
			int zoomMin,
			int zoomMax,
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

			// Define tile source.
			URL urlParsed = new URL(url);
			int index = url.indexOf( urlParsed.getFile() );
			BitmapTileSource mTileSource = new BitmapTileSource(
				url.substring( 0, index ),
				url.substring( index, url.length() ),
				zoomMin,
				zoomMax
			);

			// Setup http client, maybe with cache cache.
			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			if ( cacheSize > 0 ) {
				File cacheDirectory = new File(getReactApplicationContext().getExternalCacheDir(), "tiles");
				Cache cache = new Cache(cacheDirectory, cacheSize);
				builder.cache( cache );
			}
			mTileSource.setHttpEngine( new OkHttpEngine.OkHttpFactory( builder ) );
			mTileSource.setHttpRequestHeaders( Collections.singletonMap( "User-Agent", getCurrentActivity().getPackageName() ) );

			// Create layer from tile source.
			BitmapTileLayer mBitmapLayer = new BitmapTileLayer( mapView.map(), mTileSource );

			// Add layer to map.
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				mBitmapLayer
			);

			// Trigger update map.
			mapView.map().updateMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, mBitmapLayer );

			// Resolve layer uuid
            promise.resolve( uuid );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	@ReactMethod
	public void removeLayer(int reactTag, String uuid, Promise promise) {
		super.removeLayer( reactTag, uuid, promise );
	}

}
