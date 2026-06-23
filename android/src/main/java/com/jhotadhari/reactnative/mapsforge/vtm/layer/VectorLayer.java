package com.jhotadhari.reactnative.mapsforge.vtm.layer;

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

	protected final String mUuid;
	protected Boolean mSupportsGestures;
	protected final GestureListener mGestureListener;
	protected float mGestureScreenDistance = 30f;

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
		WritableMap params = containsGetResponse( e.getX(), e.getY() );
		if (  null != params ) {
			// Gesture.Press fires on every raw touch-down (before it's known to be a
			// tap vs. the start of a pan/drag) - consuming it here would swallow the
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
				return false;
			} else {
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
		}
		return false;
	}

	public synchronized WritableMap containsGetResponse( float x, float y ) {
		GeoPoint geoPoint = mMap.viewport().fromScreenPoint( x, y );
		org.locationtech.jts.geom.Point point = new GeomBuilder().point( geoPoint.getLongitude(), geoPoint.getLatitude() ).toPoint();
		float distance = getCoordinateDistanceFromScreenDistance( x, y, mGestureScreenDistance );
		for ( Drawable drawable : tmpDrawables ) {
			if ( drawable.getGeometry().buffer( distance ).contains( point ) ) {
				WritableMap params = new WritableNativeMap();
				params.putString( "uuid", mUuid );
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
