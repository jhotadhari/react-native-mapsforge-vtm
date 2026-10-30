package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
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
import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.renderer.bucket.TextureItem;
import org.oscim.utils.geom.GeomBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

	private static final String TAG = "PathLayerManager";

	public static final String NAME = "paths";
	/**
	 * Fallback fragment uuid when batch params carry none (single implicit
	 * fragment). Legacy — the JS scene always sends deterministic `frag:`/`run:`
	 * keys now, so this is only a safety net for callers that omit fragmentUuid.
	 */
	public static final String DEFAULT_FRAGMENT_UUID = "__vtm_shared_path__0";

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
		@NonNull
		public final String fragmentUuid;
		public int positionIndex;
		@NonNull
		public final List<LineDrawable> drawables = new ArrayList<>();
		@NonNull
		public Coordinate[] jtsCoordinates;
		/**
		 * Screen-anchored point overrides. Empty when the path is purely
		 * geographic. Anchored coordinates are resolved per frame by
		 * {@link #updateAnchoredEntries(MapView)}.
		 */
		@NonNull
		public final List<ScreenAnchorPoint> anchors = new ArrayList<>();
		public boolean supportsGestures;
		public float gestureScreenDistance;
		/** Style the drawables were last drawn with (reused for
		 * append-only updates that carry no new paint). */
		@Nullable
		public Style lastStyle;

		public PathEntry(
			@NonNull String pathUuid,
			@NonNull String fragmentUuid,
			int positionIndex,
			@NonNull Coordinate[] jtsCoordinates,
			boolean supportsGestures,
			float gestureScreenDistance
		) {
			this.pathUuid = pathUuid;
			this.fragmentUuid = fragmentUuid;
			this.positionIndex = positionIndex;
			this.jtsCoordinates = jtsCoordinates;
			this.supportsGestures = supportsGestures;
			this.gestureScreenDistance = gestureScreenDistance;
		}
	}

	// ── Instance state ──────────────────────────────────────────────────

	/** Maps each Drawable in the shared VectorLayer back to its path entry uuid. */
	protected final Map<Drawable, String> drawableToEntry = new ConcurrentHashMap<>();

	// ── Constructor ─────────────────────────────────────────────────────

	protected PathLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name);
	}

	// ── LayerManager contract ───────────────────────────────────────────

	@Nullable
	@Override
	protected String entryFragmentUuid(@NonNull PathEntry entry) {
		return entry.fragmentUuid;
	}

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
		List<ScreenAnchorPoint> anchors = parseScreenPoints(params);
		if ((coordinates == null || coordinates.size() == 0) && anchors.isEmpty()) {
			throw new IllegalArgumentException("Unable to parse coordinates");
		}


		boolean supportsGestures = Utils.rMapHasKey(params, "supportsGestures")
			&& params.getBoolean("supportsGestures");

		double gestureScreenDistance = Utils.rMapHasKey(params, "gestureScreenDistance")
			? params.getDouble("gestureScreenDistance")
			: 30d;

		// Resolve fragment uuid for this entry. Default must match
		// LayerPath.java's default (DEFAULT_FRAGMENT_UUID) so that
		// ensureSharedLayer (called with the same value from create())
		// and getSharedLayer use the same key.
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		// Parse coordinates and merge in the screen-anchored points.
		Coordinate[] jtsCoordinates = buildResolvedCoordinates(
			readableArrayToJtsCoordinates(coordinates == null
				? Arguments.createArray()
				: coordinates),
			anchors
		);
		if (jtsCoordinates == null || jtsCoordinates.length == 0) {
			throw new IllegalArgumentException("Unable to parse coordinates");
		}

		// Parse style.
		ReadableMap styleMap = Utils.rMapHasKey(params, "paint")
			? params.getMap("paint")
			: null;
		Style.Builder styleBuilder = getStyleBuilder(styleMap, reactContext);

		int positionIndex = APPEND_PRIORITY;

		PathEntry entry = new PathEntry(
			entryUuid,
			fragmentUuid,
			positionIndex,
			jtsCoordinates,
			supportsGestures,
			(float) gestureScreenDistance
		);
		entry.anchors.addAll(anchors);

		// Draw LineDrawable segments onto the fragment's VectorLayer.
		VectorLayer vectorLayer = (VectorLayer) getSharedLayer(fragmentUuid);
		if (vectorLayer == null) {
			throw new IllegalStateException(
				"No shared VectorLayer found for fragmentUuid '" + fragmentUuid
					+ "'. Known fragments: " + sharedLayerFragments.keySet());
		}
		drawSegments(jtsCoordinates, styleBuilder.build(), entryUuid, vectorLayer, entry);
		vectorLayer.update();

		if (!anchors.isEmpty()) {
			registerScreenAnchorFrameCallback();
		}

		return new CreateResult<>(entry, null);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull PathEntry entry) {
		VectorLayer layer = (VectorLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			Log.w(TAG,
				"ZOMBIE: getSharedLayer returned null for fragmentUuid="
					+ entry.fragmentUuid + " entry=" + entry.pathUuid
					+ " drawableCount=" + entry.drawables.size()
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet());
			// Clear drawable references so GC can collect, even though the
			// drawables remain in the VectorLayer's QuadTree and will render
			// as zombies until the shared layer is destroyed.
			entry.drawables.clear();
			return;
		}
		// Remove all drawables belonging to this entry.
		for (LineDrawable d : entry.drawables) {
			drawableToEntry.remove(d);
			layer.remove(d);
		}
		entry.drawables.clear();
		layer.update();
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull PathEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		VectorLayer layer = (VectorLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			return new UpdateResult(null);
		}

		ReadableArray coordinates = Utils.rMapHasKey(params, "coordinates")
			? params.getArray("coordinates")
			: null;

		ReadableMap styleMap = Utils.rMapHasKey(params, "paint")
			? params.getMap("paint")
			: null;

		// screenPoints key present = replace the whole anchor set (an empty
		// array clears it); absent = keep the existing anchors.
		boolean anchorsProvided = Utils.rMapHasKey(params, "screenPoints");
		if (anchorsProvided) {
			entry.anchors.clear();
			entry.anchors.addAll(parseScreenPoints(params));
		}
		boolean anchored = !entry.anchors.isEmpty();

		Coordinate[] jtsCoordinates = coordinates != null && coordinates.size() > 0
			? readableArrayToJtsCoordinates(coordinates)
			: null;

		if (anchored && jtsCoordinates != null) {
			// Re-insert the anchored slots into the new geo coordinates.
			jtsCoordinates = buildResolvedCoordinates(jtsCoordinates, entry.anchors);
		} else if (anchored && anchorsProvided) {
			// Anchor set changed without new coordinates: rebuild from the
			// entry's existing geo points (drop the previous anchor slots).
			jtsCoordinates = buildResolvedCoordinates(
				extractGeoCoordinates(entry.jtsCoordinates, entry.anchors),
				entry.anchors
			);
		}

		// Append-only fast path: the new coordinates extend the previous
		// ones (same prefix) and no paint change was sent. Instead of
		// removing every drawable and re-adding them (which flickers on
		// every appended point, e.g. live track recording), only the new
		// segment drawables are added on top of the existing ones.
		// Anchored entries are excluded: their stored coordinates mutate
		// per frame, so a prefix comparison would be meaningless.
		boolean appendOnly = !anchored
			&& jtsCoordinates != null
			&& jtsCoordinates.length > 0
			&& styleMap == null
			&& entry.jtsCoordinates != null
			&& jtsCoordinates.length >= entry.jtsCoordinates.length
			&& sameCoordinatePrefix(entry.jtsCoordinates, jtsCoordinates);

		// The effective style after this update — needed upfront so
		// patterned entries (texture / stipple) can take their
		// single-drawable path even when this update carries no paint.
		Style effectiveStyle = styleMap != null
			? getStyleBuilder(styleMap, mapFragment.getActivity()).build()
			: entry.lastStyle;
		boolean patterned = effectiveStyle != null
			&& (effectiveStyle.texture != null || effectiveStyle.stipple != 0);

		if (patterned && jtsCoordinates != null && jtsCoordinates.length > 0) {
			// Patterned entries render as ONE drawable spanning the whole
			// polyline (see drawSegments) — keep the texture/dash length
			// accumulating continuously by rebuilding that drawable with
			// the new coordinates instead of appending per-segment
			// drawables (which would restart the pattern at each segment).
			entry.jtsCoordinates = jtsCoordinates;
			for (LineDrawable d : entry.drawables) {
				drawableToEntry.remove(d);
				layer.remove(d);
			}
			entry.drawables.clear();
			drawSegments(jtsCoordinates, effectiveStyle, entry.pathUuid, layer, entry);
		} else if (appendOnly) {
			appendSegments(entry, jtsCoordinates, layer);
			entry.jtsCoordinates = jtsCoordinates;
		} else {
			// Remove old drawables.
			for (LineDrawable d : entry.drawables) {
				drawableToEntry.remove(d);
				layer.remove(d);
			}
			entry.drawables.clear();

			// Redraw with the new coordinates (if provided).
			if (jtsCoordinates != null && jtsCoordinates.length > 0) {
				entry.jtsCoordinates = jtsCoordinates;

				// Parse new style, or reuse the style the entry was last
				// drawn with when this update carries no paint.
				Style style = effectiveStyle != null
					? effectiveStyle
					: Style.builder().build();

				drawSegments(jtsCoordinates, style, entry.pathUuid, layer, entry);
			}
		}

		layer.update();

		// Keep the per-frame anchor updater bound exactly while some entry
		// is anchored.
		if (anchored) {
			registerScreenAnchorFrameCallback();
		} else {
			unregisterScreenAnchorFrameCallback();
		}

		// Build response including bounds if requested.
		ReadableMap responseInclude = Utils.rMapHasKey(params, "responseInclude")
			? params.getMap("responseInclude")
			: null;
		WritableMap responseData = Arguments.createMap();
		responseData.putString("uuid", entry.pathUuid);
		if (responseInclude != null) {
			addResponseData(entry.pathUuid, responseInclude, 1, responseData);
		}

		return new UpdateResult(responseData);
	}

	/**
	 * True when every coordinate in {@code oldCoords} equals the coordinate
	 * at the same index in {@code newCoords} (exact x/y/z comparison) —
	 * i.e. {@code newCoords} only appended points.
	 */
	private static boolean sameCoordinatePrefix(
		@NonNull Coordinate[] oldCoords,
		@NonNull Coordinate[] newCoords
	) {
		if (newCoords.length < oldCoords.length) {
			return false;
		}
		for (int i = 0; i < oldCoords.length; i++) {
			Coordinate a = oldCoords[i];
			Coordinate b = newCoords[i];
			if (a == null || b == null || a.x != b.x || a.y != b.y || a.z != b.z) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Adds LineDrawable segments for the coordinates appended to
	 * {@code entry.jtsCoordinates} — the existing drawables stay untouched,
	 * so the path grows without a teardown/rebuild (no flicker).
	 */
	private void appendSegments(
		@NonNull PathEntry entry,
		@NonNull Coordinate[] jtsCoordinates,
		@NonNull VectorLayer vectorLayer
	) {
		Style style = entry.lastStyle != null
			? entry.lastStyle
			: Style.builder().build();
		int oldLength = entry.jtsCoordinates.length;
		for (int i = oldLength; i < jtsCoordinates.length; i++) {
			// New segment from the previously last point (i - 1) to i.
			double[] segment = new double[4];
			segment[0] = jtsCoordinates[i].x;
			segment[1] = jtsCoordinates[i].y;
			segment[2] = jtsCoordinates[i - 1].x;
			segment[3] = jtsCoordinates[i - 1].y;
			LineDrawable drawable = new LineDrawable(segment, style);
			vectorLayer.add(drawable);
			drawableToEntry.put(drawable, entry.pathUuid);
			drawable.setPriority(entry.positionIndex);
			entry.drawables.add(drawable);
		}
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
		float threshold = getCoordinateDistanceFromScreenDistance(
				x, y, entry.gestureScreenDistance);

		for (LineDrawable drawable : entry.drawables) {
			// Use distance() instead of buffer(d).contains() — see
			// VectorLayer.containsGetResponse for rationale.
			if (drawable.getGeometry().distance(point) <= threshold) {
				WritableMap params = Arguments.createMap();
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

	@Nullable
	@Override
	protected Envelope entryEnvelope(@NonNull PathEntry entry) {
		if (entry.drawables.isEmpty()) {
			return null;
		}
		if (!entry.anchors.isEmpty()) {
			// Anchored endpoints move with the viewport every frame — a
			// world envelope keeps the spatial hit-test index valid without
			// per-frame rebuilds. The exact hit test still uses the live
			// geometry.
			return new Envelope(-180, 180, -90, 90);
		}
		Envelope envelope = new Envelope();
		for (LineDrawable drawable : entry.drawables) {
			envelope.expandToInclude(drawable.getGeometry().getEnvelopeInternal());
		}
		return envelope;
	}

	// ── Override create/remove to sync gesture support ────────────────

	@NonNull
	@Override
	public CreateResult<PathEntry> create(
		@NonNull String entryUuid,
		@NonNull String fragmentUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		CreateResult<PathEntry> result = super.create(entryUuid, fragmentUuid, params,
			mapFragment, contentResolver, reactContext);
		syncGestureSupport();
		return result;
	}

	@Override
	public void remove(@NonNull String entryUuid) {
		super.remove(entryUuid);
		syncGestureSupport();
		unregisterScreenAnchorFrameCallback();
	}

	// ── Screen-anchored points ─────────────────────────────────────────

	/**
	 * True while a per-frame callback is registered with
	 * {@link ScreenAnchorRegistry} for this map view. The callback is bound
	 * exactly while at least one entry has screen-anchored points.
	 */
	private volatile boolean frameCallbackRegistered = false;

	private final ScreenAnchorRegistry.FrameCallback screenAnchorFrameCallback = this::updateAnchoredEntries;

	@NonNull
	private static List<ScreenAnchorPoint> parseScreenPoints(@NonNull ReadableMap params) {
		List<ScreenAnchorPoint> anchors = new ArrayList<>();
		if (!Utils.rMapHasKey(params, "screenPoints")) {
			return anchors;
		}
		ReadableArray screenPoints = params.getArray("screenPoints");
		if (screenPoints == null) {
			return anchors;
		}
		for (int i = 0; i < screenPoints.size(); i++) {
			ScreenAnchorPoint anchor = ScreenAnchorPoint.fromReadableMap(screenPoints.getMap(i));
			if (anchor != null) {
				anchors.add(anchor);
			}
		}
		return anchors;
	}

	@Nullable
	private static ScreenAnchorPoint anchorAt(
		@NonNull List<ScreenAnchorPoint> anchors,
		int index
	) {
		for (ScreenAnchorPoint anchor : anchors) {
			if (anchor.index == index) {
				return anchor;
			}
		}
		return null;
	}

	/**
	 * Merges geo coordinates and anchored slots into the resolved point
	 * list. Each anchor is resolved against the current viewport (falling
	 * back to the map position center when the viewport has no size yet —
	 * the first real frame event then corrects it).
	 */
	@Nullable
	private Coordinate[] buildResolvedCoordinates(
		@NonNull Coordinate[] geoCoordinates,
		@NonNull List<ScreenAnchorPoint> anchors
	) {
		if (geoCoordinates.length == 0 && anchors.isEmpty()) {
			return null;
		}
		Coordinate[] resolved = new Coordinate[geoCoordinates.length + anchors.size()];
		int geoIdx = 0;
		for (int i = 0; i < resolved.length; i++) {
			ScreenAnchorPoint anchor = anchorAt(anchors, i);
			if (anchor != null) {
				anchor.resolve(mapView);
				if (anchor.isResolved()) {
					resolved[i] = new Coordinate(anchor.lng, anchor.lat, 0);
				} else {
					MapPosition mp = mapView.map().getMapPosition();
					resolved[i] = new Coordinate(mp.getLongitude(), mp.getLatitude(), 0);
				}
			} else {
				if (geoIdx >= geoCoordinates.length) {
					throw new IllegalArgumentException(
						"screenPoints indices exceed the provided coordinates");
				}
				resolved[i] = geoCoordinates[geoIdx++];
			}
		}
		if (geoIdx != geoCoordinates.length) {
			throw new IllegalArgumentException(
				"screenPoints indices skip provided coordinates");
		}
		return resolved;
	}

	/** Drops the coordinates at the anchored slots from a resolved list. */
	@NonNull
	private static Coordinate[] extractGeoCoordinates(
		@NonNull Coordinate[] resolved,
		@NonNull List<ScreenAnchorPoint> anchors
	) {
		Coordinate[] geo = new Coordinate[resolved.length - anchors.size()];
		int g = 0;
		for (int i = 0; i < resolved.length; i++) {
			if (anchorAt(anchors, i) != null) {
				continue;
			}
			geo[g++] = resolved[i];
		}
		return geo;
	}

	private void registerScreenAnchorFrameCallback() {
		if (!frameCallbackRegistered) {
			frameCallbackRegistered = true;
			ScreenAnchorRegistry.register(nativeNodeHandle, mapView, screenAnchorFrameCallback);
		}
	}

	private void unregisterScreenAnchorFrameCallback() {
		if (frameCallbackRegistered && !hasAnchoredEntries()) {
			frameCallbackRegistered = false;
			ScreenAnchorRegistry.unregister(nativeNodeHandle, screenAnchorFrameCallback);
		}
	}

	private boolean hasAnchoredEntries() {
		for (PathEntry entry : entries.values()) {
			if (!entry.anchors.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Per-frame entry point (registered with {@link ScreenAnchorRegistry}):
	 * re-resolves every anchored coordinate against the live viewport and
	 * mutates the touched segment drawables in place. Runs on the map event
	 * thread; the mutation sequence is serialized against the shared
	 * VectorLayer's {@code processFeatures} (worker thread) via its monitor.
	 */
	private void updateAnchoredEntries(@NonNull MapView mapView) {
		for (PathEntry entry : entries.values()) {
			if (entry.anchors.isEmpty()) {
				continue;
			}
			VectorLayer layer = (VectorLayer) getSharedLayer(entry.fragmentUuid);
			if (layer == null) {
				continue;
			}
			boolean changed = false;
			for (ScreenAnchorPoint anchor : entry.anchors) {
				changed |= anchor.resolveAndCheckChanged(mapView);
			}
			if (!changed) {
				continue;
			}
			int pointCount = entry.jtsCoordinates.length;
			synchronized (layer) {
				for (ScreenAnchorPoint anchor : entry.anchors) {
					int i = anchor.index;
					if (i < 0 || i >= pointCount) {
						continue;
					}
					entry.jtsCoordinates[i].x = anchor.lng;
					entry.jtsCoordinates[i].y = anchor.lat;
					// drawables.get(j) spans points j -> j+1, so point i is
					// the end of segment i-1 and the start of segment i.
					if (i > 0 && i - 1 < entry.drawables.size()) {
						updateDrawableEndpoint(entry.drawables.get(i - 1), 1, anchor, layer);
					}
					if (i < pointCount - 1 && i < entry.drawables.size()) {
						updateDrawableEndpoint(entry.drawables.get(i), 0, anchor, layer);
					}
				}
			}
			// Recompile the shared layer's geometry on its worker thread so
			// the mutated drawables reach the renderer this frame.
			layer.update();
		}
	}

	/**
	 * Moves one endpoint of a segment drawable: remove with the old bbox
	 * (still matches the QuadTree entry), mutate the JTS geometry in place,
	 * re-add with the fresh bbox. Must be called while holding the layer's
	 * monitor.
	 */
	private static void updateDrawableEndpoint(
		@NonNull LineDrawable drawable,
		int coordIndex,
		@NonNull ScreenAnchorPoint anchor,
		@NonNull VectorLayer layer
	) {
		layer.remove(drawable);
		Geometry geometry = drawable.getGeometry();
		if (geometry instanceof LineString) {
			LineString line = (LineString) geometry;
			line.getCoordinateSequence().setOrdinate(coordIndex, 0, anchor.lng);
			line.getCoordinateSequence().setOrdinate(coordIndex, 1, anchor.lat);
			// Invalidate JTS's cached envelope so the re-add indexes the
			// new bbox.
			line.geometryChanged();
		}
		layer.add(drawable);
	}

	// ── Batch creation / removal ──────────────────────────────────────

	/**
	 * Creates multiple path entries in a single batch: ensures all required
	 * fragment layers upfront, creates each entry, and calls updateMap once
	 * (via the coalesced scheduleUpdate). Per-item errors are captured; the
	 * response mirrors the single createLayer response for each item.
	 */
	@NonNull
	public WritableMap createPaths(
		@NonNull ReadableArray pathsArray,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext,
		@NonNull ReadableMap defaultResponseInclude
	) throws Exception {
		int count = pathsArray.size();
		ReadableMap[] allParams = new ReadableMap[count];
		String[] entryUuids = new String[count];
		String[] errors = new String[count];

		// Ensure all required fragment layers exist upfront. A fragment
		// ensure that fails (e.g. map torn down mid-request) is recorded
		// per fragment — the batch must never reject wholesale after
		// teardown; the affected items report per-item errors instead.
		Map<String, String> fragmentErrors = new HashMap<>();
		Set<String> seenFragments = new HashSet<>();
		for ( int i = 0; i < count; i++ ) {
			allParams[i] = pathsArray.getMap( i );
			String fragmentUuid = Utils.rMapHasKey( allParams[i], "fragmentUuid" )
				? allParams[i].getString( "fragmentUuid" )
				: DEFAULT_FRAGMENT_UUID;
			if ( seenFragments.add( fragmentUuid ) ) {
				try {
					ensureSharedLayer( fragmentUuid, Utils.rMapGetStringList( allParams[i], "layerUuids" ) );
				} catch ( Exception e ) {
					fragmentErrors.put( fragmentUuid, errorMessage(e) );
				}
			}
		}

		for ( int i = 0; i < count; i++ ) {
			String entryUuid = UUID.randomUUID().toString();
			entryUuids[i] = entryUuid;
			String fragmentUuid = Utils.rMapHasKey( allParams[i], "fragmentUuid" )
				? allParams[i].getString( "fragmentUuid" )
				: DEFAULT_FRAGMENT_UUID;
			String fragmentError = fragmentErrors.get( fragmentUuid );
			if ( fragmentError != null ) {
				errors[i] = fragmentError;
				continue;
			}
			try {
				// super.create — the per-item syncGestureSupport override is
				// hoisted to once per batch below (O(n) instead of O(n²)).
				super.create( entryUuid, fragmentUuid, allParams[i], mapFragment, contentResolver, reactContext );
			} catch ( Exception e ) {
				errors[i] = errorMessage(e);
			}
		}

		// One gesture-support sync for the whole batch.
		syncGestureSupport();
		scheduleUpdate();

		WritableArray results = Arguments.createArray();
		for ( int i = 0; i < count; i++ ) {
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString( "uuid", entryUuids[i] );
			if ( errors[i] != null ) {
				resultItem.putString( "error", errors[i] );
			} else {
				ReadableMap responseInclude = Utils.rMapHasKey( allParams[i], "responseInclude" )
					? allParams[i].getMap( "responseInclude" )
					: defaultResponseInclude;
				resultItem.putMap( "response", buildCreateResponse( entryUuids[i], responseInclude ) );
			}
			results.pushMap( resultItem );
		}

		WritableMap response = Arguments.createMap();
		response.putArray( "results", results );
		return response;
	}

	/**
	 * Removes multiple path entries in a single batch. The coalesced
	 * scheduleUpdate collapses the per-entry updates into one updateMap.
	 */
	@NonNull
	public WritableMap removePaths( @NonNull ReadableArray pathUuids ) {
		int count = pathUuids.size();
		WritableArray results = Arguments.createArray();
		for ( int i = 0; i < count; i++ ) {
			String uuid = pathUuids.getString( i );
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString( "uuid", uuid );
			try {
				// super.remove — per-item syncGestureSupport hoisted to
				// once per batch below.
				super.remove( uuid );
			} catch ( Exception e ) {
				resultItem.putString( "error", errorMessage(e) );
			}
			results.pushMap( resultItem );
		}
		// One gesture-support sync for the whole batch.
		syncGestureSupport();
		WritableMap response = Arguments.createMap();
		response.putArray( "results", results );
		return response;
	}

	/**
	 * Updates multiple path entries in a single batch. Each item carries its
	 * own uuid + new coordinates/paint; per-item errors are captured, and the
	 * response mirrors the single updateCoordinates response for each item.
	 * The coalesced scheduleUpdate collapses the per-entry updates into one
	 * updateMap.
	 */
	@NonNull
	public WritableMap updatePaths(
		@NonNull ReadableArray pathsArray,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) {
		int count = pathsArray.size();
		WritableArray results = Arguments.createArray();
		for ( int i = 0; i < count; i++ ) {
			ReadableMap params = pathsArray.getMap( i );
			String uuid = Utils.rMapHasKey( params, "uuid" )
				? params.getString( "uuid" ) : "";
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString( "uuid", uuid );
			try {
				WritableMap responseData = update( uuid, params, mapFragment, contentResolver );
				if ( responseData != null ) {
					resultItem.putMap( "response", responseData );
				} else {
					// Entry gone (e.g. removed/re-keyed between the JS update
					// and this batch) — a benign no-op. Log rather than emit a
					// per-item error: surfacing an error would turn a normal
					// update/remove race (line leaving the viewport during a
					// zoom re-simplification) into a spurious onError.
					Log.d( TAG, "updatePaths: entry not found (no-op) for uuid=" + uuid );
				}
			} catch ( Exception e ) {
				resultItem.putString( "error", errorMessage(e) );
			}
			results.pushMap( resultItem );
		}
		WritableMap response = Arguments.createMap();
		response.putArray( "results", results );
		return response;
	}

	// ── Path-specific public API ────────────────────────────────────────

	/**
	 * Applies sparse drawable priorities to entries of a shared fragment.
	 * Only the listed entries are touched — the JS scene emits O(changed)
	 * assignments per mutation. The upstream VectorLayer sorts drawables by
	 * getPriority() on every frame, so no explicit re-sort is needed.
	 *
	 * @return true when the fragment exists and the assignments were applied;
	 *         false when the fragment is missing (the caller must REJECT so
	 *         the JS presenter keeps the state uncommitted and re-sends)
	 */
	public boolean applyEntryPriorities( @NonNull String fragmentUuid, @NonNull ReadableArray assignments ) {
		VectorLayer layer = (VectorLayer) getSharedLayer( fragmentUuid );
		if ( layer == null ) {
			Log.d( TAG,
				"ZOMBIE: applyEntryPriorities — getSharedLayer returned null for fragmentUuid="
					+ fragmentUuid
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet() );
			return false;
		}
		for ( int i = 0; i < assignments.size(); i++ ) {
			ReadableMap assignment = assignments.getMap( i );
			String uuid = assignment.getString( "uuid" );
			int priority = assignment.getInt( "priority" );
			PathEntry entry = entries.get( uuid );
			// Guard against cross-fragment assignments: never touch an
			// entry that doesn't belong to this fragment's layer.
			if ( entry == null || !fragmentUuid.equals( entry.fragmentUuid ) ) {
				continue;
			}
			entry.positionIndex = priority;
			for ( LineDrawable drawable : entry.drawables ) {
				drawable.setPriority( priority );
			}
		}
		scheduleUpdate();
		return true;
	}

	public void updateSupportsGestures(@NonNull String entryUuid, boolean supportsGestures) {
		PathEntry entry = entries.get(entryUuid);
		if (entry != null) {
			entry.supportsGestures = supportsGestures;
			syncGestureSupport();
		}
	}

	/**
	 * Syncs the shared VectorLayer's {@code mSupportsGestures} flag to whether
	 * any PathEntry has gesture handlers. When no entries want gestures, the
	 * shared layer's {@code onGesture()} short-circuits at the first guard —
	 * no hit-test loop at all.
	 *
	 * <p>Called from the overridden {@link #create}, {@link #remove}, and
	 * {@link #updateSupportsGestures} so the flag stays consistent across
	 * the entry lifecycle.
	 */
	protected void syncGestureSupport() {
		boolean hasAny = false;
		for (PathEntry entry : entries.values()) {
			if (entry.supportsGestures) {
				hasAny = true;
				break;
			}
		}
		applyGestureSupport(hasAny);
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
		WritableMap responseData = Arguments.createMap();
		responseData.putString("uuid", entryUuid);
		addResponseData(entryUuid, responseInclude, 0, responseData);
		return responseData;
	}

	// ── Internal helpers ────────────────────────────────────────────────

	/** Decoded line textures shared across all path layers (asset path → texture). */
	private static final Map<String, TextureItem> textureCache = new ConcurrentHashMap<>();

	/**
	 * Returns the (shared, lazily decoded) line texture for the given
	 * Android assets path, or null when the asset can't be read.
	 */
	@Nullable
	public static TextureItem getTextureItem(
		@NonNull String assetPath,
		@NonNull Context context
	) {
		return textureCache.computeIfAbsent(assetPath, path -> {
			try {
				InputStream is = context.getAssets().open(path);
				try (is) {
					// repeat=true (GL_REPEAT wrap): the fixed-texture
					// shader samples with mod(s + 1, 2) — with the default
					// CLAMP_TO_EDGE the first stipple window would show
					// only the tile's right edge. REPEAT makes the pattern
					// period exactly `stipple` px with the tile filling it.
					return new TextureItem(CanvasAdapter.decodeBitmap(is), true);
				}
			} catch (IOException e) {
				Log.w(TAG, "Unable to decode line texture asset: " + path, e);
				return null;
			}
		});
	}

	/**
	 * Mirrors {@code LayerPath.getStyleBuilderFromMap} — kept here so
	 * PathLayerManager is self-contained.
	 */
	@NonNull
	protected Style.Builder getStyleBuilder(@Nullable ReadableMap styleMap) {
		// Kept as an overload for subclasses (e.g. the color-ramp
		// extension) that override the single-argument form.
		return getStyleBuilder(styleMap, null);
	}

	@NonNull
	protected Style.Builder getStyleBuilder(
		@Nullable ReadableMap styleMap,
		@Nullable Context context
	) {
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

		boolean patterned = false;

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
				if (styleMap.getInt("stipple") != 0) {
					patterned = true;
				}
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
			if (styleMap.hasKey("textureFilePath")) {
				String textureFilePath = styleMap.getString("textureFilePath");
				if (textureFilePath != null && !textureFilePath.isEmpty()) {
					TextureItem texture = context != null
						? getTextureItem(textureFilePath, context)
						: null;
					if (texture != null) {
						styleBuilder.texture(texture);
						patterned = true;
						// A texture without a period would sample with
						// u_pscale == 0 (degenerate texture coordinates).
						if (!styleMap.hasKey("stipple")) {
							styleBuilder.stipple(64);
						}
					}
				}
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

		// The engine's LineTexBucket computes the extrusion width as
		// `lb.scale * width / s` with s = f / (f / 4) = 4 for vector
		// layers (UNSCALE_COORD = 4), while solid LineBuckets use
		// `width / sqrt(div)` with div = 4 → width / 2. Patterned styles
		// therefore render at HALF the solid width for the same
		// strokeWidth — compensate here so textured/stippled paths match
		// solid ones exactly. TEXLINE buckets only ever serve vector
		// layers, so the factor is zoom-invariant and exact.
		if (patterned) {
			styleBuilder.strokeWidth((float) (strokeWidth * 2.0));
		}

		return styleBuilder;
	}

	/**
	 * Mirrors {@code LayerPath.readableArrayToJtsCoordinates}.
	 */
		@NonNull
		private static Coordinate[] readableArrayToJtsCoordinates(
			@NonNull ReadableArray coordinates) {
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
		return jtsCoordinates;
	}

	/**
	 * Draws LineDrawable segments onto the shared VectorLayer.
	 * Mirrors {@code LayerPath.drawLineForCoordinates}.
	 *
	 * Patterned styles (texture / stipple) are drawn as ONE drawable
	 * spanning all coordinates: the engine accumulates the texture/dash
	 * length per drawable, so per-segment drawables would restart the
	 * pattern at every segment and crumble at low zoom (segments shorter
	 * than the pattern period on screen).
	 */
	protected void drawSegments(
		@NonNull Coordinate[] jtsCoordinates,
		@NonNull Style style,
		@NonNull String entryUuid,
		@NonNull VectorLayer vectorLayer,
		@NonNull PathEntry entry
	) {
		entry.lastStyle = style;

		if (style.texture != null || style.stipple != 0) {
			double[] coords = new double[jtsCoordinates.length * 2];
			int c = 0;
			for (Coordinate jts : jtsCoordinates) {
				coords[c++] = jts.x;
				coords[c++] = jts.y;
			}
			LineDrawable drawable = new LineDrawable(coords, style);
			vectorLayer.add(drawable);
			drawableToEntry.put(drawable, entryUuid);
			drawable.setPriority(entry.positionIndex);
			entry.drawables.add(drawable);
			return;
		}

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
				// Set priority so the upstream VectorLayer's built-in
				// Comparator (which sorts by getPriority() every frame)
				// renders drawables in JSX tree order.
				drawable.setPriority(entry.positionIndex);
				entry.drawables.add(drawable);
			}
		}

		// Drawable ordering: each LineDrawable's priority was set to the entry's
		// positionIndex above. The upstream VectorLayer sorts tmpDrawables by
		// getPriority() on every frame before rendering, so JSX tree order
		// determines paint order automatically.
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
		// Guarded reads: a partial JS responseInclude must never throw here
		// (a throw would reject the whole batch AFTER the entries were
		// created, orphaning them as zombies).
		if (Utils.rMapHasKey(responseInclude, "coordinates")
			&& responseInclude.getInt("coordinates") > includeLevel) {
			addCoordinatesToResponse(entry.jtsCoordinates, responseParams);
		}
		if (Utils.rMapHasKey(responseInclude, "bounds")
			&& responseInclude.getInt("bounds") > includeLevel) {
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
		com.facebook.react.bridge.WritableArray bboxParams = Arguments.createArray();
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
			com.facebook.react.bridge.WritableArray arr = Arguments.createArray();
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
	protected VectorLayer.GestureListener createGestureListener() {
		return (type, eventParams) -> {
			// eventParams already has the correct per-entry uuid from the
			// UuidResolver. Add type, emit through the callback.
			if (eventCallback == null) {
				return;
			}
			// The GestureListener is called by VectorLayer.onGesture, which
			// already populated uuid/distance/nearestPoint in containsGetResponse.
			WritableMap payload = Arguments.createMap();
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
		if (frameCallbackRegistered) {
			frameCallbackRegistered = false;
			ScreenAnchorRegistry.unregister(nativeNodeHandle, screenAnchorFrameCallback);
		}
		drawableToEntry.clear();
		super.destroy();
	}
}
