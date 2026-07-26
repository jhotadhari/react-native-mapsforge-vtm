package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.content.ContentResolver;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerPathJtsSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.PathLayerJtsWrapper;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.vector.geometries.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TurboModule for the {@code LayerPathJts} component.
 *
 * <p>Creates a dedicated {@link PathLayerJtsWrapper} (which wraps vtm-jts's
 * {@code org.oscim.layers.vector.PathLayer}) per JS component instance.
 * This gives each path its own native layer — simpler lifecycle and guaranteed
 * render ordering, at the cost of higher per-instance overhead compared to the
 * shared-layer {@code LayerPath}.
 */
@ReactModule(name = LayerPathJts.NAME)
public class LayerPathJts extends NativeLayerPathJtsSpec {

	public static final String NAME = "LayerPathJts";

	// ── Coalesced map updates ───────────────────────────────────────────
	// Dispatches updateMap() to the UI thread via Handler.post, matching the
	// pattern used by LayerManager.scheduleUpdate(). The native-module thread
	// must never call updateMap() directly — vtm's Map is not thread-safe.

	private final java.util.Map<Integer, AtomicBoolean> updatePendingMap = new HashMap<>();
	private final Handler uiHandler = new Handler(Looper.getMainLooper());

	private void scheduleUpdate(@NonNull MapView mapView) {
		int handle = mapView.hashCode();
		AtomicBoolean pending = updatePendingMap.computeIfAbsent(handle, k -> new AtomicBoolean(false));
		if (pending.compareAndSet(false, true)) {
			uiHandler.post(() -> {
				pending.set(false);
				if (mapView.map() != null) {
					mapView.map().updateMap();
				}
			});
		}
	}

	public LayerPathJts(ReactApplicationContext reactContext) {
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
		style.putDouble("strokeWidth", 4);
		style.putString("strokeColor", "#ff0000");
		constants.put("paint", style);
		WritableMap responseInclude = new WritableNativeMap();
		responseInclude.putInt("coordinates", 0);
		responseInclude.putInt("bounds", 0);
		constants.put("gestureScreenDistance", 20d);
		constants.put("responseInclude", responseInclude);
		return constants;
	}

	// ── Style parsing (mirrors LayerPath.getStyleBuilderFromMap) ──────────

	@NonNull
	private Style.Builder getStyleBuilderFromMap(@Nullable ReadableMap styleMap) {
		ReadableMap styleConstants = (ReadableMap) getConstants().get("paint");
		double strokeWidth = (styleMap != null && Utils.rMapHasKey(styleMap, "strokeWidth"))
			? styleMap.getDouble("strokeWidth")
			: styleConstants.getDouble("strokeWidth");
		String strokeColor = (styleMap != null && Utils.rMapHasKey(styleMap, "strokeColor"))
			? styleMap.getString("strokeColor")
			: styleConstants.getString("strokeColor");

		Style.Builder styleBuilder = Style.builder();
		styleBuilder.strokeWidth((float) strokeWidth);
		styleBuilder.strokeColor(Color.parseColor(Objects.requireNonNull(strokeColor)));

		if (styleMap != null) {
			if (styleMap.hasKey("fillColor")) {
				styleBuilder.fillColor(Color.parseColor(
					Objects.requireNonNull(styleMap.getString("fillColor"))));
			}
			if (styleMap.hasKey("fillAlpha")) {
				styleBuilder.fillAlpha((float) styleMap.getDouble("fillAlpha"));
			}
			if (styleMap.hasKey("buffer")) {
				styleBuilder.buffer(styleMap.getDouble("buffer"));
			}
			if (styleMap.hasKey("scalingZoomLevel")) {
				styleBuilder.scaleZoomLevel((int) styleMap.getDouble("scalingZoomLevel"));
			}
			if (styleMap.hasKey("cap")) {
				Paint.Cap cap = switch (Objects.requireNonNull(styleMap.getString("cap"))) {
					case "ROUND" -> Paint.Cap.ROUND;
					case "BUTT" -> Paint.Cap.BUTT;
					case "SQUARE" -> Paint.Cap.SQUARE;
					default -> null;
				};
				if (cap != null) {
					styleBuilder.cap(cap);
				}
			}
			if (styleMap.hasKey("fixed")) {
				styleBuilder.fixed(styleMap.getBoolean("fixed"));
			}
			if (styleMap.hasKey("strokeIncrease")) {
				styleBuilder.strokeIncrease(styleMap.getDouble("strokeIncrease"));
			}
			if (styleMap.hasKey("blur")) {
				styleBuilder.blur((float) styleMap.getDouble("blur"));
			}
			if (styleMap.hasKey("stipple")) {
				styleBuilder.stipple(styleMap.getInt("stipple"));
			}
			if (styleMap.hasKey("stippleColor")) {
				styleBuilder.stippleColor(Color.parseColor(
					Objects.requireNonNull(styleMap.getString("stippleColor"))));
			}
			if (styleMap.hasKey("stippleWidth")) {
				styleBuilder.stippleWidth((float) styleMap.getDouble("stippleWidth"));
			}
			if (styleMap.hasKey("dropDistance")) {
				styleBuilder.dropDistance((float) styleMap.getDouble("dropDistance"));
			}
			if (styleMap.hasKey("textureRepeat")) {
				styleBuilder.textureRepeat(styleMap.getBoolean("textureRepeat"));
			}
			if (styleMap.hasKey("heightOffset")) {
				styleBuilder.heightOffset((float) styleMap.getDouble("heightOffset"));
			}
			if (styleMap.hasKey("randomOffset")) {
				styleBuilder.randomOffset(styleMap.getBoolean("randomOffset"));
			}
			if (styleMap.hasKey("transparent")) {
				styleBuilder.transparent(styleMap.getBoolean("transparent"));
			}
			// JTS-specific: generalization level for Douglas-Peucker simplification.
			if (styleMap.hasKey("generalization")) {
				styleBuilder.generalization(styleMap.getInt("generalization"));
			}
		}

		return styleBuilder;
	}

