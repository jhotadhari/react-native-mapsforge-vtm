package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.core.GeoPoint;
import org.oscim.layers.PathLayer;
import org.oscim.theme.styles.LineStyle;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;

public class MapLayerPathModule extends MapLayerBase {

	public String getName() {
		return "MapLayerPathModule";
	}

	public MapLayerPathModule(ReactApplicationContext context) {
		super(context);
	}

	protected static List positionsToPointsList( ReadableArray positions ) {
		List<GeoPoint> pts = new ArrayList<>();
		for (int index = 0; index < positions.size(); index++) {
			ReadableType readableType = positions.getType(index);
			if ( readableType == ReadableType.Map ) {
				pts.add( new GeoPoint(
					(Double) positions.getMap( index ).getDouble( "lat" ),
					(Double) positions.getMap( index ).getDouble( "lng" )
				) );
			}
		}
		return pts;
	}

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

	@ReactMethod
	public void createLayer(
		int reactTag,
		ReadableArray positions,
		String filePath,
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

			LineStyle lineStyle = new LineStyle( Color.parseColor("#ff0000"), 7 );
			PathLayer pathLayer = new PathLayer( mapView.map(), lineStyle );

			if ( null != positions && positions.size() > 0 ) {
				pathLayer.setPoints( positionsToPointsList( positions ) );
			} else if ( filePath != null && filePath.length() > 0 && filePath.endsWith( ".gpx" ) ) {

				InputStream in = null;
				if ( filePath.startsWith( "content://" ) ) {
					DocumentFile dir = DocumentFile.fromSingleUri( mapView.getContext(), Uri.parse( filePath ) );
					if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
						promise.reject( "Error", "filePath does not exist or is not a file" );
					}
					if ( ! Utils.hasScopedStoragePermission( mapView.getContext(), filePath, false ) ) {
						promise.reject( "Error", "No scoped storage read permission for filePath " + filePath );
					}
					in = mapView.getContext().getContentResolver().openInputStream( Uri.parse( filePath ) );
					assert in != null;
				}

				if ( filePath.startsWith( "/" ) ) {
					File gpxFile = new File( filePath );
					if( ! gpxFile.exists() || ! gpxFile.isFile() ) {
						promise.reject( "Error", "filePath does not exist or is not a file. " + filePath );
					}
					in = new FileInputStream( gpxFile );
				}
				if( in == null ) {
					promise.reject( "Error", "Unable to load mapFile: " + filePath );
				}

				GPXParser parser = new GPXParser();
				Gpx parsedGpx = null;
				try {
					parsedGpx = parser.parse(in);
				} catch ( IOException | XmlPullParserException e) {
					e.printStackTrace();
					promise.reject( "Error", "Unable to load mapFile: " + filePath );
				}
				if (parsedGpx == null) {
					promise.reject( "Error", "Unable to load mapFile: " + filePath );
				} else {
					List points = parsedGpx.getTracks().get(0).getTrackSegments().get(0).getTrackPoints();
					for (int index = 0; index < points.size(); index++) {
						TrackPoint point = (TrackPoint) points.get( index );
						pathLayer.addPoint( new GeoPoint(
							(Double) point.getLatitude(),
							(Double) point.getLongitude()
						) );
					}
				}
			}

			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				pathLayer
			);
			mapView.map().updateMap(true);

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, pathLayer );

			// Resolve layer hash
			promise.resolve( uuid );
		} catch(Exception e) {
			e.printStackTrace();
			promise.reject("Create Event Error", e );
		}
	}

	@ReactMethod
	public void removeLayer(int reactTag, String uuid, Promise promise) {
		super.removeLayer( reactTag, uuid, promise );
	}

}
