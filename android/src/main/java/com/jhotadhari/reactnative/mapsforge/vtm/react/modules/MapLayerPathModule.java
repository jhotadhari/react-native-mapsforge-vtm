package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.net.Uri;
import android.util.Log;

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
import com.jhotadhari.reactnative.mapsforge.vtm.Gradient;
import com.jhotadhari.reactnative.mapsforge.vtm.layers.vector.PathLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.joda.time.DateTime;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.oscim.android.MapView;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeoPoint;
//import org.oscim.layers.vector.PathLayer;
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
import java.util.Objects;
import java.util.UUID;

import io.ticofab.androidgpxparser.parser.GPXParser;
import io.ticofab.androidgpxparser.parser.domain.Gpx;
import io.ticofab.androidgpxparser.parser.domain.Point;
import io.ticofab.androidgpxparser.parser.domain.TrackPoint;

public class MapLayerPathModule extends MapLayerBase {

	public String getName() {
		return "MapLayerPathModule";
	}

	protected Map<String, List<TrackPoint>> trackPointsMap = new HashMap<>();

	public MapLayerPathModule(ReactApplicationContext context) {
		super(context);
	}

	protected static List<TrackPoint> positionsToTrackPointList( ReadableArray positions ) {
		List<TrackPoint> trackPoints = new ArrayList<>();


		for (int index = 0; index < positions.size(); index++) {
			ReadableType readableType = positions.getType(index);
			if ( readableType == ReadableType.Map ) {
				ReadableMap position = positions.getMap( index );
				Point.Builder pointBuilder = new TrackPoint.Builder()
					.setLatitude( position.getDouble( "lat" ) )
					.setLongitude( position.getDouble( "lng" ) );
				if ( position.hasKey("alt" ) ) {
					pointBuilder.setElevation( position.getDouble( "alt" ) );
				}
				if ( position.hasKey("time" ) ) {
					pointBuilder.setTime( new DateTime( position.getLong( "time" ) ) );
				}
				TrackPoint point =  (TrackPoint) pointBuilder.build();
				trackPoints.add( point );
			}
		}

		return trackPoints;
	}

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int reactTag, int reactTreeIndex, Promise promise ) {}

	protected Style buildStyleFromMap( ReadableMap styleMap ) {
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

		return styleBuilder.build();
	}

	protected void addBoundsToResponse(
		PathLayer pathLayer,
		WritableMap responseParams
	){
		LineDrawable line = pathLayer.getDrawable();
		Envelope boundingBox = line.getGeometry().getEnvelopeInternal();
		WritableMap boundsParams = new WritableNativeMap();
		boundsParams.putDouble("minLat", boundingBox.getMinY());
		boundsParams.putDouble("minLng", boundingBox.getMinX());
		boundsParams.putDouble("maxLat", boundingBox.getMaxY());
		boundsParams.putDouble("maxLng", boundingBox.getMaxX());
		responseParams.putMap("bounds", boundsParams);
	}

	protected void addCoordinatesToResponse(
		List<TrackPoint> trackPoints,
		WritableMap responseParams
	) {
		WritableArray coordinatesResponseArray = new WritableNativeArray();
		double accumulatedDistance = 0;
		for (int i = 0; i < trackPoints.size(); i++) {
			TrackPoint point = (TrackPoint) trackPoints.get( i );
			double distanceToLast = 0;
			if ( i > 0 ) {
				TrackPoint pointLast = (TrackPoint) trackPoints.get( i - 1 );
				distanceToLast = new GeoPoint(
					(double) point.getLatitude(),
					(double) point.getLongitude()
				).sphericalDistance( new GeoPoint(
					(double) pointLast.getLatitude(),
					(double) pointLast.getLongitude()
				) );
			}
			accumulatedDistance += distanceToLast;
			WritableMap position = new WritableNativeMap();
			position.putDouble( "lng", (double) point.getLongitude() );
			position.putDouble( "lat", (double) point.getLatitude() );
			position.putDouble( "distance", (double) accumulatedDistance );
			Double elevation = point.getElevation();
			if ( null != elevation ) {
				position.putDouble( "alt", elevation );
			}
			DateTime time = point.getTime();
			if ( null != time ) {
				position.putDouble( "time", (double) ( time.getMillis() / 1000L ) );
			}
			coordinatesResponseArray.pushMap( position );
		}
		// Add to responseParams.
		responseParams.putArray( "coordinates", coordinatesResponseArray );
	}

	protected void drawTrackPoints(
		List<TrackPoint> trackPoints,
		PathLayer pathLayer
	) {
		for (int index = 0; index < trackPoints.size(); index++) {
			TrackPoint point = (TrackPoint) trackPoints.get( index );
			pathLayer.addPoint( new GeoPoint(
				(Double) point.getLatitude(),
				(Double) point.getLongitude()
			) );
		}
	}

	protected List<TrackPoint> getTrackPointsFromProps(
		MapView mapView,
		ReadableArray positions,
		String filePath,
		WritableMap responseParams,
		Promise promise
	) {
		List<TrackPoint> trackPoints = new ArrayList<>();
		if ( null != positions && positions.size() > 0 ) {
			trackPoints = positionsToTrackPointList( positions );
		} else if ( filePath != null && filePath.length() > 0 && filePath.endsWith( ".gpx" ) ) {
			try {
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
					promise.reject( "Error", "Unable to load gpx file: " + filePath );
				}

				GPXParser parser = new GPXParser();
				Gpx parsedGpx = null;
				try {
					parsedGpx = parser.parse(in);
				} catch ( IOException | XmlPullParserException e) {
					e.printStackTrace();
					promise.reject( "Error", "Unable to load gpx file: " + filePath );
				}
				if (parsedGpx == null) {
					promise.reject( "Error", "Unable to load gpx file: " + filePath );
				} else {
					trackPoints = parsedGpx.getTracks().get(0).getTrackSegments().get(0).getTrackPoints();
				}
			} catch( Exception e ) {
				e.printStackTrace();
				promise.reject( "Error", e );
			}
		}
		return trackPoints;
	}

	@ReactMethod
	public void createLayer(
		int reactTag,
		ReadableArray positions,
		String filePath,
		ReadableMap styleMap,
		ReadableMap responseInclude,
		int reactTreeIndex,
		Promise promise
	) {
		try {
			MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), reactTag );
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );

			if ( mapFragment == null || null == mapView ) {
                promise.reject( "Error", "Unable to find mapView or mapFragment" );
			}

			// The promise response
			WritableMap responseParams = new WritableNativeMap();

			// Init layer
			PathLayer pathLayer = new PathLayer( mapView.map(), buildStyleFromMap( styleMap ) );

			// Store layer.
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, pathLayer );

			List<TrackPoint> trackPoints = getTrackPointsFromProps(
				mapView,
				positions,
				filePath,
				responseParams,
				promise
			);
			if ( null == trackPoints || trackPoints.isEmpty() ) {
				promise.reject( "Error", "Unable to parse positions or gpx file" );
			}

			// Store trackPoints
			trackPointsMap.put( uuid, trackPoints );

			// Draw line.
			drawTrackPoints( trackPoints, pathLayer );

			// Maybe add coordinates to response.
			if ( ! trackPoints.isEmpty() && responseInclude.getInt( "coordinates" ) > 0 ) {
				addCoordinatesToResponse( trackPoints, responseParams );
			}

			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 0 ) {
				addBoundsToResponse( pathLayer, responseParams );
			}

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				pathLayer
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

	@ReactMethod
	public void updateStyle( int reactTag, String uuid, ReadableMap styleMap, ReadableMap responseInclude, Promise promise ) {
		WritableMap responseParams = new WritableNativeMap();
		try {
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), reactTag );
			if ( null == mapView ) {
                promise.reject( "Error", "Unable to find mapView" );
			}

			int layerIndex = getLayerIndexInMapLayers( reactTag, uuid );
			if ( -1 == layerIndex ) {
				promise.reject( "Error", "Layer not found" );
			}

			// Create new vectorLayer.
			PathLayer pathLayerNew = new PathLayer( mapView.map(), buildStyleFromMap( styleMap ) );

			// Draw new.
			List<TrackPoint> trackPoints = trackPointsMap.get( uuid );
			if ( null == trackPoints ) {
				promise.reject( "Error", "Unable to find coordinates" );
			}
			drawTrackPoints( trackPoints, pathLayerNew );

			// Replace old vectorLayer with new one on map.
			mapView.map().layers().set( layerIndex, pathLayerNew );
			layers.put( uuid, pathLayerNew );
			mapView.map().updateMap( true );

			// Maybe add coordinates to promise response.
			if ( responseInclude.getInt( "coordinates" ) > 1 ) {
				addCoordinatesToResponse( trackPoints, responseParams );
			}

			// Maybe add bounds to response.
			if ( responseInclude.getInt( "bounds" ) > 1 ) {
				addBoundsToResponse( pathLayerNew, responseParams );
			}

		} catch( Exception e ) {
			e.printStackTrace();
			promise.reject( "Error ", e );
		}
		promise.resolve( responseParams );
	}

	@ReactMethod
	public void removeLayer(int reactTag, String uuid, Promise promise) {
		trackPointsMap.remove( uuid );
		super.removeLayer( reactTag, uuid, promise );
	}

}
