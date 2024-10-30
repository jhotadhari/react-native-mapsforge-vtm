package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

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
import com.jhotadhari.reactnative.mapsforge.vtm.Coordinate;
import com.jhotadhari.reactnative.mapsforge.vtm.Gradient;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.joda.time.DateTime;
//import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.VectorLayer;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;
import io.vacco.savitzkygolay.SgFilter;

public class MapLayerPathSlopeGradientModule extends MapLayerBase {

    public String getName() {
        return "MapLayerPathSlopeGradientModule";
    }

	public MapLayerPathSlopeGradientModule(ReactApplicationContext context) { super(context); }

	protected Map<String, VectorLayer> layers = new HashMap<>();
	protected Map<String, Coordinate[]> originalJtsCoordinatesMap = new HashMap<>();
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

	protected static Coordinate[] readableArrayToJtsCoordinates( ReadableArray positions ) {
		Coordinate[] jtsCoordinates = new Coordinate[positions.size()];
		for ( int i = 0; i < positions.size(); i++ ) {
			ReadableType readableType = positions.getType( i );
			if ( readableType == ReadableType.Map ) {
				ReadableMap position = positions.getMap( i );
				jtsCoordinates[i] = new Coordinate(
					(double) position.getDouble( "lng" ),
					(double) position.getDouble( "lat" ),
					(double) ( position.hasKey( "alt" ) ? position.getDouble( "alt" ) : 0 )
				);
			}
		}
		return jtsCoordinates;
	}

	protected Coordinate[] loadGpxToJtsCoordinates( Context context, String filePath, Promise promise ) throws URISyntaxException, IOException {
		Coordinate[] jtsCoordinates = new Coordinate[0];

		InputStream in = null;
		if ( filePath.startsWith( "content://" ) ) {
			DocumentFile dir = DocumentFile.fromSingleUri( context, Uri.parse( filePath ) );
			if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
				return null;
			}
			if ( ! Utils.hasScopedStoragePermission( context, filePath, false ) ) {
				promise.reject( "Error", "No scoped storage read permission for filePath " + filePath );
			}
			in = context.getContentResolver().openInputStream( Uri.parse( filePath ) );
			assert in != null;
		}

		if ( filePath.startsWith( "/" ) ) {
			File gpxFile = new File( filePath );
			if( ! gpxFile.exists() || ! gpxFile.isFile() || ! file.canRead() ) {
				return null;
			}
			in = new FileInputStream( gpxFile );
		}
		if( in == null ) {
			return null;
		}

