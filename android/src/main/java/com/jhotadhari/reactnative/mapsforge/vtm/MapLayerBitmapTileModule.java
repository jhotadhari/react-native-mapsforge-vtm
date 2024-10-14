package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

import java.io.File;
import java.net.URL;
import java.util.Collections;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class MapLayerBitmapTileModule extends MapLayerBase {

    public String getName() {
        return "MapLayerBitmapTileModule";
    }

    MapLayerBitmapTileModule(ReactApplicationContext context) {
        super(context);
    }

	@ReactMethod
    public void createLayer(
            int reactTag,
            int reactTreeIndex,
            Promise promise
    ) {
		createLayer(
			reactTag,
			"",
			1,
			20,
			0,
			reactTreeIndex,
			promise
		);
	}

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
            int hash = mBitmapLayer.hashCode();
			layers.put( hash, mBitmapLayer );

			// Resolve layer hash
            promise.resolve( hash );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	@ReactMethod
	public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
