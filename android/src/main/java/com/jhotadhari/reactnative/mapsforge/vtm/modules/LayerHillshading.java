package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerZoomBoundsHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerHillshadingSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.hills.AClasyHillShading;
import org.mapsforge.map.layer.hills.AdaptiveClasyHillShading;
import org.mapsforge.map.layer.hills.DemFolder;
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

import java.util.HashMap;
import java.util.Map;

@ReactModule( name = LayerHillshading.NAME )
public class LayerHillshading extends NativeLayerHillshadingSpec {

	public static final String NAME = "LayerHillshading";

	private final LayerZoomBoundsHelper layerHelper;

	public LayerHillshading(ReactApplicationContext reactContext) {
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
		constants.put( "hgtDirPath", "" );
		constants.put( "zoomMin", 6 );
		constants.put( "zoomMax", 20 );
		constants.put( "enabledZoomMin", 6 );
		constants.put( "enabledZoomMax", 20 );
		constants.put( "shadingAlgorithm", "SimpleShadingAlgorithm" );
		constants.put( "magnitude", 90 );
		constants.put( "cacheSize", 64 );
		constants.put( "cacheDirBase", "" );	// Empty string will be handled by Utils.getCacheDirParent.
		constants.put( "cacheDirChild", "" );	// Empty string will be the generated cache db name.
		return constants;
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" ); return;
			}
			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			if ( ! Utils.rMapHasKey( params, "hgtDirPath" ) ) {
				Utils.promiseReject( promise, "Undefined hgtDirPath" ); return;
			}
			String hgtDirPath = params.getString( "hgtDirPath" );

			DemFolder demFolder = Utils.buildDemFolder( hgtDirPath, getReactApplicationContext() );
			if ( null == demFolder ) {
				Utils.promiseReject( promise, "hgtDirPath does not exist or is not a readable directory" );
				return;
			}

			// Get params, assign defaults.
			int zoomMin = Utils.rMapHasKey( params, "zoomMin" ) ? (int) params.getDouble( "zoomMin" ) : (int) getConstants().get( "zoomMin" );
			int zoomMax = Utils.rMapHasKey( params, "zoomMax" ) ? (int) params.getDouble( "zoomMax" ) : (int) getConstants().get( "zoomMax" );
			String shadingAlgorithmKey = Utils.rMapHasKey( params, "shadingAlgorithm" ) ? params.getString( "shadingAlgorithm" ) : (String) getConstants().get( "shadingAlgorithm" );
			ReadableMap shadingAlgorithmOptions = Utils.rMapHasKey( params, "shadingAlgorithmOptions" ) ? params.getMap( "shadingAlgorithmOptions" ) : null;
			int magnitude = Utils.rMapHasKey( params, "magnitude" ) ? params.getInt( "magnitude" ) : (int) getConstants().get( "magnitude" );
			int cacheSize = Utils.rMapHasKey( params, "cacheSize" ) ? params.getInt( "cacheSize" ) : (int) getConstants().get( "cacheSize" );
			String cacheDirBase = Utils.rMapHasKey( params, "cacheDirBase" ) ? params.getString( "cacheDirBase" ) : (String) getConstants().get( "cacheDirBase" );
			String cacheDirChild = Utils.rMapHasKey( params, "cacheDirChild" ) ? params.getString( "cacheDirChild" ) : (String) getConstants().get( "cacheDirChild" );

			ShadingAlgorithmResult algorithmResult;
			try {
				algorithmResult = buildShadingAlgorithm( shadingAlgorithmKey, shadingAlgorithmOptions, magnitude );
			} catch ( IllegalArgumentException e ) {
				Utils.promiseReject( promise, e.getMessage() ); return;
			}

			if ( null == AndroidGraphicFactory.INSTANCE ) {
				AndroidGraphicFactory.createInstance( getReactApplicationContext() );
			}

			HillshadingTileSource hillshadingTileSource = new HillshadingTileSource(
				zoomMin,
				zoomMax,
				demFolder,
				algorithmResult.shadingAlgorithm,
				magnitude,
				Color.BLACK,
				AndroidGraphicFactory.INSTANCE
			);

