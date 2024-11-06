package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.goebl.simplify.Point;
import com.goebl.simplify.Simplify;
import com.jhotadhari.reactnative.mapsforge.vtm.Coordinate;
import com.jhotadhari.reactnative.mapsforge.vtm.Gradient;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layers.vector.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.joda.time.DateTime;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.core.GeoPoint;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.vacco.savitzkygolay.SgFilter;

public class MapLayerPathSlopeGradientModule extends MapLayerPathModule {

	protected Map<String, CoordPoint[]> coordinatesSimplifiedMap = new HashMap<>();

	protected Map<String, Gradient> gradients = new HashMap<>();

    public String getName() {
        return "MapLayerPathSlopeGradientModule";
    }

	public MapLayerPathSlopeGradientModule(ReactApplicationContext context) { super(context); }

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

    @ReactMethod
    public void createLayer(
		int nativeNodeHandle,
		ReadableArray positions,
		String filePath,
		ReadableMap styleMap,
		ReadableArray slopeColors,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		ReadableMap responseInclude,
		boolean supportsGestures,
		float gestureScreenDistance,
		float simplificationTolerance,
		int reactTreeIndex,
		Promise promise
    ) {
        try {
            MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
            MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );

            if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
            }

			String uuid = UUID.randomUUID().toString();

			// The promise response
			WritableMap responseParams = new WritableNativeMap();

			// Init layer
			VectorLayer vectorLayer = new VectorLayer(
				mapView.map(),
				uuid,
				mapFragment.getReactContext(),
				supportsGestures,
				"PathSlopeGradientGesture",
				gestureScreenDistance
			);

			// Store layer.
			layers.put( uuid, vectorLayer );

			// Convert input params to jtsCoordinates
			Coordinate[] jtsCoordinates = new Coordinate[0];
			if ( null != positions && positions.size() > 0 ) {
				jtsCoordinates = readableArrayToJtsCoordinates( positions, simplificationTolerance );
			} else if ( filePath != null && filePath.length() > 0 && filePath.endsWith( ".gpx" ) ) {
				jtsCoordinates = loadGpxToJtsCoordinates( mapView.getContext(), filePath, simplificationTolerance, promise );
			}
			if ( null == jtsCoordinates || jtsCoordinates.length == 0 ) {
				promise.reject( "Error", "Unable to parse positions or gpx file" ); return;
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
			if ( responseInclude.hasKey( "coordinatesSimplified" ) && responseInclude.getInt( "coordinatesSimplified" ) > 0 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			// Draw line.
			drawLineForCoordinates(
				jtsCoordinates,
				getStyleBuilderFromMap( styleMap ),
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

	protected void addStuffToResponse( String uuid, ReadableMap responseInclude, int includeLevel, WritableMap responseParams ) {
		super.addStuffToResponse( uuid, responseInclude, includeLevel, responseParams );
		// Maybe add coordinatesSimplified to response.
		if ( responseInclude.hasKey( "coordinatesSimplified" ) && responseInclude.getInt( "coordinatesSimplified" ) > includeLevel ) {
			addCoordinatesSimplifiedToResponse( coordinatesSimplifiedMap.get( uuid ), responseParams );
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
	public void triggerEvent( int nativeNodeHandle, String layerUuid, float x, float y, Promise promise ) {
		super.triggerEvent( nativeNodeHandle, layerUuid, x, y, promise );
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
		ReadableMap styleMap,
		double slopeSimplificationTolerance,
		int flattenWindowSize,
		ReadableMap responseInclude,
		Promise promise
	) {
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		try {

			MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( mapFragment == null || null == mapView ) {
				promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
			}
			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer index not found" ); return;
			}
			VectorLayer vectorLayer = layers.get( uuid );
			if ( null == vectorLayer ) {
				promise.reject( "Error", "Layer not found" ); return;
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer(
				mapView.map(),
				uuid,
				mapFragment.getReactContext(),
				vectorLayer.getSupportsGestures(),
				vectorLayer.getGestureEventName(),
				vectorLayer.getGestureScreenDistance()
			);

			// Update coordinatesSimplified
			CoordPoint[] coordinatesSimplified = setupCoordinatesSimplified(
				uuid,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude.getInt( "coordinates" ) > 1,
				responseParams
			);
			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.hasKey( "coordinatesSimplified" ) && responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
				addCoordinatesSimplifiedToResponse( coordinatesSimplified, responseParams );
			}

			// draw new
			drawLineForCoordinates(
				originalJtsCoordinatesMap.get( uuid ),
				getStyleBuilderFromMap( styleMap ),
				uuid,
				vectorLayerNew
			);

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, vectorLayerNew );
			layers.put( uuid, vectorLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.hasKey( "coordinatesSimplified" ) && responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
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
	public void updateStyle( int nativeNodeHandle, String uuid, ReadableMap styleMap, ReadableMap responseInclude, Promise promise ) {
		super.updateStyle( nativeNodeHandle, uuid, styleMap, responseInclude, promise );
	}

	@ReactMethod
	public void updateSlopeColors( int nativeNodeHandle, String uuid, ReadableMap styleMap, ReadableArray slopeColors, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		try {
			MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( mapFragment == null || null == mapView ) {
				promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
			}
			int layerIndex = getLayerIndexInMapLayers( nativeNodeHandle, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer index not found" ); return;
			}
			VectorLayer vectorLayer = layers.get( uuid );
			if ( null == vectorLayer ) {
				promise.reject( "Error", "Layer not found" ); return;
			}

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer(
				mapView.map(),
				uuid,
				mapFragment.getReactContext(),
				vectorLayer.getSupportsGestures(),
				vectorLayer.getGestureEventName(),
				vectorLayer.getGestureScreenDistance()
			);

			// Setup gradient and store it.
			Gradient gradient = setupGradient( slopeColors );
			gradients.put( uuid, gradient );

			// draw new
			drawLineForCoordinates(
				originalJtsCoordinatesMap.get( uuid ),
				getStyleBuilderFromMap( styleMap ),
				uuid,
				vectorLayerNew
			);

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, vectorLayerNew );
			layers.put( uuid, vectorLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinatesSimplified to response.
			if ( responseInclude.hasKey( "coordinatesSimplified" ) && responseInclude.getInt( "coordinatesSimplified" ) > 1 ) {
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
		Style.Builder styleBuilder,
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
							styleBuilder.strokeColor( strokeColor ).build()
						) );
					}
				}
			}
		}
	}

	protected void addCoordinatesSimplifiedToResponse(
		@Nullable CoordPoint[] coordinatesSimplified,
		WritableMap responseParams
	) {
		if ( null != coordinatesSimplified ) {
			WritableArray coordinatesSimplifiedResponseArray = new WritableNativeArray();
			for ( int i = 0; i < coordinatesSimplified.length; i++ ) {
				coordinatesSimplifiedResponseArray.pushMap( coordinatesSimplified[i].toResponseMap() );
			}
			responseParams.putArray( "coordinatesSimplified", coordinatesSimplifiedResponseArray );
		}
	}

	// Implements Point, so Simplify can use an array of these.
	protected static class CoordPoint implements Point {

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

		@NonNull
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
		coordinatesSimplifiedMap.remove( uuid );
		gradients.remove( uuid );
		super.removeLayer( nativeNodeHandle, uuid, promise );
	}

}
