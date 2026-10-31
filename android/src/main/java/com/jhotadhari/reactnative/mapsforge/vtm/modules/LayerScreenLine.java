package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeScreenLineSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.ScreenLineLayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TurboModule behind the {@code LayerScreenLine} component: a polyline
 * rebuilt per frame on the GL thread from the frame's own viewport —
 * zero-latency tracking of screen-anchored points (see
 * {@link ScreenLineLayerManager}).
 */
@ReactModule( name = LayerScreenLine.NAME )
public class LayerScreenLine extends NativeScreenLineSpec {

	public static final String NAME = "LayerScreenLine";
	private static final String TAG = "LayerScreenLine";

	public LayerScreenLine( ReactApplicationContext reactContext ) {
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
		paint.putString( "strokeColor", "#000000" );
		paint.putDouble( "strokeWidth", 2d );
		paint.putString( "cap", "ROUND" );
		constants.put( "paint", paint );
		return constants;
	}

	@Override
	public void createLine( ReadableMap params, Promise promise ) {
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
				: ScreenLineLayerManager.DEFAULT_FRAGMENT_UUID;

			ScreenLineLayerManager manager = ScreenLineLayerManager.get( nativeNodeHandle, mapView );
			manager.create( uuid, fragmentUuid, params, mapFragment,
				mapFragment.getActivity().getContentResolver(),
				getReactApplicationContext() );

			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString( "uuid", uuid );
			responseParams.putInt( "nativeNodeHandle", nativeNodeHandle );
			promise.resolve( responseParams );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e );
		}
	}

	@Override
	public void updateLine( ReadableMap params, Promise promise ) {
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

			ScreenLineLayerManager manager = ScreenLineLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				Utils.promiseReject( promise, "ScreenLineLayerManager not found" );
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
			Utils.promiseReject( promise, e );
		}
	}

	@Override
	public void removeLine( ReadableMap params, Promise promise ) {
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

			ScreenLineLayerManager manager = ScreenLineLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				// Map already destroyed — resolve with the uuid.
				promise.resolve( uuid );
				return;
			}

			manager.remove( uuid );
			promise.resolve( uuid );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e );
		}
	}
}
