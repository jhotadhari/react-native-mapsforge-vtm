package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.ScreenLineLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;

/**
 * One dedicated {@link ScreenLineLayer} per {@code LayerScreenLine}
 * component. The line's endpoints (geographic points + screen-anchored
 * overrides) are handed to the per-frame renderer; no map-event listener
 * or spatial index is involved — the renderer rebuilds its geometry from
 * the frame's own viewport on the GL thread.
 */
public class ScreenLineLayerManager extends LayerManager<ScreenLineLayerManager.ScreenLineEntry> {

	private static final String TAG = "ScreenLineLayerManager";

	public static final String NAME = "screenLines";

	/** Fallback fragment uuid when params carry none (single implicit fragment). */
	public static final String DEFAULT_FRAGMENT_UUID = "__vtm_shared_screenLines__0";

	// ── Factory ─────────────────────────────────────────────────────────

	private static final Factory<ScreenLineLayerManager> FACTORY = ScreenLineLayerManager::new;

	@NonNull
	public static ScreenLineLayerManager get(int nativeNodeHandle, @NonNull MapView mapView) {
		return LayerManager.get(nativeNodeHandle, mapView, NAME, FACTORY);
	}

	@Nullable
	public static ScreenLineLayerManager getInstance(int nativeNodeHandle) {
		return (ScreenLineLayerManager) LayerManager.getInstance(nativeNodeHandle, NAME);
	}

	// ── Entry type ──────────────────────────────────────────────────────

	public static class ScreenLineEntry {
		@NonNull
		public final String entryUuid;
		@NonNull
		public final String fragmentUuid;

		/** Resolved point list: lng/lat per point (unused for anchored slots). */
		@NonNull
		public double[] lngs = new double[0];
		@NonNull
		public double[] lats = new double[0];
		/** Index-parallel anchor overrides; null entries are geographic. */
		@NonNull
		public ScreenAnchorPoint[] anchors = new ScreenAnchorPoint[0];
		public int pointCount = 0;

		public int strokeColor = 0xff000000;
		public float strokeWidthDp = 2f;
		@NonNull
		public Paint.Cap cap = Paint.Cap.ROUND;

		public ScreenLineEntry(@NonNull String entryUuid, @NonNull String fragmentUuid) {
			this.entryUuid = entryUuid;
			this.fragmentUuid = fragmentUuid;
		}
	}

	// ── Constructor ─────────────────────────────────────────────────────

