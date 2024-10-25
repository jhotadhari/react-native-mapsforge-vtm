package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.UiThreadUtil;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;

public class MapContainerModule extends ReactContextBaseJavaModule {

    public MapContainerModule(ReactApplicationContext context) {
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
    public void setZoomLevel(int reactTag, int zoom, Promise promise ) {
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
			GeoPoint geoPoint = new GeoPoint(
				(double) center.toArrayList().get(0),
				(double) center.toArrayList().get(1)
			);

			mapView.map().setMapPosition( new MapPosition(
				geoPoint.getLatitude(),
				geoPoint.getLongitude(),
				mapView.map().getMapPosition().getScale()
			) );
            promise.resolve(true);
        } catch( Exception e ) {
            promise.reject("setCenter Error", e);
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
        } catch( Exception e ) {
            promise.reject("setMinZoom Error", e);
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
            promise.reject("setMaxZoom Error", e);
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
        } catch( Exception e ) {
            promise.reject("zoomIn Error", e);
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
        } catch( Exception e ) {
            promise.reject("zoomOut Error", e);
        }
    }

	@ReactMethod
	public void setPropsInteractionsEnabled( int reactTag, String propKey, int value, Promise promise ) {
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}
			switch ( propKey ) {
				case "moveEnabled":
					mapView.map().getEventLayer().enableMove( value == 1 );
					break;
				case "tiltEnabled":
					mapView.map().getEventLayer().enableTilt( value == 1 );
					break;
				case "rotationEnabled":
					mapView.map().getEventLayer().enableRotation( value == 1 );
					break;
				case "zoomEnabled":
					mapView.map().getEventLayer().enableZoom( value == 1 );
					break;
				default: {}
			}
			promise.resolve(true);
		} catch( Exception e ) {
			promise.reject("setPropsInteractionsEnabled Error", e);
		}
	}

	@ReactMethod
	public void setViewport( int reactTag, String propKey, float value, Promise promise ) {
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}
			UiThreadUtil.runOnUiThread( new Runnable() {
				@Override
				public void run() {
					switch ( propKey ) {
						case "tilt":
							mapView.map().viewport().setTilt( (float) value );
							break;
						case "minTilt":
							mapView.map().viewport().setMinTilt( (float) value );
							break;
						case "maxTilt":
							mapView.map().viewport().setMaxTilt( (float) value );
							break;
						case "bearing":
							mapView.map().viewport().setRotation( (double) value );
							break;
						case "minBearing":
							mapView.map().viewport().setMinBearing( (float) value );
							break;
						case "maxBearing":
							mapView.map().viewport().setMaxBearing( (float) value );
							break;
						case "roll":
							mapView.map().viewport().setRoll( (double) value );
							break;
						case "minRoll":
							mapView.map().viewport().setMinRoll( (float) value );
							break;
						case "maxRoll":
							mapView.map().viewport().setMaxRoll( (float) value );
							break;
						default: {}
					}
				}
			} );
			mapView.map().updateMap();
			promise.resolve(true);
		} catch( Exception e ) {
			promise.reject("setViewport Error", e);
		}
	}

	@ReactMethod
	public void setHgtDirPath(int reactTag, String hgtDirPath, Promise promise ) {
		try {
			MapFragment mapFragment = (MapFragment) Utils.getMapFragment( this.getReactApplicationContext(), reactTag );

			if ( null == mapFragment ) {
				promise.resolve( false );
				return;
			}
			mapFragment.setPropHgtDirPath( hgtDirPath );
			promise.resolve(true);
		} catch( Exception e ) {
			promise.reject("setHgtDirPath Error", e);
		}
	}

}
