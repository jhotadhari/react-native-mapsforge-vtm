package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerShapeSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.vector.geometries.CircleDrawable;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.layers.vector.geometries.HexagonDrawable;
import org.oscim.layers.vector.geometries.PointDrawable;
import org.oscim.layers.vector.geometries.PolygonDrawable;
import org.oscim.layers.vector.geometries.RectangleDrawable;
import org.oscim.layers.vector.geometries.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TurboModule for the {@code LayerShape} component.
 *
 * <p>Creates a dedicated vtm-jts {@link VectorLayer} per JS component instance,
 * with the appropriate JTS {@link Drawable} (polygon, circle, rectangle, hexagon,
 * or point). Each shape gets its own native layer — simple lifecycle and correct
 * render ordering.
 */
@ReactModule(name = LayerShape.NAME)
public class LayerShape extends NativeLayerShapeSpec {

	public static final String NAME = "LayerShape";

	// ── Coalesced map updates ───────────────────────────────────────────

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

	// ── Style parsing ────────────────────────────────────────────────────

	@NonNull
	private Style.Builder getStyleBuilderFromMap(@Nullable ReadableMap styleMap) {
		ReadableMap styleConstants = (ReadableMap) getConstants().get("style");
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
				styleBuilder.scaleZoomLevel(styleMap.getInt("scalingZoomLevel"));
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
			if (styleMap.hasKey("generalization")) {
				styleBuilder.generalization(styleMap.getInt("generalization"));
			}
		}

