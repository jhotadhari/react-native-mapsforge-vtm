package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.net.Uri;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

//import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
//import org.mapsforge.map.android.util.AndroidUtil;
//import org.mapsforge.map.android.view.MapView;
//import org.mapsforge.map.layer.cache.TileCache;
//import org.mapsforge.map.layer.hills.DemFolder;
//import org.mapsforge.map.layer.hills.DemFolderFS;
//import org.mapsforge.map.layer.hills.HillsRenderConfig;
//import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
//import org.mapsforge.map.layer.hills.ShadingAlgorithm;
//import org.mapsforge.map.layer.hills.SimpleShadingAlgorithm;
//import org.mapsforge.map.layer.renderer.TileRendererLayer;
//import org.mapsforge.map.reader.MapFile;
//import org.mapsforge.map.rendertheme.ExternalRenderTheme;
//import org.mapsforge.map.rendertheme.InternalRenderTheme;
//import org.mapsforge.map.rendertheme.XmlRenderTheme;
//import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
//import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
//import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;

import org.oscim.android.MapView;
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

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapLayerMapsforgeModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMapsforgeModule";
    }

    public MapLayerMapsforgeModule(ReactApplicationContext context) {
        super(context);
    }

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

    @ReactMethod
    public void getRenderThemeOptions( String renderThemePath, Promise promise ) {
        try {
			// Check if file exists. Will false for built in themes.
			File file = new File( renderThemePath );
			if ( ! file.exists() ) {
				promise.resolve( false );
				return;
			}
			// Load theme, parse options and send response.
			IRenderTheme theme = ThemeLoader.load(
				renderThemePath,
				new XmlRenderThemeMenuCallback() {
					@Override
					public Set<String> getCategories( XmlRenderThemeStyleMenu renderThemeStyleMenu ) {
						WritableMap response = parseRenderThemeOptions( renderThemeStyleMenu );
						promise.resolve( response );
						return null;
					}
				}
			);
		} catch ( Exception e) {
			e.printStackTrace();
            promise.reject("Error getRenderThemeOptions", e );
		}
    }

    protected IRenderTheme loadTheme(
			int reactTag,
            String renderThemePath,
            String renderStyle,
            ReadableArray renderOverlays
    ) {
		IRenderTheme theme;
		ReactContext reactContext = this.getReactApplicationContext();
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
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
							params.putInt( "nativeTag", reactTag );
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

	@Override
	public void createLayer(int reactTag, int reactTreeIndex, Promise promise) {
		createLayer(
			reactTag,
			"",
			"DEFAULT",
			"",
			Utils.getEmptyReadableArray(),
			reactTreeIndex,
			promise
		);
	}

    @ReactMethod
    public void createLayer(
            int reactTag,
            String mapFileName,
            String renderThemePath,
            String renderStyle,
            ReadableArray renderOverlays,
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
            File mapfile = new File( mapFileName );
            if ( ! mapfile.exists() ) {
                promise.resolve( false );
                return;
            }

			// Tile source
			Uri mapUri = Uri.parse("file://" + mapFileName );
			MapFileTileSource tileSource = new MapFileTileSource();
			FileInputStream fis = ( FileInputStream ) mapFragment.getActivity().getContentResolver().openInputStream( mapUri );
			tileSource.setMapFileInputStream( fis );

			// VectorTileLayer
			VectorTileLayer tileLayer = new OsmTileLayer( mapView.map() );
			tileLayer.setTileSource( tileSource );

			// Add tilelayer to map, in order to be able to load the render theme.
			int zIndex = Math.min( mapView.map().layers().size(), (int) reactTreeIndex );
			mapView.map().layers().add(
				zIndex,
				tileLayer
			);

			// Render theme
			IRenderTheme theme = loadTheme( reactTag, renderThemePath, renderStyle, renderOverlays );
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
			int hash = groupLayer.hashCode();
			layers.put( hash, groupLayer );

			// Resolve layer hash
            promise.resolve(hash);
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	@ReactMethod
	public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}
}
