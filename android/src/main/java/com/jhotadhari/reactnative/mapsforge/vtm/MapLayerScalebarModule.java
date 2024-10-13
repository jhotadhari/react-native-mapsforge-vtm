package com.jhotadhari.reactnative.mapsforge.vtm;

import android.net.Uri;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.OsmTileLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;
import org.oscim.theme.IRenderTheme;
import org.oscim.theme.ThemeLoader;
import org.oscim.theme.XmlRenderThemeMenuCallback;
import org.oscim.theme.XmlRenderThemeStyleLayer;
import org.oscim.theme.XmlRenderThemeStyleMenu;
import org.oscim.theme.internal.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapLayerScalebarModule extends ReactContextBaseJavaModule {

    public String getName() {
        return "MapLayerScalebarModule";
    }

	protected Map<Integer, Layer> layers = new HashMap<>();

    MapLayerScalebarModule(ReactApplicationContext context) {
        super(context);
    }

    @ReactMethod
    public void createLayer(
            int reactTag,
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

			// Create scalebar and add to map.
			DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar( mapView.map() );
			mapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
			mapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
			mapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
			MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer( mapView.map(), mapScaleBar );
			mapScaleBarLayer.getRenderer().setPosition( GLViewport.Position.BOTTOM_LEFT );
			mapScaleBarLayer.getRenderer().setOffset(5 * CanvasAdapter.getScale(), 0 );
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				mapScaleBarLayer
			);

			// ??? bug, somehow need to trigger update map to show the scalebar.

			// Store layer
            int hash = mapScaleBarLayer.hashCode();
			layers.put( hash, mapScaleBarLayer );

			// Resolve layer hash
            promise.resolve( hash );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

    @ReactMethod
    public void removeLayer(int reactTag, int hash, Promise promise) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }

			Layer layer = layers.get( hash );

			if ( null == layer )  {
				promise.resolve( false );
				return;
			}

			// Remove layer from map.
			int layerIndex = -1;
			for ( int i = 0; i < mapView.map().layers().size(); i++ ) {
				if ( layer == mapView.map().layers().get( i ) ) {
					layerIndex = i;
				}
			}
			if ( layerIndex != -1 ) {
				mapView.map().layers().remove( layerIndex );
			}

			// Remove layer from layers.
			layers.remove( hash );

			// Trigger map update.
			mapView.map().updateMap();

			// Resolve hash
			promise.resolve( hash );
        } catch(Exception e) {
            promise.reject("Remove Layer Error", e);
        }
    }

}
