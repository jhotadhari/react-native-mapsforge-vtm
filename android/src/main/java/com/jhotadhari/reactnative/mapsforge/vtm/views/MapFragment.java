package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.FixedWindowRateLimiter;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.GestureLayer;

import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;


import java.lang.reflect.InvocationTargetException;

public class MapFragment extends Fragment {

	private MapView mapView;

	private Map.UpdateListener updateListener;

	private GestureLayer gestureLayer;

	protected FixedWindowRateLimiter rateLimiter;

	private Handler mainHandler;

	private Runnable pendingTrailingEdge;


	public MapView getMapView() {
		return mapView;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate( R.layout.fragment_map, container, false );
	}

	@Override
	public void onViewCreated( @NonNull View view, Bundle savedInstanceState ) {
		createMapView( view );
		WritableMap payload = Arguments.createMap();
		if ( null != getMapsforgeVtmView() ) {
			getMapsforgeVtmView().emitMapEvent( "onMapCreated", payload );
		}

	}

	protected void createMapView( View view ) {
		try {
			updateRateLimiterRate();
			mapView = new MapView( getContext() );
			RelativeLayout relativeLayout = view.findViewById( R.id.mapView );
			relativeLayout.addView( mapView );
			updateCenter();
			updateZoomBounds();
			updateZoomLevel();
			updateViewportBounds( "tilt" );
			updateViewportBounds( "bearing" );
			updateViewportBounds( "roll" );
			updateViewportValue( "tilt" );
			updateViewportValue( "bearing" );
			updateViewportValue( "roll" );
			updateInteractionEnabled();
			updateUpdateListener();
			bindGestureLayer();
		} catch ( Exception e ) {
			e.printStackTrace();
			emitError( e.getMessage() );
		}

	}



	/**
	 * Configures EGL context sharing for this MapView to work around vtm 0.28.0's
	 * static rendering state limitation.  The first MapView creates the context
	 * (which all subsequent MapViews share), so that vtm's static texture cache
	 * — created in the first context — remains valid for every instance.
	 *
	 * <p>Must be called <b>after</b> {@code new MapView()} (constructor) but
	 * <b>before</b> {@code addView(mapView)} — EGL context creation is lazy and
	 * fires when the GLSurfaceView is first attached to the window.
	 */
	public void updateRateLimiterRate() {
		if ( null != getMapsforgeVtmView() ) {
			rateLimiter = new FixedWindowRateLimiter( getMapsforgeVtmView().getMapUpdateInterval(), 1 );
		}

		// Cancel any pending trailing-edge flush scheduled with the old
		// window size. The next onMapEvent will re-schedule a fresh one.
		if ( null != pendingTrailingEdge && null != mainHandler ) {
			mainHandler.removeCallbacks( pendingTrailingEdge );
			pendingTrailingEdge = null;
		}

	}

	public void updateUpdateListener() {
		if ( null != getMapsforgeVtmView() && getMapsforgeVtmView().getEmitsMapUpdateEvents() && updateListener == null ) {
			bindUpdateListener();
		} else if ( ! getMapsforgeVtmView().getEmitsMapUpdateEvents() && updateListener != null ) {
			unbindUpdateListener();
		}

	}

	protected void unbindUpdateListener() {
		if ( updateListener != null ) {
			mapView.map().events.unbind( updateListener );
			updateListener = null;
		}

		// Cancel any pending trailing-edge flush so it doesn't fire
		// after the listener has been unbound (would deliver a stale
		// event to a component that is no longer listening).
		if ( null != pendingTrailingEdge && null != mainHandler ) {
			mainHandler.removeCallbacks( pendingTrailingEdge );
			pendingTrailingEdge = null;
		}

	}

	protected void bindGestureLayer() {
		if ( null == mapView || null == getMapsforgeVtmView() ) {
			return;
		}

		// Remove any stale instance first (e.g. after fragment recreation).
		if ( gestureLayer != null && mapView.map() != null ) {
			mapView.map().layers().remove( gestureLayer );
			gestureLayer = null;
		}

		gestureLayer = new GestureLayer(
			mapView.map(),
			( eventName, x, y ) -> {
				MapsforgeVtmView parent = getMapsforgeVtmView();
				if ( parent == null || mapView == null || mapView.map() == null ) {
					return;
				}
				org.oscim.core.GeoPoint geo = mapView.map().viewport().fromScreenPoint( x, y );
				WritableMap payload = Arguments.createMap();
				payload.putDouble( "lng", geo.getLongitude() );
				payload.putDouble( "lat", geo.getLatitude() );
				payload.putDouble( "x", x );
				payload.putDouble( "y", y );
				parent.emitMapEvent( eventName, payload );
			}
		);
		// Add at the highest index so it only receives gestures that marker/path
		// layers returned false for (i.e. the tap missed their geometry).
		mapView.map().layers().add( gestureLayer );
	}

