package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.core.GeoPoint;
import org.oscim.layers.vector.VectorLayer;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;

public class MapLayerPathSlopeGradientModule extends MapLayerBase {

    public String getName() {
        return "MapLayerPathSlopeGradientModule";
    }

	protected final PackedCoordinateSequenceFactory coordFactory;
	protected final GeometryFactory geomFactory;

	MapLayerPathSlopeGradientModule(ReactApplicationContext context) {
		super(context);
		coordFactory = new PackedCoordinateSequenceFactory();
		geomFactory = new GeometryFactory( coordFactory );
	}

	protected Gradient setupGradient( ReadableArray slopeColors ) {
		float[] positions = new float[slopeColors.size()];
		int[] colors = new int[slopeColors.size()];
		for ( int i = 0; i < slopeColors.size(); i++ ) {
			ReadableType readableType = slopeColors.getType( i );
			if ( readableType == ReadableType.Array ) {
				ArrayList<Object> arrList = slopeColors.getArray( i ).toArrayList();
				double position = (double) arrList.get( 0 );
				positions[i] = (float) position;
				colors[i] = Color.parseColor( (String) arrList.get( 1 ) );
			}
		}
		return new Gradient(
			colors,
			positions
		);
	}

	protected static Coordinate[] readableArrayToCoordinates( ReadableArray positions ) {
		Coordinate[] coordinates = new Coordinate[positions.size()];
		for ( int i = 0; i < positions.size(); i++ ) {
			ReadableType readableType = positions.getType( i );
			if ( readableType == ReadableType.Array ) {
				ArrayList<Object> arrList = positions.getArray( i ).toArrayList();
				coordinates[i] = new Coordinate(
					(double) arrList.get( 0 ),
					(double) arrList.get( 1 ),
					(double) ( arrList.size() > 2 ? arrList.get( 2 ) : 0 )
				);
			}
		}
		return coordinates;
	}

	protected Coordinate[] loadGpx( String filePath, Promise promise ) {
		Coordinate[] coordinates = new Coordinate[0];
		File gpxFile = new File( filePath );
		if( gpxFile.exists() ) {
			GPXParser parser = new GPXParser();
			Gpx parsedGpx = null;
			try {
				InputStream in = new FileInputStream( gpxFile );
				parsedGpx = parser.parse(in);
			} catch ( IOException | XmlPullParserException e ) {
				e.printStackTrace();
				promise.resolve( false );
				return coordinates;
			}
			if ( parsedGpx == null ) {
				promise.resolve(false );
				return coordinates;
			}
			List points = parsedGpx.getTracks().get(0).getTrackSegments().get(0).getTrackPoints();
			coordinates = new Coordinate[points.size()];
			for ( int i = 0; i < points.size(); i++) {
				TrackPoint point = (TrackPoint) points.get( i );
				coordinates[i] = new Coordinate(
					point.getLongitude(),
					point.getLatitude(),
					point.getElevation()
				);
			}
		}
		return coordinates;
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
			4,
			Utils.getEmptyReadableArray(),
			0.0005,
			reactTreeIndex,
			promise
		);
	}

    @ReactMethod
    public void createLayer(
		int reactTag,
		ReadableArray positions,
		String filePath,
		int strokeWidth,
		ReadableArray slopeColors,
		double slopeSimplificationTolerance,
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

			// Init layer
			VectorLayer layer = new VectorLayer( mapView.map() );

			// Store layer.
			int hash = layer.hashCode();
			layers.put( hash, layer );

			// Convert input params to coordinates
			Coordinate[] coordinates = new Coordinate[0];
			if ( null != positions && positions.size() > 0 ) {
				coordinates = readableArrayToCoordinates( positions );
			} else if ( filePath != null && filePath.length() > 0 && filePath.startsWith( "/" ) && filePath.endsWith( ".gpx" ) ) {
				coordinates = loadGpx( filePath, promise );
			}

			drawLineForCoordinates(
				coordinates,
				strokeWidth,
				setupGradient( slopeColors ),
				slopeSimplificationTolerance,
				layer
			);

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				layer
			);
			mapView.map().updateMap(true);

			// Resolve layer hash
            promise.resolve( hash );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	protected double getSlopeBetweenCoordinates( Coordinate coordinate, Coordinate coordinateLast ) {
		double distance = new GeoPoint(
			(double) coordinate.y,
			(double) coordinate.x
		).sphericalDistance(  new GeoPoint(
			(double) coordinateLast.y,
			(double) coordinateLast.x
		) );
		return ( coordinate.z - coordinateLast.z ) / distance * 100;
	}

	protected void drawLineForCoordinates(
		Coordinate[] coordinates,
		int strokeWidth,
		Gradient gradient,
		double slopeSimplificationTolerance,
		VectorLayer layer
	) {

		double slope = 0;
		// Map of slopes for simplified coordinates. And set first slope match.
		Map<String, Double> simplifiedSlopes = new HashMap<>();

		if ( slopeSimplificationTolerance > 0 ) {
			// Array of simplified coordinates.
			Coordinate[] simplified = DouglasPeuckerSimplifier.simplify(
				new LineString( coordFactory.create( coordinates ), geomFactory ),
				slopeSimplificationTolerance
			).getCoordinates();

			for ( int i = 0; i < simplified.length; i++ ) {
				if ( i != 0 ) {
					double newSlope = getSlopeBetweenCoordinates( simplified[i], simplified[i-1] );
					// Store slope in simplifiedSlopes map.
					simplifiedSlopes.put( simplified[i].toString(), newSlope );
					// Set first slope match.
					if ( i == 1 ) {
						slope = newSlope;
					}
				}
			}
		}

		// Draw the path, but use simplified slopes.
		for (int i = 0; i < coordinates.length; i++) {
			if ( i != 0 ) {
				// Get slope
				if ( slopeSimplificationTolerance > 0 ) {
					// Check if slope match is existing for this coordinate and the following coordinates.
					Double newSlope = simplifiedSlopes.get( coordinates[i].toString() );
					if ( newSlope != null ) {
						slope = newSlope;
					}
				} else {
					slope = getSlopeBetweenCoordinates( coordinates[i], coordinates[i-1] );
				}
				// Get color for slope.
				int strokeColor = gradient.getColorAtPosition( (int) slope );
				if ( strokeColor != 0 ) {
					// Add new line segment to layer.
					double[] segment = new double[4];
					segment[0] = coordinates[i].x;
					segment[1] = coordinates[i].y;
					segment[2] = coordinates[i-1].x;
					segment[3] = coordinates[i-1].y;
					layer.add( new LineDrawable(
						segment,
						Style.builder().strokeWidth( strokeWidth ).strokeColor( strokeColor ).build()
					) );
				}
			}

		}
	}

    @ReactMethod
    public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