		GPXParser parser = new GPXParser();
		Gpx parsedGpx = null;
		try {
			parsedGpx = parser.parse( in );
		} catch ( IOException | XmlPullParserException e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
			return jtsCoordinates;
		}
		if ( parsedGpx == null ) {
			promise.reject( "Error", "Unable to parse gpx file: " + filePath );
			return jtsCoordinates;
		}
		List points = parsedGpx.getTracks().get(0).getTrackSegments().get(0).getTrackPoints();
		jtsCoordinates = new Coordinate[points.size()];
		for ( int i = 0; i < points.size(); i++) {
			TrackPoint point = (TrackPoint) points.get( i );
			jtsCoordinates[i] = new Coordinate(
				point.getLongitude(),
				point.getLatitude(),
				point.getElevation(),
				point.getTime()
			);
		}
		return jtsCoordinates;
	}

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

	protected CoordPoint[] setupCoordinatesSimplified(
		String uuid,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		boolean shouldAddCoordinatesToResponse,
		WritableMap responseParams
	) {
		CoordPoint[] coordinatesSimplified = new CoordPoint[0];
		if ( slopeSimplificationTolerance > 0 || ( ( flattenWindowSize & 1 ) != 0 && flattenWindowSize > 5 ) ) {
			coordinatesSimplified = getCoordinatesSimplified(
				originalJtsCoordinatesMap.get( uuid ),
				slopeSimplificationTolerance,
				flattenWindowSize,
				shouldAddCoordinatesToResponse,
				responseParams
			);
			// Store coordinatesSimplified;
			coordinatesSimplifiedMap.put( uuid, coordinatesSimplified );
		}
		return coordinatesSimplified;
	}

    @ReactMethod
    public void createLayer(
		int nativeNodeHandle,
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
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );

            if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" );
            }

			// The promise response
			WritableMap responseParams = new WritableNativeMap();

			// Init layer
			VectorLayer vectorLayer = new VectorLayer( mapView.map() );

			// Store layer.
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, vectorLayer );

			// Convert input params to jtsCoordinates
			Coordinate[] jtsCoordinates = new Coordinate[0];
			if ( null != positions && positions.size() > 0 ) {
				jtsCoordinates = readableArrayToJtsCoordinates( positions );
			} else if ( filePath != null && filePath.length() > 0 && filePath.endsWith( ".gpx" ) ) {
				jtsCoordinates = loadGpxToJtsCoordinates( mapView.getContext(), filePath, promise );
			}
			if ( null == jtsCoordinates || jtsCoordinates.length == 0 ) {
				promise.reject( "Error", "Unable to parse positions or gpx file" );
				return;
			}

			// Store coordinates
			originalJtsCoordinatesMap.put( uuid, jtsCoordinates );

			// Setup gradient and store it.
			Gradient gradient = setupGradient( slopeColors );
			gradients.put( uuid, gradient );

			CoordPoint[] coordinatesSimplified = setupCoordinatesSimplified(
				uuid,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude.getInt( "coordinates" ) > 0,
				responseParams
			);
			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 0 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			// Draw line.
			drawLineForCoordinates(
				jtsCoordinates,
				strokeWidth,
				uuid,
				vectorLayer
			);

			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 0 ) {
				addCoordinatesToResponse( jtsCoordinates, responseParams );
			}
			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 0 ) {
				addBoundsToResponse( jtsCoordinates, responseParams );
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
            promise.reject( "Error", e );
        }
    }

	protected static double getSlopeBetweenCoordinates( Coordinate jtsCoordinate, Coordinate jtsCoordinatePrev ) {
		double distance = new GeoPoint(
			(double) jtsCoordinate.y,
			(double) jtsCoordinate.x
		).sphericalDistance( new GeoPoint(
			(double) jtsCoordinatePrev.y,
			(double) jtsCoordinatePrev.x
		) );
		return ( jtsCoordinate.z - jtsCoordinatePrev.z ) / distance * 100;
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

	protected WritableMap getResponsePositionFromJtsCoordinate( Coordinate coordinate, double accumulatedDistance ){
		WritableMap position = new WritableNativeMap();
		position.putDouble( "lng", (double) coordinate.x );
		position.putDouble( "lat", (double) coordinate.y );
		position.putDouble( "alt", (double) coordinate.z );
		position.putDouble( "distance", (double) accumulatedDistance );
		DateTime time = coordinate.dateTime;
		if ( null != time ) {
			position.putDouble( "time", (double) ( time.getMillis() / 1000L ) );
		}
		return position;
	}

	private CoordPoint[] getCoordinatesSimplified(
		Coordinate[] jtsCoordinates,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		boolean shouldAddCoordinatesToResponse,
		WritableMap responseParams
	) {

		WritableArray coordinatesResponseArray = new WritableNativeArray();

		// Create coordinatesSimplified array. This array might be simplified and flattened.
		CoordPoint[] coordinatesSimplified = new CoordPoint[jtsCoordinates.length];
		double accumulatedDistance = 0;
		for ( int i = 0; i < jtsCoordinates.length; i++ ) {
			// Accumulate distance.
			double distanceToLast =  i == 0
				? 0
				: new GeoPoint(
				(double) jtsCoordinates[i].y,
				(double) jtsCoordinates[i].x
			).sphericalDistance( new GeoPoint(
				(double) jtsCoordinates[i-1].y,
				(double) jtsCoordinates[i-1].x
			) );
			accumulatedDistance += distanceToLast;
			// Init CoordPoint and add to coordinatesSimplified array.
			coordinatesSimplified[i] = new CoordPoint(
				i,
				(double) jtsCoordinates[i].x,
				(double) jtsCoordinates[i].y,
				(double) jtsCoordinates[i].z,
				(double) accumulatedDistance,
				jtsCoordinates[i].dateTime
			);
			// Maybe add coordinates to response.
			if ( shouldAddCoordinatesToResponse ) {
				WritableMap position = getResponsePositionFromJtsCoordinate( jtsCoordinates[i], accumulatedDistance );
				coordinatesResponseArray.pushMap( position );
			}
		}

		if ( shouldAddCoordinatesToResponse ) {
			responseParams.putArray("coordinates", coordinatesResponseArray);
		}

		// Simplify coordinatesSimplified.
		if ( slopeSimplificationTolerance > 0 ) {
			Simplify<CoordPoint> simplify = new Simplify<CoordPoint>( new CoordPoint[0] );
			coordinatesSimplified = simplify.simplify( coordinatesSimplified, slopeSimplificationTolerance, true );
		}

		// Flatten altitude noise in coordinatesSimplified.
		if ( ( flattenWindowSize & 1 ) != 0 && flattenWindowSize > 5 && coordinatesSimplified.length > flattenWindowSize ) {	// Must be odd and >= 5
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

	@ReactMethod
	public void updateCoordinatesSimplified(
		int nativeNodeHandle,
		String uuid,
		int strokeWidth,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		ReadableMap responseInclude,
		Promise promise
	) {
		WritableMap responseParams = new WritableNativeMap();
		try {

			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" );
			}

			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer not found" );
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// Update coordinatesSimplified
			CoordPoint[] coordinatesSimplified = setupCoordinatesSimplified(
				uuid,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude.getInt( "coordinates" ) > 1,
				responseParams
			);
			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			// draw new
			drawLineForCoordinates(
				originalJtsCoordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew
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
				addCoordinatesToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 1 ) {
				addBoundsToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void updateStrokeWidth( int nativeNodeHandle, String uuid, int strokeWidth, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" );
			}

			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer not found" );
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// draw new
			drawLineForCoordinates(
				originalJtsCoordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew
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
				addCoordinatesToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 1 ) {
				addBoundsToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			promise.reject( "Error", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void updateSlopeColors( int nativeNodeHandle, String uuid, int strokeWidth, ReadableArray slopeColors, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" );
			}

			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer not found" );
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer( mapView.map() );

			// Setup gradient and store it.
			Gradient gradient = setupGradient( slopeColors );
			gradients.put( uuid, gradient );

			// draw new
			drawLineForCoordinates(
				originalJtsCoordinatesMap.get( uuid ),
				strokeWidth,
				uuid,
				vectorLayerNew
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
				addCoordinatesToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 1 ) {
				addBoundsToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
			}
		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
		}
		promise.resolve( responseParams );
	}

	protected void drawLineForCoordinates(
		Coordinate[] jtsCoordinates,
		int strokeWidth,
		String uuid,
		VectorLayer vectorLayer
	) {
		if ( null == jtsCoordinates || jtsCoordinates.length == 0 ) {
			return;
		}

		// Draw the path, but maybe use simplified slopes (if slopeSimplificationTolerance greater 0).
		Map<Integer, Double> simplifiedSlopes = getSimplifiedSlopes( coordinatesSimplifiedMap.get( uuid ) );
		double slope = 0;
		for (int i = 0; i < jtsCoordinates.length; i++) {
			if ( i != 0 ) {
				// Get slope
				if ( ! simplifiedSlopes.isEmpty() ) {
					// Check if slope match is existing for this coordinate and the following coordinates.
					Double newSlope = simplifiedSlopes.get( i );
					if ( newSlope != null ) {
						slope = newSlope;
					}
				} else {
					slope = getSlopeBetweenCoordinates( jtsCoordinates[i], jtsCoordinates[i-1] );
				}
				// Get color for slope.
				if ( gradients.containsKey( uuid ) ) {
					Gradient gradient = gradients.get( uuid );
					int strokeColor = gradient.getColorAtPosition( (int) slope );
					if ( strokeColor != 0 ) {
						// Add new line segment to vectorLayer.
						double[] segment = new double[4];
						segment[0] = jtsCoordinates[i].x;
						segment[1] = jtsCoordinates[i].y;
						segment[2] = jtsCoordinates[i-1].x;
						segment[3] = jtsCoordinates[i-1].y;
						vectorLayer.add( new LineDrawable(
							segment,
							Style.builder().strokeWidth( strokeWidth ).strokeColor( strokeColor ).build()
						) );
					}
				}
			}
		}

	}

	protected void addBoundsToResponse(
		Coordinate[] jtsCoordinates,
		WritableMap responseParams
	) {
		Geometry geometry = new LineString( new CoordinateArraySequence( jtsCoordinates ), new GeometryFactory() );
		Envelope boundingBox = geometry.getEnvelopeInternal();
		WritableMap boundsParams = new WritableNativeMap();
		boundsParams.putDouble("minLat", boundingBox.getMinY());
		boundsParams.putDouble("minLng", boundingBox.getMinX());
		boundsParams.putDouble("maxLat", boundingBox.getMaxY());
		boundsParams.putDouble("maxLng", boundingBox.getMaxX());
		responseParams.putMap("bounds", boundsParams);
	}

	protected void addCoordinatesSimplifiedToResponse(
		CoordPoint[] coordinatesSimplified,
		WritableMap responseParams
	) {
		WritableArray coordinatesSimplifiedResponseArray = new WritableNativeArray();
		for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
			coordinatesSimplifiedResponseArray.pushMap( coordinatesSimplified[i].toResponseMap() );
		}
		responseParams.putArray( "coordinatesSimplified", coordinatesSimplifiedResponseArray );
	}

	protected void addCoordinatesToResponse(
		Coordinate[] jtsCoordinates,
		WritableMap responseParams
	) {
		if ( jtsCoordinates.length > 0 && ! responseParams.hasKey( "coordinates" ) ) {
			WritableArray coordinatesResponseArray = new WritableNativeArray();
			double accumulatedDistance = 0;
			for (int i = 0; i < jtsCoordinates.length; i++) {
				double distanceToLast = i == 0
					? 0
					: new GeoPoint(
					(double) jtsCoordinates[i].y,
					(double) jtsCoordinates[i].x
				).sphericalDistance( new GeoPoint(
					(double) jtsCoordinates[i-1].y,
					(double) jtsCoordinates[i-1].x
				) );
				accumulatedDistance += distanceToLast;
				WritableMap position = getResponsePositionFromJtsCoordinate( jtsCoordinates[i], accumulatedDistance );
				coordinatesResponseArray.pushMap( position );
			}
			// Add to responseParams.
			responseParams.putArray( "coordinates", coordinatesResponseArray );
		}
	}

	// Implements Point, so Simplify can use an array of these.
	private static class CoordPoint implements Point {

		int index;		// index within original coordinates
		double lat;		// latitude
		double lng;		// longitude
		double alt; 	// altitude
		double accumulatedDistance; // accumulated distance
		double distancePrev;	//
		double slope;	//
		@Nullable DateTime dateTime;

		private CoordPoint(
			int index,
			double lat,
			double lng,
			double alt,
			double accumulatedDistance,
			@Nullable DateTime dateTime
		) {
			this.index = index;
			this.lat = lat;
			this.lng = lng;
			this.alt = alt;
			this.accumulatedDistance = accumulatedDistance;
			this.distancePrev = 0;
			this.slope = 0;
			if ( null != dateTime ) {
				this.dateTime = dateTime;
			}
		}

		public void setSlope( double slope ) {
			this.slope = slope;
		}

		public void setAlt( double alt ) {
			this.alt = alt;
		}

		public void setDistanceLast( double distanceLast ) {
			this.distancePrev = distanceLast;
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
			return "[" + "index=" + index + " lat=" + lat + ", lng=" + lng + ", alt=" + alt + ", accumulatedDistance=" + accumulatedDistance + ", distanceLast=" + distancePrev + ", slope=" + slope + ']';
		}

		public WritableMap toResponseMap() {
			WritableMap position = new WritableNativeMap();
			position.putDouble( "lng", lng );
			position.putDouble( "lat", lat );
			position.putDouble( "alt", alt );
			position.putDouble( "distance", accumulatedDistance );
			position.putDouble( "slope", slope );
			if ( null != dateTime ) {
				position.putDouble( "time", (double) ( dateTime.getMillis() / 1000L ) );
			}
			return position;
		}

		@Override
		public boolean equals( Object o ) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			CoordPoint myPoint = (CoordPoint) o;

			if (Double.compare(myPoint.index, index) != 0) return false;
			if (Double.compare(myPoint.lat, lat) != 0) return false;
			if (Double.compare(myPoint.lng, lng) != 0) return false;
			if (Double.compare(myPoint.alt, alt) != 0) return false;
			if (Double.compare(myPoint.accumulatedDistance, accumulatedDistance) != 0) return false;

			return true;
		}

	}

    @ReactMethod
    public void removeLayer( int nativeNodeHandle, String uuid, Promise promise ) {
		originalJtsCoordinatesMap.remove( uuid );
		coordinatesSimplifiedMap.remove( uuid );
		gradients.remove( uuid );
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

	/**
	 * Copy of parent, because layers is different
	 */
	protected int getLayerIndexInMapLayers(
		int nativeNodeHandle,
		String uuid
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( null == mapView ) {
			return -1;
		}

		Layer layer = layers.get( uuid );
		if ( null == layer ) {
			return -1;
		}

		int layerIndex = -1;
		int i = 0;
		while ( layerIndex == -1 || i < mapView.map().layers().size() ) {
			if ( layer == mapView.map().layers().get( i ) ) {
				layerIndex = i;
			}
			i++;
		}
		return layerIndex;
	}

}