	protected void bindUpdateListener() {
		if ( null != getMapsforgeVtmView() && getMapsforgeVtmView().getEmitsMapUpdateEvents() && null == updateListener ) {
			if ( null == mainHandler ) {
				mainHandler = new Handler( Looper.getMainLooper() );
			}
			updateListener = new Map.UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					// Cancel any previously scheduled trailing-edge flush —
					// each new vtm event resets the silence timer.
					if ( null != pendingTrailingEdge ) {
						mainHandler.removeCallbacks( pendingTrailingEdge );
					}
					// Leading edge: emit immediately if the rate limiter allows.
					if ( rateLimiter.tryAcquire() ) {
						getMapsforgeVtmView().emitMapEvent( "onMapUpdate", getResponseBase( 2 ) );
					}
					// Schedule trailing-edge flush: guarantees the final position
					// after a gesture is never lost, even if the last vtm event
					// fell in an already-consumed rate-limit window. Fires only
					// after mapUpdateInterval ms of silence.
					pendingTrailingEdge = new Runnable() {
						@Override
						public void run() {
							pendingTrailingEdge = null;
							MapsforgeVtmView parent = getMapsforgeVtmView();
							if ( null != parent && null != mapView && null != mapView.map() ) {
								parent.emitMapEvent( "onMapUpdate", getResponseBase( 2 ) );
							}
						}
					};
					mainHandler.postDelayed(
						pendingTrailingEdge,
						getMapsforgeVtmView().getMapUpdateInterval()
					);
				}
			};
			mapView.map().events.bind( updateListener );
		}

	}

	protected WritableMap getResponseBase( int includeLevel ) {
		WritableMap payload = Arguments.createMap();
		if ( null == getMapsforgeVtmView() ) {
			return payload;
		}

		ReadableMap responseInclude = getMapsforgeVtmView().getResponseInclude();
		MapPosition mapPosition = mapView.map().getMapPosition();
		if ( responseInclude.getInt( "zoomLevel" ) >= includeLevel ) {
			payload.putDouble( "zoomLevel", mapPosition.getZoomLevel() );
		}

		if ( responseInclude.getInt( "zoom" ) >= includeLevel ) {
			payload.putDouble( "zoom", mapPosition.getZoom() );
		}

		if ( responseInclude.getInt( "scale" ) >= includeLevel ) {
			payload.putDouble( "scale", mapPosition.getScale() );
		}

		if ( responseInclude.getInt( "zoomScale" ) >= includeLevel ) {
			payload.putDouble( "zoomScale", mapPosition.getZoomScale() );
		}

		if ( responseInclude.getInt( "bearing" ) >= includeLevel ) {
			payload.putDouble( "bearing", mapPosition.getBearing() );
		}

		if ( responseInclude.getInt( "roll" ) >= includeLevel ) {
			payload.putDouble( "roll", mapPosition.getRoll() );
		}

		if ( responseInclude.getInt( "tilt" ) >= includeLevel ) {
			payload.putDouble( "tilt", mapPosition.getTilt() );
		}

		// center
		if ( responseInclude.getInt( "center" ) >= includeLevel ) {
			double lng = mapPosition.getLongitude();
			double lat = mapPosition.getLatitude();
			Double alt = null;
			MapsforgeVtmView parent = getMapsforgeVtmView();
			if ( null != parent ) {
				com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader reader =
					com.jhotadhari.reactnative.mapsforge.vtm.modules.MapContainer.getElevationReader(
						parent.getId(), parent.getReactContext() );
				if ( null != reader ) {
					// Use cached-only lookup to avoid blocking the render
					// thread on file I/O. On cache miss, kick off an async
					// preload so subsequent frames pick up the elevation.
					Short elevation = reader.getElevationIfCached( lng, lat );
					if ( null != elevation ) {
						alt = elevation.doubleValue();
					} else {
						reader.preloadAsync( lng, lat );
					}
				}
			}
			payload.putArray( "center", Utils.positionToWritableArray( lng, lat, alt ) );
		}

		return payload;
	}

	public void updateCenter() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.map().setMapPosition( new MapPosition(
				Utils.latFromPosition( getMapsforgeVtmView().getCenter() ),
				Utils.lngFromPosition( getMapsforgeVtmView().getCenter() ),
				mapView.map().getMapPosition().getScale()
			) );
		}

	}

	public void updateZoomLevel() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( getMapsforgeVtmView().getZoomLevel() );
			mapView.map().setMapPosition( mapPosition );
		}

	}

	public void updateZoomBounds() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.map().viewport().setMinZoomLevel( getMapsforgeVtmView().getZoomBounds( "min" ) );
			mapView.map().viewport().setMaxZoomLevel( getMapsforgeVtmView().getZoomBounds( "max" ) );
		}

	}

	public void updateViewportBounds( String key ) {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			switch( key ) {
				case "tilt":
					mapView.map().viewport().setMinTilt( (float) getMapsforgeVtmView().getViewportBounds( key, "min" ) );
					mapView.map().viewport().setMaxTilt( (float) getMapsforgeVtmView().getViewportBounds( key, "max" ) );
					break;
				case "bearing":
					mapView.map().viewport().setMinBearing( (float) getMapsforgeVtmView().getViewportBounds( key, "min" ) );
					mapView.map().viewport().setMaxBearing( (float) getMapsforgeVtmView().getViewportBounds( key, "max" ) );
					break;
				case "roll":
					mapView.map().viewport().setMinRoll( (float) getMapsforgeVtmView().getViewportBounds( key, "min" ) );
					mapView.map().viewport().setMaxRoll( (float) getMapsforgeVtmView().getViewportBounds( key, "max" ) );
					break;
			}
		}

	}

	public void updateViewportValue( String key ) {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			switch( key ) {
				case "tilt":
					mapView.map().viewport().setTilt( (float) getMapsforgeVtmView().getViewportValue( key ) );
					break;
				case "bearing":
					mapView.map().viewport().setRotation( (float) getMapsforgeVtmView().getViewportValue( key ) );
					break;
				case "roll":
					mapView.map().viewport().setRoll( (float) getMapsforgeVtmView().getViewportValue( key ) );
					break;
			}
		}

	}

	public void updateInteractionEnabled() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.map().getEventLayer().enableMove( getMapsforgeVtmView().getInteractionEnabled( "move" ) );
			mapView.map().getEventLayer().enableTilt( getMapsforgeVtmView().getInteractionEnabled( "tilt" ) );
			mapView.map().getEventLayer().enableRotation( getMapsforgeVtmView().getInteractionEnabled( "rotation" ) );
			mapView.map().getEventLayer().enableZoom( getMapsforgeVtmView().getInteractionEnabled( "zoom" ) );
		}

	}

	@Override
	public void onPause() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.onPause();
			getMapsforgeVtmView().emitMapEvent( "onPause", getResponseBase( 1 ) );
			for ( Layer layer : mapView.map().layers() ) {
				try {
					layer.getClass().getMethod("onPause").invoke( layer );
				} catch ( NoSuchMethodException | InvocationTargetException | IllegalAccessException e ) {
					if ( !( e instanceof NoSuchMethodException ) ) {
						e.printStackTrace();
						emitError( e.getMessage() );
					}
				}
			}
		}

		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.onResume();
			getMapsforgeVtmView().emitMapEvent( "onResume", getResponseBase( 1 ) );
			for ( Layer layer : mapView.map().layers() ) {
				try {
					layer.getClass().getMethod("onResume").invoke( layer );
				} catch ( NoSuchMethodException | InvocationTargetException | IllegalAccessException e ) {
					if ( !( e instanceof NoSuchMethodException ) ) {
						e.printStackTrace();
						emitError( e.getMessage() );
					}
				}
			}
		}

	}

	@Override
	public void onDestroy() {
		unbindUpdateListener();
		// Tear down LayerManagers FIRST — they need a live mapView to remove
		// their shared layers from mapView.map().layers(). Then tear down the
		// MapMutationQueue so pending layer mutations don't hang. Only then
		// destroy the mapView itself.
		MapsforgeVtmView parent = getMapsforgeVtmView();
		int handle = parent != null ? parent.getId() : 0;
		if ( parent != null ) {
			com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager.removeAll( handle );
			com.jhotadhari.reactnative.mapsforge.vtm.MapMutationQueue.remove( handle );
			com.jhotadhari.reactnative.mapsforge.vtm.modules.MapContainer.removeElevationReader( handle, parent.getReactContext() );
		}

		if ( mapView != null ) {
			if ( gestureLayer != null && mapView.map() != null ) {
				mapView.map().layers().remove( gestureLayer );
				gestureLayer = null;
			}
			mapView.onDestroy();
			mapView = null;
		}

		// Remove the explicit fragment registry entry so Utils.getMapFragment
		// won't return a stale fragment after teardown (multi-map support).
		if ( handle != 0 ) {
			MapsforgeVtmView.removeFragment( handle );
		}

		super.onDestroy();
	}

	public void fixViewLayoutSize() {
		if ( null != getView() && null != getView().findViewById( R.id.mapView ) ) {
			ViewGroup.LayoutParams params = getView().findViewById( R.id.mapView ).getLayoutParams();
			params.width = getView().getWidth();
			params.height = getView().getHeight();
			getView().findViewById( R.id.mapView ).setLayoutParams( params );
		}

	}

	private MapsforgeVtmView getMapsforgeVtmView() {
		if ( null == getView() ) {
			return null;
		}

		return (MapsforgeVtmView) getView().getParent();
	}

	protected void emitError( String errorMsg ) {
		if ( null != getMapsforgeVtmView() ) {
			WritableMap payload = Arguments.createMap();
			payload.putString( "errorMsg", errorMsg );
			getMapsforgeVtmView().emitMapEvent( "onError", payload );
		}

	}

}
