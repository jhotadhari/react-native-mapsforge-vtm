package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerZoomBoundsHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerBitmapTileSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

@ReactModule( name = LayerBitmapTile.NAME )
public class LayerBitmapTile extends NativeLayerBitmapTileSpec {

	public static final String NAME = "LayerBitmapTile";

	private final LayerZoomBoundsHelper layerHelper;

	public LayerBitmapTile(ReactApplicationContext reactContext) {
		super(reactContext);
		layerHelper = new LayerZoomBoundsHelper( this, this.getReactApplicationContext() );
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected Map<String, Object> getTypedExportedConstants() {
		final Map<String, Object> constants = new HashMap<>();
		constants.put( "url", "https://tile.openstreetmap.org/{Z}/{X}/{Y}.png" );
		constants.put( "zoomMin", 1 );
		constants.put( "zoomMax", 20 );
		constants.put( "enabledZoomMin", 1 );
		constants.put( "enabledZoomMax", 20 );
		constants.put( "alpha", 1 );
		constants.put( "cacheSize", 0 );
		constants.put( "cacheDirBase", "" );	// Empty string will be handled by Utils.getCacheDirParent.
		constants.put( "cacheDirChild", "" );	// Empty string will be Utils.slugify( url ).
		return constants;
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}

			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView ) {
				Utils.promiseReject( promise,"Unable to find mapView" ); return;
			}

			// Get params, assign defaults.
			String url = Utils.rMapHasKey( params, "url" ) ? params.getString( "url" ) : (String) getConstants().get( "url" );
			int zoomMin = Utils.rMapHasKey( params, "zoomMin" ) ? (int) params.getDouble( "zoomMin" ) : (int) getConstants().get( "zoomMin" );
			int zoomMax = Utils.rMapHasKey( params, "zoomMax" ) ? (int) params.getDouble( "zoomMax" ) : (int) getConstants().get( "zoomMax" );
			double alpha = Utils.rMapHasKey( params, "alpha" ) ? params.getDouble( "alpha" ) : (int) getConstants().get( "alpha" );
			int cacheSize = Utils.rMapHasKey( params, "cacheSize" ) ? params.getInt( "cacheSize" ) : (int) getConstants().get( "cacheSize" );
			String cacheDirBase = Utils.rMapHasKey( params, "cacheDirBase" ) ? params.getString( "cacheDirBase" ) : (String) getConstants().get( "cacheDirBase" );
			String cacheDirChild = Utils.rMapHasKey( params, "cacheDirChild" ) ? params.getString( "cacheDirChild" ) : (String) getConstants().get( "cacheDirChild" );

			// Define tile source.
			url = url.length() > 0 ? url : (String) getConstants().get( "url"  );
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
				cacheDirChild = cacheDirChild.length() > 0 ? cacheDirChild :  Utils.slugify( url );
				File cacheDirectory = new File( cacheDirParent, cacheDirChild );
				Cache cache = new Cache( cacheDirectory, (long) cacheSize * 1024 * 1024 );
				builder.cache( cache );
			}
			tileSource.setHttpEngine( new OkHttpEngine.OkHttpFactory( builder ) );
			tileSource.setHttpRequestHeaders( Collections.singletonMap( "User-Agent", getCurrentActivity().getPackageName() ) );

			// Create layer from tile source.
			BitmapTileLayer bitmapLayer = new BitmapTileLayer( mapView.map(), tileSource, (float) alpha );

			// Store layer
			layerHelper.addLayerAsync( bitmapLayer, params )
				.thenAccept( uid -> promise.resolve( uid ) )
				.exceptionally( throwable -> {
					Utils.promiseReject( promise, "Unable to add layer: " + throwable.getMessage() );
					return null;
				});
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		layerHelper.removeLayer( params, promise );
	}

	@Override
	public void updateEnabledZoomMinMax( ReadableMap params, Promise promise ) {
		layerHelper.updateEnabledZoomMinMax( params, promise );
	}

	@Override
	public void setAlpha( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "uuid" ) || ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined uuid or nativeNodeHandle" ); return;
			}
			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView ) {
				Utils.promiseReject( promise,"Unable to find mapView or mapFragment" ); return;
			}

			// Get params, assign defaults.
			double alpha = Utils.rMapHasKey( params, "alpha" ) ? params.getDouble( "alpha" ) : (int) getConstants().get( "alpha" );

			// Find layer
			BitmapTileLayer bitmapTileLayer = (BitmapTileLayer) layerHelper.getLayers( params.getInt( "nativeNodeHandle" ) ).get( params.getString( "uuid" ) );
			if ( null == bitmapTileLayer ) {
				Utils.promiseReject( promise, "Unable to find bitmapTileLayer" ); return;
			}
			// Set alpha
			bitmapTileLayer.setBitmapAlpha( (float) alpha, true );
			// Resolve uuid
			promise.resolve( params.getString( "uuid" ) );
		} catch( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise,e.getMessage() );
		}
	}

}

