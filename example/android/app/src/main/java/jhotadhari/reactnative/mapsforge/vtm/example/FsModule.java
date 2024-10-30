package jhotadhari.reactnative.mapsforge.vtm.example;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;

public class FsModule extends ReactContextBaseJavaModule {

	final static int APP_STORAGE_ACCESS_REQUEST_CODE = 501; // Any value

	ReactContext reactContext;

	public FsModule(@Nullable ReactApplicationContext reactContext_) {
		super(reactContext_);
		reactContext = reactContext_;
	}

	@Override
    public String getName() {
        return "FsModule";
    }

    @ReactMethod
    public void getInfo( String navDir, Promise promise ) {
        try {
            WritableMap response = new WritableNativeMap();
            File path = new File( navDir );

            // navParent
			response.putString( "navParent", String.valueOf( path.getParent() ) );

            // navChildren
            WritableArray navChildrenArray = new WritableNativeArray();
            if ( path.isDirectory() ) {
                File[] files = path.listFiles();
                for (int i = 0; i < files.length; i++) {
                    WritableMap fileInfoMap = new WritableNativeMap();
                    fileInfoMap.putString( "name", files[i].toString() );
                    fileInfoMap.putBoolean( "isDir", files[i].isDirectory() );
                    fileInfoMap.putBoolean( "isFile", files[i].isFile() );
                    fileInfoMap.putBoolean( "canRead", files[i].canRead() );
                    fileInfoMap.putBoolean( "canExecute", files[i].canExecute() );
                    navChildrenArray.pushMap( fileInfoMap );
                }

            }
			response.putArray( "navChildren", navChildrenArray );

            // Return response
            promise.resolve( response );
        } catch(Exception e) {
            promise.reject("Error", e);
        }
    }

}
