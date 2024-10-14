package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.DialogInterface;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;

import org.oscim.android.MapView;
import org.oscim.android.tiling.source.mbtiles.MBTilesBitmapTileSource;
import org.oscim.android.tiling.source.mbtiles.MBTilesTileSource;
import org.oscim.backend.canvas.Color;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

import java.io.File;
import java.net.URL;
import java.util.Collections;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class MapLayerMBTilesBitmapModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMBTilesBitmapModule";
    }

    MapLayerMBTilesBitmapModule(ReactApplicationContext context) {
        super(context);
    }

	@ReactMethod
    public void createLayer(
            int reactTag,
            int reactTreeIndex,
            Promise promise
    ) {
		createLayer(
			reactTag,
			"",
			256,
			"",
			reactTreeIndex,
			promise
		);
	}

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
            int hash = bitmapLayer.hashCode();
			layers.put( hash, bitmapLayer );

			// Resolve layer hash
            promise.resolve( hash );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	@ReactMethod
	public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
