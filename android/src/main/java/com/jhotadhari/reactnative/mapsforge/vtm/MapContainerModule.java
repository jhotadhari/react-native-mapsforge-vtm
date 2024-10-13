package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;

//import org.mapsforge.core.model.LatLong;
//import org.mapsforge.map.android.view.MapView;

public class MapContainerModule extends ReactContextBaseJavaModule {

    MapContainerModule(ReactApplicationContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "MapContainerModule";
    }

    @ReactMethod
    public void getLayersCreated(int reactTag, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
            promise.resolve( true );
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }
    @ReactMethod
    public void setZoom(int reactTag, int zoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) zoom );
			mapView.map().setMapPosition( mapPosition );
            promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }

    @ReactMethod
    public void setCenter(int reactTag, ReadableArray center, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			GeoPoint geoPoint = Utils.arrayToGeoPoint( center );
			mapView.map().setMapPosition( new MapPosition(
				geoPoint.getLatitude(),
				geoPoint.getLongitude(),
				mapView.map().getMapPosition().getScale()
			) );
            promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }

    @ReactMethod
    public void setMinZoom(int reactTag, int minZoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			mapView.map().viewport().setMinZoomLevel( (int) minZoom );
            promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }
    @ReactMethod
    public void setMaxZoom(int reactTag, int maxZoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			mapView.map().viewport().setMaxZoomLevel( (int) maxZoom );
            promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }
    @ReactMethod
    public void zoomIn(int reactTag, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) mapPosition.getZoomLevel() + 1 );
			mapView.map().setMapPosition( mapPosition );
			promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }
    @ReactMethod
    public void zoomOut(int reactTag, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
            if ( null == mapView ) {
                promise.resolve( false );
                return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) mapPosition.getZoomLevel() - 1 );
			mapView.map().setMapPosition( mapPosition );
			promise.resolve(true);
        } catch(Exception e) {
            promise.reject("Create Event Error", e);
        }
    }

}
