package com.jhotadhari.reactnative.mapsforge.vtm.react.views;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.FixedWindowRateLimiter;
import com.jhotadhari.reactnative.mapsforge.vtm.HardwareKeyListener;
import com.jhotadhari.reactnative.mapsforge.vtm.HgtReader;
import com.jhotadhari.reactnative.mapsforge.vtm.R;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.mapsforge.map.android.hills.DemFolderAndroidContent;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.map.Map.UpdateListener;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class MapFragment extends Fragment {

	protected HgtReader hgtReader;

	protected UpdateListener updateListener;

	protected RelativeLayout relativeLayout;
	protected View view;

	protected MapView mapView;

    protected MapViewManager mapViewManager;

	protected double propWidthForLayoutSize;
	protected double propHeightForLayoutSize;

	protected ReadableMap propCenter;

	protected boolean propMoveEnabled;
	protected boolean propRotationEnabled;
	protected boolean propZoomEnabled;
	protected boolean propTiltEnabled;

	protected int propZoomLevel;
	protected int propZoomMin;
	protected int propZoomMax;
	protected float propTilt;
	protected float propMinTilt;
	protected float propMaxTilt;
	protected float propBearing;
	protected float propMinBearing;
	protected float propMaxBearing;
	protected float propRoll;
	protected float propMinRoll;
	protected float propMaxRoll;
	protected String propHgtDirPath;
	protected boolean propHgtInterpolation;
	protected int propHgtReadFileRate;
	protected int propHgtFileInfoPurgeThreshold;
	protected ReadableMap propResponseInclude;
	protected boolean propEmitsMapEvents;
	protected List<String> propEmitsHardwareKeyUp;

	protected FixedWindowRateLimiter rateLimiter;
    protected String hardwareKeyListenerUid = null;

    public MapView getMapView() {
        return mapView;
    }

    protected void sendEventMapLayersCreated() {
        WritableMap params = new WritableNativeMap();
        params.putInt( "nativeNodeHandle", this.getId() );
        Utils.sendEvent( mapViewManager.getReactContext(), "MapLayersCreated", params );
    }

    MapFragment(
		MapViewManager mapViewManager_,

		double widthForLayoutSize,
		double heightForLayoutSize,

		ReadableMap center,

		boolean moveEnabled,
		boolean rotationEnabled,
		boolean zoomEnabled,
		boolean tiltEnabled,

		int zoomLevel,
		int zoomMin,
		int zoomMax,

		float tilt,
		float minTilt,
		float maxTilt,

		float bearing,
		float minBearing,
		float maxBearing,

		float roll,
		float minRoll,
		float maxRoll,

		String hgtDirPath,

		ReadableMap responseInclude,

		int mapEventRate,
		boolean hgtInterpolation,
		int hgtReadFileRate,
		int hgtFileInfoPurgeThreshold,
		boolean emitsMapEvents,
		List<String> emitsHardwareKeyUp
	) {
        super();

		updateRateLimiterRate( mapEventRate );

		mapViewManager = mapViewManager_;

		propHeightForLayoutSize = heightForLayoutSize;
		propWidthForLayoutSize = widthForLayoutSize;

        propCenter = center;

		propMoveEnabled = moveEnabled;
		propRotationEnabled = rotationEnabled;
		propZoomEnabled = zoomEnabled;
		propTiltEnabled = tiltEnabled;

		propZoomLevel = zoomLevel;
        propZoomMin = zoomMin;
        propZoomMax = zoomMax;

		propTilt = tilt;
		propMinTilt = minTilt;
		propMaxTilt = maxTilt;

		propBearing = bearing;
		propMinBearing = minBearing;
		propMaxBearing = maxBearing;

		propRoll = roll;
		propMinRoll = minRoll;
		propMaxRoll = maxRoll;

		propHgtDirPath = hgtDirPath;

		propResponseInclude = responseInclude;

		propHgtInterpolation = hgtInterpolation;
		propHgtReadFileRate = hgtReadFileRate;
		propHgtFileInfoPurgeThreshold = hgtFileInfoPurgeThreshold;

		propEmitsMapEvents = emitsMapEvents;
		propEmitsHardwareKeyUp = emitsHardwareKeyUp;
    }

	public void updateHardwareKeyListener( List<String> emitsHardwareKeyUp ) {
		propEmitsHardwareKeyUp = emitsHardwareKeyUp;
		removeHardwareKeyListener();
		addHardwareKeyListener();
	}

    protected void addHardwareKeyListener() {
        try {
            HardwareKeyListener hardwareKeyListener = new HardwareKeyListener(
				propEmitsHardwareKeyUp,
				mapViewManager.getReactContext(),
				this.getId()
			);
            Class[] cArg = new Class[1];
            cArg[0] = HardwareKeyListener.class;
            Method meth = mapViewManager.getReactContext().getCurrentActivity().getClass().getMethod(
				"addHardwareKeyListener",
				cArg
            );
            Object value = meth.invoke(
                 mapViewManager.getReactContext().getCurrentActivity(),
                hardwareKeyListener
            );
            String uid = (String) value;
            hardwareKeyListenerUid = uid;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    protected void removeHardwareKeyListener() {
        try {
            if ( null != hardwareKeyListenerUid ) {
                Class[] cArg = new Class[1];
                cArg[0] = String.class;
                Method meth = mapViewManager.getReactContext().getCurrentActivity().getClass().getMethod(
					"removeHardwareKeyListener",
					cArg
                );
                meth.invoke(
					mapViewManager.getReactContext().getCurrentActivity(),
					hardwareKeyListenerUid
                );
                hardwareKeyListenerUid = null;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

	public void setPropResponseInclude( ReadableMap responseInclude ) {
		propResponseInclude = responseInclude;
	}

	public void setPropHgtDirPath( String hgtDirPath ) {
		propHgtDirPath = hgtDirPath;
		if ( null != hgtDirPath ) {
			setHgtReader();
		} else {
			hgtReader = null;
		}
	}

	public void updateRateLimiterRate( int mapEventRate ) {
		rateLimiter = new FixedWindowRateLimiter( mapEventRate, 1 );
	}

	public void updateHgtReader( boolean hgtInterpolation, int hgtReadFileRate, int hgtFileInfoPurgeThreshold ) {
		propHgtInterpolation = hgtInterpolation;
		propHgtReadFileRate = hgtReadFileRate;
		propHgtFileInfoPurgeThreshold = hgtFileInfoPurgeThreshold;
		if ( null == hgtReader ) {
			setHgtReader();
		} else {
			hgtReader.updateInterpolation( hgtInterpolation );
			hgtReader.updateRateLimiterWindowSize( hgtReadFileRate );
			hgtReader.updateHgtFileInfoPurgeThreshold( hgtFileInfoPurgeThreshold );
		}
	}

	protected void setHgtReader() {
		// Init hgtReader
		if ( null != propHgtDirPath && ! propHgtDirPath.isEmpty() ) {
			DemFolder demFolder = null;
			if ( propHgtDirPath.startsWith( "content://" ) ) {
				Uri uri = Uri.parse( propHgtDirPath );
				DocumentFile dir = DocumentFile.fromSingleUri( mapView.getContext(), uri );
				if ( dir != null && dir.exists() && dir.isDirectory() ) {
					if ( Utils.hasScopedStoragePermission( mapView.getContext(), propHgtDirPath, false ) ) {
						demFolder = new DemFolderAndroidContent( uri, mapView.getContext(), mapView.getContext().getContentResolver() );
					}
				}
			} else if ( propHgtDirPath.startsWith( "/" ) ) {
				File demFolderFile = new File( propHgtDirPath );
				if ( demFolderFile.exists() && demFolderFile.isDirectory() && demFolderFile.canRead() ) {
					demFolder = new DemFolderFS( demFolderFile );
				}
			}
			if ( null != demFolder ) {
				hgtReader = new HgtReader(
					demFolder,
					propHgtInterpolation,
					propHgtReadFileRate,
					propHgtFileInfoPurgeThreshold
				);
			}
		}
	}

	public void updateUpdateListener( boolean emitsMapEvents ) {
		propEmitsMapEvents = emitsMapEvents;
		if ( propEmitsMapEvents && updateListener == null ) {
			bindUpdateListener();
		} else if ( ! propEmitsMapEvents && updateListener != null ) {
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
		if ( propEmitsMapEvents && null == updateListener ) {
			updateListener = new UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( rateLimiter.tryAcquire() ) {
						Utils.sendEvent( mapViewManager.getReactContext(), "onMapEvent", getResponseBase( 2 ) );
					}
				}
			};
			mapView.map().events.bind( updateListener );
		}
	}

	protected void createMapView(View v) {
		try {

			mapView = initMapView( v );

			// Initial position and zoomLevel.
			MapPosition mapPosition = new MapPosition( propCenter.getDouble( "lat" ), propCenter.getDouble( "lng" ), 1 );

			mapPosition.setZoomLevel( propZoomLevel );
			mapView.map().setMapPosition( mapPosition );

			mapView.map().getEventLayer().enableMove( propMoveEnabled );
			mapView.map().getEventLayer().enableRotation( propRotationEnabled );
			mapView.map().getEventLayer().enableZoom( propZoomEnabled );	// ??? bug, doesn't work properly, and still possible on .setZoom
			mapView.map().getEventLayer().enableTilt( propTiltEnabled );

			mapView.map().viewport().setMinZoomLevel( (int) propZoomMin );
			mapView.map().viewport().setMaxZoomLevel( (int) propZoomMax );

			mapView.map().viewport().setTilt( (float) propTilt );
			mapView.map().viewport().setMinTilt( (float) propMinTilt );
			mapView.map().viewport().setMaxTilt( (float) propMaxTilt );

			mapView.map().viewport().setRotation( (double) propBearing );
			mapView.map().viewport().setMinBearing( (float) propMinBearing );
			mapView.map().viewport().setMaxBearing( (float) propMaxBearing );

			mapView.map().viewport().setRoll( (double) propRoll );
			mapView.map().viewport().setMinRoll( (float) propMinRoll );
			mapView.map().viewport().setMaxRoll( (float) propMaxRoll );

			bindUpdateListener();

			setHgtReader();

		} catch ( Exception e ) {
			// Something went wrong. Should notice user!!!???
			e.printStackTrace();
		}
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		view = inflater.inflate( R.layout.fragment_map, container, false );
        createMapView( view );
		sendEventMapLayersCreated();
        addHardwareKeyListener();
        return view;
    }

	protected WritableMap getResponseBase( int includeLevel ) {
		WritableMap params = new WritableNativeMap();
		params.putInt( "nativeNodeHandle", this.getId() );
		MapPosition mapPosition = mapView.map().getMapPosition();

		if ( propResponseInclude.getInt( "zoomLevel" ) >= includeLevel ) {
			params.putDouble( "zoomLevel", mapPosition.getZoomLevel() );
		}
		if ( propResponseInclude.getInt( "zoom" ) >= includeLevel ) {
			params.putDouble( "zoom", mapPosition.getZoom() );
		}
		if ( propResponseInclude.getInt( "scale" ) >= includeLevel ) {
			params.putDouble( "scale", mapPosition.getScale() );
		}
		if ( propResponseInclude.getInt( "zoomScale" ) >= includeLevel ) {
			params.putDouble( "zoomScale", mapPosition.getZoomScale() );
		}
		if ( propResponseInclude.getInt( "bearing" ) >= includeLevel ) {
			params.putDouble( "bearing", mapPosition.getBearing() );
		}
		if ( propResponseInclude.getInt( "roll" ) >= includeLevel ) {
			params.putDouble( "roll", mapPosition.getRoll() );
		}
		if ( propResponseInclude.getInt( "tilt" ) >= includeLevel ) {
			params.putDouble( "tilt", mapPosition.getTilt() );
		}
		// center
		if ( propResponseInclude.getInt( "center" ) >= includeLevel ) {
			WritableMap center = new WritableNativeMap();
			center.putDouble("lng", mapPosition.getLongitude());
			center.putDouble("lat", mapPosition.getLatitude());
			if ( null != hgtReader ) {
				Short altitude = hgtReader.getAltitudeAtPosition( (ReadableMap) center, true );
				if ( null == altitude ) {
					center.putNull("alt");
				} else {
					center.putDouble("alt", altitude.doubleValue() );
				}
			}
			params.putMap("center", center);
		}
		return params;
	}

    protected void sendLifecycleEvent( String type ) {
        WritableMap params = getResponseBase( 1 );
        params.putString( "type", type );
        Utils.sendEvent(  mapViewManager.getReactContext(), "MapLifecycle", params );
    }

    @Override
    public void onPause() {
		if (mapView != null) {
			mapView.onPause();
		}
        sendLifecycleEvent( "onPause" );
		for ( Layer layer : mapView.map().layers() ) {
            try {
				layer.getClass().getMethod("onPause").invoke( layer );
            } catch (NoSuchMethodException e) {
				//
            } catch (InvocationTargetException e) {
				//
            } catch (IllegalAccessException e) {
				//
            }
        }
        super.onPause();
    }

	@Override
	public void onResume() {
		super.onResume();
		if (mapView != null) {
			mapView.onResume();
		}
        sendLifecycleEvent( "onResume" );
		for ( Layer layer : mapView.map().layers() ) {
			try {
				layer.getClass().getMethod("onResume").invoke( layer );
			} catch (NoSuchMethodException e) {
				//
			} catch (InvocationTargetException e) {
				//
			} catch (IllegalAccessException e) {
				//
			}
		}
	}

    @Override
    public void onDestroy() {
		unbindUpdateListener();
        removeHardwareKeyListener();
		if ( mapView != null ) {
			mapView.onDestroy();
			mapView = null;
		}
        super.onDestroy();
    }

    protected MapView initMapView( View view ) {
		mapView = new MapView( this.getContext() );
		relativeLayout = view.findViewById( R.id.mapView );
		relativeLayout.addView( mapView );
		fixViewLayoutSize();
        return mapView;
    }

	protected void fixViewLayoutSize() {
		if ( null != relativeLayout ) {
			ViewGroup.LayoutParams params = relativeLayout.getLayoutParams();
			params.width = (int) propWidthForLayoutSize;
			params.height = (int) propHeightForLayoutSize;
			view.setLayoutParams( params );
		}
	}

	protected void updateViewLayoutSize( double widthForLayoutSize, double heightForLayoutSize ) {
		if ( widthForLayoutSize != propWidthForLayoutSize || heightForLayoutSize != propHeightForLayoutSize ) {
			propWidthForLayoutSize = widthForLayoutSize;
			propHeightForLayoutSize = heightForLayoutSize;
			fixViewLayoutSize();
		}
	}

	public ReactContext getReactContext() {
		return mapViewManager.getReactContext();
	}

}
