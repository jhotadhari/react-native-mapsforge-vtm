package com.jhotadhari.reactnative.mapsforge.vtm.layers.vector;

import com.facebook.react.bridge.ReactContext;
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

	protected final ReactContext mReactContext;
	protected final String mUuid;
	protected final String mGestureEventName;
	protected float mGestureScreenDistance = 30f;

	public VectorLayer( Map map, SpatialIndex<Drawable> index ) {
		super( map, index );
		mReactContext = null;
		mUuid = null;
		mGestureEventName = null;
	}

	public VectorLayer( Map map ) {
		super( map );
		mReactContext = null;
		mUuid = null;
		mGestureEventName = null;
	}

	public VectorLayer( Map map, String uuid, ReactContext reactContext, String gestureEventName, float gestureScreenDistance ) {
		super( map );
		mReactContext = reactContext;
		mUuid = uuid;
		mGestureEventName = gestureEventName;
		mGestureScreenDistance = gestureScreenDistance;
	}

	public void setGestureScreenDistance( float gestureScreenDistance ) {
		mGestureScreenDistance = gestureScreenDistance;
	}

	public float getGestureScreenDistance() {
		return mGestureScreenDistance;
	}

	public String getGestureEventName() {
		return mGestureEventName;
	}

	@Override
	public boolean onGesture( Gesture g, MotionEvent e ) {
		if ( mReactContext == null ) {
			return false;
		}
		WritableMap params = containsGetResponse( e.getX(), e.getY() );
		if (  null != params ) {
			String type = null;
			if ( g instanceof Gesture.DoubleTap ) {
				type = "doubleTap";
			} else if ( g instanceof Gesture.LongPress ) {
				type = "longPress";
			} else if ( g instanceof Gesture.Press ) {
				type = "press";
			}
			if ( null == type ) {
				return false;
			} else {
				// Add type
				params.putString( "type", type );
				// Add eventPosition
				WritableMap eventPosition = new WritableNativeMap();
				GeoPoint eventPoint = mMap.viewport().fromScreenPoint( e.getX(), e.getY() );
				eventPosition.putDouble("lng", eventPoint.getLongitude() );
				eventPosition.putDouble("lat", eventPoint.getLatitude() );
				params.putMap( "eventPosition", eventPosition );
				// sendEvent
				Utils.sendEvent( mReactContext, mGestureEventName, params );
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
				WritableMap nearestPoint = new WritableNativeMap();
				org.locationtech.jts.geom.Coordinate[] nearestPoints = DistanceOp.nearestPoints( drawable.getGeometry(), point);
				nearestPoint.putDouble("lng", nearestPoints[0].x );
				nearestPoint.putDouble("lat", nearestPoints[0].y );
				params.putMap( "nearestPoint", nearestPoint );
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
