package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.HandleGroupLayerZoomBounds;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.layers.GroupLayer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.OsmTileLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.theme.IRenderTheme;
import org.oscim.theme.ThemeLoader;
import org.oscim.theme.XmlRenderThemeMenuCallback;
import org.oscim.theme.XmlRenderThemeStyleLayer;
import org.oscim.theme.XmlRenderThemeStyleMenu;
import org.oscim.theme.internal.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MapInfo;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MapLayerMapsforgeModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMapsforgeModule";
    }

	protected java.util.Map<String, HandleGroupLayerZoomBounds> handleLayerZoomBoundss = new HashMap<>();

	public final String[] BUILT_IN_THEMES = {
		"DEFAULT",
		"BIKER",
		"MOTORIDER",
		"MOTORIDER_DARK",
		"NEWTRON",
		"OSMAGRAY",
		"OSMARENDER",
		"TRONRENDER",
	};

    public MapLayerMapsforgeModule(ReactApplicationContext context) { super(context); }

	protected WritableMap parseRenderThemeOptions( XmlRenderThemeStyleMenu renderThemeStyleMenu ) {

		if ( null == renderThemeStyleMenu ) {
			return null;
		}

		Map layers = renderThemeStyleMenu.getLayers();
		WritableMap response = new WritableNativeMap();

		for ( Object key : layers.keySet() ) {
			XmlRenderThemeStyleLayer layer = (XmlRenderThemeStyleLayer) layers.get(String.valueOf(key));
			if ( null != layer && ! layer.isEnabled() && layer.isVisible() ) {

				WritableMap responseItem = new WritableNativeMap();
				WritableMap opts = new WritableNativeMap();
				for (XmlRenderThemeStyleLayer overlay : layer.getOverlays()) {
					opts.putString(
						String.valueOf( overlay.getId() ),
						overlay.getTitle( renderThemeStyleMenu.getDefaultLanguage() )
					);
				}
				responseItem.putString( "value", layer.getId() );
				responseItem.putString( "label", layer.getTitle( renderThemeStyleMenu.getDefaultLanguage() ) );
				responseItem.putMap( "options", opts );
				if ( Objects.equals( layer.getId(), renderThemeStyleMenu.getDefaultValue() ) )  {
					responseItem.putBoolean( "default", true );
				}

				response.putMap(
					String.valueOf( layer.getId() ),
					responseItem
				);
			}
		}

		return response;
	}

	protected void checkRenderThemeValid( String renderThemePath, Promise promise ) {
		if ( renderThemePath.startsWith( "content://" ) ) {
			DocumentFile dir = DocumentFile.fromSingleUri( this.getReactApplicationContext(), Uri.parse( renderThemePath ) );
			if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
				promise.reject( "Error", "renderThemePath is not existing or not a file" ); return;
			}
			if ( ! Utils.hasScopedStoragePermission( this.getReactApplicationContext(), renderThemePath, false ) ) {
				promise.reject( "Error", "No scoped storage read permission for renderThemePath" ); return;
			}
		}

		if ( renderThemePath.startsWith( "/" ) ) {
			File file = new File( renderThemePath );
			if( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
				promise.reject( "Error", "renderThemePath does not exist or is not a file" ); return;
			}
		}
	}

    @ReactMethod
    public void getRenderThemeOptions( String renderThemePath, Promise promise ) {
        try {
			checkRenderThemeValid( renderThemePath, promise );

			if ( renderThemePath.isEmpty()
				|| Arrays.asList( BUILT_IN_THEMES ).contains( renderThemePath )
				|| ! renderThemePath.startsWith( "/" )
			) {
				promise.resolve( new WritableNativeMap() );
				return;
			}

			// Load theme, parse options and send response.
			IRenderTheme theme = ThemeLoader.load(
				renderThemePath,
				new XmlRenderThemeMenuCallback() {
					@Override
					public Set<String> getCategories( XmlRenderThemeStyleMenu renderThemeStyleMenu ) {
						promise.resolve( parseRenderThemeOptions( renderThemeStyleMenu ) );
						return null;
					}
				}
			);
		} catch ( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
		}
    }

    protected IRenderTheme loadTheme(
		int nativeNodeHandle,
		String renderThemePath,
		String renderStyle,
		ReadableArray renderOverlays,
		Promise promise
    ) {

		IRenderTheme theme;
		ReactContext reactContext = this.getReactApplicationContext();
		checkRenderThemeValid( renderThemePath, promise );
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		switch( renderThemePath ) {
			case "DEFAULT":
				theme = mapView.map().setTheme( VtmThemes.DEFAULT );
				break;
			case "BIKER":
				theme = mapView.map().setTheme( VtmThemes.BIKER );
				break;
			case "MOTORIDER":
				theme = mapView.map().setTheme( VtmThemes.MOTORIDER );
				break;
			case "MOTORIDER_DARK":
				theme = mapView.map().setTheme( VtmThemes.MOTORIDER_DARK );
				break;
			case "NEWTRON":
				theme = mapView.map().setTheme( VtmThemes.NEWTRON );
				break;
			case "OSMAGRAY":
				theme = mapView.map().setTheme( VtmThemes.OSMAGRAY );
				break;
			case "OSMARENDER":
				theme = mapView.map().setTheme( VtmThemes.OSMARENDER );
				break;
			case "TRONRENDER":
				theme = mapView.map().setTheme( VtmThemes.TRONRENDER );
				break;
			default:
				IRenderTheme theme_ = ThemeLoader.load(
					renderThemePath,
					new XmlRenderThemeMenuCallback() {
						@Override
						public Set<String> getCategories( XmlRenderThemeStyleMenu renderThemeStyleMenu ) {
							// Use the selected style or the default
							String style = renderStyle != null ? renderStyle : renderThemeStyleMenu.getDefaultValue();

							WritableMap params = new WritableNativeMap();
							params.putInt( "nativeNodeHandle", nativeNodeHandle );
							params.putString( "filePath", renderThemePath );
							params.putMap( "collection", parseRenderThemeOptions( renderThemeStyleMenu ) );
							Utils.sendEvent( reactContext, "RenderThemeParsed", params );

							// Retrieve the layer from the style id
							XmlRenderThemeStyleLayer renderThemeStyleLayer = renderThemeStyleMenu.getLayer(style);
							if (renderThemeStyleLayer == null) {
								System.err.println("Invalid style " + style);
								return null;
							}

							// First get the selected layer's categories that are enabled together
							Set<String> categories = renderThemeStyleLayer.getCategories();

							// add the categories from overlays that are enabled
							for ( XmlRenderThemeStyleLayer overlay : renderThemeStyleLayer.getOverlays() ) {
								if ( renderOverlays.toArrayList().contains( overlay.getId() ) ) {
									categories.addAll(overlay.getCategories());
								}
							}

							// This is the whole categories set to be enabled
							return categories;
						}
					}
				);
				mapView.map().setTheme( theme_ );
				theme = theme_;
				break;
		}
		return theme;
    }

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

	protected void addTileSourceToResponse( WritableMap responseParams, MapFileTileSource tileSource ) {
		MapInfo mapInfo = tileSource.getMapInfo();
		if ( null != mapInfo ) {
			// Add tileSource bounds to response.
			WritableMap boundsParams = new WritableNativeMap();
			BoundingBox boundingBox = mapInfo.boundingBox;
			boundsParams.putDouble( "minLat", boundingBox.getMinLatitude() );
			boundsParams.putDouble( "minLng", boundingBox.getMinLongitude() );
			boundsParams.putDouble( "maxLat", boundingBox.getMaxLatitude() );
			boundsParams.putDouble( "maxLng", boundingBox.getMaxLongitude() );
			responseParams.putMap( "bounds", boundsParams );
			// Add tileSource center to response.
			WritableMap centerParams = new WritableNativeMap();
			centerParams.putDouble( "lng", mapInfo.mapCenter.getLongitude() );
			centerParams.putDouble( "lat", mapInfo.mapCenter.getLatitude() );
			responseParams.putMap( "center", centerParams );
			// Add tileSource info to response.
			responseParams.putString( "createdBy", mapInfo.createdBy );
			responseParams.putString( "projectionName", mapInfo.projectionName );
			responseParams.putString( "comment", mapInfo.comment );
			responseParams.putString( "fileSize", String.valueOf( mapInfo.fileSize ) );
			responseParams.putInt( "fileVersion", mapInfo.fileVersion );
			responseParams.putString( "mapDate", String.valueOf( mapInfo.mapDate ) );
		}
	}

	@ReactMethod
	public void updateEnabledZoomMinMax( int nativeNodeHandle, String uuid, int enabledZoomMin, int enabledZoomMax, Promise promise ) {
		if ( ! handleLayerZoomBoundss.containsKey( uuid ) ) {
			promise.reject( "Error", "Unable to find HandleGroupLayerZoomBounds" ); return;
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
    public void createLayer(
		int nativeNodeHandle,
		String mapFileName,
		String renderThemePath,
		String renderStyle,
		ReadableArray renderOverlays,
		int enabledZoomMin,
		int enabledZoomMax,
		int reactTreeIndex,
		Promise promise
    ) {
        try {
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );

            if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
            }

			WritableMap responseParams = new WritableNativeMap();

			// Tile source
			MapFileTileSource tileSource = new MapFileTileSource();
			FileInputStream fis = null;
			if ( mapFileName.startsWith( "content://" ) ) {
				Uri mapUri = Uri.parse( mapFileName );
				DocumentFile dir = DocumentFile.fromSingleUri(mapView.getContext(), mapUri );
				if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
					promise.reject( "Error", "mapFileName does not exist or is not a file. " + mapFileName ); return;
				}
				if ( ! Utils.hasScopedStoragePermission( mapView.getContext(), mapFileName, false ) ) {
					promise.reject( "Error", "No scoped storage read permission for mapFileName" + mapFileName ); return;
				}
				fis = ( FileInputStream ) mapFragment.getActivity().getContentResolver().openInputStream( mapUri );
			}

			if ( mapFileName.startsWith( "/" ) ) {
				File mapfile = new File( mapFileName );
				if( ! mapfile.exists() || ! mapfile.isFile() || ! mapfile.canRead() ) {
					promise.reject( "Error", "mapFileName does not exist or is not a file. " + mapFileName ); return;
				}
				fis = new FileInputStream( mapFileName );
			}
			if ( fis == null ) {
				promise.reject( "Error", "Unable to load mapFile: " + mapFileName ); return;
			}
			tileSource.setMapFileInputStream( fis );

			// VectorTileLayer
			VectorTileLayer tileLayer = new OsmTileLayer( mapView.map() );
			tileLayer.setTileSource( tileSource );

			addTileSourceToResponse( responseParams, tileSource );

			// Add tilelayer to map, in order to be able to load the render theme.
			int zIndex = Math.min( mapView.map().layers().size(), (int) reactTreeIndex );
			mapView.map().layers().add(
				zIndex,
				tileLayer
			);

			// Render theme
			IRenderTheme theme = loadTheme( nativeNodeHandle, renderThemePath, renderStyle, renderOverlays, promise );
			tileLayer.setTheme( theme );

			// Building layer
			BuildingLayer buildingLayer = new BuildingLayer( mapView.map(), tileLayer );

			// Label layer
			LabelLayer labelLayer = new LabelLayer( mapView.map(), tileLayer );

			// Combine to groupLayer.
			GroupLayer groupLayer = new GroupLayer( mapView.map() );
			groupLayer.layers.add( tileLayer );
			groupLayer.layers.add( buildingLayer );
			groupLayer.layers.add( labelLayer );

			// Replace previous added tilelayer with groupLayer.
			// set doesn't work, have to remove and add.
			mapView.map().layers().remove(zIndex);
			mapView.map().layers().add(
				zIndex,
				groupLayer
			);

			// Trigger update map.
			mapView.map().updateMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, groupLayer );

			// Handle enabledZoomMin, enabledZoomMax
			HandleGroupLayerZoomBounds handleLayerZoomBounds = new HandleGroupLayerZoomBounds( this, getReactApplicationContext() );
			handleLayerZoomBoundss.put( uuid, handleLayerZoomBounds );
			handleLayerZoomBounds.updateEnabled( groupLayer, enabledZoomMin, enabledZoomMax, mapView.map().getMapPosition().getZoomLevel() );
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
	public void removeLayer(int nativeNodeHandle, String uuid, Promise promise) {
		if ( handleLayerZoomBoundss.containsKey( uuid ) ) {
			HandleGroupLayerZoomBounds handleLayerZoomBounds = handleLayerZoomBoundss.get( uuid );
			handleLayerZoomBounds.removeUpdateListener( nativeNodeHandle );
			handleLayerZoomBoundss.remove( uuid );
		}
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}
}
