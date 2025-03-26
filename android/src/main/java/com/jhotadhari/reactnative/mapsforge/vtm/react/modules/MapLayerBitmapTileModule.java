package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.HandleLayerZoomBounds;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import java.text.Normalizer;

public class MapLayerBitmapTileModule extends MapLayerBase {

    public String getName() {
        return "MapLayerBitmapTileModule";
    }

    public MapLayerBitmapTileModule(ReactApplicationContext context) {
        super(context);
    }

	protected java.util.Map<String, HandleLayerZoomBounds> handleLayerZoomBoundss = new HashMap<>();

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
    public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
            int nativeNodeHandle,
			String url,
			int zoomMin,
			int zoomMax,
			int enabledZoomMin,
			int enabledZoomMax,
			int cacheSize,	// mb
			String cacheDirBase,
			String cacheDirChild,
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

			// Define tile source.
			URL urlParsed = new URL(url);
			int index = url.indexOf( urlParsed.getFile() );
			BitmapTileSource tileSource = new BitmapTileSource(
				url.substring( 0, index ),
				url.substring( index, url.length() ),
				zoomMin,
				zoomMax
			);

			// Setup http client, maybe with cache cache.
			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			if ( cacheSize > 0 ) {
				File cacheDirParent = Utils.getCacheDirParent( cacheDirBase, getReactApplicationContext() );
				cacheDirChild = ! cacheDirChild.isEmpty() ? cacheDirChild : Utils.slugify( url );
				File cacheDirectory = new File( cacheDirParent, cacheDirChild );
				Cache cache = new Cache( cacheDirectory, (long) cacheSize * 1024 * 1024 );
				builder.cache( cache );
			}
			tileSource.setHttpEngine( new OkHttpEngine.OkHttpFactory( builder ) );
			tileSource.setHttpRequestHeaders( Collections.singletonMap( "User-Agent", getCurrentActivity().getPackageName() ) );

			// Create layer from tile source.
			BitmapTileLayer bitmapLayer = new BitmapTileLayer( mapView.map(), tileSource );

			// Add layer to map.
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				bitmapLayer
			);

			// Trigger update map.
			mapView.map().updateMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, bitmapLayer );

			// Handle enabledZoomMin, enabledZoomMax
			HandleLayerZoomBounds handleLayerZoomBounds = new HandleLayerZoomBounds( this, getReactApplicationContext() );
			handleLayerZoomBoundss.put( uuid, handleLayerZoomBounds );
			handleLayerZoomBounds.updateEnabled( bitmapLayer, enabledZoomMin, enabledZoomMax, mapView.map().getMapPosition().getZoomLevel() );
			handleLayerZoomBounds.updateUpdateListener( nativeNodeHandle, uuid, enabledZoomMin, enabledZoomMax );

			// Resolve layer uuid
			responseParams.putString( "uuid", uuid );
            promise.resolve( responseParams );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

	@ReactMethod
	public void updateEnabledZoomMinMax( int nativeNodeHandle, String uuid, int enabledZoomMin, int enabledZoomMax, Promise promise ) {
		if ( ! handleLayerZoomBoundss.containsKey( uuid ) ) {
			promise.reject( "Error", "Unable to find HandleLayerZoomBounds" ); return;
		}
		String errorMsg = handleLayerZoomBoundss.get( uuid ).updateUpdateListener( nativeNodeHandle, uuid, enabledZoomMin, enabledZoomMax );
		if ( null != errorMsg ) {
			promise.reject( "Error", errorMsg ); return;
		}
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void removeLayer(int nativeNodeHandle, String uuid, Promise promise) {
		if ( handleLayerZoomBoundss.containsKey( uuid ) ) {
			HandleLayerZoomBounds handleLayerZoomBounds = handleLayerZoomBoundss.get( uuid );
			handleLayerZoomBounds.removeUpdateListener( nativeNodeHandle );
			handleLayerZoomBoundss.remove( uuid );
		}
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

}
