package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;

//import org.mapsforge.core.model.LatLong;
//import org.mapsforge.core.model.Point;
//import org.mapsforge.map.android.view.MapView;

public class Utils {

    public static MapFragment getMapFragment(ReactContext reactContext, int reactTag ) {
        try {
            FragmentActivity activity = (FragmentActivity) reactContext.getCurrentActivity();
            if ( null == activity ) {
                return null;
            }
            MapFragment mapFragment = (MapFragment) activity.getSupportFragmentManager().findFragmentById( (int) reactTag );
            return mapFragment;
        } catch(Exception e) {
            return null;
        }
    }

    public static MapView getMapView(ReactContext reactContext, int reactTag ) {
        try {
            MapFragment mapFragment = getMapFragment( reactContext, reactTag );
            if ( null == mapFragment ) {
                return null;
            }
            MapView mapView = (MapView) mapFragment.getMapView();
            return mapView;
        } catch(Exception e) {
            return null;
        }
    }

    public static double convertPixelsToDp(ReactContext reactContext, double pixels) {
        double screenPixelDensity = reactContext.getApplicationContext().getResources().getDisplayMetrics().density;
        return pixels / screenPixelDensity;
    }


    public static void sendEvent( ReactContext reactContext, String eventName, @Nullable WritableMap params ) {
        reactContext.getJSModule(
                DeviceEventManagerModule.RCTDeviceEventEmitter.class
        ).emit( eventName, params );
    }

    public static WritableArray mapPositionToArray( MapPosition mapPosition ) {
        WritableArray latLongArray = new WritableNativeArray();
        latLongArray.pushDouble( mapPosition.getLatitude() );
        latLongArray.pushDouble( mapPosition.getLongitude() );
        return latLongArray;
    }

//    public static LatLong aarrayToLatLong( ReadableArray latLong ) {
//        return new LatLong(
//            (Double) latLong.toArrayList().get(0),
//            (Double) latLong.toArrayList().get(1)
//        );
//    }
//    public static LatLong arrayToLatLong( ReadableArray latLong ) {
//        return new LatLong(
//            (Double) latLong.toArrayList().get(0),
//            (Double) latLong.toArrayList().get(1)
//        );
//    }
    public static GeoPoint arrayToGeoPoint( ReadableArray arr ) {

		GeoPoint geoPoint = new GeoPoint(
			(double) arr.toArrayList().get(0),
			(double) arr.toArrayList().get(1)
		);



        return geoPoint;
    }

//    public static WritableMap pointToObj( Point point ) {
//        WritableMap pointMap = new WritableNativeMap();
//        pointMap.putDouble( "y", point.y );
//        pointMap.putDouble( "x", point.x );
//        return pointMap;
//    }

}
