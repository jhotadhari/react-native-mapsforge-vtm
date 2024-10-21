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
package com.jhotadhari.reactnative.mapsforge.vtm.react.views;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.FixedWindowRateLimiter;
import com.jhotadhari.reactnative.mapsforge.vtm.HardwareKeyListener;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.HgtReader;

import android.widget.RelativeLayout;

import org.mapsforge.map.layer.hills.DemFolderFS;
import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map.UpdateListener;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class MapFragment extends Fragment {

	protected HgtReader hgtReader;


	protected RelativeLayout relativeLayout;
	protected View view;

	protected MapView mapView;

    protected MapViewManager mapViewManager;

	protected static double propWidthForLayoutSize = 200;
	protected static double propHeightForLayoutSize = 200;

	protected ReadableMap propCenter;

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
	protected static String propHgtDirPath = "";

	protected FixedWindowRateLimiter rateLimiter;
    protected String hardwareKeyListenerUid = null;

    public MapView getMapView() {
        return mapView;
    }

    protected void sendEventMapLayersCreated() {
        WritableMap params = new WritableNativeMap();
        params.putInt( "nativeTag", this.getId() );
        Utils.sendEvent( mapViewManager.getReactContext(), "MapLayersCreated", params );
    }

    MapFragment(
		MapViewManager mapViewManager_,

		double widthForLayoutSize,
		double heightForLayoutSize,

		ReadableMap center,

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
		float maxRoll,

		String hgtDirPath
	) {
        super();

		rateLimiter = new FixedWindowRateLimiter( 100, 1 );

		mapViewManager = mapViewManager_;

		propHeightForLayoutSize = heightForLayoutSize;
		propWidthForLayoutSize = widthForLayoutSize;

        propCenter = center;

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

		propHgtDirPath = hgtDirPath;
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
                    Utils.sendEvent( mapViewManager.getReactContext(), "onHardwareKeyUp", params );
                    return true;
                }
            };
            Class[] cArg = new Class[1];
            cArg[0] = HardwareKeyListener.class;
            Method meth = mapViewManager.getReactContext().getCurrentActivity().getClass().getMethod(
				"addHardwareKeyListener",
				cArg
            );
            Object value = meth.invoke(
                 mapViewManager.getReactContext().getCurrentActivity(),
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
                Method meth = mapViewManager.getReactContext().getCurrentActivity().getClass().getDeclaredMethod(
					"removeHardwareKeyListener",
					cArg
                );
                meth.invoke(
					mapViewManager.getReactContext().getCurrentActivity(),
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
			MapPosition mapPosition = new MapPosition( propCenter.getDouble( "lat" ), propCenter.getDouble( "lng" ), 1 );

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

			// Init hgtReader
			DemFolderFS demFolderFS = new DemFolderFS( getDemFolder( propHgtDirPath ) );
			hgtReader = new HgtReader( demFolderFS );

			// Event listener.
			mapView.map().events.bind( new UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( rateLimiter.tryAcquire() ) {
						WritableMap params = null;
						Utils.sendEvent(  mapViewManager.getReactContext(), "onMapEvent", getResponseBase() );
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
		view = inflater.inflate( R.layout.fragment_map, container, false );
        createMapView( view );
		sendEventMapLayersCreated();
        addHardwareKeyListener();
        return view;
    }

	protected WritableMap getResponseBase() {
		WritableMap params = new WritableNativeMap();
		params.putInt( "nativeTag", this.getId() );
		MapPosition mapPosition = mapView.map().getMapPosition();
		params.putDouble( "zoomLevel", mapPosition.getZoomLevel() );
		params.putDouble( "zoom", mapPosition.getZoom() );
		params.putDouble( "scale", mapPosition.getScale() );
		params.putDouble( "zoomScale", mapPosition.getZoomScale() );
		params.putDouble( "bearing", mapPosition.getBearing() );
		params.putDouble( "roll", mapPosition.getRoll() );
		params.putDouble( "tilt", mapPosition.getTilt() );
		// center
		WritableMap center = new WritableNativeMap();
		center.putDouble( "lng", mapPosition.getLongitude() );
		center.putDouble( "lat", mapPosition.getLatitude() );
		if ( null != hgtReader ) {
			Short altitude = hgtReader.getAltitudeAtPosition( (ReadableMap) center );
			if ( null == altitude ) {
				center.putNull( "alt" );
			} else {
				center.putDouble( "alt", altitude.doubleValue() );
			}
		}
		params.putMap( "center", center );
		return params;
	}

	public static File getDemFolder( String hgtDirPath ) {
		File demFolder = new File( hgtDirPath );
		if ( demFolder.exists() && demFolder.isDirectory() && demFolder.canRead() ) {
			return demFolder;
		}
		return null;
	}

    protected void sendLifecycleEvent( String type ) {
        WritableMap params = getResponseBase();
        params.putString( "type", type );
        Utils.sendEvent(  mapViewManager.getReactContext(), "MapLifecycle", params );
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
		relativeLayout = view.findViewById( R.id.mapView );
		relativeLayout.addView( mapView );
		fixViewLayoutSize();
        return mapView;
    }

	protected void fixViewLayoutSize() {
		ViewGroup.LayoutParams params = relativeLayout.getLayoutParams();
		params.width = (int) propWidthForLayoutSize;
		params.height = (int) propHeightForLayoutSize;
		view.setLayoutParams( params );
	}

	protected void updateViewLayoutSize( double widthForLayoutSize, double heightForLayoutSize ) {
		if ( widthForLayoutSize != propWidthForLayoutSize || heightForLayoutSize != propHeightForLayoutSize ) {
			propWidthForLayoutSize = widthForLayoutSize;
			propHeightForLayoutSize = heightForLayoutSize;
			fixViewLayoutSize();
		}
	}

}
