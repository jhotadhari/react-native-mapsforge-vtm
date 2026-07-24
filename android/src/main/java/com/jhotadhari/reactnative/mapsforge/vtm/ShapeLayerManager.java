package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.geometries.CircleDrawable;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.layers.vector.geometries.JtsDrawable;
import org.oscim.layers.vector.geometries.HexagonDrawable;
import org.oscim.layers.vector.geometries.PointDrawable;
import org.oscim.layers.vector.geometries.PolygonDrawable;
import org.oscim.layers.vector.geometries.RectangleDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.utils.geom.GeomBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collapses all {@code LayerShape} components into shared
 * {@link VectorLayer}s per fragment per map view.
 *
 * <h3>Hit-test routing</h3>
 * The shared {@code VectorLayer}'s {@code UuidResolver} maps each
 * {@link Drawable} back to its owning shape entry uuid, so gesture events
 * carry the correct per-component uuid.
 */
public class ShapeLayerManager extends LayerManager<ShapeLayerManager.ShapeEntry> {

	private static final String TAG = "ShapeLayerManager";

	public static final String NAME = "shapes";
	/** Position in map.layers(): same tier as markers, above paths. */
	public static final int BASE_POSITION = Integer.MAX_VALUE;
	/**
	 * Default fragment UUID prefix used when JS doesn't supply one. Must match
	 * the default in the owning TurboModule ({@code LayerShape.java}'s createLayer).
	 */
	public static final String DEFAULT_FRAGMENT_UUID = "__vtm_shared_shape__0";

	// ── Factory ─────────────────────────────────────────────────────────

	private static final Factory<ShapeLayerManager> FACTORY = ShapeLayerManager::new;

	@NonNull
	public static ShapeLayerManager get(int nativeNodeHandle, @NonNull MapView mapView) {
		return LayerManager.get(nativeNodeHandle, mapView, NAME, FACTORY);
	}

	@Nullable
	public static ShapeLayerManager getInstance(int nativeNodeHandle) {
		return (ShapeLayerManager) LayerManager.getInstance(nativeNodeHandle, NAME);
	}

	// ── Entry type ──────────────────────────────────────────────────────

	public static class ShapeEntry {
		@NonNull
		public final String shapeUuid;
		@NonNull
		public final String fragmentUuid;
		public int positionIndex;
		@NonNull
		public JtsDrawable drawable;
		public boolean supportsGestures;
		public float gestureScreenDistance;

		public ShapeEntry(
			@NonNull String shapeUuid,
			@NonNull String fragmentUuid,
			int positionIndex,
			@NonNull JtsDrawable drawable,
			boolean supportsGestures,
			float gestureScreenDistance
		) {
			this.shapeUuid = shapeUuid;
			this.fragmentUuid = fragmentUuid;
			this.positionIndex = positionIndex;
			this.drawable = drawable;
			this.supportsGestures = supportsGestures;
			this.gestureScreenDistance = gestureScreenDistance;
		}
	}

	// ── Instance state ──────────────────────────────────────────────────

	/** Maps each Drawable in a shared VectorLayer back to its shape entry uuid. */
	protected final Map<Drawable, String> drawableToEntry = new ConcurrentHashMap<>();

	// ── Constructor ─────────────────────────────────────────────────────

