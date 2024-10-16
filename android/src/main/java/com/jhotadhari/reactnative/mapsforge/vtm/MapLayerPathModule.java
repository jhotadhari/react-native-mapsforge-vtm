package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;

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

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;

public class MapLayerPathModule extends MapLayerBase {

	public String getName() {
		return "MapLayerPathModule";
	}

	MapLayerPathModule(ReactApplicationContext context) {
		super(context);
	}

	protected static List positionsToPointsList( ReadableArray positions ) {
		List<GeoPoint> pts = new ArrayList<>();
		for (int index = 0; index < positions.size(); index++) {
			ReadableType readableType = positions.getType(index);
			if ( readableType == ReadableType.Array ) {
				pts.add( new GeoPoint(
					(Double) positions.getArray( index ).toArrayList().get(0),
					(Double) positions.getArray (index ).toArrayList().get(1)
				) );
			}
		}
		return pts;
	}

	@ReactMethod
	public void createLayer(
		int reactTag,
		int reactTreeIndex,
		Promise promise
	) {
		createLayer(
			reactTag,
			Utils.getEmptyReadableArray(),
			"",
			reactTreeIndex,
			promise
		);
	}

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
			} else if ( filePath != null && filePath.length() > 0 && filePath.startsWith( "/" ) && filePath.endsWith( ".gpx" ) ) {
				File gpxFile = new File( filePath );
				if( gpxFile.exists() ) {
					GPXParser parser = new GPXParser();
					Gpx parsedGpx = null;
					try {
						InputStream in = new FileInputStream(gpxFile);
						parsedGpx = parser.parse(in);
					} catch (IOException | XmlPullParserException e) {
						e.printStackTrace();
						promise.resolve( false );
						return;
					}
					if (parsedGpx == null) {
						promise.resolve(false);
						return;
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
			}

			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				pathLayer
			);
			mapView.map().updateMap(true);

			// Store layer
			int hash = pathLayer.hashCode();
			layers.put( hash, pathLayer );

			// Resolve layer hash
			promise.resolve( hash );
		} catch(Exception e) {
			e.printStackTrace();
			promise.reject("Create Event Error", e);
		}
	}

	@ReactMethod
	public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
