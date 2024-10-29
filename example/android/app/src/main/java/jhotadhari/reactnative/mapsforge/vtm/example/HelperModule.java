package jhotadhari.reactnative.mapsforge.vtm.example;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelperModule extends ReactContextBaseJavaModule {

	public String getName() { return "HelperModule"; };

	public HelperModule(ReactApplicationContext context) {
		super(context);
	}

	@ReactMethod
	public void getAppDirs(
		Promise promise
	) {
		WritableMap responseParams = new WritableNativeMap();

		// appDataDir
		responseParams.putString( "appDataDir", getReactApplicationContext().getApplicationInfo().dataDir );

		// externalMediaDir
		File[] externalMediaDirs = getReactApplicationContext().getExternalMediaDirs();
		if ( externalMediaDirs.length > 0 ) {
			responseParams.putString( "externalMediaDir", externalMediaDirs[0].toString() );
		}

		Map<String, String> mediaDirs = getMediaDirs();
		for ( Map.Entry<String, String> entry : mediaDirs.entrySet()) {
			responseParams.putString( entry.getKey(), entry.getValue() );
		}

		promise.resolve( responseParams );
	};

	public Map<String, String> getMediaDirs() {
		Map<String, String> mediaSubDirs = new HashMap<>();
		File[] externalMediaDirs = getReactApplicationContext().getExternalMediaDirs();
		if ( externalMediaDirs.length > 0 ) {
			String[] subdirs = {
				"dem",
				"mapfiles",
				"mapstyles",
				"tracks",
			};
			for ( int i = 0; i < subdirs.length; i++ ) {
				String pathName = externalMediaDirs[0].toString() + "/" + subdirs[i];
				File dir = new File( pathName );
				if ( ! dir.exists() ) {
					dir.mkdirs();
				}
				mediaSubDirs.put( subdirs[i], pathName );
			}
		}
		return mediaSubDirs;
	}
}

