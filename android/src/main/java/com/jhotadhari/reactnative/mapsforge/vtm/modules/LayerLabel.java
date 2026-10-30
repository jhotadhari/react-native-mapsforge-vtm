package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LabelLayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerLabelSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TurboModule behind the {@code LayerLabel} component: a text label rendered
 * into the map (bitmap layer) at a screen position, optionally following a
 * geographic point on the line to the screen anchor — see
 * {@link LabelLayerManager}.
 */
@ReactModule( name = LayerLabel.NAME )
public class LayerLabel extends NativeLayerLabelSpec {

	public static final String NAME = "LayerLabel";
	private static final String TAG = "LayerLabel";

	public LayerLabel( ReactApplicationContext reactContext ) {
		super( reactContext );
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected Map<String, Object> getTypedExportedConstants() {
		final Map<String, Object> constants = new HashMap<>();
		WritableMap paint = new WritableNativeMap();
		paint.putInt( "fontSize", 12 );
		paint.putString( "textColor", "#000000" );
		paint.putString( "backgroundColor", "#ffffff" );
		paint.putDouble( "backgroundAlpha", 1d );
		paint.putInt( "borderRadius", 6 );
		paint.putInt( "paddingHorizontal", 8 );
		paint.putInt( "paddingVertical", 3 );
		paint.putString( "fontFamily", "DEFAULT" );
		paint.putString( "fontStyle", "BOLD" );
		constants.put( "paint", paint );
		return constants;
	}

	@Override
	public void createLabel( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" );
				return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseRejectMapViewGone( promise );
				return;
			}

			String uuid = UUID.randomUUID().toString();

			String fragmentUuid = Utils.rMapHasKey( params, "fragmentUuid" )
				? params.getString( "fragmentUuid" )
				: LabelLayerManager.DEFAULT_FRAGMENT_UUID;

			LabelLayerManager manager = LabelLayerManager.get( nativeNodeHandle, mapView );
			manager.create( uuid, fragmentUuid, params, mapFragment,
				mapFragment.getActivity().getContentResolver(),
				getReactApplicationContext() );

			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString( "uuid", uuid );
			responseParams.putInt( "nativeNodeHandle", nativeNodeHandle );
			promise.resolve( responseParams );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void updateLabel( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "uuid" ) ) {
				Utils.promiseReject( promise, "Undefined uuid" );
				return;
			}
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" );
				return;
			}
			String uuid = params.getString( "uuid" );
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );

			LabelLayerManager manager = LabelLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				Utils.promiseReject( promise, "LabelLayerManager not found" );
				return;
			}
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapFragment || null == mapFragment.getActivity() ) {
				Utils.promiseReject( promise, "Unable to find mapFragment" );
				return;
			}

			WritableMap responseParams = manager.update( uuid, params, mapFragment,
				mapFragment.getActivity().getContentResolver() );

			promise.resolve( responseParams != null ? responseParams : new WritableNativeMap() );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeLabel( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" );
				return;
			}
			if ( ! Utils.rMapHasKey( params, "uuid" ) ) {
				Utils.promiseReject( promise, "Undefined uuid" );
				return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			String uuid = params.getString( "uuid" );

			LabelLayerManager manager = LabelLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				// Map already destroyed — resolve with the uuid.
				promise.resolve( uuid );
				return;
			}

			manager.remove( uuid );
			promise.resolve( uuid );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}
}