	protected ShapeLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name, BASE_POSITION);
	}

	// ── LayerManager contract ───────────────────────────────────────────

	@NonNull
	@Override
	protected Layer createSharedLayer() {
		VectorLayer layer = new VectorLayer(
			mapView.map(),
			sharedLayerUuid,
			true, // supportsGestures — the manager routes events per-entry
			createGestureListener(),
			30f   // default gestureScreenDistance
		);
		// Install the UuidResolver so containsGetResponse returns per-entry uuids.
		layer.setUuidResolver(drawable -> {
			String uuid = drawableToEntry.get(drawable);
			return uuid != null ? uuid : sharedLayerUuid;
		});
		return layer;
	}

	@NonNull
	@Override
	protected CreateResult<ShapeEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		if (!Utils.rMapHasKey(params, "shape")) {
			throw new IllegalArgumentException("Missing 'shape' parameter");
		}
		ReadableMap shapeMap = params.getMap("shape");

		// Parse style.
		ReadableMap styleMap = Utils.rMapHasKey(params, "style")
			? params.getMap("style")
			: null;
		Style style = getStyleBuilderFromMap(styleMap).build();

		// Create the drawable.
		JtsDrawable drawable = createDrawable(shapeMap, style);

		// Resolve fragment uuid for this entry.
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		boolean supportsGestures = Utils.rMapHasKey(params, "supportsGestures")
			&& params.getBoolean("supportsGestures");

		double gestureScreenDistance = Utils.rMapHasKey(params, "gestureScreenDistance")
			? params.getDouble("gestureScreenDistance")
			: 20d;

		int positionIndex = resolvePositionIndex(params);

		ShapeEntry entry = new ShapeEntry(
			entryUuid,
			fragmentUuid,
			positionIndex,
			drawable,
			supportsGestures,
			(float) gestureScreenDistance
		);

		// Add drawable to the fragment's VectorLayer.
		VectorLayer vectorLayer = (VectorLayer) getSharedLayer(fragmentUuid);
		if (vectorLayer == null) {
			throw new IllegalStateException(
				"No shared VectorLayer found for fragmentUuid '" + fragmentUuid
					+ "'. Known fragments: " + sharedLayerFragments.keySet());
		}
		drawable.setPriority(positionIndex);
		vectorLayer.add(drawable);
		vectorLayer.update();
		drawableToEntry.put(drawable, entryUuid);

		return new CreateResult<>(entry, null);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull ShapeEntry entry) {
		// Always clean up the drawable → entry mapping so the GC can collect,
		// even if the shared layer is already gone (ZOMBIE path).
		drawableToEntry.remove(entry.drawable);
		VectorLayer layer = (VectorLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			Log.w(TAG,
				"ZOMBIE: getSharedLayer returned null for fragmentUuid="
					+ entry.fragmentUuid + " entry=" + entry.shapeUuid
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet());
			return;
		}
		layer.remove(entry.drawable);
		layer.update();
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull ShapeEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		VectorLayer layer = (VectorLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			return new UpdateResult(null);
		}

		// If a new shape is provided, clear and redraw.
		if (Utils.rMapHasKey(params, "shape")) {
			ReadableMap shapeMap = params.getMap("shape");
			ReadableMap styleMap = Utils.rMapHasKey(params, "style")
				? params.getMap("style")
				: null;
			Style style = getStyleBuilderFromMap(styleMap).build();
			JtsDrawable newDrawable = createDrawable(shapeMap, style);

			// Remove old drawable.
			drawableToEntry.remove(entry.drawable);
			layer.remove(entry.drawable);

			// Add new drawable with same priority.
			newDrawable.setPriority(entry.positionIndex);
			layer.add(newDrawable);
			drawableToEntry.put(newDrawable, entry.shapeUuid); // shapeUuid === entryUuid


			entry.drawable = newDrawable;
			layer.update();
		}

		return new UpdateResult(null);
	}

	@Nullable
	@Override
	protected WritableMap hitTestEntry(
		@NonNull ShapeEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	) {
		org.locationtech.jts.geom.Point point = new GeomBuilder()
			.point(eventPoint.getLongitude(), eventPoint.getLatitude())
			.toPoint();
		float threshold = getCoordinateDistanceFromScreenDistance(
			x, y, entry.gestureScreenDistance);

		double geoDist = entry.drawable.getGeometry().distance(point);
		if (geoDist <= threshold) {
			WritableMap params = new WritableNativeMap();
			params.putString("uuid", entry.shapeUuid);
			params.putDouble("distance", geoDist);
			org.locationtech.jts.geom.Coordinate[] nearestPoints =
				DistanceOp.nearestPoints(entry.drawable.getGeometry(), point);
			params.putArray("nearestPoint", Utils.positionToWritableArray(
				nearestPoints[0].x,
				nearestPoints[0].y,
				null
			));
			return params;
		}
		return null;
	}

	// ── Override create/remove to sync gesture support ──────────────────

	@NonNull
	@Override
	public CreateResult<ShapeEntry> create(
		@NonNull String entryUuid,
		@NonNull String fragmentUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		CreateResult<ShapeEntry> result = super.create(entryUuid, fragmentUuid, params,
			mapFragment, contentResolver, reactContext);
		syncGestureSupport();
		return result;
	}

	@Override
	public void remove(@NonNull String entryUuid) {
		super.remove(entryUuid);
		syncGestureSupport();
	}

	// ── Shape-specific public API ───────────────────────────────────────

	/**
	 * Syncs the shared VectorLayer's {@code mSupportsGestures} flag to whether
	 * any ShapeEntry has gesture handlers. When no entries want gestures, the
	 * shared layer's {@code onGesture()} short-circuits at the first guard —
	 * no hit-test loop at all.
	 */
	protected void syncGestureSupport() {
		boolean hasAny = false;
		for (ShapeEntry entry : entries.values()) {
			if (entry.supportsGestures) {
				hasAny = true;
				break;
			}
		}
		for (Layer layer : sharedLayerFragments.values()) {
			if (layer instanceof VectorLayer) {
				((VectorLayer) layer).setSupportsGestures(hasAny);
			}
		}
	}

	// ── Style parsing ────────────────────────────────────────────────────

	@NonNull
	protected Style.Builder getStyleBuilderFromMap(@Nullable ReadableMap styleMap) {
		double strokeWidth = 2d;
		String strokeColor = "#4488ff";

		if (styleMap != null) {
			if (styleMap.hasKey("strokeWidth")) {
				strokeWidth = styleMap.getDouble("strokeWidth");
			}
			if (styleMap.hasKey("strokeColor")) {
				strokeColor = styleMap.getString("strokeColor");
			}
		}

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
			if (styleMap.hasKey("generalization")) {
				styleBuilder.generalization(styleMap.getInt("generalization"));
			}
		}

		return styleBuilder;
	}

	// ── Coordinate helpers ───────────────────────────────────────────────

	@NonNull
	private static GeoPoint readableArrayToGeoPoint(@NonNull ReadableArray arr) {
		return new GeoPoint(
			Utils.latFromPosition(arr),
			Utils.lngFromPosition(arr)
		);
	}

	@NonNull
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
	protected JtsDrawable createDrawable(@NonNull ReadableMap shapeMap, @NonNull Style style) throws Exception {
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
				// Use the 2-arg constructor, not the 4-arg one (Mercator bug).
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

	// ── Gesture listener ────────────────────────────────────────────────

	@NonNull
	protected VectorLayer.GestureListener createGestureListener() {
		return (type, eventParams) -> {
			if (eventCallback == null) {
				return;
			}
			WritableMap payload = new WritableNativeMap();
			if (eventParams.hasKey("uuid")) {
				payload.putString("uuid", eventParams.getString("uuid"));
			}
			if (eventParams.hasKey("distance")) {
				payload.putDouble("distance", eventParams.getDouble("distance"));
			}
			if (eventParams.hasKey("nearestPoint")) {
				payload.putArray("nearestPoint", eventParams.getArray("nearestPoint"));
			}
			if (eventParams.hasKey("eventPosition")) {
				payload.putArray("eventPosition", eventParams.getArray("eventPosition"));
			}
			payload.putString("type", type);
			payload.putInt("nativeNodeHandle", nativeNodeHandle);
			eventCallback.emit("onShapeEvent", payload);
		};
	}

	// ── Helpers ─────────────────────────────────────────────────────────

	/**
	 * Mirrors {@code VectorLayer.getCoordinateDistanceFromScreenDistance}.
	 */
	private float getCoordinateDistanceFromScreenDistance(float x, float y, float screenDistance) {
		return (float) Math.abs(
			mapView.map().viewport().fromScreenPoint(x, y).getLongitude()
				- mapView.map().viewport().fromScreenPoint(x + screenDistance, y).getLongitude()
		);
	}

	@Override
	protected void destroy() {
		drawableToEntry.clear();
		super.destroy();
	}
}
