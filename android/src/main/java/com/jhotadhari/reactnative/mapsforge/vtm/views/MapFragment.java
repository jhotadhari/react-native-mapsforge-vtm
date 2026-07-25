package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.GestureLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.gnss.GnssManager;

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

	private GnssManager gnssManager;



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



	public void updateUpdateListener() {
		MapsforgeVtmView parent = getMapsforgeVtmView();
		if ( null != parent && parent.getEmitsMapUpdateEvents() && updateListener == null ) {
			bindUpdateListener();
		} else if ( null != parent && ! parent.getEmitsMapUpdateEvents() && updateListener != null ) {
			unbindUpdateListener();
		}

	}

	protected void unbindUpdateListener() {
		if ( updateListener != null ) {
			mapView.map().events.unbind( updateListener );
			updateListener = null;
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
			updateListener = new Map.UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					MapsforgeVtmView parent = getMapsforgeVtmView();
					if ( null != parent && null != mapView && null != mapView.map() ) {
						parent.emitMapEvent( "onMapUpdate", getResponseBase() );

						// Write position directly to reanimated Synchronizable
						// primitives on the render thread, bypassing the JS
						// bridge entirely for 60fps overlay tracking.
						// Guarded so an UnsatisfiedLinkError (native library
						// not built because reanimated isn't installed) won't
						// crash the update listener.
						if ( com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
								.isAvailable() ) {
							MapPosition mp = mapView.map().getMapPosition();
							try {
								com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
									.nativeSetPosition(
										parent.getId(),
										mp.getLongitude(),
										mp.getLatitude(),
										mp.getZoom(),
										mp.getBearing(),
										mp.getTilt(),
										parent.getWidthInDp(),
										parent.getHeightInDp()
									);
							} catch ( UnsatisfiedLinkError ignored ) {
								// Library was unloaded between check and call
								// (shouldn't happen in practice).
							}
						}
					}
				}
			};
			mapView.map().events.bind( updateListener );
		}

	}

	protected WritableMap getResponseBase() {
		WritableMap payload = Arguments.createMap();
		if ( null == getMapsforgeVtmView() ) {
			return payload;
		}

		MapPosition mapPosition = mapView.map().getMapPosition();
		// Use getZoom() (fractional double) instead of getZoomLevel() (int)
		// so reanimated overlays track the map smoothly during pinch-zoom
		// where the scale can be e.g. 2.7, not just an integer.
		// All fields are always emitted — no responseInclude gating.
		MapsforgeVtmView parent = getMapsforgeVtmView();

		payload.putDouble( "zoomLevel", mapPosition.getZoom() );
		payload.putDouble( "bearing", mapPosition.getBearing() );
		payload.putDouble( "tilt", mapPosition.getTilt() );

		double lng = mapPosition.getLongitude();
		double lat = mapPosition.getLatitude();
		Double alt = null;
		if ( null != parent && null != mapView && null != mapView.map() ) {
			com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader reader =
				com.jhotadhari.reactnative.mapsforge.vtm.modules.MapContainer.getElevationReader(
					parent.getId(), parent.getReactContext() );
			if ( null != reader ) {
				Short elevation = reader.getElevation( lng, lat, 100 );
				if ( null != elevation ) {
					alt = elevation.doubleValue();
				}
			}
		}
		payload.putArray( "center", Utils.positionToWritableArray( lng, lat, alt ) );

		if ( null != parent && null != mapView && null != mapView.map() ) {
			payload.putDouble( "viewportWidth", parent.getWidthInDp() );
			payload.putDouble( "viewportHeight", parent.getHeightInDp() );
			// Emit live Tile.SIZE (post-theme) for overlay projection.
			payload.putDouble( "tileSize", (double) org.oscim.core.Tile.SIZE );
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
			mapPosition.setZoomLevel( (int) getMapsforgeVtmView().getZoomLevel() );
			mapView.map().setMapPosition( mapPosition );
		}

	}

	public void updateZoomBounds() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.map().viewport().setMinZoomLevel( (int) getMapsforgeVtmView().getZoomBounds( "min" ) );
			mapView.map().viewport().setMaxZoomLevel( (int) getMapsforgeVtmView().getZoomBounds( "max" ) );
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

	/**
	 * Start or stop the GNSS position filter based on the current
	 * {@code gnssFilter} prop on the parent {@link MapsforgeVtmView}.
	 */
	public void updateGnssFilter() {
		MapsforgeVtmView parent = getMapsforgeVtmView();
		if ( null == parent ) return;

		// Stop any existing listener first.
		if ( null != gnssManager ) {
			gnssManager.stop();
			gnssManager = null;
		}

		ReadableMap filter = parent.getGnssFilter();
		if ( null == filter ) return;

		Context ctx = getContext();
		if ( null == ctx ) return;

		LocationManager lm = (LocationManager) ctx.getSystemService( Context.LOCATION_SERVICE );
		if ( null == lm ) return;

		com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader reader =
			com.jhotadhari.reactnative.mapsforge.vtm.modules.MapContainer.getElevationReader(
				parent.getId(), parent.getReactContext() );

		gnssManager = GnssManager.create(
			lm, filter, reader,
			payload -> parent.emitGnssPosition( payload )
		);

		if ( null != gnssManager ) {
			gnssManager.start();
		}
	}

	@Override
	public void onPause() {
		if ( null != mapView && null != getMapsforgeVtmView() ) {
			mapView.onPause();
			getMapsforgeVtmView().emitMapEvent( "onPause", getResponseBase() );
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
			getMapsforgeVtmView().emitMapEvent( "onResume", getResponseBase() );
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
		if ( null != gnssManager ) {
			gnssManager.stop();
			gnssManager = null;
		}
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
				com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
					.nativeReleaseWriter( handle );
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
			View mapViewContainer = getView().findViewById( R.id.mapView );
			int w = getView().getWidth();
			int h = getView().getHeight();
			if ( w > 0 && h > 0 ) {
				mapViewContainer.measure(
					View.MeasureSpec.makeMeasureSpec( w, View.MeasureSpec.EXACTLY ),
					View.MeasureSpec.makeMeasureSpec( h, View.MeasureSpec.EXACTLY )
				);
				mapViewContainer.layout( 0, 0, w, h );
			}
		}
	}

	protected MapsforgeVtmView getMapsforgeVtmView() {
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
