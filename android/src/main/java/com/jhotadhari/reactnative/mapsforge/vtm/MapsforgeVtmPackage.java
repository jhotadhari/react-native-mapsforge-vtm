package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapContainerModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerBitmapTileModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerHillshadingModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerMBTilesBitmapModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerMapsforgeModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerMarkerModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerPathModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerPathSlopeGradientModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.modules.MapLayerScalebarModule;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapViewManager;

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
        modules.add(new MapLayerHillshadingModule(reactContext));
        modules.add(new MapLayerScalebarModule(reactContext));
        modules.add(new MapLayerPathModule(reactContext));
        modules.add(new MapLayerPathSlopeGradientModule(reactContext));
        modules.add(new MapLayerMarkerModule(reactContext));
//        modules.add(new MapMarkerModule(reactContext));
//        modules.add(new MapPolylineModule(reactContext));
        return modules;
    }

}
