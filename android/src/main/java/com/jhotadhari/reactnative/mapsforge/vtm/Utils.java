package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.Context;
import android.content.UriPermission;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;

import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.List;

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

	public static boolean hasScopedStoragePermission( Context context, String string, boolean checkWritePermission ) {
		// list of all persisted permissions for our app
		List<UriPermission> uriList = context.getContentResolver().getPersistedUriPermissions();
		try {
			// Fake "document" to tree. "document" is first part of path.
			URI jUri = new URI( string );
			String[] pathArray= jUri.getPath().substring(1 ).split( "/" );
			Array.set( pathArray, 0, "tree" );
			String testString = jUri.getScheme() + "://" + jUri.getHost() + "/" + String.join( "/", pathArray );

			for ( UriPermission uriPermission : uriList ) {
				String uriString = URLDecoder.decode( uriPermission.getUri().toString() );
				if ( ( uriString.startsWith( testString ) || testString.startsWith( uriString ) ) && uriPermission.isReadPermission() && ( ! checkWritePermission || uriPermission.isWritePermission() ) ) {
					return true;
				}
			}
		} catch ( URISyntaxException e ) {
			e.printStackTrace();
		}
		return false;
	}
}
