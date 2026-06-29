package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.oscim.core.GeoPoint;
import org.oscim.event.Gesture;
import org.oscim.event.MotionEvent;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.map.Map;

/**
 * Thin wrapper around {@code org.oscim.layers.vector.PathLayer} (vtm-jts)
 * that adds per-layer gesture event routing.
 *
 * <p>Each {@code LayerPathJts} JS component owns one dedicated instance of
 * this wrapper — unlike the shared-layer pattern used by {@code LayerPath}
 * and {@code PathLayerManager}.
 */
public class PathLayerJtsWrapper extends org.oscim.layers.vector.PathLayer {

	/**
	 * Callback for gesture events. The wrapper converts vtm gesture types
	 * to JS event names and includes hit-test results (distance, nearestPoint,
	 * eventPosition).
	 */
	public interface GestureCallback {
		void onGesture(@NonNull String type, @NonNull WritableMap payload);
	}

	@Nullable
	private GestureCallback gestureCallback;

	/** Screen-distance threshold for hit-testing in pixels. */
	private float gestureScreenDistance = 20f;

	@Nullable
	private String entryUuid;

	public PathLayerJtsWrapper(@NonNull Map map, @NonNull Style style) {
		super(map, style);
	}

	public PathLayerJtsWrapper(@NonNull Map map, int lineColor, float lineWidth) {
		super(map, lineColor, lineWidth);
	}

	public void setGestureCallback(@Nullable GestureCallback callback) {
		this.gestureCallback = callback;
	}

	public void setGestureScreenDistance(float distance) {
		this.gestureScreenDistance = distance;
	}

	public void setEntryUuid(@Nullable String uuid) {
		this.entryUuid = uuid;
	}

	@Nullable
	public String getEntryUuid() {
		return entryUuid;
	}

	@Override
	public boolean onGesture(Gesture g, MotionEvent e) {
		if (gestureCallback == null || entryUuid == null) {
			return false;
		}

		String type;
		if (g instanceof Gesture.Tap) {
			type = "press";
		} else if (g instanceof Gesture.LongPress) {
			type = "longPress";
		} else if (g instanceof Gesture.DoubleTap) {
			type = "doubleTap";
		} else {
			// Let Move and Press events pass through so they don't block
			// map panning.
			return false;
		}

		// Hit-test against the path geometry at the screen coordinates.
		float x = e.getX();
		float y = e.getY();

		if (!contains(x, y)) {
			return false;
		}

		// Build hit-test result payload.
		WritableMap payload = Arguments.createMap();
		payload.putString("uuid", entryUuid);
		payload.putDouble("x", x);
		payload.putDouble("y", y);
		payload.putString("type", type);

		// Compute event position in geo coordinates.
		if (mMap != null) {
			GeoPoint eventPoint = mMap.viewport().fromScreenPoint(x, y);
			payload.putArray("eventPosition", Utils.positionToWritableArray(
				eventPoint.getLongitude(),
				eventPoint.getLatitude(),
				null
			));

			// Compute nearest point on the path and distance.
			Point point = new GeometryFactory().createPoint(
				new Coordinate(eventPoint.getLongitude(), eventPoint.getLatitude())
			);

			// Build a LineString from the current points for distance calculation.
			java.util.List<GeoPoint> points = getPoints();
			if (points != null && points.size() >= 2) {
				Coordinate[] coords = new Coordinate[points.size()];
				for (int i = 0; i < points.size(); i++) {
					GeoPoint gp = points.get(i);
					coords[i] = new Coordinate(gp.getLongitude(), gp.getLatitude());
				}
				LineString lineString = new GeometryFactory().createLineString(coords);
				double distance = lineString.distance(point);
				payload.putDouble("distance", distance);

				Coordinate[] nearestPoints = DistanceOp.nearestPoints(lineString, point);
				if (nearestPoints.length >= 1) {
					payload.putArray("nearestPoint", Utils.positionToWritableArray(
						nearestPoints[0].x,
						nearestPoints[0].y,
						null
					));
				}
			}
		}

		gestureCallback.onGesture(type, payload);
		return true;
	}
}
