package com.jhotadhari.reactnative.mapsforge.vtm;

import androidx.annotation.NonNull;

import com.facebook.react.BaseReactPackage;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.facebook.react.uimanager.ViewManager;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.CanvasAdapter;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerHillshading;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerMapsforge;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerMarker;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerMBTilesBitmap;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerPath;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerPathJts;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerScalebar;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.MapContainer;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerBitmapTile;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapsforgeVtmViewManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "implements ReactPackage" is redundant (BaseReactPackage already implements it) but required
// for the react-native CLI's autolinking regex to detect this as the module's package class.
public class MapsforgeVtmViewPackage extends BaseReactPackage implements ReactPackage {

	@NonNull
	@Override
	public List<ViewManager> createViewManagers( @NonNull ReactApplicationContext reactContext ) {
		List<ViewManager> viewManagers = new ArrayList<>();
		viewManagers.add( new MapsforgeVtmViewManager() );
		return viewManagers;
	}

	@Override
	public NativeModule getModule( @NonNull String s, @NonNull ReactApplicationContext reactApplicationContext ) {
		if ( MapContainer.NAME.equals( s ) ) {
			return new MapContainer( reactApplicationContext );
		}
		if ( LayerBitmapTile.NAME.equals( s ) ) {
			return new LayerBitmapTile( reactApplicationContext );
		}
		if ( LayerMarker.NAME.equals( s ) ) {
			return new LayerMarker( reactApplicationContext );
		}
		if ( LayerPath.NAME.equals( s ) ) {
			return new LayerPath( reactApplicationContext );
		}
		if ( LayerPathJts.NAME.equals( s ) ) {
			return new LayerPathJts( reactApplicationContext );
		}
		if ( LayerScalebar.NAME.equals( s ) ) {
			return new LayerScalebar( reactApplicationContext );
		}
		if ( LayerMBTilesBitmap.NAME.equals( s ) ) {
			return new LayerMBTilesBitmap( reactApplicationContext );
		}
		if ( LayerHillshading.NAME.equals( s ) ) {
			return new LayerHillshading( reactApplicationContext );
		}
		if ( LayerMapsforge.NAME.equals( s ) ) {
			return new LayerMapsforge( reactApplicationContext );
		}
		if ( CanvasAdapter.NAME.equals( s ) ) {
			return new CanvasAdapter( reactApplicationContext );
		}
		return null;
	}

	@NonNull
	@Override
	public ReactModuleInfoProvider getReactModuleInfoProvider() {
		return new ReactModuleInfoProvider() {
			@NonNull
			@Override
			public Map<String, ReactModuleInfo> getReactModuleInfos() {
				Map<String, ReactModuleInfo> map = new HashMap<>();
				map.put( MapsforgeVtmViewManager.NAME, new ReactModuleInfo(
					MapsforgeVtmViewManager.NAME, 	// name
					MapsforgeVtmViewManager.NAME, 	// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( MapContainer.NAME, new ReactModuleInfo(
					MapContainer.NAME,				// name
					MapContainer.NAME,				// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerBitmapTile.NAME, new ReactModuleInfo(
					LayerBitmapTile.NAME,			// name
					LayerBitmapTile.NAME,			// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerMarker.NAME, new ReactModuleInfo(
					LayerMarker.NAME,				// name
					LayerMarker.NAME,				// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerPath.NAME, new ReactModuleInfo(
					LayerPath.NAME,					// name
					LayerPath.NAME,					// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerPathJts.NAME, new ReactModuleInfo(
					LayerPathJts.NAME,				// name
					LayerPathJts.NAME,				// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerScalebar.NAME, new ReactModuleInfo(
					LayerScalebar.NAME,				// name
					LayerScalebar.NAME,				// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerMBTilesBitmap.NAME, new ReactModuleInfo(
					LayerMBTilesBitmap.NAME,		// name
					LayerMBTilesBitmap.NAME,		// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerHillshading.NAME, new ReactModuleInfo(
					LayerHillshading.NAME,			// name
					LayerHillshading.NAME,			// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( LayerMapsforge.NAME, new ReactModuleInfo(
					LayerMapsforge.NAME,			// name
					LayerMapsforge.NAME,			// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				map.put( CanvasAdapter.NAME, new ReactModuleInfo(
					CanvasAdapter.NAME,				// name
					CanvasAdapter.NAME,				// className
					false,							// canOverrideExistingModule
					false,							// needsEagerInit
					false,							// isCxxModule
					true							// isTurboModule
				) );
				return map;
			}
		};
	}
}
