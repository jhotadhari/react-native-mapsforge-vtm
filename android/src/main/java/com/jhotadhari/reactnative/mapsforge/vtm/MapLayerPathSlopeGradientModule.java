package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.goebl.simplify.Simplify;
import com.goebl.simplify.Point;

import org.locationtech.jts.geom.Coordinate;
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

	MapLayerPathSlopeGradientModule(ReactApplicationContext context) {
		super(context);
	}

	protected static Gradient setupGradient( ReadableArray slopeColors ) {
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

	protected static Coordinate[] loadGpx( String filePath, Promise promise ) {
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
			5,
			Utils.getEmptyReadableArray(),
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
		ReadableArray responseInclude,
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

			// The promise response
			WritableMap params = new WritableNativeMap();

			// Convert responseInclude ReadableArray to responseIncludeList List of strings.
			List<String> responseIncludeList = new ArrayList<String>();
			for ( int i = 0; i < responseInclude.size(); i++ ) {
				ReadableType readableType = responseInclude.getType( i );
				Log.d( "debug readableType", String.valueOf( readableType ) );
				if ( readableType == ReadableType.String ) {
					responseIncludeList.add( responseInclude.getString( i ) );
				}
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
				layer,
				responseIncludeList,
				params
			);

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				layer
			);
			mapView.map().updateMap(true);

			// Resolve promise
			params.putInt( "hash", hash );
            promise.resolve( params );
        } catch( Exception e ) {
			e.printStackTrace();
            promise.reject("Create Event Error", e);
        }
    }

	protected static double getSlopeBetweenCoordinates( Coordinate coordinate, Coordinate coordinateLast ) {
		double distance = new GeoPoint(
			(double) coordinate.y,
			(double) coordinate.x
		).sphericalDistance( new GeoPoint(
			(double) coordinateLast.y,
			(double) coordinateLast.x
		) );
		return ( coordinate.z - coordinateLast.z ) / distance * 100;
	}

	protected static void drawLineForCoordinates(
		Coordinate[] coordinates,
		int strokeWidth,
		Gradient gradient,
		double slopeSimplificationTolerance,
		VectorLayer layer,
		List<String> responseIncludeList,
		WritableMap responseParams
	) {

		// ??? TODO flatten after simplifiing

		WritableArray coordinatesResponseArray = new WritableNativeArray();

		double slope = 0;
		// Map of slopes for simplified coordinates. And set first slope match. Keyed by their index within original coordinates.
		Map<Integer, Double> simplifiedSlopes = new HashMap<>();

		if ( slopeSimplificationTolerance > 0 ) {

			Simplify<CoordPoint> simplify = new Simplify<CoordPoint>( new CoordPoint[0] );

			CoordPoint[] coordinatePoints = new CoordPoint[coordinates.length];

			double accumulatedDistance = 0;
			for ( int i = 0; i < coordinates.length; i++ ) {
				double distanceToLast =  i == 0
					? 0
					: new GeoPoint(
					(double) coordinates[i].y,
					(double) coordinates[i].x
				).sphericalDistance( new GeoPoint(
					(double) coordinates[i-1].y,
					(double) coordinates[i-1].x
				) );
				accumulatedDistance += distanceToLast;

				coordinatePoints[i] = new CoordPoint(
					i,
					(double) coordinates[i].x,
					(double) coordinates[i].y,
					(double) coordinates[i].z,
					(double) accumulatedDistance
				);

				if ( responseIncludeList.contains( "coordinates" ) ) {
					WritableArray latLongAlt = new WritableNativeArray();
					// ??? maybe other way around, should do everywhere same order!!!
					latLongAlt.pushDouble( (double) coordinates[i].x );
					latLongAlt.pushDouble( (double) coordinates[i].y );
					latLongAlt.pushDouble( (double) coordinates[i].z );
					latLongAlt.pushDouble( (double) accumulatedDistance );
					coordinatesResponseArray.pushArray( latLongAlt );
				}
			}

			CoordPoint[] coordinatesSimplified = simplify.simplify( coordinatePoints, slopeSimplificationTolerance, true );

			for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
				if ( i != 0 ) {
					double distance = coordinatesSimplified[i].accumulatedDistance - coordinatesSimplified[i-1].accumulatedDistance;
					double newSlope = ( coordinatesSimplified[i].alt - coordinatesSimplified[i-1].alt ) / distance * 100;
					coordinatesSimplified[i].setDistanceLast( distance );
					coordinatesSimplified[i].setSlope( newSlope );
					// Store slope in simplifiedSlopes map.
					simplifiedSlopes.put( coordinatesSimplified[i].index, newSlope );
					// Set first slope match.
					if ( i == 1 ) {
						slope = newSlope;
					}
				}
			}

			if ( responseIncludeList.contains( "coordinatesSimplified" ) ) {
				WritableArray coordinatesSimplifiedResponseArray = new WritableNativeArray();
				for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
					coordinatesSimplifiedResponseArray.pushArray( coordinatesSimplified[i].toResponseArray() );
				}
				responseParams.putArray( "coordinatesSimplified", coordinatesSimplifiedResponseArray );
			}

		}

		// Draw the path, but maybe use simplified slopes (if slopeSimplificationTolerance greater 0).
		for (int i = 0; i < coordinates.length; i++) {
			if ( i != 0 ) {
				// Get slope
				if ( slopeSimplificationTolerance > 0 ) {
					// Check if slope match is existing for this coordinate and the following coordinates.
					Double newSlope = simplifiedSlopes.get( i );
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

		// Maybe add coordinates to promise response.
		if ( responseIncludeList.contains( "coordinates" ) ) {
			// if simplification happend, then this loop is already done. Otherwise do it again.
			if ( 0 == coordinatesResponseArray.size() ) {
				double accumulatedDistance = 0;
				for (int i = 0; i < coordinates.length; i++) {
					double distanceToLast = i == 0
						? 0
						: new GeoPoint(
							(double) coordinates[i].y,
							(double) coordinates[i].x
						).sphericalDistance( new GeoPoint(
							(double) coordinates[i-1].y,
							(double) coordinates[i-1].x
						) );
					accumulatedDistance += distanceToLast;
					WritableArray latLongAlt = new WritableNativeArray();
					// ??? maybe other way around, should do everywhere same order!!!
					latLongAlt.pushDouble( (double) coordinates[i].x );
					latLongAlt.pushDouble( (double) coordinates[i].y );
					latLongAlt.pushDouble( (double) coordinates[i].z );
					latLongAlt.pushDouble( (double) accumulatedDistance );
					coordinatesResponseArray.pushArray( latLongAlt );
				}
			}
			// Add to responseParams.
			responseParams.putArray( "coordinates", coordinatesResponseArray );
		}

	}

	private static class CoordPoint implements Point {

		int index;		// index within original coordinates
		double lat;		// latitude
		double lon;		// longitude
		double alt; 	// altitude
		double accumulatedDistance; // accumulated distance
		double distanceLast;	//
		double slope;	//

		private CoordPoint(
			int index,
			double lat,
			double lon,
			double alt,
			double accumulatedDistance

		) {
			this.index = index;
			this.lat = lat;
			this.lon = lon;
			this.alt = alt;
			this.accumulatedDistance = accumulatedDistance;
			this.distanceLast = 0;
			this.slope = 0;
		}

		public void setSlope( double slope ) {
			this.slope = slope;
		}

		public void setDistanceLast( double distanceLast ) {
			this.distanceLast = distanceLast;
		}

		@Override
		public double getX() {
			return accumulatedDistance + 10000;	// Ensure value is greater than 0.
		}

		@Override
		public double getY() {
			return alt + 100000;				// Ensure value is greater than 0.
		}

		@Override
		public String toString() {
			return "[" + "index=" + index + " lat=" + lat + ", lon=" + lon + ", alt=" + alt + ", accumulatedDistance=" + accumulatedDistance + ", distanceLast=" + distanceLast + ", slope=" + slope + ']';
		}

		public WritableArray toResponseArray() {
			WritableArray latLongAlt = new WritableNativeArray();
			// ??? maybe other way around, should do everywhere same order!!!
			latLongAlt.pushDouble( lon );
			latLongAlt.pushDouble( lat );
			latLongAlt.pushDouble( alt );
			latLongAlt.pushDouble( accumulatedDistance );
			return latLongAlt;
		}

		@Override
		public boolean equals( Object o ) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CoordPoint myPoint = (CoordPoint) o;

			if (Double.compare(myPoint.index, index) != 0) return false;
			if (Double.compare(myPoint.lat, lat) != 0) return false;
			if (Double.compare(myPoint.lon, lon) != 0) return false;
			if (Double.compare(myPoint.alt, alt) != 0) return false;
			if (Double.compare(myPoint.accumulatedDistance, accumulatedDistance) != 0) return false;

			return true;
		}

	}

    @ReactMethod
    public void removeLayer(int reactTag, int hash, Promise promise) {
		super.removeLayer( reactTag, hash, promise );
	}

}
