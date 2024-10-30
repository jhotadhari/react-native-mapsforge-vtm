package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;

public class MapContainerModule extends ReactContextBaseJavaModule {

    public MapContainerModule(ReactApplicationContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "MapContainerModule";
    }

    @ReactMethod
    public void getLayersCreated(int nativeNodeHandle, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.resolve( false );
            } else {
            	promise.resolve( true );
			}
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }
    @ReactMethod
    public void setZoomLevel(int nativeNodeHandle, int zoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) zoom );
			mapView.map().setMapPosition( mapPosition );
            promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

    @ReactMethod
    public void setCenter(int nativeNodeHandle, ReadableMap center, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			mapView.map().setMapPosition( new MapPosition(
				center.getDouble("lat" ),
				center.getDouble("lng" ),
				mapView.map().getMapPosition().getScale()
			) );
            promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

	@ReactMethod
	public void setToBounds(int nativeNodeHandle, ReadableMap bounds, Promise promise ) {
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
			}
			BoundingBox boundingBox = new BoundingBox(
				bounds.getDouble( "minLat" ),
				bounds.getDouble( "minLng" ),
				bounds.getDouble( "maxLat" ),
				bounds.getDouble( "maxLng" )
			);
			MapPosition pos = new MapPosition();
			pos.setByBoundingBox( boundingBox, Tile.SIZE * 4, Tile.SIZE * 4 );
			mapView.map().setMapPosition( pos );
			promise.resolve( true );
		} catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
		}
	}

    @ReactMethod
    public void setMinZoom(int nativeNodeHandle, int minZoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			mapView.map().viewport().setMinZoomLevel( (int) minZoom );
            promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

    @ReactMethod
    public void setMaxZoom(int nativeNodeHandle, int maxZoom, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			mapView.map().viewport().setMaxZoomLevel( (int) maxZoom );
            promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }
    @ReactMethod
    public void zoomIn(int nativeNodeHandle, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) mapPosition.getZoomLevel() + 1 );
			mapView.map().setMapPosition( mapPosition );
			promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }
    @ReactMethod
    public void zoomOut(int nativeNodeHandle, Promise promise ) {
        try {
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
            if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
            }
			MapPosition mapPosition = mapView.map().getMapPosition();
			mapPosition.setZoomLevel( (int) mapPosition.getZoomLevel() - 1 );
			mapView.map().setMapPosition( mapPosition );
			promise.resolve( true );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

	@ReactMethod
	public void setPropsInteractionsEnabled( int nativeNodeHandle, String propKey, int value, Promise promise ) {
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
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
			promise.resolve( true );
		} catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
		}
	}

	@ReactMethod
	public void setViewport( int nativeNodeHandle, String propKey, float value, Promise promise ) {
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" ); return;
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
			promise.resolve( true );
		} catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
		}
	}

	@ReactMethod
	public void setHgtDirPath(int nativeNodeHandle, String hgtDirPath, Promise promise ) {
		try {
			MapFragment mapFragment = (MapFragment) Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapFragment ) {
                promise.reject( "Error", "Unable to find mapFragment" ); return;
			}
			mapFragment.setPropHgtDirPath( hgtDirPath );
			promise.resolve( true );
		} catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
		}
	}

	@ReactMethod
	public void setResponseInclude(int nativeNodeHandle, ReadableMap responseInclude, Promise promise ) {
		try {
			MapFragment mapFragment = (MapFragment) Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapFragment ) {
				promise.reject( "Error", "Unable to find mapFragment" ); return;
			}
			mapFragment.setPropResponseInclude( responseInclude );
			promise.resolve( true );
		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
		}
	}

}
