package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.graphics.Bitmap;
import android.graphics.RenderNode;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.goebl.simplify.Simplify;
import com.goebl.simplify.Point;
import com.jhotadhari.reactnative.mapsforge.vtm.Gradient;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.locationtech.jts.geom.Coordinate;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.VectorLayer;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.renderer.LayerRenderer;
import org.oscim.renderer.MapRenderer;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.TreeMap;

import java.util.UUID;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;
import io.vacco.savitzkygolay.SgFilter;

public class MapLayerPathSlopeGradientModule extends MapLayerBase {

    public String getName() {
        return "MapLayerPathSlopeGradientModule";
    }

	public MapLayerPathSlopeGradientModule(ReactApplicationContext context) {
		super(context);
	}

	protected Map<String, VectorLayer> layers = new HashMap<>();
	protected Map<String, Coordinate[]> coordinatesMap = new HashMap<>();
	protected Map<String, CoordPoint[]> coordinatesSimplifiedMap = new HashMap<>();
	protected Map<String, Gradient> gradients = new HashMap<>();

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

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

	protected CoordPoint[] setupCoordinatesSimplified(
		String uuid,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		boolean shouldAddCoordinatesToResponse
	) {
		CoordPoint[] coordinatesSimplified = new CoordPoint[0];
		if ( slopeSimplificationTolerance > 0 || ( ( flattenWindowSize & 1 ) != 0 && flattenWindowSize > 5 ) ) {
			coordinatesSimplified = getCoordinatesSimplified(
				coordinatesMap.get( uuid ),
				slopeSimplificationTolerance,
				flattenWindowSize,
				shouldAddCoordinatesToResponse
			);
			// Store coordinatesSimplified;
			coordinatesSimplifiedMap.put( uuid, coordinatesSimplified );
		}
		return coordinatesSimplified;
	}

