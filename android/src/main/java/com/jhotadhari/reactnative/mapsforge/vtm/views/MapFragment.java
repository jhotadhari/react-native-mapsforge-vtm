package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.HitTestLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.gnss.GnssManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;


import java.lang.reflect.InvocationTargetException;

public class MapFragment extends Fragment {

	private MapView mapView;

	private Map.UpdateListener updateListener;

	private HitTestLayer gestureLayer;

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
			// Re-apply the stored gnssFilter prop. The prop can arrive
			// before this fragment exists (cold start / surface
			// recreation race) — setGnssFilter only applies it when the
			// fragment is already there, so without this call the GNSS
			// listener never starts and no positions flow until the
			// prop changes (e.g. a GNSS toggle).
			updateGnssFilter();
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

		gestureLayer = new HitTestLayer(
			mapView.map(),
			getMapsforgeVtmView().getId(),
			( eventName, x, y, hits ) -> {
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
				// codegen cannot type arrays of objects in event payloads —
				// serialize the hit list to a JSON string for JS to parse.
				payload.putString( "hits", serializeHits( hits ) );
				parent.emitMapEvent( eventName, payload );
			}
		);
		// Add at the LOWEST index so it runs before the geometry layers and
		// aggregates hits across all layer managers. It never consumes, so
		// the geometry layers' own gesture handling is unaffected.
		mapView.map().layers().add( 0, gestureLayer );
	}

	/**
	 * Serializes the hit-test payloads to a JSON string, element by element.
	 * Every top-level element is guaranteed to be a JSON object — unlike
	 * {@code new JSONArray(Arguments.toList(hits))}, whose
	 * {@code JSONObject.wrap()} silently returns null on failure and would
	 * produce malformed arrays that crash the JS consumers. Non-finite
	 * numbers are skipped (they'd make the whole string fail to serialize),
	 * and failures are logged instead of silently corrupting the payload.
	 */
	private String serializeHits( @NonNull com.facebook.react.bridge.WritableArray hits ) {
		JSONArray json = new JSONArray();
		for ( int i = 0; i < hits.size(); i++ ) {
			ReadableMap hit = hits.getMap( i );
			if ( hit == null ) {
				Log.w( "MapFragment", "HitTestLayer: skipped non-map hit element at index " + i );
				continue;
			}
			json.put( serializeHitObject( hit ) );
		}
		return json.toString();
	}

	@NonNull
	private JSONObject serializeHitObject( @Nullable ReadableMap map ) {
		JSONObject out = new JSONObject();
		if ( map == null ) {
			return out;
		}
		ReadableMapKeySetIterator it = map.keySetIterator();
		while ( it.hasNextKey() ) {
			String key = it.nextKey();
			try {
				switch ( map.getType( key ) ) {
					case Null: out.put( key, JSONObject.NULL ); break;
					case Boolean: out.put( key, map.getBoolean( key ) ); break;
					case Number: {
						double d = map.getDouble( key );
						if ( Double.isFinite( d ) ) {
							out.put( key, d );
						}
						break;
					}
					case String: out.put( key, map.getString( key ) ); break;
					case Map: out.put( key, serializeHitObject( map.getMap( key ) ) ); break;
					case Array: out.put( key, serializeHitArray( map.getArray( key ) ) ); break;
					default: break;
				}
			} catch ( Exception e ) {
				Log.w( "MapFragment", "HitTestLayer: failed to serialize hit key '" + key + "': " + e.getMessage() );
			}
		}
		return out;
	}

	@NonNull
	private JSONArray serializeHitArray( @Nullable ReadableArray array ) {
		JSONArray out = new JSONArray();
		if ( array == null ) {
			return out;
		}
		for ( int i = 0; i < array.size(); i++ ) {
			try {
				switch ( array.getType( i ) ) {
					case Null: out.put( JSONObject.NULL ); break;
					case Boolean: out.put( array.getBoolean( i ) ); break;
					case Number: {
						double d = array.getDouble( i );
						if ( Double.isFinite( d ) ) {
							out.put( d );
						}
						break;
					}
					case String: out.put( array.getString( i ) ); break;
					case Map: out.put( serializeHitObject( array.getMap( i ) ) ); break;
					case Array: out.put( serializeHitArray( array.getArray( i ) ) ); break;
					default: break;
				}
			} catch ( Exception e ) {
				Log.w( "MapFragment", "HitTestLayer: failed to serialize array element " + i + ": " + e.getMessage() );
			}
		}
		return out;
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
			// Tear down any per-frame screen-anchor callbacks while the
			// mapView (and its event dispatcher) is still alive.
			if ( mapView != null ) {
				com.jhotadhari.reactnative.mapsforge.vtm.ScreenAnchorRegistry.clear( handle, mapView );
			}
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

	public MapsforgeVtmView getMapsforgeVtmView() {
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
