package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.android.tiling.source.mbtiles.MBTilesBitmapTileSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileDataSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileSource;
import org.oscim.backend.canvas.Color;
import org.oscim.core.BoundingBox;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.map.Map;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MapLayerMBTilesBitmapModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMBTilesBitmapModule";
    }

    public MapLayerMBTilesBitmapModule(ReactApplicationContext context) {
        super(context);
    }

	protected java.util.Map<String, Map.UpdateListener> updateListeners = new HashMap<>();

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

	protected void addTileSourceToResponse( WritableMap responseParams, MBTilesTileSource tileSource ) {
		WritableMap boundsParams = new WritableNativeMap();
		MBTilesTileDataSource dataSource = tileSource.getDataSource();
		if ( null != dataSource ) {
			// Add dataSource bounds to response.
			BoundingBox boundingBox = dataSource.getBounds();
			boundsParams.putDouble( "minLat", boundingBox.getMinLatitude() );
			boundsParams.putDouble( "minLng", boundingBox.getMinLongitude() );
			boundsParams.putDouble( "maxLat", boundingBox.getMaxLatitude() );
			boundsParams.putDouble( "maxLng", boundingBox.getMaxLongitude() );
			responseParams.putMap( "bounds", boundsParams );
			// Maybe add dataSource center to response.
			MapPosition center = dataSource.getCenter();
			if ( center != null ) {
				WritableMap centerParams = new WritableNativeMap();
				centerParams.putDouble( "lng", center.getLongitude() );
				centerParams.putDouble( "lat", center.getLatitude() );
				responseParams.putMap( "center", centerParams );
			}
			// Add supported formats.
			List<String> supportedFormats = dataSource.getSupportedFormats();
			WritableArray supportedFormatsArr = new WritableNativeArray();
			for ( int i = 0; i < supportedFormats.size(); i++) {
				supportedFormatsArr.pushString( supportedFormats.get( i ) );
			}
			responseParams.putArray( "supportedFormats", supportedFormatsArr );
			// Add dataSource info to response.
			responseParams.putString( "format", dataSource.getFormat() );
			responseParams.putString( "attribution", dataSource.getAttribution() );
			responseParams.putString( "description", dataSource.getDescription() );
			responseParams.putString( "version", dataSource.getVersion() );
			responseParams.putInt( "maxZoom", dataSource.getMaxZoom() );
			responseParams.putInt( "minZoom", dataSource.getMinZoom() );
		}
	}

	protected void updateEnabled(
		Layer bitmapLayer,
		int zoomMin,
		int zoomMax,
		int zoomLevel
	) {
		if ( zoomLevel <= zoomMax && zoomLevel >= zoomMin ) {
			bitmapLayer.setEnabled( true );
		} else {
			bitmapLayer.setEnabled( false );
		}
	}

	protected String removeUpdateListener(
		int nativeNodeHandle,
		String uuid
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( null == mapView ) {
			return "Unable to find mapView or mapFragment";
		}
		if ( updateListeners.containsKey( uuid ) ) {
			Map.UpdateListener updateListenerOld = updateListeners.get( uuid );
			mapView.map().events.unbind( updateListenerOld );
			updateListeners.remove( uuid );
		}
		return null;
	}

	@ReactMethod
	public void updateZoomMinZoomMax( int nativeNodeHandle, String uuid, int zoomMin, int zoomMax, Promise promise ) {
		String errorMsg = updateUpdateListener( nativeNodeHandle, uuid, zoomMin, zoomMax );
		if ( null != errorMsg ) {
			promise.reject( "Error", errorMsg ); return;
		}
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		promise.resolve( responseParams );
	}

	protected String updateUpdateListener(
		int nativeNodeHandle,
		String uuid,
		int zoomMin,
		int zoomMax
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( null == mapView ) {
			return "Unable to find mapView";
		}
		String errorMsg = removeUpdateListener( nativeNodeHandle, uuid );
		if ( null != errorMsg ) {
			return errorMsg;
		}
		Layer bitmapLayer = layers.get( uuid );
		if ( bitmapLayer == null ) {
			return "Unable to find bitmapLayer";
		}
		Map.UpdateListener updateListener = new Map.UpdateListener() {
			@Override
			public void onMapEvent( Event e, MapPosition mapPosition ) {
				updateEnabled( bitmapLayer, zoomMin, zoomMax, mapPosition.getZoomLevel() );
			}
		};
		updateListeners.put( uuid, updateListener );
		mapView.map().events.bind( updateListener );
		updateEnabled( bitmapLayer, zoomMin, zoomMax, mapView.map().viewport().getMaxZoomLevel() );
		mapView.map().updateMap( true );
		return null;
	}

	@ReactMethod
    public void createLayer(
		int nativeNodeHandle,
		@Nullable String mapFile,
		int zoomMin,
		int zoomMax,
		int alpha,
		@Nullable String transparentColor,
		int reactTreeIndex,
		Promise promise
    ) {
        try {
			if ( null == mapFile ) {
                promise.reject( "WARNING", "mapFile is null" ); return;
			}

            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );

            if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
            }

			File file = new File( mapFile );
			if( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
                promise.reject( "Error", "mapFile does not exist or is not readable: " + mapFile ); return;
			}

			WritableMap responseParams = new WritableNativeMap();

			String uuid = UUID.randomUUID().toString();

			MBTilesTileSource tileSource = new MBTilesBitmapTileSource(
				file.getAbsolutePath(),
				alpha,
				null != transparentColor && transparentColor.startsWith( "#" ) ?
					Color.parseColor( transparentColor )
					: null
			);

			BitmapTileLayer bitmapLayer = new BitmapTileLayer( mapView.map(), tileSource );

			addTileSourceToResponse( responseParams, tileSource );

			// Add layer to map.
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				bitmapLayer
			);

			// Trigger update map.
			mapView.map().updateMap();

			// Store layer
			layers.put( uuid, bitmapLayer );

			// Handle zoomMin, zoomMax
			updateEnabled( bitmapLayer, zoomMin, zoomMax, mapView.map().viewport().getMaxZoomLevel() );
			updateUpdateListener( nativeNodeHandle, uuid, zoomMin, zoomMax );

			// Resolve layer uuid
			responseParams.putString( "uuid", uuid );
            promise.resolve( responseParams );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

	@ReactMethod
	public void removeLayer(int nativeNodeHandle, String uuid, Promise promise) {
		removeUpdateListener( nativeNodeHandle, uuid );
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

}
