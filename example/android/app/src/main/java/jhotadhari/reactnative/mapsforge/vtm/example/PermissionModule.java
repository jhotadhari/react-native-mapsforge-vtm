package jhotadhari.reactnative.mapsforge.vtm.example;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

public class PermissionModule extends ReactContextBaseJavaModule {

	final static int APP_STORAGE_ACCESS_REQUEST_CODE = 501; // Any value

	public final ActivityEventListener mActivityEventListener = new BaseActivityEventListener(){
		@Override
		public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
			WritableMap params = new WritableNativeMap();
				params.putBoolean( "granted", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() );
			if ( APP_STORAGE_ACCESS_REQUEST_CODE == requestCode ) {
				Utils.sendEvent( reactContext, "PermissionsMaybeChanged", params );
			}
		}
	};

	ReactContext reactContext;

	public PermissionModule(@Nullable ReactApplicationContext reactContext_) {
		super(reactContext_);
		reactContext = reactContext_;
		reactContext.addActivityEventListener(mActivityEventListener);
	}

	@Override
    public String getName() {
        return "PermissionModule";
    }

    @ReactMethod
    public void getStatus( Promise promise ) {
        try {
            WritableMap response = new WritableNativeMap();
            Boolean needExternalStorageManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
            Boolean isExternalStorageManager = false;
            if ( needExternalStorageManager ) {
                isExternalStorageManager = Environment.isExternalStorageManager();
            }
            response.putBoolean( "needExternalStorageManager", needExternalStorageManager );
            response.putBoolean( "isExternalStorageManager", isExternalStorageManager );
            promise.resolve( response );
        } catch(Exception e) {
            promise.reject("Error", e);
        }
    }

    @ReactMethod
    public void requestPermission( Promise promise ) {
        try {
            if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.R ) {
				promise.resolve(false);
			} else {
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				Uri uri = Uri.fromParts("package", reactContext.getPackageName(), null);

				Log.d("uri.toString()", uri.toString());


				intent.setData( uri );
				reactContext.startActivityForResult(intent, APP_STORAGE_ACCESS_REQUEST_CODE, new Bundle() );
				promise.resolve(true);
            }
            promise.resolve(false);
        } catch(Exception e) {
            promise.reject("Error", e);
        }
    }

}
