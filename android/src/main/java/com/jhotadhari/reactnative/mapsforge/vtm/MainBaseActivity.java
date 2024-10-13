package com.jhotadhari.reactnative.mapsforge.vtm;

import android.view.KeyEvent;

import com.facebook.react.ReactActivity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class MainBaseActivity extends ReactActivity {

    protected Map<String, HardwareKeyListener> hardwareKeyListeners = new HashMap<>();

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ( event.getDownTime() != event.getEventTime() ) { // only on up events
            for ( HardwareKeyListener hardwareKeyListener : hardwareKeyListeners.values() ) {
                hardwareKeyListener.onKeyUp( event.getKeyCode(), event );
            }
        }
//        return super.dispatchKeyEvent(event);
        return true;
    }

    public String addHardwareKeyListener( HardwareKeyListener hardwareKeyListener ) {
        String uid = UUID.randomUUID().toString();
        hardwareKeyListeners.put( uid, hardwareKeyListener );
        return uid;
    }

    public void removeHardwareKeyListener( String uid ) {
        hardwareKeyListeners.remove( uid );
    }


}