	// ── Coordinate parsing ───────────────────────────────────────────────

	@NonNull
	private static List<GeoPoint> readableArrayToGeoPoints(@NonNull ReadableArray coordinates) {
		List<GeoPoint> points = new ArrayList<>(coordinates.size());
		for (int i = 0; i < coordinates.size(); i++) {
			if (coordinates.getType(i) == ReadableType.Array) {
				ReadableArray position = coordinates.getArray(i);
				points.add(new GeoPoint(
					Utils.latFromPosition(position),
					Utils.lngFromPosition(position)
				));
			}
		}
		return points;
	}

	// ── Response helpers ─────────────────────────────────────────────────

	private void addResponseData(
		@NonNull List<GeoPoint> points,
		@Nullable ReadableMap responseInclude,
		@NonNull WritableMap responseParams
	) {
		if (responseInclude == null) {
			return;
		}
		if (responseInclude.hasKey("coordinates") && responseInclude.getInt("coordinates") > 0) {
			com.facebook.react.bridge.WritableArray arr = Arguments.createArray();
			for (GeoPoint gp : points) {
				arr.pushArray(Utils.positionToWritableArray(
					gp.getLongitude(), gp.getLatitude(), null));
			}
			responseParams.putArray("coordinates", arr);
		}
		if (responseInclude.hasKey("bounds") && responseInclude.getInt("bounds") > 0) {
			double minLng = Double.MAX_VALUE, minLat = Double.MAX_VALUE;
			double maxLng = Double.MIN_VALUE, maxLat = Double.MIN_VALUE;
			for (GeoPoint gp : points) {
				if (gp.getLongitude() < minLng) minLng = gp.getLongitude();
				if (gp.getLatitude() < minLat) minLat = gp.getLatitude();
				if (gp.getLongitude() > maxLng) maxLng = gp.getLongitude();
				if (gp.getLatitude() > maxLat) maxLat = gp.getLatitude();
			}
			com.facebook.react.bridge.WritableArray bbox = Arguments.createArray();
			bbox.pushDouble(minLng);
			bbox.pushDouble(minLat);
			bbox.pushDouble(maxLng);
			bbox.pushDouble(maxLat);
			responseParams.putArray("bbox", bbox);
		}
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

			ReadableArray coordinates = Utils.rMapHasKey(params, "coordinates")
				? params.getArray("coordinates")
				: null;
			if (coordinates == null || coordinates.size() < 2) {
				Utils.promiseReject(promise, "Missing or insufficient coordinates");
				return;
			}

			// Parse style.
			ReadableMap styleMap = Utils.rMapHasKey(params, "paint")
				? params.getMap("paint")
				: null;
			Style style = getStyleBuilderFromMap(styleMap).build();

			// Create the dedicated JTS PathLayer wrapper.
			PathLayerJtsWrapper pathLayer = new PathLayerJtsWrapper(
				mapView.map(), style);

			// Set coordinates.
			List<GeoPoint> points = readableArrayToGeoPoints(coordinates);
			pathLayer.setPoints(points);

			// Generate uuid.
			String uuid = UUID.randomUUID().toString();
			pathLayer.setEntryUuid(uuid);

			// Set up gesture support.
			boolean supportsGestures = Utils.rMapHasKey(params, "supportsGestures")
				&& params.getBoolean("supportsGestures");
			if (supportsGestures) {
				double gestureScreenDistance = Utils.rMapHasKey(params, "gestureScreenDistance")
					? params.getDouble("gestureScreenDistance")
					: 20d;

				pathLayer.setGestureScreenDistance((float) gestureScreenDistance);
				pathLayer.setGestureCallback((type, payload) -> {
					// Add nativeNodeHandle and emit through codegen EventEmitter.
					payload.putInt("nativeNodeHandle", nativeNodeHandle);
					emitOnPathJtsEvent(payload);
				});
			}

			// Register the layer via LayerHelper (async, through MapMutationQueue).
			ReadableMap responseInclude = Utils.rMapHasKey(params, "responseInclude")
				? params.getMap("responseInclude")
				: null;

			LayerHelper layerHelper = new LayerHelper(this, getReactApplicationContext());

			// Build helper params that include positionIndex for position-aware insertion.
			WritableMap helperParams = new WritableNativeMap();
			helperParams.putInt("nativeNodeHandle", nativeNodeHandle);
			if (Utils.rMapHasKey(params, "positionIndex")) {
				helperParams.putInt("positionIndex", params.getInt("positionIndex"));
			}

			layerHelper.addLayerAsync(pathLayer, helperParams, uuid)
				.thenAccept(resolvedUuid -> {
					// Build response.
					WritableMap responseParams = new WritableNativeMap();
					responseParams.putString("uuid", resolvedUuid);
					responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
					addResponseData(points, responseInclude, responseParams);
					promise.resolve(responseParams);
				})
				.exceptionally(t -> {
					Utils.promiseReject(promise, t.getMessage());
					return null;
				});

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
			String uuid = params.getString("uuid");
			int nativeNodeHandle = params.getInt("nativeNodeHandle");

			LayerHelper layerHelper = new LayerHelper(this, getReactApplicationContext());
			layerHelper.removeLayerAsync(params)
				.thenRun(() -> promise.resolve(uuid))
				.exceptionally(t -> {
					Utils.promiseReject(promise, t.getMessage());
					return null;
				});
		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── updateCoordinates ────────────────────────────────────────────────

	@Override
	public void updateCoordinates(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "nativeNodeHandle")) {
				Utils.promiseReject(promise, "Undefined nativeNodeHandle");
				return;
			}
			if (!Utils.rMapHasKey(params, "uuid")) {
				Utils.promiseReject(promise, "Undefined uuid");
				return;
			}
			String uuid = params.getString("uuid");
			int nativeNodeHandle = params.getInt("nativeNodeHandle");

			MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);
			if (null == mapView) {
				Utils.promiseReject(promise, "Unable to find mapView");
				return;
			}

			// Look up the existing layer.
			org.oscim.layers.Layer layer = LayerHelper.getLayer(nativeNodeHandle, uuid);
			if (!(layer instanceof PathLayerJtsWrapper)) {
				Utils.promiseReject(promise, "Layer not found or wrong type");
				return;
			}
			PathLayerJtsWrapper pathLayer = (PathLayerJtsWrapper) layer;

			ReadableArray coordinates = Utils.rMapHasKey(params, "coordinates")
				? params.getArray("coordinates")
				: null;

			// Update style if provided.
			ReadableMap styleMap = Utils.rMapHasKey(params, "paint")
				? params.getMap("paint")
				: null;
			if (styleMap != null) {
				Style style = getStyleBuilderFromMap(styleMap).build();
				pathLayer.setStyle(style);
			}

			// Update coordinates if provided (minimum 2 points for a valid path).
			List<GeoPoint> points = null;
			if (coordinates != null && coordinates.size() >= 2) {
				pathLayer.clearPath();
				points = readableArrayToGeoPoints(coordinates);
				pathLayer.setPoints(points);
			} else {
				// No coordinate update — read current points for response.
				points = pathLayer.getPoints();
			}

			// Schedule a coalesced map update on the UI thread.
			scheduleUpdate(mapView);

			// Build response.
			ReadableMap responseInclude = Utils.rMapHasKey(params, "responseInclude")
				? params.getMap("responseInclude")
				: null;
			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString("uuid", uuid);
			responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
			if (points != null) {
				addResponseData(points, responseInclude, responseParams);
			}
			promise.resolve(responseParams);

		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── addGreatCircle ───────────────────────────────────────────────────

	@Override
	public void addGreatCircle(ReadableMap params, Promise promise) {
		try {
			if (!Utils.rMapHasKey(params, "nativeNodeHandle") || !Utils.rMapHasKey(params, "uuid")) {
				Utils.promiseReject(promise, "Undefined nativeNodeHandle or uuid");
				return;
			}
			if (!Utils.rMapHasKey(params, "from") || !Utils.rMapHasKey(params, "to")) {
				Utils.promiseReject(promise, "Missing 'from' or 'to' coordinates");
				return;
			}

			String uuid = params.getString("uuid");
			int nativeNodeHandle = params.getInt("nativeNodeHandle");

			MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);
			if (null == mapView) {
				Utils.promiseReject(promise, "Unable to find mapView");
				return;
			}

			// Look up the existing layer.
			org.oscim.layers.Layer layer = LayerHelper.getLayer(nativeNodeHandle, uuid);
			if (!(layer instanceof PathLayerJtsWrapper)) {
				Utils.promiseReject(promise, "Layer not found or wrong type");
				return;
			}
			PathLayerJtsWrapper pathLayer = (PathLayerJtsWrapper) layer;

			ReadableArray fromArr = params.getArray("from");
			ReadableArray toArr = params.getArray("to");
			int numPoints = Utils.rMapHasKey(params, "numPoints")
				? params.getInt("numPoints")
				: 32;

			GeoPoint from = new GeoPoint(
				Utils.latFromPosition(fromArr),
				Utils.lngFromPosition(fromArr)
			);
			GeoPoint to = new GeoPoint(
				Utils.latFromPosition(toArr),
				Utils.lngFromPosition(toArr)
			);

			pathLayer.addGreatCircle(from, to, numPoints);
			scheduleUpdate(mapView);

			// Build response with updated coordinates.
			List<GeoPoint> points = pathLayer.getPoints();
			WritableMap responseParams = new WritableNativeMap();
			responseParams.putString("uuid", uuid);
			responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
			if (points != null) {
				ReadableMap responseInclude = Utils.rMapHasKey(params, "responseInclude")
					? params.getMap("responseInclude")
					: null;
				addResponseData(points, responseInclude, responseParams);
			}
			promise.resolve(responseParams);

		} catch (Exception e) {
			e.printStackTrace();
			Utils.promiseReject(promise, e.getMessage());
		}
	}

	// ── triggerEvent ─────────────────────────────────────────────────────

	@Override
	public void triggerEvent(ReadableMap params) {
		if (!Utils.rMapHasKey(params, "nativeNodeHandle")) {
			return;
		}
		int nativeNodeHandle = params.getInt("nativeNodeHandle");
		if (!Utils.rMapHasKey(params, "uuid") || !Utils.rMapHasKey(params, "x") || !Utils.rMapHasKey(params, "y")) {
			return;
		}
		String uuid = params.getString("uuid");
		float x = (float) params.getDouble("x");
		float y = (float) params.getDouble("y");

		MapView mapView = Utils.getMapView(getReactApplicationContext(), nativeNodeHandle);

		// Look up the existing layer.
		org.oscim.layers.Layer layer = LayerHelper.getLayer(nativeNodeHandle, uuid);
		if (!(layer instanceof PathLayerJtsWrapper)) {
			return;
		}
		PathLayerJtsWrapper pathLayer = (PathLayerJtsWrapper) layer;

		// Use the wrapper's contains() method for hit-testing.
		if (!pathLayer.contains(x, y)) {
			return;
		}

		// Build event payload.
		WritableMap eventParams = new WritableNativeMap();
		eventParams.putInt("nativeNodeHandle", nativeNodeHandle);
		eventParams.putString("uuid", uuid);
		eventParams.putString("type", "trigger");
		eventParams.putDouble("x", x);
		eventParams.putDouble("y", y);

		// Compute event position in geo coordinates.
		if (mapView != null) {
			GeoPoint eventPoint = mapView.map().viewport().fromScreenPoint(x, y);
			eventParams.putArray("eventPosition", Utils.positionToWritableArray(
				eventPoint.getLongitude(),
				eventPoint.getLatitude(),
				null
			));

			// Compute nearest point and distance.
			java.util.List<GeoPoint> points = pathLayer.getPoints();
			if (points != null && points.size() >= 2) {
				org.locationtech.jts.geom.Coordinate[] coords =
					new org.locationtech.jts.geom.Coordinate[points.size()];
				for (int i = 0; i < points.size(); i++) {
					GeoPoint gp = points.get(i);
					coords[i] = new org.locationtech.jts.geom.Coordinate(
						gp.getLongitude(), gp.getLatitude());
				}
				org.locationtech.jts.geom.LineString lineString =
					new org.locationtech.jts.geom.GeometryFactory().createLineString(coords);
				org.locationtech.jts.geom.Point point =
					new org.locationtech.jts.geom.GeometryFactory().createPoint(
						new org.locationtech.jts.geom.Coordinate(
							eventPoint.getLongitude(), eventPoint.getLatitude()));

				double distance = lineString.distance(point);
				eventParams.putDouble("distance", distance);

				org.locationtech.jts.geom.Coordinate[] nearestPoints =
					org.locationtech.jts.operation.distance.DistanceOp.nearestPoints(lineString, point);
				if (nearestPoints.length >= 1) {
					eventParams.putArray("nearestPoint", Utils.positionToWritableArray(
						nearestPoints[0].x,
						nearestPoints[0].y,
						null
					));
				}
			}
		}

		emitOnPathJtsEvent(eventParams);
	}
}
