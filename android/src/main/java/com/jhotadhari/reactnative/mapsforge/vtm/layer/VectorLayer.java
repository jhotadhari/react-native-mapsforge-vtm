package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.locationtech.jts.operation.distance.DistanceOp;
import org.oscim.core.GeoPoint;
import org.oscim.event.Gesture;
import org.oscim.event.MotionEvent;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.map.Map;
import org.oscim.utils.SpatialIndex;
import org.oscim.utils.geom.GeomBuilder;

public class VectorLayer extends org.oscim.layers.vector.VectorLayer {

	/**
	 * Notified whenever a gesture (or a manual trigger) hits this layer's geometry.
	 * Implemented by the owning module, so it can emit the event through its own
	 * codegen EventEmitter without this layer needing a reference to the TurboModule.
	 */
	public interface GestureListener {
		void onGesture( String type, WritableMap params );
	}

	/**
	 * Resolves which uuid a hit-tested {@link Drawable} belongs to.
	 * When set, {@link #containsGetResponse} returns the resolved uuid
	 * instead of the layer's own {@link #mUuid}. Used by
	 * {@code PathLayerManager} so gesture events carry the correct
	 * per-component entry uuid even though all path entries share a
	 * single {@code VectorLayer}.
	 */
	@FunctionalInterface
	public interface UuidResolver {
		String resolveUuid(Drawable hitDrawable);
	}

	protected final String mUuid;
	protected Boolean mSupportsGestures;
	protected final GestureListener mGestureListener;
	protected float mGestureScreenDistance = 30f;
	@Nullable
	protected UuidResolver mUuidResolver;

	public VectorLayer( Map map, SpatialIndex<Drawable> index ) {
		super( map, index );
		mUuid = null;
		mSupportsGestures = false;
		mGestureListener = null;
	}

	public VectorLayer( Map map ) {
		super( map );
		mUuid = null;
		mSupportsGestures = false;
		mGestureListener = null;
	}

	public VectorLayer( Map map, String uuid, Boolean supportsGestures, GestureListener gestureListener, float gestureScreenDistance ) {
		super( map );
		mUuid = uuid;
		mSupportsGestures = supportsGestures;
		mGestureListener = gestureListener;
		mGestureScreenDistance = gestureScreenDistance;
	}

	public void setGestureScreenDistance( float gestureScreenDistance ) {
		mGestureScreenDistance = gestureScreenDistance;
	}

	public float getGestureScreenDistance() {
		return mGestureScreenDistance;
	}

	public GestureListener getGestureListener() {
		return mGestureListener;
	}

	public boolean getSupportsGestures() {
		return mSupportsGestures;
	}

	public void setSupportsGestures( boolean supportsGestures ) {
		mSupportsGestures = supportsGestures;
	}

	@Nullable
	public UuidResolver getUuidResolver() {
		return mUuidResolver;
	}

	public void setUuidResolver( @Nullable UuidResolver resolver ) {
		mUuidResolver = resolver;
	}

	/**
	 * Drops all previously drawn geometries (mDrawables, inherited from the jts VectorLayer)
	 * so callers can redraw new geometry in place, without replacing this Layer instance on
	 * the map (which would need re-binding gesture/update listeners, see Layers#set).
	 */
	public synchronized void clearDrawables() {
		mDrawables.clear();
	}

	@Override
	public boolean onGesture( Gesture g, MotionEvent e ) {
		if ( mGestureListener == null || ! mSupportsGestures ) {
			return false;
		}
		// Determine gesture type BEFORE the expensive hit-test so Move/Press
		// events (which fire continuously during panning) return immediately.
		// Gesture.Press fires on every raw touch-down (before it's known to be a
		// tap vs. the start of a pan/drag) — consuming it here would swallow the
		// down event before it reaches MapEventLayer and break map panning.
		// Gesture.Tap only fires once a single tap is confirmed, mirroring how
		// ItemizedLayer (markers) detects taps.
		String type = null;
		if ( g instanceof Gesture.DoubleTap ) {
			type = "doubleTap";
		} else if ( g instanceof Gesture.LongPress ) {
			type = "longPress";
		} else if ( g instanceof Gesture.Tap ) {
			type = "press";
		}
		if ( null == type ) {
			return false; // Move, Press, or unrecognized — skip the hit-test
		}
		WritableMap params = containsGetResponse( e.getX(), e.getY() );
		if (  null != params ) {
			// Add type
			params.putString( "type", type );
			// Add eventPosition
			GeoPoint eventPoint = mMap.viewport().fromScreenPoint( e.getX(), e.getY() );
			params.putArray( "eventPosition", Utils.positionToWritableArray(
				eventPoint.getLongitude(),
				eventPoint.getLatitude(),
				null
			) );
			mGestureListener.onGesture( type, params );
			return true;
		}
		return false;
	}

	public synchronized WritableMap containsGetResponse( float x, float y ) {
		GeoPoint geoPoint = mMap.viewport().fromScreenPoint( x, y );
		org.locationtech.jts.geom.Point point = new GeomBuilder().point( geoPoint.getLongitude(), geoPoint.getLatitude() ).toPoint();
		float distance = getCoordinateDistanceFromScreenDistance( x, y, mGestureScreenDistance );
		for ( Drawable drawable : tmpDrawables ) {
			// Use distance() instead of buffer(d).contains() — mathematically
			// equivalent for a point-vs-segment hit-test (both answer "is the
			// point within distance d of the line?"), but distance() computes
			// inline without allocating a JTS buffered polygon per drawable.
			// For a simple LineDrawable (2-point segment), this is O(1) with
			// zero allocation.
			if ( drawable.getGeometry().distance( point ) <= distance ) {
				WritableMap params = new WritableNativeMap();
				params.putString( "uuid", mUuidResolver != null
						? mUuidResolver.resolveUuid( drawable )
						: mUuid );
				// Distance
				params.putDouble( "distance", drawable.getGeometry().distance( point ) );
				// Nearest point
				org.locationtech.jts.geom.Coordinate[] nearestPoints = DistanceOp.nearestPoints( drawable.getGeometry(), point);
				params.putArray( "nearestPoint", Utils.positionToWritableArray(
					nearestPoints[0].x,
					nearestPoints[0].y,
					null
				) );
				return params;
			}
		}
		return null;
	}

	private float getCoordinateDistanceFromScreenDistance( float x, float y, float screenDistance ) {
		return (float) Math.abs(
			mMap.viewport().fromScreenPoint( x, y ).getLongitude()
				- mMap.viewport().fromScreenPoint( x + screenDistance, y ).getLongitude()
		);
	}
}
