package com.jhotadhari.reactnative.mapsforge.vtm.views;

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
import com.jhotadhari.reactnative.mapsforge.vtm.FixedWindowRateLimiter;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map;

import java.lang.reflect.InvocationTargetException;

public class MapFragment extends Fragment {

	private MapView mapView;

	private Map.UpdateListener updateListener;

	protected FixedWindowRateLimiter rateLimiter;

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
		} catch ( Exception e ) {
			e.printStackTrace();
			emitError( e.getMessage() );
		}
	}


	public void updateRateLimiterRate() {
		if ( null != getMapsforgeVtmView() ) {
			rateLimiter = new FixedWindowRateLimiter( getMapsforgeVtmView().getMapEventRate(), 1 );
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
	}

	protected void bindUpdateListener() {
		if ( null != getMapsforgeVtmView() && getMapsforgeVtmView().getEmitsMapUpdateEvents() && null == updateListener ) {
			updateListener = new Map.UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( rateLimiter.tryAcquire() ) {
						getMapsforgeVtmView().emitMapEvent( "onMapUpdate", getResponseBase( 2 ) );
					}
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
			if ( null != getMapsforgeVtmView().getHgtReader() ) {
				Short altitude = getMapsforgeVtmView().getHgtReader().getAltitudeAtPosition( lng, lat, true );
				if ( null != altitude ) {
					alt = altitude.doubleValue();
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
		if ( mapView != null ) {
			mapView.onDestroy();
			mapView = null;
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
