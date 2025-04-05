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
import org.mapsforge.map.layer.hills.AClasyHillShading;
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HalfResClasyHillShading;
import org.mapsforge.map.layer.hills.HiResClasyHillShading;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.mapsforge.map.layer.hills.SimpleClasyHillShading;
import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
import org.mapsforge.map.layer.hills.StandardClasyHillShading;
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

	protected static AClasyHillShading.ClasyParams getClasyParams( ReadableMap shadingAlgorithmOptions ) {
		// maxSlope
		double maxSlope = ( shadingAlgorithmOptions.hasKey( "maxSlope" )
			? shadingAlgorithmOptions.getDouble( "maxSlope" )
			: AClasyHillShading.MaxSlopeDefault
		);
		maxSlope = maxSlope > 0 && maxSlope < 100 ? maxSlope : AClasyHillShading.MaxSlopeDefault;
		// minSlope
		double minSlope = ( shadingAlgorithmOptions.hasKey( "minSlope" )
			? shadingAlgorithmOptions.getDouble( "minSlope" )
			: AClasyHillShading.MinSlopeDefault
		);
		minSlope = minSlope >= 0 && minSlope < 100 && minSlope < maxSlope ? minSlope : AClasyHillShading.MinSlopeDefault;
		// asymmetryFactor
		double asymmetryFactor = ( shadingAlgorithmOptions.hasKey( "asymmetryFactor" )
			? shadingAlgorithmOptions.getDouble( "asymmetryFactor" )
			: AClasyHillShading.AsymmetryFactorDefault
		);
		asymmetryFactor = asymmetryFactor >= 0 && asymmetryFactor <= 1 ? asymmetryFactor : AClasyHillShading.AsymmetryFactorDefault;
		// readingThreadsCount
		int readingThreadsCount = ( shadingAlgorithmOptions.hasKey( "readingThreadsCount" )
			? shadingAlgorithmOptions.getInt( "readingThreadsCount" )
			: AClasyHillShading.ReadingThreadsCountDefault
		);
		readingThreadsCount = readingThreadsCount > 0 ? readingThreadsCount : AClasyHillShading.ReadingThreadsCountDefault;
		// computingThreadsCount
		int computingThreadsCount = ( shadingAlgorithmOptions.hasKey( "computingThreadsCount" )
			? shadingAlgorithmOptions.getInt( "computingThreadsCount" )
			: AClasyHillShading.ComputingThreadsCountDefault
		);
		computingThreadsCount = computingThreadsCount >= 0 ? computingThreadsCount : AClasyHillShading.ComputingThreadsCountDefault;
		// asymmetryFactor
		boolean isPreprocess = ( shadingAlgorithmOptions.hasKey( "isPreprocess" )
			? shadingAlgorithmOptions.getBoolean( "isPreprocess" )
			: AClasyHillShading.IsPreprocessDefault
		);
		AClasyHillShading.ClasyParams clasyParams = new AClasyHillShading.ClasyParams();
		clasyParams.setMaxSlope( maxSlope );
		clasyParams.setMinSlope( minSlope );
		clasyParams.setAsymmetryFactor( asymmetryFactor );
		clasyParams.setReadingThreadsCount( readingThreadsCount );
		clasyParams.setComputingThreadsCount( computingThreadsCount );
		clasyParams.setPreprocess( isPreprocess );
		return clasyParams;
	}

	public static String slugifyNumber( double number ) {
		return String.valueOf( number )
			.replace( '-', 'm' )
			.replace( '.', 'd' );
	}

	protected static String clasyParamsToString( AClasyHillShading.ClasyParams clasyParams ) {
		return String.join( "_",
			slugifyNumber( clasyParams.getMaxSlope() ),
			slugifyNumber( clasyParams.getMinSlope() ),
			slugifyNumber( clasyParams.getAsymmetryFactor() ),
			slugifyNumber( clasyParams.getReadingThreadsCount() ),
			slugifyNumber( clasyParams.getComputingThreadsCount() ),
			clasyParams.isPreprocess() ? "1" : "0"
		);
	}

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

			String dbname = "hillshading_" + shadingAlgorithmKey + "_" + String.valueOf( magnitude );
			ShadingAlgorithm shadingAlgorithm;
			AClasyHillShading.ClasyParams clasyParams = getClasyParams( shadingAlgorithmOptions );
			switch ( shadingAlgorithmKey ) {
				case "StandardClasyHillShading":
					shadingAlgorithm = new StandardClasyHillShading( clasyParams );
					dbname += "_" + clasyParamsToString( clasyParams );
					break;
				case "SimpleClasyHillShading":
					shadingAlgorithm = new SimpleClasyHillShading( clasyParams );
					dbname += "_" + clasyParamsToString( clasyParams );
					break;
				case "HalfResClasyHillShading":
					shadingAlgorithm = new HalfResClasyHillShading( clasyParams );
					dbname += "_" + clasyParamsToString( clasyParams );
					break;
				case "HiResClasyHillShading":
					shadingAlgorithm = new HiResClasyHillShading( clasyParams );
					dbname += "_" + clasyParamsToString( clasyParams );
					break;
				case "AdaptiveClasyHillShading":
					boolean isHqEnabled = ( shadingAlgorithmOptions.hasKey( "isHqEnabled" )
						? shadingAlgorithmOptions.getBoolean( "isHqEnabled" )
						: AdaptiveClasyHillShading.IsHqEnabledDefault
					);
					double qualityScale = ( shadingAlgorithmOptions.hasKey( "qualityScale" )
						? shadingAlgorithmOptions.getDouble( "qualityScale" )
						: 1
					);
					shadingAlgorithm = new AdaptiveClasyHillShading( clasyParams, isHqEnabled ).setCustomQualityScale( qualityScale );
					dbname += "_" + String.join( "_",
						clasyParamsToString( clasyParams ),
						isHqEnabled ? "1" : "0",
						slugifyNumber( qualityScale )
					);
					break;
				case "DiffuseLightShadingAlgorithm":
					Double heightAngle = (Double) ( shadingAlgorithmOptions.hasKey( "heightAngle" )
						? shadingAlgorithmOptions.getDouble( "heightAngle" )
						: 50
					);
					shadingAlgorithm = new DiffuseLightShadingAlgorithm( heightAngle.floatValue() );
					dbname += "_" + slugifyNumber( heightAngle );
					break;
				case "SimpleShadingAlgorithm":
					double linearity = ( shadingAlgorithmOptions.hasKey( "linearity" )
						? shadingAlgorithmOptions.getDouble( "linearity" )
						: 0.1
					);
					double scale = ( shadingAlgorithmOptions.hasKey( "scale" )
						? shadingAlgorithmOptions.getDouble( "scale" )
						: 0.666
					);
					shadingAlgorithm = new SimpleShadingAlgorithm( linearity, scale );
					dbname += "_" + String.join( "_",
						slugifyNumber( linearity ),
						slugifyNumber( scale )
					);
					break;
				default: {
					promise.reject( "Error", "Unknown shading algorithm" ); return;
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
				magnitude,
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
