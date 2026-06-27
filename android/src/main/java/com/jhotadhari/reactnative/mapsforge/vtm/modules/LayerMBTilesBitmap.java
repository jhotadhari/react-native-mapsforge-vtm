package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerZoomBoundsHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerMBTilesBitmapSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.android.tiling.source.mbtiles.MBTilesBitmapTileSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileDataSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileSource;
import org.oscim.backend.canvas.Color;
import org.oscim.core.BoundingBox;
import org.oscim.core.MapPosition;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.ITileCache;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ReactModule( name = LayerMBTilesBitmap.NAME )
public class LayerMBTilesBitmap extends NativeLayerMBTilesBitmapSpec {

	public static final String NAME = "LayerMBTilesBitmap";

	private final LayerZoomBoundsHelper layerHelper;

	public LayerMBTilesBitmap(ReactApplicationContext reactContext) {
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
		constants.put( "mapFile", "" );
		constants.put( "transparentColor", "" );
		constants.put( "alpha", 1 );
		constants.put( "enabledZoomMin", 1 );
		constants.put( "enabledZoomMax", 20 );
		constants.put( "cacheSize", 0 );	// 0 disables caching -- this layer already reads straight from a local file, so caching is opt-in rather than on by default like LayerHillshading's.
		constants.put( "cacheDirBase", "" );	// Empty string will be handled by Utils.getCacheDirParent.
		constants.put( "cacheDirChild", "" );	// Empty string will be the generated cache db name.
		return constants;
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );

			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
				Utils.promiseReject( promise,"Unable to find mapView" ); return;
			}

			if ( ! Utils.rMapHasKey( params, "mapFile" ) ) {
				Utils.promiseReject( promise, "Undefined mapFile" ); return;
			}
			String mapFile = params.getString( "mapFile" );

			File file = new File( mapFile );
			if ( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
				Utils.promiseReject( promise, "mapFile does not exist or is not readable: " + mapFile ); return;
			}

			// Get params, assign defaults.
			double alpha = Utils.rMapHasKey( params, "alpha" ) ? params.getDouble( "alpha" ) : (int) getConstants().get( "alpha" );
			String transparentColor = Utils.rMapHasKey( params, "transparentColor" ) ? params.getString( "transparentColor" ) : (String) getConstants().get( "transparentColor" );
			int cacheSize = Utils.rMapHasKey( params, "cacheSize" ) ? params.getInt( "cacheSize" ) : (int) getConstants().get( "cacheSize" );
			String cacheDirBase = Utils.rMapHasKey( params, "cacheDirBase" ) ? params.getString( "cacheDirBase" ) : (String) getConstants().get( "cacheDirBase" );
			String cacheDirChild = Utils.rMapHasKey( params, "cacheDirChild" ) ? params.getString( "cacheDirChild" ) : (String) getConstants().get( "cacheDirChild" );

			// Define tile source. Alpha is handled live via BitmapTileLayer.setBitmapAlpha below,
			// not baked into the tile source at decode time.
			MBTilesTileSource tileSource = new MBTilesBitmapTileSource(
				file.getAbsolutePath(),
				null,
				null != transparentColor && transparentColor.startsWith( "#" ) ? Color.parseColor( transparentColor ) : null
			);

			// Cache MUST be set before the tile source is attached to a TileLayer.
			ITileCache tileCache = Utils.buildTileCache(
				getCurrentActivity(), getReactApplicationContext(),
				cacheSize, cacheDirBase, cacheDirChild,
				"mbtiles_" + Utils.slugify( file.getName() )
			);
			if ( null != tileCache ) {
				tileSource.setCache( tileCache );
			}

			// Create layer from tile source.
			BitmapTileLayer bitmapLayer = new BitmapTileLayer( mapView.map(), tileSource, (float) alpha );

			// Store layer
			layerHelper.addLayerAsync( bitmapLayer, params )
				.thenAccept( uid -> {
					// Resolve layer uuid plus the .mbtiles file's own metadata.
					WritableMap responseParams = new WritableNativeMap();
					responseParams.putString( "uuid", uid );
					responseParams.putInt( "nativeNodeHandle", nativeNodeHandle );
					addTileSourceToResponse( responseParams, tileSource );
					promise.resolve( responseParams );
				})
				.exceptionally( throwable -> {
					Utils.promiseReject( promise, "Unable to add layer: " + throwable.getMessage() );
					return null;
				});
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	protected void addTileSourceToResponse( WritableMap responseParams, MBTilesTileSource tileSource ) {
		MBTilesTileDataSource dataSource = tileSource.getDataSource();
		if ( null == dataSource ) {
			return;
		}
		BoundingBox boundingBox = dataSource.getBounds();
		if ( null != boundingBox ) {
			// [ west, south, east, north ], mirroring geojson's `bbox` member.
			WritableArray bboxParams = new WritableNativeArray();
			bboxParams.pushDouble( boundingBox.getMinLongitude() );
			bboxParams.pushDouble( boundingBox.getMinLatitude() );
			bboxParams.pushDouble( boundingBox.getMaxLongitude() );
			bboxParams.pushDouble( boundingBox.getMaxLatitude() );
			responseParams.putArray( "bbox", bboxParams );
		}
		MapPosition center = dataSource.getCenter();
		if ( null != center ) {
			// [ lng, lat ], mirroring geojson's `Position`.
			WritableArray centerParams = new WritableNativeArray();
			centerParams.pushDouble( center.getLongitude() );
			centerParams.pushDouble( center.getLatitude() );
			responseParams.putArray( "center", centerParams );
		}
		List<String> supportedFormats = dataSource.getSupportedFormats();
		WritableArray supportedFormatsArr = new WritableNativeArray();
		for ( int i = 0; i < supportedFormats.size(); i++ ) {
			supportedFormatsArr.pushString( supportedFormats.get( i ) );
		}
		responseParams.putArray( "supportedFormats", supportedFormatsArr );
		responseParams.putString( "format", dataSource.getFormat() );
		responseParams.putString( "attribution", dataSource.getAttribution() );
		responseParams.putString( "description", dataSource.getDescription() );
		responseParams.putString( "version", dataSource.getVersion() );
		responseParams.putInt( "zoomMin", dataSource.getMinZoom() );
		responseParams.putInt( "zoomMax", dataSource.getMaxZoom() );
	}

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		if ( Utils.rMapHasKey( params, "uuid" ) && Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
			Layer layer = layerHelper.getLayers( params.getInt( "nativeNodeHandle" ) ).get( params.getString( "uuid" ) );
			if ( layer instanceof BitmapTileLayer ) {
				// Layers().remove() only detaches the layer from the map's list, it doesn't call
				// onDetach() (that only happens on a full map/Layers destroy) -- so close the
				// underlying MBTiles SQLite connection explicitly here, otherwise it leaks every time
				// this layer is torn down and recreated (e.g. on mapFile/transparentColor change).
				( (BitmapTileLayer) layer ).onDetach();
			}
		}
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
				Utils.promiseReject( promise, "Unable to find mbTilesBitmapLayer" ); return;
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
