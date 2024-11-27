package com.jhotadhari.reactnative.mapsforge.vtm;

import android.view.KeyEvent;

import com.facebook.react.ReactActivity;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class MainBaseActivity extends ReactActivity {

    protected Map<String, HardwareKeyListener> hardwareKeyListeners = new HashMap<>();

    public static String getKeyEventKeyCodeString( KeyEvent event ) {
        String keyCodeString = null;
        for ( Field field : KeyEvent.class.getFields() ) {
            if ( null == keyCodeString && field.getName().startsWith( "KEYCODE_" ) ) {
                try {
                    int fieldKeyCode = (int) field.get( event );
                    if ( fieldKeyCode == event.getKeyCode() ) {
                        keyCodeString = field.getName();
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return keyCodeString;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ( ! hardwareKeyListeners.isEmpty() ) {
            String keyCodeString = getKeyEventKeyCodeString( event );
            for ( HardwareKeyListener hardwareKeyListener : hardwareKeyListeners.values() ) {

                boolean emitEvent = false;
                for ( String buttonKeyCodeString : hardwareKeyListener.buttons ) {
                    emitEvent = emitEvent || buttonKeyCodeString.equals( keyCodeString );
                }

                if ( emitEvent ) {
                    if ( event.getDownTime() != event.getEventTime() ) { // only on up events
                        hardwareKeyListener.onKeyUp( keyCodeString, event );
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
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

