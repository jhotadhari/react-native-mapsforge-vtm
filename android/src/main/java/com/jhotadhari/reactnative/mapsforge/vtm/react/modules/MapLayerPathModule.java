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
import com.jhotadhari.reactnative.mapsforge.vtm.Coordinate;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.layers.vector.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.joda.time.DateTime;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;

public class MapLayerPathModule extends MapLayerBase {

	protected Map<String, VectorLayer> layers = new HashMap<>();

	protected Map<String, Coordinate[]> originalJtsCoordinatesMap = new HashMap<>();

	public String getName() {
		return "MapLayerPathModule";
	}

	public MapLayerPathModule(ReactApplicationContext context) {
		super(context);
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
				promise.reject( "Error", "No scoped storage read permission for filePath " + filePath ); return null;
			}
			in = context.getContentResolver().openInputStream( Uri.parse( filePath ) );
			assert in != null;
		}

		if ( filePath.startsWith( "/" ) ) {
			File gpxFile = new File( filePath );
			if( ! gpxFile.exists() || ! gpxFile.isFile() || ! gpxFile.canRead() ) {
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
			promise.reject( "Error", e ); return jtsCoordinates;
		}
		if ( parsedGpx == null ) {
			promise.reject( "Error", "Unable to parse gpx file: " + filePath ); return jtsCoordinates;
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

	@ReactMethod
	public void createLayer(
		int nativeNodeHandle,
		ReadableArray positions,
		String filePath,
		ReadableMap styleMap,
		ReadableMap responseInclude,
		float gestureScreenDistance,
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
				"PathGesture",
				gestureScreenDistance
			);

			// Store layer.
			layers.put( uuid, vectorLayer );

			// Convert input params to jtsCoordinates
			Coordinate[] jtsCoordinates = new Coordinate[0];
			if ( null != positions && positions.size() > 0 ) {
				jtsCoordinates = readableArrayToJtsCoordinates( positions );
			} else if ( filePath != null && filePath.length() > 0 && filePath.endsWith( ".gpx" ) ) {
				jtsCoordinates = loadGpxToJtsCoordinates( mapView.getContext(), filePath, promise );
			}
			if ( null == jtsCoordinates || jtsCoordinates.length == 0 ) {
				promise.reject( "Error", "Unable to parse positions or gpx file" ); return;
			}

			// Store coordinates
			originalJtsCoordinatesMap.put( uuid, jtsCoordinates );

			// Draw line.
			drawLineForCoordinates(
				jtsCoordinates,
				getStyleBuilderFromMap( styleMap ),
				uuid,
				vectorLayer
			);

			addStuffToResponse( uuid, responseInclude, 0, responseParams );

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				vectorLayer
			);
			mapView.map().updateMap(true);

			// Resolve layer hash
			responseParams.putString( "uuid", uuid );
			promise.resolve( responseParams );
		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
		}
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
		Style style = styleBuilder.build();
		for (int i = 0; i < jtsCoordinates.length; i++) {
			if ( i != 0 ) {
				double[] segment = new double[4];
				segment[0] = jtsCoordinates[i].x;
				segment[1] = jtsCoordinates[i].y;
				segment[2] = jtsCoordinates[i-1].x;
				segment[3] = jtsCoordinates[i-1].y;
				vectorLayer.add( new LineDrawable(
					segment,
					style
				) );
			}
		}
	}

	@ReactMethod
	public void triggerEvent(
		int nativeNodeHandle,
		String layerUuid,
		float x,
		float y,
		Promise promise
	) {
		MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( mapFragment == null || null == mapView ) {
			promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
		}
		VectorLayer vectorLayer = layers.get( layerUuid );
		if ( vectorLayer == null ) {
			promise.reject( "Error", "Unable to find vectorLayer" ); return;
		}
		WritableMap params = vectorLayer.containsGetResponse( x, y );
		if (  null != params ) {
			// Add type
			params.putString( "type", "trigger" );
			// Add eventPosition
			WritableMap eventPosition = new WritableNativeMap();
			GeoPoint eventPoint = mapView.map().viewport().fromScreenPoint( x, y );
			eventPosition.putDouble("lng", eventPoint.getLongitude() );
			eventPosition.putDouble("lat", eventPoint.getLatitude() );
			params.putMap( "eventPosition", eventPosition );
			// sendEvent
			Utils.sendEvent( mapFragment.getReactContext(), vectorLayer.getGestureEventName(), params );
		}
		promise.resolve( params );
	}

	@ReactMethod
	public void updateStyle( int nativeNodeHandle, String uuid, ReadableMap styleMap, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		Log.d( "testtest uuid", uuid );
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

			Log.d( "testtest vectorLayer", vectorLayer.toString());

			// Create new vectorLayer.
			VectorLayer vectorLayerNew = new VectorLayer(
				mapView.map(),
				uuid,
				mapFragment.getReactContext(),
				vectorLayer.getGestureEventName(),
				vectorLayer.getGestureScreenDistance()
			);

			Log.d( "testtest vectorLayerNew", vectorLayerNew.toString());

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

			addStuffToResponse( uuid, responseInclude, 1, responseParams );

		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void updateGestureScreenDistance( int nativeNodeHandle, String uuid, float gestureScreenDistance, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		responseParams.putString( "uuid", uuid );
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
				promise.reject( "Error", "Unable to find mapView" ); return;
			}
			VectorLayer vectorLayer = layers.get( uuid );
			if ( null == vectorLayer ) {
				promise.reject( "Error", "Unable to find vectorLayer" ); return;
			}
			vectorLayer.setGestureScreenDistance( gestureScreenDistance );
			addStuffToResponse( uuid, responseInclude, 1, responseParams );
		} catch( Exception e ) {
			promise.reject( "Error", e );
		}
		promise.resolve( responseParams );
	}

	protected void addStuffToResponse( String uuid, ReadableMap responseInclude, int includeLevel, WritableMap responseParams ) {
		// Maybe add coordinates to promise response.
		if ( responseInclude.getInt( "coordinates" ) > includeLevel ) {
			addCoordinatesToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
		}
		// Maybe add bounds to response.
		if ( responseInclude.getInt( "bounds" ) > includeLevel ) {
			addBoundsToResponse( originalJtsCoordinatesMap.get( uuid ), responseParams );
		}
	}

	protected void addBoundsToResponse(
		@Nullable Coordinate[] jtsCoordinates,
		WritableMap responseParams
	) {
		if ( null != jtsCoordinates ) {
			Geometry geometry = new LineString( new CoordinateArraySequence( jtsCoordinates ), new GeometryFactory() );
			Envelope boundingBox = geometry.getEnvelopeInternal();
			WritableMap boundsParams = new WritableNativeMap();
			boundsParams.putDouble("minLat", boundingBox.getMinY());
			boundsParams.putDouble("minLng", boundingBox.getMinX());
			boundsParams.putDouble("maxLat", boundingBox.getMaxY());
			boundsParams.putDouble("maxLng", boundingBox.getMaxX());
			responseParams.putMap("bounds", boundsParams);
		}
	}

	protected void addCoordinatesToResponse(
		@Nullable Coordinate[] jtsCoordinates,
		WritableMap responseParams
	) {
		if ( null != jtsCoordinates && jtsCoordinates.length > 0 && ! responseParams.hasKey( "coordinates" ) ) {
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

	protected Style.Builder getStyleBuilderFromMap( ReadableMap styleMap ) {
		Style.Builder styleBuilder = Style.builder();
		if ( styleMap.hasKey( "strokeWidth" ) ) {
			styleBuilder.strokeWidth( (float) styleMap.getDouble( "strokeWidth" ) );
		}
		if ( styleMap.hasKey( "strokeColor" ) ) {
			styleBuilder.strokeColor( Color.parseColor( Objects.requireNonNull( styleMap.getString("strokeColor" ) ) ) );
		}
		if ( styleMap.hasKey( "fillColor" ) ) {
			styleBuilder.fillColor( Color.parseColor( Objects.requireNonNull( styleMap.getString("fillColor" ) ) ) );
		}
		if ( styleMap.hasKey( "fillAlpha" ) ) {
			styleBuilder.fillAlpha( (float) styleMap.getDouble( "fillAlpha" ) );
		}
		if ( styleMap.hasKey( "buffer" ) ) {
			styleBuilder.buffer( styleMap.getDouble( "buffer" ) );
		}
		if ( styleMap.hasKey( "scalingZoomLevel" ) ) {
			styleBuilder.scaleZoomLevel( (int) styleMap.getInt( "scalingZoomLevel" ) );
		}
		if ( styleMap.hasKey( "cap" ) ) {
			Paint.Cap cap = switch ( Objects.requireNonNull( styleMap.getString("cap" ) ) ) {
				case "ROUND" -> Paint.Cap.ROUND;
				case "BUTT" -> Paint.Cap.BUTT;
				case "SQUARE" -> Paint.Cap.SQUARE;
				default -> null;
			};
			if ( cap != null ) {
				styleBuilder.cap( cap );
			}
		}
		if ( styleMap.hasKey( "fixed" ) ) {
			styleBuilder.fixed( styleMap.getBoolean( "fixed" ) );
		}
		if ( styleMap.hasKey( "strokeIncrease" ) ) {
			styleBuilder.strokeIncrease( styleMap.getDouble( "strokeIncrease" ) );
		}
		if ( styleMap.hasKey( "blur" ) ) {
			styleBuilder.blur( (float) styleMap.getDouble( "blur" ) );
		}
		if ( styleMap.hasKey( "stipple" ) ) {
			styleBuilder.stipple( (int) styleMap.getInt( "stipple" ) );
		}
		if ( styleMap.hasKey( "stippleColor" ) ) {
			styleBuilder.stippleColor( Color.parseColor( Objects.requireNonNull( styleMap.getString("stippleColor" ) ) ) );
		}
		if ( styleMap.hasKey( "stippleWidth" ) ) {
			styleBuilder.stippleWidth( (float) styleMap.getDouble( "stippleWidth" ) );
		}

		// texture ???


		if ( styleMap.hasKey( "dropDistance" ) ) {
			styleBuilder.dropDistance( (float) styleMap.getDouble( "dropDistance" ) );
		}
		if ( styleMap.hasKey( "textureRepeat" ) ) {
			styleBuilder.textureRepeat( styleMap.getBoolean( "textureRepeat" ) );
		}
		if ( styleMap.hasKey( "heightOffset" ) ) {
			styleBuilder.heightOffset( (float) styleMap.getDouble( "heightOffset" ) );
		}
		if ( styleMap.hasKey( "randomOffset" ) ) {
			styleBuilder.randomOffset( styleMap.getBoolean( "randomOffset" ) );
		}
		if ( styleMap.hasKey( "transparent" ) ) {
			styleBuilder.transparent( styleMap.getBoolean( "transparent" ) );
		}

		return styleBuilder;
	}

	@ReactMethod
	public void removeLayer( int nativeNodeHandle, String uuid, Promise promise ) {
		originalJtsCoordinatesMap.remove( uuid );
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
