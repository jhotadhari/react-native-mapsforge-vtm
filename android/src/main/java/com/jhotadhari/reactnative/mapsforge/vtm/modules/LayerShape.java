package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerShapeSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.ShapeLayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TurboModule for the {@code LayerShape} component.
 *
 * <p>Delegates to {@link ShapeLayerManager}, which collapses all
 * {@code LayerShape} components into shared {@code VectorLayer}s
 * per fragment per map view — one native layer per consecutive run
 * of same-type components.
 */
@ReactModule(name = LayerShape.NAME)
public class LayerShape extends NativeLayerShapeSpec {

	public static final String NAME = "LayerShape";
	private static final String TAG = "LayerShape";

	public LayerShape(ReactApplicationContext reactContext) {
		super(reactContext);
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected Map<String, Object> getTypedExportedConstants() {
		final Map<String, Object> constants = new HashMap<>();
		WritableMap style = new WritableNativeMap();
		style.putDouble("strokeWidth", 2);
		style.putString("strokeColor", "#4488ff");
		constants.put("style", style);
		constants.put("gestureScreenDistance", 20d);
		return constants;
	}

	// ── createLayer ──────────────────────────────────────────────────────

	@Override
	public void createLayer(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "nativeNodeHandle")) {
				Utils.promiseReject(promise, "Undefined nativeNodeHandle");
				return;
			}
			int nativeNodeHandle = params.getInt("nativeNodeHandle");
			MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);
			MapFragment mapFragment = Utils.getMapFragment(getReactApplicationContext(), nativeNodeHandle);
			if (null == mapView || null == mapFragment) {
				Utils.promiseReject(promise, "Unable to find mapView or mapFragment");
				return;
			}

			if (!Utils.rMapHasKey(params, "shape")) {
				Utils.promiseReject(promise, "Missing 'shape' parameter");
				return;
			}

			String uuid = UUID.randomUUID().toString();

			// Resolve fragment uuid from JS params.
			String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
				? params.getString("fragmentUuid")
				: ShapeLayerManager.DEFAULT_FRAGMENT_UUID;

			// Delegate to ShapeLayerManager.
			ShapeLayerManager manager = ShapeLayerManager.get(nativeNodeHandle, mapView);
			manager.setEventCallback((eventName, payload) -> {
				if ("onShapeEvent".equals(eventName)) {
					emitOnShapeEvent(payload);
				}
			});

			manager.create(uuid, fragmentUuid, params, mapFragment,
				mapFragment.getActivity().getContentResolver(),
				getReactApplicationContext());

			// Build response.
			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString("uuid", uuid);
			responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
			ReadableMap shapeMap = params.getMap("shape");
			WritableMap shapeResponse = new WritableNativeMap();
			shapeResponse.putString("type", shapeMap.getString("type"));
			responseParams.putMap("shape", shapeResponse);

			promise.resolve(responseParams);
		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── removeLayer ──────────────────────────────────────────────────────

	@Override
	public void removeLayer(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "uuid") || !Utils.rMapHasKey(params, "nativeNodeHandle")) {
				Utils.promiseReject(promise, "Undefined uuid or nativeNodeHandle");
				return;
			}
			int nativeNodeHandle = params.getInt("nativeNodeHandle");
			String uuid = params.getString("uuid");

			ShapeLayerManager manager = ShapeLayerManager.getInstance(nativeNodeHandle);
			if (manager == null) {
				promise.resolve(uuid);
				return;
			}
			manager.remove(uuid);
			promise.resolve(uuid);
		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── updateShape ──────────────────────────────────────────────────────

	@Override
	public void updateShape(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "nativeNodeHandle") || !Utils.rMapHasKey(params, "uuid")) {
				Utils.promiseReject(promise, "Undefined nativeNodeHandle or uuid");
				return;
			}
			String uuid = params.getString("uuid");
			int nativeNodeHandle = params.getInt("nativeNodeHandle");

			MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);
			if (null == mapView) {
				Utils.promiseReject(promise, "Unable to find mapView");
				return;
			}
			MapFragment mapFragment = Utils.getMapFragment(getReactApplicationContext(), nativeNodeHandle);
			if (null == mapFragment) {
				Utils.promiseReject(promise, "Unable to find mapFragment");
				return;
			}

			ShapeLayerManager manager = ShapeLayerManager.getInstance(nativeNodeHandle);
			if (manager == null) {
				Utils.promiseReject(promise, "ShapeLayerManager not found");
				return;
			}

			manager.update(uuid, params, mapFragment,
				mapFragment.getActivity().getContentResolver());

			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString("uuid", uuid);
			responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
			promise.resolve(responseParams);
		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── triggerEvent ─────────────────────────────────────────────────────

	@Override
	public void triggerEvent(ReadableMap params) {
		if (!Utils.rMapHasKey(params, "nativeNodeHandle") || !Utils.rMapHasKey(params, "uuid")
			|| !Utils.rMapHasKey(params, "x") || !Utils.rMapHasKey(params, "y")) {
			return;
		}
		int nativeNodeHandle = params.getInt("nativeNodeHandle");
		String uuid = params.getString("uuid");
		float x = (float) params.getDouble("x");
		float y = (float) params.getDouble("y");

		MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);

		ShapeLayerManager manager = ShapeLayerManager.getInstance(nativeNodeHandle);
		if (manager == null) {
			return;
		}
		WritableMap eventParams = manager.triggerEvent(uuid, x, y);
		if (null == eventParams) {
			return;
		}
		eventParams.putInt("nativeNodeHandle", nativeNodeHandle);
		eventParams.putString("type", "trigger");
		if (mapView != null) {
			GeoPoint eventPoint = mapView.map().viewport().fromScreenPoint(x, y);
			eventParams.putArray("eventPosition", Utils.positionToWritableArray(
				eventPoint.getLongitude(),
				eventPoint.getLatitude(),
				null
			));
		}
		emitOnShapeEvent(eventParams);
	}
}
