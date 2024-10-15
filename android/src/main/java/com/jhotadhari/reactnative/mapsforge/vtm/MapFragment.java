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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import android.widget.RelativeLayout;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map.UpdateListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MapFragment extends Fragment {

	protected MapView mapView;

    protected ReactContext reactContext;

	protected static double propWidthForLayoutSize = 200;
	protected static double propHeightForLayoutSize = 200;

	protected GeoPoint propCenterGeoPoint;

	protected static boolean propMoveEnabled = true;
	protected static boolean propRotationEnabled = true;
	protected static boolean propZoomEnabled = true;
	protected static boolean propTiltEnabled = true;

	protected static int propZoomLevel = 12;
	protected static int propMinZoom = 3;
	protected static int propMaxZoom = 20;
	protected static float propTilt = 0;
	protected static float propMinTilt = 0;
	protected static float propMaxTilt = 65;
	protected static float propBearing = 0;
	protected static float propMinBearing = -180;
	protected static float propMaxBearing = 180;
	protected static float propRoll = 0;
	protected static float propMinRoll = -180;
	protected static float propMaxRoll = 180;

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

    MapFragment(
		ReactContext reactContext_,

		double widthForLayoutSize,
		double heightForLayoutSize,

		ArrayList center,

		boolean moveEnabled,
		boolean rotationEnabled,
		boolean zoomEnabled,
		boolean tiltEnabled,

		int zoomLevel,
		int minZoom,
		int maxZoom,

		float tilt,
		float minTilt,
		float maxTilt,

		float bearing,
		float minBearing,
		float maxBearing,

		float roll,
		float minRoll,
		float maxRoll
	) {
        super();

		rateLimiter = new FixedWindowRateLimiter( 100, 1 );

		reactContext = reactContext_;

		propHeightForLayoutSize = heightForLayoutSize;
		propWidthForLayoutSize = widthForLayoutSize;

        propCenterGeoPoint = new GeoPoint(
            (double) center.get(0),
            (double) center.get(1)
        );

		propMoveEnabled = moveEnabled;
		propRotationEnabled = rotationEnabled;
		propZoomEnabled = zoomEnabled;
		propTiltEnabled = tiltEnabled;

		propZoomLevel = zoomLevel;
        propMinZoom = minZoom;
        propMaxZoom = maxZoom;

		propTilt = tilt;
		propMinTilt = minTilt;
		propMaxTilt = maxTilt;

		propBearing = bearing;
		propMinBearing = minBearing;
		propMaxBearing = maxBearing;

		propRoll = roll;
		propMinRoll = minRoll;
		propMaxRoll = maxRoll;
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

			// Initial position and zoomLevel.
			MapPosition mapPosition = new MapPosition( propCenterGeoPoint.getLatitude(), propCenterGeoPoint.getLongitude(), 1 );

			mapPosition.setZoomLevel( propZoomLevel );
			mapView.map().setMapPosition( mapPosition );

			mapView.map().getEventLayer().enableMove( propMoveEnabled );
			mapView.map().getEventLayer().enableRotation( propRotationEnabled );
			mapView.map().getEventLayer().enableZoom( propZoomEnabled );	// ??? bug, doesn't work properly, and still possible on .setZoom
			mapView.map().getEventLayer().enableTilt( propTiltEnabled );

			mapView.map().viewport().setMinZoomLevel( (int) propMinZoom );
			mapView.map().viewport().setMaxZoomLevel( (int) propMaxZoom );

			mapView.map().viewport().setTilt( (float) propTilt );
			mapView.map().viewport().setMinTilt( (float) propMinTilt );
			mapView.map().viewport().setMaxTilt( (float) propMaxTilt );

			mapView.map().viewport().setRotation( (double) propBearing );
			mapView.map().viewport().setMinBearing( (float) propMinBearing );
			mapView.map().viewport().setMaxBearing( (float) propMaxBearing );

			mapView.map().viewport().setRoll( (double) propRoll );
			mapView.map().viewport().setMinRoll( (float) propMinRoll );
			mapView.map().viewport().setMaxRoll( (float) propMaxRoll );

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
		params.putDouble( "bearing", mapPosition.getBearing() );
		params.putDouble( "roll", mapPosition.getRoll() );
		params.putDouble( "tilt", mapPosition.getTilt() );

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