    @ReactMethod
    public void createLayer(
		int reactTag,
		ReadableArray positions,
		String filePath,
		int strokeWidth,
		ReadableArray slopeColors,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		ReadableMap responseInclude,
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
			WritableMap responseParams = new WritableNativeMap();

			// Init layer
			VectorLayer vectorLayer = new VectorLayer( mapView.map() );

			// Store layer.
			String uuid = UUID.randomUUID().toString();UUID.randomUUID().toString();
			layers.put( uuid, vectorLayer );

			// Convert input params to coordinates
			Coordinate[] coordinates = new Coordinate[0];
			if ( null != positions && positions.size() > 0 ) {
				coordinates = readableArrayToCoordinates( positions );
			} else if ( filePath != null && filePath.length() > 0 && filePath.startsWith( "/" ) && filePath.endsWith( ".gpx" ) ) {
				coordinates = loadGpx( filePath, promise );
			}

			// Store coordinates
			coordinatesMap.put( uuid, coordinates );

			// Setup gradient and store it.
			Gradient gradient = setupGradient( slopeColors );
			gradients.put( uuid, gradient );

			CoordPoint[] coordinatesSimplified = setupCoordinatesSimplified(
				uuid,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude.getInt( "coordinates" ) > 0
			);
			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 0 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			drawLineForCoordinates(
				coordinates,
				strokeWidth,
				uuid,
				vectorLayer,
				responseInclude,
				responseParams
			);

			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 0 ) {
				addCoordinatesToResponse( coordinates, responseParams );
			}

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				vectorLayer
			);
			mapView.map().updateMap(true );

			// Resolve promise
			responseParams.putString( "uuid", uuid );
            promise.resolve( responseParams );
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

	// Map of slopes for simplified coordinates. Keyed by their index within original coordinates.
	private Map<Integer, Double> getSimplifiedSlopes( CoordPoint[] coordinatesSimplified ) {
		Map<Integer, Double> simplifiedSlopes = new HashMap<>();
		if ( coordinatesSimplified != null ) {
			for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
				if ( i != 0 ) {
					simplifiedSlopes.put( coordinatesSimplified[i].index, coordinatesSimplified[i].slope );
				}
			}
		}
		return simplifiedSlopes;
	}

	private CoordPoint[] getCoordinatesSimplified(
		Coordinate[] coordinates,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		boolean shouldAddCoordinatesToResponse
	) {

		// Create coordinatesSimplified array. This array might be simplified and flattened.
		CoordPoint[] coordinatesSimplified = new CoordPoint[coordinates.length];
		double accumulatedDistance = 0;
		for ( int i = 0; i < coordinates.length; i++ ) {
			// Accumulate distance.
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
			// Init CoordPoint and add to coordinatesSimplified array.
			coordinatesSimplified[i] = new CoordPoint(
				i,
				(double) coordinates[i].x,
				(double) coordinates[i].y,
				(double) coordinates[i].z,
				(double) accumulatedDistance
			);
			// Maybe add coordinates to response.
			if ( shouldAddCoordinatesToResponse ) {
				WritableArray coordinatesResponseArray = new WritableNativeArray();
				WritableArray latLongAlt = new WritableNativeArray();
				// ??? maybe other way around, should do everywhere same order!!!
				latLongAlt.pushDouble( (double) coordinates[i].x );
				latLongAlt.pushDouble( (double) coordinates[i].y );
				latLongAlt.pushDouble( (double) coordinates[i].z );
				latLongAlt.pushDouble( (double) accumulatedDistance );
				coordinatesResponseArray.pushArray( latLongAlt );
			}
		}

		// Simplify coordinatesSimplified.
		if ( slopeSimplificationTolerance > 0 ) {
			Simplify<CoordPoint> simplify = new Simplify<CoordPoint>( new CoordPoint[0] );
			coordinatesSimplified = simplify.simplify( coordinatesSimplified, slopeSimplificationTolerance, true );
		}

		// Flatten altitude noise in coordinatesSimplified.
		if ( ( flattenWindowSize & 1) != 0 && flattenWindowSize > 5 ) {	// Must be odd and >= 5
			float[] xs = new float[coordinatesSimplified.length];
			float[] ys = new float[coordinatesSimplified.length];
			float[] altsFlattened = new float[coordinatesSimplified.length];
			for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
				xs[i] = (float) coordinatesSimplified[i].accumulatedDistance;
				ys[i] = (float) coordinatesSimplified[i].alt;
			}
			SgFilter sgf = new SgFilter( flattenWindowSize );
			sgf.process(ys, xs, altsFlattened);
			for ( int i = 0; i < altsFlattened.length; i++ ) {
				Float alt = (Float) altsFlattened[i];
				coordinatesSimplified[i].setAlt( alt.doubleValue() );
			}
		}

		// Calc slope for coordinatesSimplified. (Uses distance between points of full coordinates array)
		for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
			if ( i != 0 ) {
				double distance = coordinatesSimplified[i].accumulatedDistance - coordinatesSimplified[i-1].accumulatedDistance;
				double newSlope = ( coordinatesSimplified[i].alt - coordinatesSimplified[i-1].alt ) / distance * 100;
				coordinatesSimplified[i].setDistanceLast( distance );
				coordinatesSimplified[i].setSlope( newSlope );
			}
		}

		return coordinatesSimplified;
	}

	protected int getVectorLayerIndex(
		int reactTag,
		String uuid
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
		if ( null == mapView ) {
			return -1;
		}

		VectorLayer vectorLayerOld = layers.get( uuid );
		if ( null == vectorLayerOld ) {
			return -1;
		}

		int layerIndex = -1;
		int i = 0;
		while ( layerIndex == -1 || i < mapView.map().layers().size() ) {
			if ( vectorLayerOld == mapView.map().layers().get( i ) ) {
				layerIndex = i;
			}
			i++;
		}
		return layerIndex;
	}

	@ReactMethod
	public void updateCoordinatesSimplified(
		int reactTag,
		String uuid,
		int strokeWidth,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		ReadableMap responseInclude,
		Promise promise
	) {
		WritableMap responseParams = new WritableNativeMap();
		try {

			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}

			int layerIndex = getVectorLayerIndex( reactTag, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error updateCoordinatesSimplified", "Layer not found" );
				return;
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// Update coordinatesSimplified
			CoordPoint[] coordinatesSimplified = setupCoordinatesSimplified(
				uuid,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude.getInt( "coordinates" ) > 1
			);
			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			// draw new
			drawLineForCoordinates(
				coordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew,
				responseInclude,
				responseParams
			);

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, vectorLayerNew );
			layers.put( uuid, vectorLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplifiedMap.get( uuid ), responseParams );
			}
			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 1 ) {
				addCoordinatesToResponse( coordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			promise.reject("Error updateCoordinatesSimplified ", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void updateStrokeWidth( int reactTag, String uuid, int strokeWidth, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}

			int layerIndex = getVectorLayerIndex( reactTag, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error updateStrokeWidth", "Layer not found" );
				return;
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// draw new
			drawLineForCoordinates(
				coordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew,
				responseInclude,
				responseParams
			);

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, vectorLayerNew );
			layers.put( uuid, vectorLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplifiedMap.get( uuid ), responseParams );
			}
			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 1 ) {
				addCoordinatesToResponse( coordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			promise.reject("Error updateStrokeWidth ", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void updateSlopeColors( int reactTag, String uuid, int strokeWidth, ReadableArray slopeColors, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}

			int layerIndex = getVectorLayerIndex( reactTag, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error updateStrokeWidth", "Layer not found" );
				return;
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// Setup gradient and store it.
			Gradient gradient = setupGradient( slopeColors );
			gradients.put( uuid, gradient );

			// draw new
			drawLineForCoordinates(
				coordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew,
				responseInclude,
				responseParams
			);

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, vectorLayerNew );
			layers.put( uuid, vectorLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplifiedMap.get( uuid ), responseParams );
			}
			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 1 ) {
				addCoordinatesToResponse( coordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			promise.reject("Error updateStrokeWidth ", e );
		}
		promise.resolve( responseParams );
	}

	protected void drawLineForCoordinates(
		Coordinate[] coordinates,
		int strokeWidth,
		String uuid,
		VectorLayer vectorLayer,
		ReadableMap responseInclude,
		WritableMap responseParams
	) {
		if ( null == coordinates || coordinates.length == 0 ) {
			return;
		}

		// Draw the path, but maybe use simplified slopes (if slopeSimplificationTolerance greater 0).
		Map<Integer, Double> simplifiedSlopes = getSimplifiedSlopes( coordinatesSimplifiedMap.get( uuid ) );
		double slope = 0;
		for (int i = 0; i < coordinates.length; i++) {
			if ( i != 0 ) {
				// Get slope
				if ( ! simplifiedSlopes.isEmpty() ) {
					// Check if slope match is existing for this coordinate and the following coordinates.
					Double newSlope = simplifiedSlopes.get( i );
					if ( newSlope != null ) {
						slope = newSlope;
					}
				} else {
					slope = getSlopeBetweenCoordinates( coordinates[i], coordinates[i-1] );
				}
				// Get color for slope.
				if ( gradients.containsKey( uuid ) ) {
					Gradient gradient = gradients.get( uuid );
					int strokeColor = gradient.getColorAtPosition( (int) slope );
					if ( strokeColor != 0 ) {
						// Add new line segment to vectorLayer.
						double[] segment = new double[4];
						segment[0] = coordinates[i].x;
						segment[1] = coordinates[i].y;
						segment[2] = coordinates[i-1].x;
						segment[3] = coordinates[i-1].y;
						vectorLayer.add( new LineDrawable(
							segment,
							Style.builder().strokeWidth( strokeWidth ).strokeColor( strokeColor ).build()
						) );
					}
				}
			}
		}

	}

	protected void addCoordinatesSimplifiedToResponse(
		CoordPoint[] coordinatesSimplified,
		WritableMap responseParams
	) {
		WritableArray coordinatesSimplifiedResponseArray = new WritableNativeArray();
		for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
			coordinatesSimplifiedResponseArray.pushArray( coordinatesSimplified[i].toResponseArray() );
		}
		responseParams.putArray( "coordinatesSimplified", coordinatesSimplifiedResponseArray );
	}

	protected void addCoordinatesToResponse(
		Coordinate[] coordinates,
		WritableMap responseParams
	) {
		if ( coordinates.length > 0 && ! responseParams.hasKey( "coordinates" ) ) {
			WritableArray coordinatesResponseArray = new WritableNativeArray();
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
			// Add to responseParams.
			responseParams.putArray( "coordinates", coordinatesResponseArray );
		}
	}

	// Implements Point, so Simplify can use an array of these.
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

		public void setAlt( double alt ) {
			this.alt = alt;
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
    public void removeLayer( int reactTag, String uuid, Promise promise ) {
		coordinatesMap.remove( uuid );
		coordinatesSimplifiedMap.remove( uuid );
		gradients.remove( uuid );





		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
				promise.resolve( false );
				return;
			}

			Layer layer = layers.get( uuid );

			if ( null == layer )  {
				promise.resolve( false );
				return;
			}

			// Remove layer from map.
			int layerIndex = -1;
			for ( int i = 0; i < mapView.map().layers().size(); i++ ) {
				if ( layer == mapView.map().layers().get( i ) ) {
					layerIndex = i;
				}
			}
			if ( layerIndex != -1 ) {
				mapView.map().layers().remove( layerIndex );
			}

			// Remove layer from layers.
			layers.remove( uuid );

			// Trigger map update.
			mapView.map().updateMap();

			// Resolve uuid
			promise.resolve( uuid );
		} catch(Exception e) {
			promise.reject("Remove Layer Error", e);
		}






//		super.removeLayer( reactTag, uuid, promise );
	}

}
