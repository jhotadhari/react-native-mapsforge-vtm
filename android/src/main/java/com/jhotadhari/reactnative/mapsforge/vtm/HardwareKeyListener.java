package com.jhotadhari.reactnative.mapsforge.vtm;

import android.view.KeyEvent;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.List;

public class HardwareKeyListener {

    protected final List<String> buttons;
    protected final ReactContext mReactContext;
    protected final int mNativeNodeHandle;

    protected boolean onKeyUp( String keyCodeString, KeyEvent event ) {
        WritableMap params = new WritableNativeMap();
        params.putInt( "nativeNodeHandle", mNativeNodeHandle );
        params.putInt( "keyCode", event.getKeyCode() );
        params.putString( "keyCodeString", keyCodeString );
        Utils.sendEvent( mReactContext, "onHardwareKeyUp", params );
        return true;
    }

    public HardwareKeyListener( List<String> buttons, ReactContext reactContext, int nativeNodeHandle ) {
		this.buttons = buttons;
		this.mReactContext = reactContext;
		this.mNativeNodeHandle = nativeNodeHandle;
	}


}
