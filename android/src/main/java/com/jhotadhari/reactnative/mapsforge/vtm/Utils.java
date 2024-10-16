package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

	public static ReadableMap getEmptyReadableMap() {
		return new ReadableMap() {
			@NonNull
			@Override
			public Iterator<Map.Entry<String, Object>> getEntryIterator() {
				return null;
			}

			@Nullable
			@Override
			public ReadableArray getArray(@NonNull String s) {
				return null;
			}

			@Override
			public boolean getBoolean(@NonNull String s) {
				return false;
			}

			@Override
			public double getDouble(@NonNull String s) {
				return 0;
			}

			@NonNull
			@Override
			public Dynamic getDynamic(@NonNull String s) {
				return null;
			}

			@Override
			public int getInt(@NonNull String s) {
				return 0;
			}

			@Override
			public long getLong(@NonNull String s) {
				return 0;
			}

			@Nullable
			@Override
			public ReadableMap getMap(@NonNull String s) {
				return null;
			}

			@Nullable
			@Override
			public String getString(@NonNull String s) {
				return "";
			}

			@NonNull
			@Override
			public ReadableType getType(@NonNull String s) {
				return null;
			}

			@Override
			public boolean hasKey(@NonNull String s) {
				return false;
			}

			@Override
			public boolean isNull(@NonNull String s) {
				return false;
			}

			@NonNull
			@Override
			public ReadableMapKeySetIterator keySetIterator() {
				return null;
			}

			@NonNull
			@Override
			public HashMap<String, Object> toHashMap() {
				return null;
			}
		};
	}


	public static ReadableArray getEmptyReadableArray() {
		return new ReadableArray() {
			@NonNull
			@Override
			public ArrayList<Object> toArrayList() {
				return null;
			}

			@Override
			public int size() {
				return 0;
			}

			@Override
			public boolean isNull(int i) {
				return false;
			}

			@NonNull
			@Override
			public ReadableType getType(int i) {
				return null;
			}

			@NonNull
			@Override
			public String getString(int i) {
				return "";
			}

			@NonNull
			@Override
			public ReadableMap getMap(int i) {
				return null;
			}

			@Override
			public long getLong(int i) {
				return 0;
			}

			@Override
			public int getInt(int i) {
				return 0;
			}

			@NonNull
			@Override
			public Dynamic getDynamic(int i) {
				return null;
			}

			@Override
			public double getDouble(int i) {
				return 0;
			}

			@Override
			public boolean getBoolean(int i) {
				return false;
			}

			@NonNull
			@Override
			public ReadableArray getArray(int i) {
				return null;
			}
		};
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
