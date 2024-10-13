/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015-2019 devemux86
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jhotadhari.reactnative.mapsforge.vtm;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.RelativeLayout;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBar;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;
import org.oscim.theme.IRenderTheme;
import org.oscim.theme.ThemeLoader;
import org.oscim.theme.XmlRenderThemeMenuCallback;
import org.oscim.theme.XmlRenderThemeStyleLayer;
import org.oscim.theme.XmlRenderThemeStyleMenu;
import org.oscim.theme.internal.VtmThemes;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MapInfo;
import org.oscim.map.Map.UpdateListener;

import java.io.File;
import java.io.FileInputStream;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapFragment extends Fragment {

	protected MapView mapView;

    protected ReactContext reactContext;

    // Initial variables for controlling the map.
    protected static double propZoom = 12;
    protected static double propMinZoom = 3;
    protected static double propMaxZoom = 50;

	// Dimensions variables.
	protected static double propWidthForLayoutSize = 200;
	protected static double propHeightForLayoutSize = 200;

    protected GeoPoint propCenterGeoPoint;

	protected FixedWindowRateLimiter rateLimiter;

    protected String hardwareKeyListenerUid = null;

    public MapView getMapView() {
        return mapView;
    }

    protected void sendEventMapLayersCreated() {
        WritableMap params = new WritableNativeMap();
        params.putInt( "nativeTag", this.getId() );
        Utils.sendEvent( reactContext, "MapLayersCreated", params );
    }

    MapFragment( ReactContext reactContext_, ArrayList center, double zoom, double minZoom, double maxZoom, double widthForLayoutSize, double heightForLayoutSize ) {
        super();

        reactContext = reactContext_;

		rateLimiter = new FixedWindowRateLimiter( 100, 1 );

        propCenterGeoPoint = new GeoPoint(
            (double) center.get(0),
            (double) center.get(1)
        );
        propZoom = zoom;
        propMinZoom = minZoom;
        propMaxZoom = maxZoom;
		propHeightForLayoutSize = heightForLayoutSize;
		propWidthForLayoutSize = widthForLayoutSize;
    }

    protected void addHardwareKeyListener() {
        try {
            HardwareKeyListener hardwareKeyListener = new HardwareKeyListener() {
                @Override
                public boolean onKeyUp(int keyCode, KeyEvent event) {
                    String keyCodeString = null;
                    for ( Field field : KeyEvent.class.getFields() ) {
                        if ( null == keyCodeString && field.getName().startsWith( "KEYCODE_" ) ) {
                            try {
                                int fieldKeyCode = (int) field.get( event );
                                if ( fieldKeyCode == keyCode ) {
                                    keyCodeString = field.getName();
                                }
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    WritableMap params = new WritableNativeMap();
                    params.putInt( "keyCode", keyCode );
                    params.putString( "keyCodeString", keyCodeString );
                    Utils.sendEvent( reactContext, "onHardwareKeyUp", params );
                    return true;
                }
            };
            Class[] cArg = new Class[1];
            cArg[0] = HardwareKeyListener.class;
            Method meth = reactContext.getCurrentActivity().getClass().getMethod(
                    "addHardwareKeyListener",
                    cArg
            );
            Object value = meth.invoke(
                reactContext.getCurrentActivity(),
                hardwareKeyListener
            );
            String uid = (String) value;
            hardwareKeyListenerUid = uid;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    protected void removeHardwareKeyListener() {
        try {
            if ( null != hardwareKeyListenerUid ) {
                Class[] cArg = new Class[1];
                cArg[0] = String.class;
                Method meth = reactContext.getCurrentActivity().getClass().getDeclaredMethod(
                        "removeHardwareKeyListener",
                        cArg
                );
                meth.invoke(
                        reactContext.getCurrentActivity(),
                        hardwareKeyListenerUid
                );
                hardwareKeyListenerUid = null;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    protected void createMapView(View v) {
		try {

			mapView = initMapView( v );

			// Scale bar
			DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar( mapView.map() );
			mapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
			mapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
			mapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
			MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer( mapView.map(), mapScaleBar );
			mapScaleBarLayer.getRenderer().setPosition( GLViewport.Position.BOTTOM_LEFT );
			mapScaleBarLayer.getRenderer().setOffset(5 * CanvasAdapter.getScale(), 0 );
			mapView.map().layers().add( mapScaleBarLayer );

			// Initial position and zoomLevel.
			MapPosition mapPosition = new MapPosition( propCenterGeoPoint.getLatitude(), propCenterGeoPoint.getLongitude(), 1 );
			mapPosition.setZoomLevel( (int) propZoom );
			mapView.map().setMapPosition( mapPosition );
			// Set min and max zoomLevel.
			mapView.map().viewport().setMinZoomLevel( (int) propMinZoom );
			mapView.map().viewport().setMaxZoomLevel( (int) propMaxZoom );

			// Event listener.
			mapView.map().events.bind( new UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( rateLimiter.tryAcquire() ) {
						WritableMap params = getResponseBase();
						Utils.sendEvent( reactContext, "onMapEvent", params );
					}
				}
			} );

//			// Set position based on loaded map.
//			MapInfo info = tileSource.getMapInfo();
//			if ( ! info.boundingBox.contains( mapView.map().getMapPosition().getGeoPoint() ) ) {
//				MapPosition pos = new MapPosition();
//				pos.setByBoundingBox( info.boundingBox, Tile.SIZE * 4, Tile.SIZE * 4 );
//				mapView.map().setMapPosition( pos );
//			}

		} catch (Exception e) {
			// Something went wrong. Should notice user!!!???
			e.printStackTrace();
		}
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate( R.layout.fragment_map, container, false );
        createMapView( view );
		sendEventMapLayersCreated();
        addHardwareKeyListener();
        return view;
    }

	protected WritableMap getResponseBase() {
		WritableMap params = new WritableNativeMap();
		params.putInt( "nativeTag", this.getId() );

		MapPosition mapPosition = mapView.map().getMapPosition();
		params.putArray( "center",  Utils.mapPositionToArray( mapPosition ) );
		params.putDouble( "zoomLevel", mapPosition.getZoomLevel() );
		params.putDouble( "zoom", mapPosition.getZoom() );
		params.putDouble( "scale", mapPosition.getScale() );
		params.putDouble( "zoomScale", mapPosition.getZoomScale() );
//		params.putDouble( "bearing", mapPosition.getBearing() );
//		params.putDouble( "roll", mapPosition.getRoll() );
//		params.putDouble( "tilt", mapPosition.getTilt() );

		return params;
	}

    protected void sendLifecycleEvent( String type ) {
        WritableMap params = getResponseBase();
        params.putString( "type", type );
        Utils.sendEvent( reactContext, "MapLifecycle", params );
    }

    @Override
    public void onPause() {
		if (mapView != null) {
			mapView.onPause();
		}
        sendLifecycleEvent( "onPause" );
		for ( Layer layer : mapView.map().layers() ) {
            try {
				layer.getClass().getMethod("onPause").invoke( layer );
            } catch (NoSuchMethodException e) {
				//
            } catch (InvocationTargetException e) {
				//
            } catch (IllegalAccessException e) {
				//
            }
        }
        super.onPause();
    }

	@Override
	public void onResume() {
		super.onResume();
		if (mapView != null) {
			mapView.onResume();
		}
        sendLifecycleEvent( "onResume" );
		for ( Layer layer : mapView.map().layers() ) {
			try {
				layer.getClass().getMethod("onResume").invoke( layer );
			} catch (NoSuchMethodException e) {
				//
			} catch (InvocationTargetException e) {
				//
			} catch (IllegalAccessException e) {
				//
			}
		}
	}

    @Override
    public void onDestroy() {
        removeHardwareKeyListener();
		if ( mapView != null ) {
			mapView.onDestroy();
			mapView = null;
		}
        super.onDestroy();
    }

    protected MapView initMapView( View view ) {

		mapView = new MapView( this.getContext() );
		RelativeLayout relativeLayout = view.findViewById( R.id.mapView );
		relativeLayout.addView( mapView );

		// Fix view size
		ViewGroup.LayoutParams params = relativeLayout.getLayoutParams();
		params.width = (int) propWidthForLayoutSize;
		params.height = (int) propHeightForLayoutSize;
		view.setLayoutParams( params );

        return mapView;
    }

}
