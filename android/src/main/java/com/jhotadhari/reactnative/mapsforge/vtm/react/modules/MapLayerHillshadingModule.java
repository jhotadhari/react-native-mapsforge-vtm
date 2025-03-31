package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.graphics.Color;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.HandleLayerZoomBounds;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.hills.DemFolderAndroidContent;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.oscim.android.MapView;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.ITileCache;
import org.oscim.tiling.source.hills.HillshadingTileSource;
import org.oscim.android.cache.TileCache;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public class MapLayerHillshadingModule extends MapLayerBase {

    public String getName() {
        return "MapLayerHillshadingModule";
    }

    public MapLayerHillshadingModule(ReactApplicationContext context) { super(context); }

	protected java.util.Map<String, HandleLayerZoomBounds> handleLayerZoomBoundss = new HashMap<>();

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
            int nativeNodeHandle,
			String hgtDirPath,
			int zoomMin,
			int zoomMax,
			int enabledZoomMin,
			int enabledZoomMax,
			String shadingAlgorithmKey,
			ReadableMap shadingAlgorithmOptions,
			int magnitude,
			int cacheSize,	// ??? doesn't really work. cache seems always unlimited if on.
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

			if ( hgtDirPath.startsWith( "content://" ) ) {
				DocumentFile dir = DocumentFile.fromSingleUri( mapView.getContext(), Uri.parse( hgtDirPath ) );
				if ( dir == null || ! dir.exists() || ! dir.isDirectory() ) {
					promise.reject( "Error", "hgtDirPath is not existing or not a directory" ); return;
				}
				if ( ! Utils.hasScopedStoragePermission( mapView.getContext(), hgtDirPath, false ) ) {
					promise.reject( "Error", "No scoped storage read permission for hgtDirPath" ); return;
				}
			}

			if ( hgtDirPath.startsWith( "/" ) ) {
				File file = new File( hgtDirPath );
				if( ! file.exists() || ! file.isDirectory() || ! file.canRead() ) {
					promise.reject( "Error", "hgtDirPath does not exist or is not a directory" ); return;
				}
			}

			double linearity = shadingAlgorithmOptions.getDouble( "linearity" );
			double scale = shadingAlgorithmOptions.getDouble( "scale" );
			Double heightAngle = (Double) shadingAlgorithmOptions.getDouble( "heightAngle" );

			String dbname = "hillshading_" + shadingAlgorithmKey + "_" + String.valueOf( magnitude );
			ShadingAlgorithm shadingAlgorithm;
			switch ( shadingAlgorithmKey ) {


				// ??? implement new algorithms


				case "DiffuseLightShadingAlgorithm":
					shadingAlgorithm = new DiffuseLightShadingAlgorithm( heightAngle.floatValue() );
					dbname += "_" + String.valueOf( heightAngle );
					break;
				default: {
					shadingAlgorithm = new SimpleShadingAlgorithm( linearity, scale );
					dbname += "_" + String.valueOf( linearity ) + "_" + String.valueOf( scale );
				}
			}

			DemFolder demFolder = null;
			if ( hgtDirPath.startsWith( "content://" ) ) {
				Uri uri = Uri.parse( hgtDirPath );
				demFolder = new DemFolderAndroidContent( uri, getReactApplicationContext(), getReactApplicationContext().getContentResolver() );
			} else if ( hgtDirPath.startsWith( "/" ) ) {
				File demFolderFile = new File( hgtDirPath );
				if ( demFolderFile.exists() && demFolderFile.isDirectory() && demFolderFile.canRead() ) {
					demFolder = new DemFolderFS( demFolderFile );
				}
			}

			if ( demFolder == null ) {
				promise.reject( "Error", "Unable to find demFolder" ); return;
			}

			HillshadingTileSource hillshadingTileSource = new HillshadingTileSource(
					zoomMin,
					zoomMax,
					demFolder,
					shadingAlgorithm,
					128,
					Color.BLACK,
					AndroidGraphicFactory.INSTANCE
			);

			if ( cacheSize > 0 ) {
				File cacheDirParent = Utils.getCacheDirParent( cacheDirBase, getReactApplicationContext() );
				cacheDirChild = ! cacheDirChild.isEmpty() ? cacheDirChild : dbname;
				File cacheDirectory = new File( cacheDirParent, cacheDirChild );
				ITileCache mCache = new TileCache(
					getCurrentActivity(),
					cacheDirectory.toString(),
					dbname
				);
				mCache.setCacheSize( (long) cacheSize * ( 1 << 10 ) );
				hillshadingTileSource.setCache( mCache );
			}

			BitmapTileLayer layer = new BitmapTileLayer( mapView.map(), hillshadingTileSource );

			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				layer
			);

			// Update map.
			mapView.map().clearMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, layer );

			// Handle enabledZoomMin, enabledZoomMax
			HandleLayerZoomBounds handleLayerZoomBounds = new HandleLayerZoomBounds( this, getReactApplicationContext() );
			handleLayerZoomBoundss.put( uuid, handleLayerZoomBounds );
			handleLayerZoomBounds.updateEnabled( layer, enabledZoomMin, enabledZoomMax, mapView.map().getMapPosition().getZoomLevel() );
			handleLayerZoomBounds.updateUpdateListener( nativeNodeHandle, uuid, enabledZoomMin, enabledZoomMax );

			// Resolve promise
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
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

}
