package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerZoomBoundsHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerMapsforgeSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.RenderThemeMenuLoader;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.OsmTileLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.theme.ExternalRenderTheme;
import org.oscim.theme.IRenderTheme;
import org.oscim.theme.ThemeFile;
import org.oscim.theme.ThemeLoader;
import org.oscim.theme.XmlRenderThemeMenuCallback;
import org.oscim.theme.XmlRenderThemeStyleLayer;
import org.oscim.theme.XmlRenderThemeStyleMenu;
import org.oscim.theme.XmlThemeResourceProvider;
import org.oscim.theme.internal.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MapInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ReactModule( name = LayerMapsforge.NAME )
public class LayerMapsforge extends NativeLayerMapsforgeSpec {

	public static final String NAME = "LayerMapsforge";

	// Main vector tile layer, buildings sub-layer, and labels sub-layer are each their own
	// independent native Layer with their own uuid -- not bundled into an org.oscim.layers.GroupLayer
	// (see AGENTS.md). Each needs its own LayerZoomBoundsHelper, since that helper only tracks a
	// single zoom-change listener per instance.
	private final LayerZoomBoundsHelper layerHelper;
	private final LayerZoomBoundsHelper buildingLayerHelper;
	private final LayerZoomBoundsHelper labelLayerHelper;

	// Routes the single generic updateEnabledZoomMinMax/removeLayer-style calls to whichever of the
	// three helpers above actually owns a given uuid -- the params arriving from JS carry only a
	// uuid, not which role created it.
	private final Map<String, LayerZoomBoundsHelper> helpersByUuid = new HashMap<>();

