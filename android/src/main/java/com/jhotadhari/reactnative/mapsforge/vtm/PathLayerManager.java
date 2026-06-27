package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.utils.geom.GeomBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collapses all {@code LayerPath} components into a single shared
 * {@link VectorLayer} per map view.
 *
 * <h3>Hit-test routing</h3>
 * The shared {@code VectorLayer}'s {@code UuidResolver} maps each
 * {@link Drawable} back to its owning path entry uuid, so gesture events
 * carry the correct per-component uuid.
 */
public class PathLayerManager extends LayerManager<PathLayerManager.PathEntry> {

	public static final String NAME = "paths";
	/** Position in map.layers(): at the bottom of JS-managed layers. */
	public static final int BASE_POSITION = Integer.MAX_VALUE - 1;

	// ── Factory ─────────────────────────────────────────────────────────

	private static final Factory<PathLayerManager> FACTORY = PathLayerManager::new;

	@NonNull
	public static PathLayerManager get(int nativeNodeHandle, @NonNull MapView mapView) {
		return LayerManager.get(nativeNodeHandle, mapView, NAME, FACTORY);
	}

	@Nullable
	public static PathLayerManager getInstance(int nativeNodeHandle) {
		return (PathLayerManager) LayerManager.getInstance(nativeNodeHandle, NAME);
	}

	// ── Entry type ──────────────────────────────────────────────────────

	public static class PathEntry {
		@NonNull
		public final String pathUuid;
		public int positionIndex;
		@NonNull
		public final List<LineDrawable> drawables = new ArrayList<>();
		@NonNull
		public Coordinate[] jtsCoordinates;
		public boolean supportsGestures;
		public float gestureScreenDistance;

		public PathEntry(
			@NonNull String pathUuid,
			int positionIndex,
			@NonNull Coordinate[] jtsCoordinates,
			boolean supportsGestures,
			float gestureScreenDistance
		) {
			this.pathUuid = pathUuid;
			this.positionIndex = positionIndex;
			this.jtsCoordinates = jtsCoordinates;
			this.supportsGestures = supportsGestures;
			this.gestureScreenDistance = gestureScreenDistance;
		}
	}

	// ── Instance state ──────────────────────────────────────────────────

	/** Maps each Drawable in the shared VectorLayer back to its path entry uuid. */
	private final Map<Drawable, String> drawableToEntry = new ConcurrentHashMap<>();

	// ── Constructor ─────────────────────────────────────────────────────

	private PathLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
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
	protected CreateResult<PathEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		ReadableArray coordinates = Utils.rMapHasKey(params, "coordinates")
			? params.getArray("coordinates")
			: null;
		if (coordinates == null || coordinates.size() == 0) {
			throw new IllegalArgumentException("Unable to parse coordinates");
		}

		double simplificationTolerance = Utils.rMapHasKey(params, "simplificationTolerance")
			? params.getDouble("simplificationTolerance")
			: 0d;

		boolean supportsGestures = Utils.rMapHasKey(params, "supportsGestures")
			&& params.getBoolean("supportsGestures");

		double gestureScreenDistance = Utils.rMapHasKey(params, "gestureScreenDistance")
			? params.getDouble("gestureScreenDistance")
			: 30d;

		// Parse coordinates.
		Coordinate[] jtsCoordinates = readableArrayToJtsCoordinates(
			coordinates,
			(float) simplificationTolerance
		);
		if (jtsCoordinates == null || jtsCoordinates.length == 0) {
			throw new IllegalArgumentException("Unable to parse coordinates");
		}

		// Parse style.
		ReadableMap styleMap = Utils.rMapHasKey(params, "style")
			? params.getMap("style")
			: null;
		Style.Builder styleBuilder = getStyleBuilder(styleMap);

		int positionIndex = resolvePositionIndex(params);

		PathEntry entry = new PathEntry(
			entryUuid,
			positionIndex,
			jtsCoordinates,
			supportsGestures,
			(float) gestureScreenDistance
		);

		// Draw LineDrawable segments onto the shared VectorLayer.
		VectorLayer vectorLayer = (VectorLayer) sharedLayer;
		drawSegments(jtsCoordinates, styleBuilder, entryUuid, vectorLayer, entry);