			ITileCache tileCache = Utils.buildTileCache(
				getCurrentActivity(), getReactApplicationContext(),
				cacheSize, cacheDirBase, cacheDirChild,
				algorithmResult.dbname
			);
			if ( null != tileCache ) {
				hillshadingTileSource.setCache( tileCache );
			}

			BitmapTileLayer layer = new BitmapTileLayer( mapView.map(), hillshadingTileSource );

			layerHelper.addLayerAsync( layer, params )
				.thenAccept( uid -> {
					// LayerHelper.addLayer only calls updateMap(), which redraws the current frame but
					// doesn't make the TileManager (re-)schedule tile jobs for a layer added after the
					// map's initial position was already set -- without a real subsequent pan/zoom event,
					// this layer's tiles are otherwise never requested at all.
					mapView.map().clearMap();
					promise.resolve( uid );
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

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		layerHelper.removeLayer( params, promise );
	}

	@Override
	public void updateEnabledZoomMinMax( ReadableMap params, Promise promise ) {
		layerHelper.updateEnabledZoomMinMax( params, promise );
	}

	private static class ShadingAlgorithmResult {
		final ShadingAlgorithm shadingAlgorithm;
		final String dbname;
		ShadingAlgorithmResult( ShadingAlgorithm shadingAlgorithm, String dbname ) {
			this.shadingAlgorithm = shadingAlgorithm;
			this.dbname = dbname;
		}
	}

	private static ShadingAlgorithmResult buildShadingAlgorithm( String shadingAlgorithmKey, ReadableMap options, int magnitude ) {
		String dbname = "hillshading_" + shadingAlgorithmKey + "_" + magnitude;
		switch ( shadingAlgorithmKey ) {
			case "StandardClasyHillShading": {
				AClasyHillShading.ClasyParams p = getClasyParams( options );
				return new ShadingAlgorithmResult( new StandardClasyHillShading( p ), dbname + "_" + clasyParamsToString( p ) );
			}
			case "SimpleClasyHillShading": {
				AClasyHillShading.ClasyParams p = getClasyParams( options );
				return new ShadingAlgorithmResult( new SimpleClasyHillShading( p ), dbname + "_" + clasyParamsToString( p ) );
			}
			case "HalfResClasyHillShading": {
				AClasyHillShading.ClasyParams p = getClasyParams( options );
				return new ShadingAlgorithmResult( new HalfResClasyHillShading( p ), dbname + "_" + clasyParamsToString( p ) );
			}
			case "HiResClasyHillShading": {
				AClasyHillShading.ClasyParams p = getClasyParams( options );
				return new ShadingAlgorithmResult( new HiResClasyHillShading( p ), dbname + "_" + clasyParamsToString( p ) );
			}
			case "AdaptiveClasyHillShading": {
				AClasyHillShading.ClasyParams p = getClasyParams( options );
				boolean isHqEnabled = null != options && Utils.rMapHasKey( options, "isHqEnabled" ) ? options.getBoolean( "isHqEnabled" ) : AdaptiveClasyHillShading.IsHqEnabledDefault;
				double qualityScale = null != options && Utils.rMapHasKey( options, "qualityScale" ) ? options.getDouble( "qualityScale" ) : 1;
				ShadingAlgorithm algorithm = new AdaptiveClasyHillShading( p, isHqEnabled ).setCustomQualityScale( qualityScale );
				String algorithmDbname = dbname + "_" + String.join( "_",
					clasyParamsToString( p ),
					isHqEnabled ? "1" : "0",
					slugifyNumber( qualityScale )
				);
				return new ShadingAlgorithmResult( algorithm, algorithmDbname );
			}
			case "DiffuseLightShadingAlgorithm": {
				double heightAngle = null != options && Utils.rMapHasKey( options, "heightAngle" ) ? options.getDouble( "heightAngle" ) : 50;
				return new ShadingAlgorithmResult( new DiffuseLightShadingAlgorithm( (float) heightAngle ), dbname + "_" + slugifyNumber( heightAngle ) );
			}
			case "SimpleShadingAlgorithm": {
				double linearity = null != options && Utils.rMapHasKey( options, "linearity" ) ? options.getDouble( "linearity" ) : 0.1;
				double scale = null != options && Utils.rMapHasKey( options, "scale" ) ? options.getDouble( "scale" ) : 0.666;
				String algorithmDbname = dbname + "_" + String.join( "_", slugifyNumber( linearity ), slugifyNumber( scale ) );
				return new ShadingAlgorithmResult( new SimpleShadingAlgorithm( linearity, scale ), algorithmDbname );
			}
			default:
				throw new IllegalArgumentException( "Unknown shadingAlgorithm: " + shadingAlgorithmKey );
		}
	}

	private static AClasyHillShading.ClasyParams getClasyParams( ReadableMap options ) {
		double maxSlope = null != options && Utils.rMapHasKey( options, "maxSlope" ) ? options.getDouble( "maxSlope" ) : AClasyHillShading.MaxSlopeDefault;
		maxSlope = maxSlope > 0 && maxSlope < 100 ? maxSlope : AClasyHillShading.MaxSlopeDefault;

		double minSlope = null != options && Utils.rMapHasKey( options, "minSlope" ) ? options.getDouble( "minSlope" ) : AClasyHillShading.MinSlopeDefault;
		minSlope = minSlope >= 0 && minSlope < 100 && minSlope < maxSlope ? minSlope : AClasyHillShading.MinSlopeDefault;

		double asymmetryFactor = null != options && Utils.rMapHasKey( options, "asymmetryFactor" ) ? options.getDouble( "asymmetryFactor" ) : AClasyHillShading.AsymmetryFactorDefault;
		asymmetryFactor = asymmetryFactor >= 0 && asymmetryFactor <= 1 ? asymmetryFactor : AClasyHillShading.AsymmetryFactorDefault;

		int readingThreadsCount = null != options && Utils.rMapHasKey( options, "readingThreadsCount" ) ? options.getInt( "readingThreadsCount" ) : AClasyHillShading.ReadingThreadsCountDefault;
		readingThreadsCount = readingThreadsCount > 0 ? readingThreadsCount : AClasyHillShading.ReadingThreadsCountDefault;

		int computingThreadsCount = null != options && Utils.rMapHasKey( options, "computingThreadsCount" ) ? options.getInt( "computingThreadsCount" ) : AClasyHillShading.ComputingThreadsCountDefault;
		computingThreadsCount = computingThreadsCount >= 0 ? computingThreadsCount : AClasyHillShading.ComputingThreadsCountDefault;

		boolean isPreprocess = null != options && Utils.rMapHasKey( options, "isPreprocess" ) ? options.getBoolean( "isPreprocess" ) : AClasyHillShading.IsPreprocessDefault;

		AClasyHillShading.ClasyParams clasyParams = new AClasyHillShading.ClasyParams();
		clasyParams.setMaxSlope( maxSlope );
		clasyParams.setMinSlope( minSlope );
		clasyParams.setAsymmetryFactor( asymmetryFactor );
		clasyParams.setReadingThreadsCount( readingThreadsCount );
		clasyParams.setComputingThreadsCount( computingThreadsCount );
		clasyParams.setPreprocess( isPreprocess );
		return clasyParams;
	}

	private static String slugifyNumber( double number ) {
		return String.valueOf( number ).replace( '-', 'm' ).replace( '.', 'd' );
	}

	private static String clasyParamsToString( AClasyHillShading.ClasyParams clasyParams ) {
		return String.join( "_",
			slugifyNumber( clasyParams.getMaxSlope() ),
			slugifyNumber( clasyParams.getMinSlope() ),
			slugifyNumber( clasyParams.getAsymmetryFactor() ),
			slugifyNumber( clasyParams.getReadingThreadsCount() ),
			slugifyNumber( clasyParams.getComputingThreadsCount() ),
			clasyParams.isPreprocess() ? "1" : "0"
		);
	}

}