		return styleBuilder;
	}

	// ── Coordinate helpers ───────────────────────────────────────────────

	private static GeoPoint readableArrayToGeoPoint(@NonNull ReadableArray arr) {
		return new GeoPoint(
			Utils.latFromPosition(arr),
			Utils.lngFromPosition(arr)
		);
	}

	private static List<GeoPoint> readableArrayToRing(@NonNull ReadableArray ring) {
		List<GeoPoint> points = new ArrayList<>(ring.size());
		for (int i = 0; i < ring.size(); i++) {
			if (ring.getType(i) == ReadableType.Array) {
				points.add(readableArrayToGeoPoint(ring.getArray(i)));
			}
		}
		return points;
	}

	// ── Drawable factory ─────────────────────────────────────────────────

	@NonNull
	private Drawable createDrawable(@NonNull ReadableMap shapeMap, @NonNull Style style) throws Exception {
		String type = shapeMap.getString("type");
		if (type == null) {
			throw new IllegalArgumentException("Shape type is required");
		}

		switch (type) {
			case "polygon": {
				if (!Utils.rMapHasKey(shapeMap, "rings")) {
					throw new IllegalArgumentException("Polygon requires 'rings'");
				}
				ReadableArray ringsArr = shapeMap.getArray("rings");
				List<GeoPoint> outerRing = readableArrayToRing(ringsArr);
				if (outerRing.size() < 3) {
					throw new IllegalArgumentException("Polygon outer ring requires at least 3 points");
				}

				// PolygonDrawable supports a single hole (inner ring) as List<GeoPoint>.
				// If multiple holes are provided, throw — don't silently discard data.
				List<GeoPoint> holes = null;
				if (Utils.rMapHasKey(shapeMap, "holes")) {
					ReadableArray holesArr = shapeMap.getArray("holes");
					if (holesArr.size() > 1) {
						throw new IllegalArgumentException(
							"PolygonDrawable supports only a single hole; got " + holesArr.size());
					}
					if (holesArr.size() > 0) {
						holes = readableArrayToRing(holesArr.getArray(0));
						if (holes.size() < 3) {
							throw new IllegalArgumentException("Polygon hole requires at least 3 points");
						}
					}
				}
				if (holes != null) {
					return new PolygonDrawable(outerRing, holes, style);
				}
				return new PolygonDrawable(outerRing, style);
			}
			case "circle": {
				if (!Utils.rMapHasKey(shapeMap, "center") || !Utils.rMapHasKey(shapeMap, "radiusKm")) {
					throw new IllegalArgumentException("Circle requires 'center' and 'radiusKm'");
				}
				GeoPoint center = readableArrayToGeoPoint(shapeMap.getArray("center"));
				double radiusKm = shapeMap.getDouble("radiusKm");
				int numSegments = Utils.rMapHasKey(shapeMap, "numSegments")
					? shapeMap.getInt("numSegments")
					: 48;
				return new CircleDrawable(center, radiusKm, numSegments, style);
			}
			case "rectangle": {
				if (!Utils.rMapHasKey(shapeMap, "min") || !Utils.rMapHasKey(shapeMap, "max")) {
					throw new IllegalArgumentException("Rectangle requires 'min' and 'max'");
				}
				GeoPoint min = readableArrayToGeoPoint(shapeMap.getArray("min"));
				GeoPoint max = readableArrayToGeoPoint(shapeMap.getArray("max"));
				return new RectangleDrawable(min, max, style);
			}
			case "hexagon": {
				if (!Utils.rMapHasKey(shapeMap, "center") || !Utils.rMapHasKey(shapeMap, "radiusKm")) {
					throw new IllegalArgumentException("Hexagon requires 'center' and 'radiusKm'");
				}
				GeoPoint center = readableArrayToGeoPoint(shapeMap.getArray("center"));
				double radiusKm = shapeMap.getDouble("radiusKm");
				// Use the 2-arg constructor, not the 4-arg one.
				// The 4-arg HexagonDrawable(GeoPoint, double, double, Style)
				// constructor projects vertices into Mercator x/y coordinates,
				// while every other Drawable (Rectangle, Circle, Polygon, and
				// the 2-arg HexagonDrawable) stores raw lat/lng coordinates.
				// When VectorLayer later renders the geometry, it expects
				// lat/lng — feeding it Mercator coordinates makes the hexagon
				// land at a wrong screen position (invisible).
				HexagonDrawable hex = new HexagonDrawable(center, radiusKm);
				hex.setStyle(style);
				return hex;
			}
			case "point": {
				if (!Utils.rMapHasKey(shapeMap, "position")) {
					throw new IllegalArgumentException("Point requires 'position'");
				}
				GeoPoint position = readableArrayToGeoPoint(shapeMap.getArray("position"));
				return new PointDrawable(position, style);
			}
			default:
				throw new IllegalArgumentException("Unknown shape type: " + type);
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

			if (!Utils.rMapHasKey(params, "shape")) {
				Utils.promiseReject(promise, "Missing 'shape' parameter");
				return;
			}
			ReadableMap shapeMap = params.getMap("shape");

			// Parse style.
			ReadableMap styleMap = Utils.rMapHasKey(params, "style")
				? params.getMap("style")
				: null;
			Style style = getStyleBuilderFromMap(styleMap).build();

			// Create the drawable and a VectorLayer to host it.
			Drawable drawable = createDrawable(shapeMap, style);
			String uuid = UUID.randomUUID().toString();

			// Create a custom VectorLayer for this shape.
			boolean supportsGestures = Utils.rMapHasKey(params, "supportsGestures")
				&& params.getBoolean("supportsGestures");
			double gestureScreenDistance = Utils.rMapHasKey(params, "gestureScreenDistance")
				? params.getDouble("gestureScreenDistance")
				: 20d;

			VectorLayer vectorLayer = new VectorLayer(
				mapView.map(),
				uuid,
				supportsGestures,
				supportsGestures ? (type, eventParams) -> {
					eventParams.putInt("nativeNodeHandle", nativeNodeHandle);
					eventParams.putString("uuid", uuid);
					emitOnShapeEvent(eventParams);
				} : null,
				(float) gestureScreenDistance
			);
			vectorLayer.add(drawable);
			vectorLayer.update();

			// Register via LayerHelper.
			LayerHelper layerHelper = new LayerHelper(this, getReactApplicationContext());

			WritableMap helperParams = new WritableNativeMap();
			helperParams.putInt("nativeNodeHandle", nativeNodeHandle);
			if (Utils.rMapHasKey(params, "positionIndex")) {
				helperParams.putInt("positionIndex", params.getInt("positionIndex"));
			}

			layerHelper.addLayerAsync(vectorLayer, helperParams, uuid)
				.thenAccept(resolvedUuid -> {
					WritableMap responseParams = new WritableNativeMap();
					responseParams.putString("uuid", resolvedUuid);
					responseParams.putInt("nativeNodeHandle", nativeNodeHandle);
					// Include the shape type in the response.
					WritableMap shapeResponse = new WritableNativeMap();
					shapeResponse.putString("type", shapeMap.getString("type"));
					responseParams.putMap("shape", shapeResponse);
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

			// Look up the existing layer.
			org.oscim.layers.Layer layer = LayerHelper.getLayer(nativeNodeHandle, uuid);
			if (!(layer instanceof VectorLayer)) {
				Utils.promiseReject(promise, "Layer not found or wrong type");
				return;
			}
			VectorLayer vectorLayer = (VectorLayer) layer;

			// If a new shape is provided, clear and redraw.
			if (Utils.rMapHasKey(params, "shape")) {
				ReadableMap shapeMap = params.getMap("shape");
				// Style defaults are applied inside getStyleBuilderFromMap when styleMap is null.
				ReadableMap styleMap = Utils.rMapHasKey(params, "style")
					? params.getMap("style")
					: null;
				Style style = getStyleBuilderFromMap(styleMap).build();
				Drawable drawable = createDrawable(shapeMap, style);

				// Clear existing drawables and add the new one.
				vectorLayer.clearDrawables();
				vectorLayer.add(drawable);
				vectorLayer.update();
			}

			scheduleUpdate(mapView);

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

		org.oscim.layers.Layer layer = LayerHelper.getLayer(nativeNodeHandle, uuid);
		if (!(layer instanceof VectorLayer)) {
			return;
		}
		VectorLayer vectorLayer = (VectorLayer) layer;

		// Use VectorLayer's built-in hit-testing.
		WritableMap hitResult = vectorLayer.containsGetResponse(x, y);
		if (hitResult == null) {
			return;
		}

		hitResult.putInt("nativeNodeHandle", nativeNodeHandle);
		hitResult.putString("type", "trigger");

		if (mapView != null) {
			GeoPoint eventPoint = mapView.map().viewport().fromScreenPoint(x, y);
			hitResult.putArray("eventPosition", Utils.positionToWritableArray(
				eventPoint.getLongitude(),
				eventPoint.getLatitude(),
				null
			));
		}

		emitOnShapeEvent(hitResult);
	}
}