		return new CreateResult<>(entry, null);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull PathEntry entry) {
		VectorLayer layer = (VectorLayer) sharedLayer;
		if (layer == null) {
			return;
		}
		// Remove all drawables belonging to this entry.
		for (LineDrawable d : entry.drawables) {
			drawableToEntry.remove(d);
			layer.remove(d);
		}
		entry.drawables.clear();
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull PathEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		VectorLayer layer = (VectorLayer) sharedLayer;
		if (layer == null) {
			return new UpdateResult(null);
		}

		ReadableArray coordinates = Utils.rMapHasKey(params, "coordinates")
			? params.getArray("coordinates")
			: null;

		double simplificationTolerance = Utils.rMapHasKey(params, "simplificationTolerance")
			? params.getDouble("simplificationTolerance")
			: 0d;

		ReadableMap styleMap = Utils.rMapHasKey(params, "style")
			? params.getMap("style")
			: null;

		// Remove old drawables.
		for (LineDrawable d : entry.drawables) {
			drawableToEntry.remove(d);
			layer.remove(d);
		}
		entry.drawables.clear();

		// Parse new coordinates (if provided).
		if (coordinates != null && coordinates.size() > 0) {
			Coordinate[] jtsCoordinates = readableArrayToJtsCoordinates(
				coordinates,
				(float) simplificationTolerance
			);
			if (jtsCoordinates != null && jtsCoordinates.length > 0) {
				entry.jtsCoordinates = jtsCoordinates;

				// Parse new style (or reuse old style's builder defaults).
				Style.Builder styleBuilder = getStyleBuilder(styleMap);

				// Redraw.
				drawSegments(jtsCoordinates, styleBuilder, entry.pathUuid, layer, entry);
			}
		}

		layer.update();

		// Build response including bounds if requested.
		ReadableMap responseInclude = Utils.rMapHasKey(params, "responseInclude")
			? params.getMap("responseInclude")
			: null;
		WritableMap responseData = new WritableNativeMap();
		responseData.putString("uuid", entry.pathUuid);
		if (responseInclude != null) {
			addResponseData(entry.pathUuid, responseInclude, 1, responseData);
		}

		return new UpdateResult(responseData);
	}

	@Nullable
	@Override
	protected WritableMap hitTestEntry(
		@NonNull PathEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	) {
		org.locationtech.jts.geom.Point point = new GeomBuilder()
			.point(eventPoint.getLongitude(), eventPoint.getLatitude())
			.toPoint();
		float distance = getCoordinateDistanceFromScreenDistance(x, y, gestureScreenDistance);

		for (LineDrawable drawable : entry.drawables) {
			if (drawable.getGeometry().buffer(distance).contains(point)) {
				WritableMap params = new WritableNativeMap();
				params.putString("uuid", entry.pathUuid);
				params.putDouble("distance", drawable.getGeometry().distance(point));
				org.locationtech.jts.geom.Coordinate[] nearestPoints =
					DistanceOp.nearestPoints(drawable.getGeometry(), point);
				params.putArray("nearestPoint", Utils.positionToWritableArray(
					nearestPoints[0].x,
					nearestPoints[0].y,
					null
				));
				return params;
			}
		}
		return null;
	}

	// ── Path-specific public API ────────────────────────────────────────

	public void updateSupportsGestures(@NonNull String entryUuid, boolean supportsGestures) {
		PathEntry entry = entries.get(entryUuid);
		if (entry != null) {
			entry.supportsGestures = supportsGestures;
		}
	}

	public void updateGestureScreenDistance(@NonNull String entryUuid, float distance) {
		PathEntry entry = entries.get(entryUuid);
		if (entry != null) {
			entry.gestureScreenDistance = distance;
		}
	}

	/**
	 * Creates the response map with coordinates and/or bounds based on responseInclude.
	 * Mirrors the existing LayerPath.addStuffToResponse logic.
	 */
	@NonNull
	public WritableMap buildCreateResponse(
		@NonNull String entryUuid,
		@NonNull ReadableMap responseInclude
	) {
		WritableMap responseData = new WritableNativeMap();
		responseData.putString("uuid", entryUuid);
		addResponseData(entryUuid, responseInclude, 0, responseData);
		return responseData;
	}

	// ── Internal helpers ────────────────────────────────────────────────

	/**
	 * Mirrors {@code LayerPath.getStyleBuilderFromMap} — kept here so
	 * PathLayerManager is self-contained.
	 */
	@NonNull
	private static Style.Builder getStyleBuilder(@Nullable ReadableMap styleMap) {
		// Hard-coded defaults matching LayerPath.getTypedExportedConstants.
		double strokeWidth = 4d;
		String strokeColor = "#ff0000";

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
		}

		return styleBuilder;
	}

	/**
	 * Mirrors {@code LayerPath.readableArrayToJtsCoordinates}.
	 */
	@NonNull
	private static Coordinate[] readableArrayToJtsCoordinates(
		@NonNull ReadableArray coordinates,
		float simplificationTolerance
	) {
		Coordinate[] jtsCoordinates = new Coordinate[coordinates.size()];
		for (int i = 0; i < coordinates.size(); i++) {
			ReadableType readableType = coordinates.getType(i);
			if (readableType == ReadableType.Array) {
				ReadableArray position = coordinates.getArray(i);
				Double alt = Utils.altFromPosition(position);
				jtsCoordinates[i] = new Coordinate(
					Utils.lngFromPosition(position),
					Utils.latFromPosition(position),
					alt != null ? alt : 0
				);
			}
		}
		if (simplificationTolerance > 0) {
			Simplify<Coordinate> simplify = new Simplify<>(
				new Coordinate[0],
				new PointExtractor<Coordinate>() {
					@Override
					public double getX(Coordinate point) { return point.x; }
					@Override
					public double getY(Coordinate point) { return point.y; }
				}
			);
			jtsCoordinates = simplify.simplify(
				jtsCoordinates,
				simplificationTolerance,
				true
			);
		}
		return jtsCoordinates;
	}

	/**
	 * Draws LineDrawable segments onto the shared VectorLayer.
	 * Mirrors {@code LayerPath.drawLineForCoordinates}.
	 */
	private void drawSegments(
		@NonNull Coordinate[] jtsCoordinates,
		@NonNull Style.Builder styleBuilder,
		@NonNull String entryUuid,
		@NonNull VectorLayer vectorLayer,
		@NonNull PathEntry entry
	) {
		Style style = styleBuilder.build();
		for (int i = 0; i < jtsCoordinates.length; i++) {
			if (i != 0) {
				double[] segment = new double[4];
				segment[0] = jtsCoordinates[i].x;
				segment[1] = jtsCoordinates[i].y;
				segment[2] = jtsCoordinates[i - 1].x;
				segment[3] = jtsCoordinates[i - 1].y;
				LineDrawable drawable = new LineDrawable(segment, style);
				vectorLayer.add(drawable);
				drawableToEntry.put(drawable, entryUuid);
				entry.drawables.add(drawable);
			}
		}
	}

	private void addResponseData(
		@NonNull String entryUuid,
		@NonNull ReadableMap responseInclude,
		int includeLevel,
		@NonNull WritableMap responseParams
	) {
		PathEntry entry = entries.get(entryUuid);
		if (entry == null) {
			return;
		}
		if (responseInclude.getInt("coordinates") > includeLevel) {
			addCoordinatesToResponse(entry.jtsCoordinates, responseParams);
		}
		if (responseInclude.getInt("bounds") > includeLevel) {
			addBoundsToResponse(entry.jtsCoordinates, responseParams);
		}
	}

	private void addBoundsToResponse(
		@NonNull Coordinate[] jtsCoordinates,
		@NonNull WritableMap responseParams
	) {
		Geometry geometry = new LineString(
			new CoordinateArraySequence(jtsCoordinates),
			new GeometryFactory()
		);
		Envelope boundingBox = geometry.getEnvelopeInternal();
		com.facebook.react.bridge.WritableArray bboxParams = new com.facebook.react.bridge.WritableNativeArray();
		bboxParams.pushDouble(boundingBox.getMinX());
		bboxParams.pushDouble(boundingBox.getMinY());
		bboxParams.pushDouble(boundingBox.getMaxX());
		bboxParams.pushDouble(boundingBox.getMaxY());
		responseParams.putArray("bbox", bboxParams);
	}

	private void addCoordinatesToResponse(
		@NonNull Coordinate[] jtsCoordinates,
		@NonNull WritableMap responseParams
	) {
		if (jtsCoordinates.length > 0 && !responseParams.hasKey("coordinates")) {
			com.facebook.react.bridge.WritableArray arr = new com.facebook.react.bridge.WritableNativeArray();
			for (Coordinate c : jtsCoordinates) {
				arr.pushArray(Utils.positionToWritableArray(c.x, c.y, c.z));
			}
			responseParams.putArray("coordinates", arr);
		}
	}

	/**
	 * Mirrors {@code VectorLayer.getCoordinateDistanceFromScreenDistance}.
	 */
	private float getCoordinateDistanceFromScreenDistance(float x, float y, float screenDistance) {
		return (float) Math.abs(
			mapView.map().viewport().fromScreenPoint(x, y).getLongitude()
				- mapView.map().viewport().fromScreenPoint(x + screenDistance, y).getLongitude()
		);
	}

	// ── Gesture listener ────────────────────────────────────────────────

	@NonNull
	private VectorLayer.GestureListener createGestureListener() {
		return (type, eventParams) -> {
			// eventParams already has the correct per-entry uuid from the
			// UuidResolver. Add type, emit through the callback.
			if (eventCallback == null) {
				return;
			}
			// The GestureListener is called by VectorLayer.onGesture, which
			// already populated uuid/distance/nearestPoint in containsGetResponse.
			WritableMap payload = new WritableNativeMap();
			// Copy relevant fields from eventParams.
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
			eventCallback.emit("onPathEvent", payload);
		};
	}

	@Override
	protected void destroy() {
		drawableToEntry.clear();
		super.destroy();
	}
}
