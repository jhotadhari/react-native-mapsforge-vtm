package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.android.tiling.source.mbtiles.MBTilesBitmapTileSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileSource;
import org.oscim.backend.canvas.Color;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;

import java.io.File;
import java.util.UUID;

public class MapLayerMBTilesBitmapModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMBTilesBitmapModule";
    }

    public MapLayerMBTilesBitmapModule(ReactApplicationContext context) {
        super(context);
    }

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
            int reactTag,
			String mapFile,
			int alpha,
			String transparentColor,
            int reactTreeIndex,
            Promise promise
    ) {
        try {
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), reactTag );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );

            if ( mapFragment == null || null == mapView ) {
                promise.resolve( false );
                return;
            }

			File file = new File( mapFile );
			if ( ! file.exists() ) {
				promise.resolve( false );
				return;
			}

			MBTilesTileSource tileSource = new MBTilesBitmapTileSource(
				file.getAbsolutePath(),
				alpha,
				! transparentColor.isEmpty() && transparentColor.startsWith( "#" ) ?
					Color.parseColor( transparentColor )
					: null
			);

			BitmapTileLayer bitmapLayer = new BitmapTileLayer( mapView.map(), tileSource );

			// Add layer to map.
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				bitmapLayer
			);

			// Trigger update map.
			mapView.map().updateMap();

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, bitmapLayer );

			// Resolve layer uuid
            promise.resolve( uuid );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	@ReactMethod
	public void removeLayer(int reactTag, String uuid, Promise promise) {
		super.removeLayer( reactTag, uuid, promise );
	}

}