	public LayerMapsforge( ReactApplicationContext reactContext ) {
		super( reactContext );
		layerHelper = new LayerZoomBoundsHelper( this, this.getReactApplicationContext() );
		buildingLayerHelper = new LayerZoomBoundsHelper( this, this.getReactApplicationContext() );
		labelLayerHelper = new LayerZoomBoundsHelper( this, this.getReactApplicationContext() );
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
		constants.put( "renderTheme", "DEFAULT" );
		constants.put( "renderStyle", "" );
		constants.put( "renderOverlays", new WritableNativeArray() );
		constants.put( "hasBuildings", true );
		constants.put( "hasLabels", true );
		constants.put( "enabledZoomMin", 1 );
		constants.put( "enabledZoomMax", 30 );
		return constants;
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			if ( ! Utils.rMapHasKey( params, "mapFile" ) ) {
				Utils.promiseReject( promise, "Undefined mapFile" ); return;
			}

			MapFileTileSource tileSource = buildTileSource( params.getString( "mapFile" ), promise );
			if ( null == tileSource ) {
				return; // buildTileSource already rejected with a specific message.
			}

			VectorTileLayer tileLayer = new OsmTileLayer( mapView.map() );
			tileLayer.setTileSource( tileSource );

			String renderTheme = Utils.rMapHasKey( params, "renderTheme" ) ? params.getString( "renderTheme" ) : (String) getConstants().get( "renderTheme" );
			String renderStyle = Utils.rMapHasKey( params, "renderStyle" ) ? params.getString( "renderStyle" ) : (String) getConstants().get( "renderStyle" );
			ReadableArray renderOverlays = Utils.rMapHasKey( params, "renderOverlays" ) ? params.getArray( "renderOverlays" ) : new WritableNativeArray();

			IRenderTheme theme;
			try {
				theme = loadTheme( renderTheme, renderStyle, renderOverlays );
			} catch ( Exception e ) {
				Utils.promiseReject( promise, "Unable to load renderTheme: " + e.getMessage() ); return;
			}
			tileLayer.setTheme( theme );

			layerHelper.addLayerAsync( tileLayer, params )
				.thenAccept( uid -> {
					helpersByUuid.put( uid, layerHelper );
					// LayerHelper.addLayer only calls updateMap(), which doesn't make the TileManager
					// (re-)schedule tile jobs for a layer added after the map's initial position was already
					// set -- without a real subsequent pan/zoom event, tiles would otherwise never be
					// requested at all (same fix as LayerHillshading).
					mapView.map().clearMap();
					WritableMap responseParams = new WritableNativeMap();
					responseParams.putString( "uuid", uid );
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

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		if ( Utils.rMapHasKey( params, "uuid" ) ) {
			helpersByUuid.remove( params.getString( "uuid" ) );
		}
		layerHelper.removeLayer( params, promise );
	}

	@Override
	public void updateEnabledZoomMinMax( ReadableMap params, Promise promise ) {
		LayerZoomBoundsHelper helper = Utils.rMapHasKey( params, "uuid" ) ? helpersByUuid.get( params.getString( "uuid" ) ) : null;
		if ( null == helper ) {
			Utils.promiseReject( promise, "Unable to find layer for uuid" ); return;
		}
		helper.updateEnabledZoomMinMax( params, promise );
	}

	@Override
	public void createBuildingLayer( ReadableMap params, Promise promise ) {
		try {
			VectorTileLayer tileLayer = resolveParentTileLayer( params, promise );
			if ( null == tileLayer ) {
				return; // resolveParentTileLayer already rejected with a specific message.
			}
			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			BuildingLayer buildingLayer = new BuildingLayer( mapView.map(), tileLayer );
			buildingLayerHelper.addLayerAsync( buildingLayer, params )
				.thenAccept( uid -> {
					helpersByUuid.put( uid, buildingLayerHelper );
					mapView.map().clearMap();
					promise.resolve( uid );
				})
				.exceptionally( throwable -> {
					Utils.promiseReject( promise, "Unable to add buildingLayer: " + throwable.getMessage() );
					return null;
				});
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeBuildingLayer( ReadableMap params, Promise promise ) {
		if ( Utils.rMapHasKey( params, "uuid" ) ) {
			helpersByUuid.remove( params.getString( "uuid" ) );
		}
		buildingLayerHelper.removeLayer( params, promise );
	}

	@Override
	public void createLabelLayer( ReadableMap params, Promise promise ) {
		try {
			VectorTileLayer tileLayer = resolveParentTileLayer( params, promise );
			if ( null == tileLayer ) {
				return; // resolveParentTileLayer already rejected with a specific message.
			}
			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			LabelLayer labelLayer = new LabelLayer( mapView.map(), tileLayer );
			labelLayerHelper.addLayerAsync( labelLayer, params )
				.thenAccept( uid -> {
					helpersByUuid.put( uid, labelLayerHelper );
					mapView.map().clearMap();
					promise.resolve( uid );
				})
				.exceptionally( throwable -> {
					Utils.promiseReject( promise, "Unable to add labelLayer: " + throwable.getMessage() );
					return null;
				});
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeLabelLayer( ReadableMap params, Promise promise ) {
		if ( Utils.rMapHasKey( params, "uuid" ) ) {
			helpersByUuid.remove( params.getString( "uuid" ) );
		}
		labelLayerHelper.removeLayer( params, promise );
	}

	@Override
	public void getRenderThemeOptions( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "renderTheme" ) ) {
				Utils.promiseReject( promise, "Undefined renderTheme" ); return;
			}
			String renderTheme = params.getString( "renderTheme" );
			if ( null != builtInTheme( renderTheme ) ) {
				// Built-in vtm themes ship without a <stylemenu> -- resolve immediately, no I/O.
				promise.resolve( new WritableNativeArray() );
				return;
			}
			promise.resolve( RenderThemeMenuLoader.toWritableArray(
				RenderThemeMenuLoader.load( renderTheme, getReactApplicationContext() )
			) );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	private VectorTileLayer resolveParentTileLayer( ReadableMap params, Promise promise ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) || ! Utils.rMapHasKey( params, "parentUuid" ) ) {
			Utils.promiseReject( promise, "Undefined nativeNodeHandle or parentUuid" ); return null;
		}
		int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
		if ( null == Utils.getMapView( getReactApplicationContext(), nativeNodeHandle ) ) {
			Utils.promiseReject( promise, "Unable to find mapView" ); return null;
		}
		Layer parent = LayerHelper.getLayer( nativeNodeHandle, params.getString( "parentUuid" ) );
		if ( ! ( parent instanceof VectorTileLayer ) ) {
			Utils.promiseReject( promise, "Unable to find parent tile layer" ); return null;
		}
		return (VectorTileLayer) parent;
	}

	private MapFileTileSource buildTileSource( String mapFile, Promise promise ) {
		InputStream is;
		try {
			if ( mapFile.startsWith( "content://" ) ) {
				Uri mapUri = Uri.parse( mapFile );
				DocumentFile doc = DocumentFile.fromSingleUri( getReactApplicationContext(), mapUri );
				if ( null == doc || ! doc.exists() || ! doc.isFile() ) {
					Utils.promiseReject( promise, "mapFile does not exist or is not a file" ); return null;
				}
				if ( ! Utils.hasScopedStoragePermission( getReactApplicationContext(), mapFile, false ) ) {
					Utils.promiseReject( promise, "No scoped storage read permission for mapFile" ); return null;
				}
				is = getReactApplicationContext().getContentResolver().openInputStream( mapUri );
			} else if ( mapFile.startsWith( "/" ) ) {
				File file = new File( mapFile );
				if ( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
					Utils.promiseReject( promise, "mapFile does not exist or is not a file" ); return null;
				}
				is = new FileInputStream( file );
			} else {
				Utils.promiseReject( promise, "mapFile must start with '/' or 'content://'" ); return null;
			}
		} catch ( Exception e ) {
			Utils.promiseReject( promise, "Unable to open mapFile: " + e.getMessage() ); return null;
		}
		if ( ! ( is instanceof FileInputStream ) ) {
			Utils.promiseReject( promise, "Unable to open mapFile as a FileInputStream" ); return null;
		}
		MapFileTileSource tileSource = new MapFileTileSource();
		tileSource.setMapFileInputStream( (FileInputStream) is );
		return tileSource;
	}

	private void addTileSourceToResponse( WritableMap responseParams, MapFileTileSource tileSource ) {
		MapInfo mapInfo = tileSource.getMapInfo();
		if ( null == mapInfo ) {
			return;
		}
		BoundingBox boundingBox = mapInfo.boundingBox;
		// [ west, south, east, north ], mirroring geojson's `bbox` member.
		WritableArray bboxParams = new WritableNativeArray();
		bboxParams.pushDouble( boundingBox.getMinLongitude() );
		bboxParams.pushDouble( boundingBox.getMinLatitude() );
		bboxParams.pushDouble( boundingBox.getMaxLongitude() );
		bboxParams.pushDouble( boundingBox.getMaxLatitude() );
		responseParams.putArray( "bbox", bboxParams );
		// [ lng, lat ], mirroring geojson's `Position`.
		WritableArray centerParams = new WritableNativeArray();
		centerParams.pushDouble( mapInfo.mapCenter.getLongitude() );
		centerParams.pushDouble( mapInfo.mapCenter.getLatitude() );
		responseParams.putArray( "center", centerParams );
		responseParams.putString( "createdBy", mapInfo.createdBy );
		responseParams.putString( "projectionName", mapInfo.projectionName );
		responseParams.putString( "comment", mapInfo.comment );
		responseParams.putString( "fileSize", String.valueOf( mapInfo.fileSize ) );
		responseParams.putInt( "fileVersion", mapInfo.fileVersion );
		responseParams.putString( "mapDate", String.valueOf( mapInfo.mapDate ) );
	}

	// Built-in vtm themes have no <stylemenu>, so no menu callback / category selection applies to
	// them -- mirrors the old module's plain VtmThemes switch.
	private VtmThemes builtInTheme( String renderTheme ) {
		switch ( renderTheme ) {
			case "DEFAULT": return VtmThemes.DEFAULT;
			case "BIKER": return VtmThemes.BIKER;
			case "MOTORIDER": return VtmThemes.MOTORIDER;
			case "NEWTRON": return VtmThemes.NEWTRON;
			case "OSMARENDER": return VtmThemes.OSMARENDER;
			case "TRONRENDER": return VtmThemes.TRONRENDER;
			default: return null;
		}
	}

	private IRenderTheme loadTheme( String renderTheme, String renderStyle, ReadableArray renderOverlays ) throws Exception {
		VtmThemes builtIn = builtInTheme( renderTheme );
		if ( null != builtIn ) {
			return ThemeLoader.load( builtIn );
		}

		ThemeFile themeFile = renderTheme.startsWith( "content://" )
			? new ContentUriRenderTheme( renderTheme, getReactApplicationContext() )
			: new ExternalRenderTheme( renderTheme );

		themeFile.setMenuCallback( new XmlRenderThemeMenuCallback() {
			@Override
			public Set<String> getCategories( XmlRenderThemeStyleMenu renderThemeStyleMenu ) {
				// Use the selected style, or the theme's own default.
				String style = ( null != renderStyle && ! renderStyle.isEmpty() ) ? renderStyle : renderThemeStyleMenu.getDefaultValue();
				XmlRenderThemeStyleLayer renderThemeStyleLayer = renderThemeStyleMenu.getLayer( style );
				if ( null == renderThemeStyleLayer ) {
					return null;
				}
				Set<String> categories = renderThemeStyleLayer.getCategories();
				for ( XmlRenderThemeStyleLayer overlay : renderThemeStyleLayer.getOverlays() ) {
					if ( renderOverlays.toArrayList().contains( overlay.getId() ) ) {
						categories.addAll( overlay.getCategories() );
					}
				}
				return categories;
			}
		} );

		return ThemeLoader.load( themeFile );
	}

	// Mirrors org.oscim.theme.ExternalRenderTheme, but backed by a content:// stream -- the old
	// module never actually supported content:// render themes (ExternalRenderTheme only accepts a
	// plain filesystem path), this fixes that gap.
	private static class ContentUriRenderTheme implements ThemeFile {

		private final String path;
		private final ReactApplicationContext reactContext;
		private XmlRenderThemeMenuCallback menuCallback;
		private XmlThemeResourceProvider resourceProvider;
		private boolean mapsforgeTheme;

		ContentUriRenderTheme( String path, ReactApplicationContext reactContext ) {
			this.path = path;
			this.reactContext = reactContext;
		}

		@Override
		public XmlRenderThemeMenuCallback getMenuCallback() {
			return menuCallback;
		}

		@Override
		public String getRelativePathPrefix() {
			// content:// URIs can't resolve sibling resource paths the way a filesystem path can.
			return null;
		}

		@Override
		public InputStream getRenderThemeAsStream() {
			try {
				InputStream is = reactContext.getContentResolver().openInputStream( Uri.parse( path ) );
				if ( null == is ) {
					throw new IRenderTheme.ThemeException( "Unable to open render theme: " + path );
				}
				return is;
			} catch ( IRenderTheme.ThemeException e ) {
				throw e;
			} catch ( Exception e ) {
				throw new IRenderTheme.ThemeException( e.getMessage() );
			}
		}

		@Override
		public XmlThemeResourceProvider getResourceProvider() {
			return resourceProvider;
		}

		@Override
		public boolean isMapsforgeTheme() {
			return mapsforgeTheme;
		}

		@Override
		public void setMapsforgeTheme( boolean mapsforgeTheme ) {
			this.mapsforgeTheme = mapsforgeTheme;
		}

		@Override
		public void setMenuCallback( XmlRenderThemeMenuCallback menuCallback ) {
			this.menuCallback = menuCallback;
		}

		@Override
		public void setResourceProvider( XmlThemeResourceProvider resourceProvider ) {
			this.resourceProvider = resourceProvider;
		}
	}

}