	protected ScreenLineLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name);
	}

	// ── LayerManager contract ───────────────────────────────────────────

	@NonNull
	@Override
	protected String entryFragmentUuid(@NonNull ScreenLineEntry entry) {
		return entry.fragmentUuid;
	}

	@NonNull
	@Override
	protected Layer createSharedLayer() {
		return new ScreenLineLayer(mapView.map());
	}

	@NonNull
	@Override
	protected CreateResult<ScreenLineEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		ScreenLineEntry entry = new ScreenLineEntry(entryUuid, fragmentUuid);
		applyParams(entry, params, true);

		ScreenLineLayer layer = (ScreenLineLayer) getSharedLayer(fragmentUuid);
		if (layer == null) {
			throw new IllegalStateException(
				"No shared ScreenLineLayer for fragmentUuid '" + fragmentUuid
					+ "'. Known fragments: " + sharedLayerFragments.keySet());
		}

		applyToRenderer(entry, layer);

		return new CreateResult<>(entry, null);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull ScreenLineEntry entry) {
		// The shared layer itself is pruned by the base class once the last
		// entry of the fragment is gone — nothing per-entry to remove here.
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull ScreenLineEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		ScreenLineLayer layer = (ScreenLineLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			return new UpdateResult(null);
		}

		applyParams(entry, params, false);
		applyToRenderer(entry, layer);

		WritableMap responseData = new WritableNativeMap();
		responseData.putString("uuid", entry.entryUuid);
		responseData.putInt("nativeNodeHandle", nativeNodeHandle);
		return new UpdateResult(responseData);
	}

	@Nullable
	@Override
	protected WritableMap hitTestEntry(
		@NonNull ScreenLineEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	) {
		// No gesture support yet — the line is a visual connector.
		return null;
	}

	// ── Internal helpers ────────────────────────────────────────────────

	private void applyToRenderer(@NonNull ScreenLineEntry entry, @NonNull ScreenLineLayer layer) {
		layer.getScreenLineRenderer().setPoints(
			entry.lngs,
			entry.lats,
			entry.anchors,
			entry.pointCount
		);
		layer.getScreenLineRenderer().setPaint(
			entry.strokeColor,
			entry.strokeWidthDp,
			entry.cap
		);
	}

	/**
	 * Applies the param keys present in {@code params} onto the entry. On
	 * create, coordinates/screenPoints are required; on update, absent keys
	 * keep the entry's current values (sparse updates).
	 */
	private void applyParams(
		@NonNull ScreenLineEntry entry,
		@NonNull ReadableMap params,
		boolean create
	) throws Exception {
		boolean coordinatesProvided = Utils.rMapHasKey(params, "coordinates");
		boolean screenPointsProvided = Utils.rMapHasKey(params, "screenPoints");

		if (create) {
			if (!coordinatesProvided && !screenPointsProvided) {
				throw new IllegalArgumentException("LayerScreenLine requires coordinates");
			}
			coordinatesProvided = true;
			screenPointsProvided = true;
		}

		// Geo-only coordinates: from the provided key on create/coordinate
		// updates, otherwise extracted from the entry's current resolved
		// list (stripping the old anchor slots).
		double[] lngs;
		double[] lats;
		if (coordinatesProvided) {
			ReadableArray coordinates = params.getArray("coordinates");
			int geoCount = coordinates != null ? coordinates.size() : 0;
			lngs = new double[geoCount];
			lats = new double[geoCount];
			for (int i = 0; i < geoCount; i++) {
				ReadableArray position = coordinates.getArray(i);
				lngs[i] = Utils.lngFromPosition(position);
				lats[i] = Utils.latFromPosition(position);
			}
		} else {
			int geoCount = 0;
			for (ScreenAnchorPoint anchor : entry.anchors) {
				if (anchor == null) {
					geoCount++;
				}
			}
			lngs = new double[geoCount];
			lats = new double[geoCount];
			int g = 0;
			for (int i = 0; i < entry.pointCount; i++) {
				if (entry.anchors[i] == null) {
					lngs[g] = entry.lngs[i];
					lats[g] = entry.lats[i];
					g++;
				}
			}
		}

		// Anchors: only when the key is present (create always provides it).
		ScreenAnchorPoint[] anchors;
		if (screenPointsProvided) {
			ReadableArray screenPoints = params.getArray("screenPoints");
			int anchorCount = screenPoints != null ? screenPoints.size() : 0;
			anchors = new ScreenAnchorPoint[anchorCount];
			for (int i = 0; i < anchorCount; i++) {
				anchors[i] = ScreenAnchorPoint.fromReadableMap(screenPoints.getMap(i));
			}
		} else {
			// Keep the existing anchors — only meaningful when coordinates
			// were provided (the resolved slots shift with them).
			anchors = entry.anchors;
		}

		// Build the resolved point list: geo points plus anchored slots at
		// their indices (mirrors PathLayerManager's resolved layout).
		int pointCount = lngs.length + anchors.length;
		if (pointCount < 2) {
			throw new IllegalArgumentException("LayerScreenLine requires at least 2 coordinates");
		}
		double[] resolvedLngs = new double[pointCount];
		double[] resolvedLats = new double[pointCount];
		ScreenAnchorPoint[] resolvedAnchors = new ScreenAnchorPoint[pointCount];
		int geoIdx = 0;
		for (int i = 0; i < pointCount; i++) {
			ScreenAnchorPoint anchor = anchorAt(anchors, i);
			if (anchor != null) {
				resolvedAnchors[i] = anchor;
			} else {
				if (geoIdx >= lngs.length) {
					throw new IllegalArgumentException(
						"screenPoints indices exceed the provided coordinates");
				}
				resolvedLngs[i] = lngs[geoIdx];
				resolvedLats[i] = lats[geoIdx];
				geoIdx++;
			}
		}
		if (geoIdx != lngs.length) {
			throw new IllegalArgumentException(
				"screenPoints indices skip provided coordinates");
		}

		entry.lngs = resolvedLngs;
		entry.lats = resolvedLats;
		entry.anchors = resolvedAnchors;
		entry.pointCount = pointCount;

		if (!params.hasKey("paint")) {
			return;
		}
		ReadableMap paint = params.getMap("paint");
		if (paint == null) {
			return;
		}
		if (paint.hasKey("strokeColor")) {
			entry.strokeColor = Color.parseColor(paint.getString("strokeColor"));
		}
		if (paint.hasKey("strokeWidth")) {
			entry.strokeWidthDp = (float) paint.getDouble("strokeWidth");
		}
		if (paint.hasKey("cap")) {
			entry.cap = switch (paint.getString("cap")) {
				case "ROUND" -> Paint.Cap.ROUND;
				case "BUTT" -> Paint.Cap.BUTT;
				case "SQUARE" -> Paint.Cap.SQUARE;
				default -> Paint.Cap.ROUND;
			};
		}
	}

	@Nullable
	private static ScreenAnchorPoint anchorAt(
		@NonNull ScreenAnchorPoint[] anchors,
		int index
	) {
		for (ScreenAnchorPoint anchor : anchors) {
			if (anchor != null && anchor.index == index) {
				return anchor;
			}
		}
		return null;
	}
}
