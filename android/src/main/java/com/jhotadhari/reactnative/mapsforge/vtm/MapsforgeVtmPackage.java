package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsforgeVtmPackage implements ReactPackage {

   @Override
   public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
       return Arrays.<ViewManager>asList(
            new MapViewManager(reactContext)
       );
   }

    @Override
    public List<NativeModule> createNativeModules(
        ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new MapContainerModule(reactContext));
        modules.add(new MapLayerMapsforgeModule(reactContext));
        modules.add(new MapLayerBitmapTileModule(reactContext));
        modules.add(new MapLayerMBTilesBitmapModule(reactContext));
        modules.add(new MapLayerScalebarModule(reactContext));
        modules.add(new MapLayerPathModule(reactContext));
        modules.add(new MapLayerPathSlopeGradientModule(reactContext));
//        modules.add(new MapMarkerModule(reactContext));
//        modules.add(new MapPolylineModule(reactContext));
        return modules;
    }

}
