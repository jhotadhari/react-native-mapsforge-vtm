package com.jhotadhari.reactnative.mapsforge.vtm.react.modules;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.react.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.backend.canvas.Canvas;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.Box;
import org.oscim.core.GeoPoint;
import org.oscim.core.Point;
import org.oscim.core.Tile;
import org.oscim.layers.Layer;
import org.oscim.layers.marker.ItemizedLayer;
import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Viewport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MapLayerMarkerModule extends MapLayerBase {

    public String getName() {
        return "MapLayerMarkerModule";
    }

	protected final Point mTmpPoint = new Point();

	public MapLayerMarkerModule(ReactApplicationContext context) {
        super(context);
    }

	protected Map<String, ItemizedLayer> layers = new HashMap<>();
	protected Map<String, MarkerItem> markers = new HashMap<>();

	// This constructor should not be called. It's just existing to overwrite the parent constructor.
	public void createLayer( int nativeNodeHandle, int reactTreeIndex, Promise promise ) {}

	@ReactMethod
    public void createLayer(
		int nativeNodeHandle,
		ReadableMap symbolMap,
		int reactTreeIndex,
		Promise promise
    ) {
        try {
			MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
			MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
			if ( mapFragment == null || null == mapView ) {
				promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
			}

			// The promise response
			WritableMap responseParams = new WritableNativeMap();

			ItemizedLayer.OnItemGestureListener<MarkerInterface> listener = new ItemizedLayer.OnItemGestureListener() {
				@Override
				public boolean onItemSingleTapUp( int i, Object o ) {
					MarkerItem markerItem = (MarkerItem) o;
					WritableMap params = new WritableNativeMap();
					params.putInt( "index", i );
					params.putString( "uuid", markerItem.getUid().toString() );
					Utils.sendEvent(  mapFragment.getReactContext(), "MarkerItemSingleTapUp", params );
					return false;
				}
				@Override
				public boolean onItemLongPress( int i, Object o ) {
					MarkerItem markerItem = (MarkerItem) o;
					WritableMap params = new WritableNativeMap();
					params.putInt( "index", i );
					params.putString( "uuid", markerItem.getUid().toString() );
					Utils.sendEvent(  mapFragment.getReactContext(), "MarkerItemLongPress", params );
					return false;
				}
			};

			MarkerSymbol symbol = getMarkerSymbol(
				mapView.getContext(),
				symbolMap,
				mapFragment.getActivity().getContentResolver(),
				promise
			);

			ItemizedLayer markerLayer = new ItemizedLayer(
				mapView.map(),
				new ArrayList<MarkerInterface>(),
				symbol,
				listener
			);

			// Store layer
			String uuid = UUID.randomUUID().toString();
			layers.put( uuid, markerLayer );

			// Add layer to map
			mapView.map().layers().add(
				Math.min( mapView.map().layers().size(), (int) reactTreeIndex ),
				markerLayer
			);
			mapView.map().clearMap();

			// Resolve uuid
			responseParams.putString( "uuid", uuid );
            promise.resolve( responseParams );
        } catch(Exception e) {
			e.printStackTrace();
            promise.reject( "Error", e );
        }
    }

	@ReactMethod
	public void createMarker(
		int nativeNodeHandle,
		String layerUuid,
		ReadableMap position,
		String title,
		String description,
		@Nullable ReadableMap symbolMap,
		Promise promise
	) {
		MapFragment mapFragment = Utils.getMapFragment( this.getReactApplicationContext(), nativeNodeHandle );
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( mapFragment == null || null == mapView ) {
			promise.reject( "Error", "Unable to find mapView or mapFragment" ); return;
		}
		ItemizedLayer markerLayer = layers.get( layerUuid );
		if ( markerLayer == null ) {
			promise.reject( "Error", "Unable to find markerLayer" ); return;
		}
		// The promise response
		WritableMap responseParams = new WritableNativeMap();
		// Create Marker.
		String uuid = UUID.randomUUID().toString();
		MarkerItem markerItem = new MarkerItem(
			uuid,
			title,
			description,
			new GeoPoint(
				position.getDouble( "lat" ),
				position.getDouble( "lng" )
			)
		);
		// Maybe get symbol.
		if ( null != symbolMap ) {
			MarkerSymbol symbol = getMarkerSymbol(
				mapView.getContext(),
				symbolMap,
				mapFragment.getActivity().getContentResolver(),
				promise
			);
			markerItem.setMarker( symbol );
		}
		// Add index to response.
		responseParams.putInt( "index", markerLayer.getItemList().size() );
		// Add marker to markerLayer.
		markerLayer.addItem( markerItem );
		// Store marker
		markers.put( uuid, markerItem );
		// Update map.
		mapView.map().clearMap();
		// Resolve uuid
		responseParams.putString( "uuid", uuid );
		promise.resolve( responseParams );
	}

	protected MarkerSymbol getMarkerSymbol( Context context, @Nullable ReadableMap symbolMap, ContentResolver contentResolver, Promise promise ) {
		MarkerSymbol.HotspotPlace hotspotPlace = MarkerSymbol.HotspotPlace.CENTER;
		if ( null != symbolMap && symbolMap.hasKey( "hotspotPlace" ) ) {
			hotspotPlace = switch ( symbolMap.getString("hotspotPlace") ) {
				case "NONE" -> MarkerSymbol.HotspotPlace.NONE;
				case "CENTER" -> MarkerSymbol.HotspotPlace.CENTER;
				case "BOTTOM_CENTER" -> MarkerSymbol.HotspotPlace.BOTTOM_CENTER;
				case "TOP_CENTER" -> MarkerSymbol.HotspotPlace.TOP_CENTER;
				case "RIGHT_CENTER" -> MarkerSymbol.HotspotPlace.RIGHT_CENTER;
				case "LEFT_CENTER" -> MarkerSymbol.HotspotPlace.LEFT_CENTER;
				case "UPPER_RIGHT_CORNER" -> MarkerSymbol.HotspotPlace.UPPER_RIGHT_CORNER;
				case "LOWER_RIGHT_CORNER" -> MarkerSymbol.HotspotPlace.LOWER_RIGHT_CORNER;
				case "UPPER_LEFT_CORNER" -> MarkerSymbol.HotspotPlace.UPPER_LEFT_CORNER;
				case "LOWER_LEFT_CORNER" -> MarkerSymbol.HotspotPlace.LOWER_LEFT_CORNER;
				default -> hotspotPlace;
			};
		}
		return new MarkerSymbol(
			getMarkerBitmap(
				context,
				symbolMap,
				contentResolver,
				promise
			),
			hotspotPlace,
			false
		);
	}

	protected Bitmap getMarkerBitmap(
		Context context,
		@Nullable ReadableMap symbolMap,
		ContentResolver contentResolver,
		Promise promise
	) {
		int width = 30;
		int height = 30;
		int textWidth = 0;
		int textHeight = 0;
		String fillColor = null;
		String strokeColor = null;
		int strokeWidth = 5;
		Bitmap bitmap = null;
		String text = null;
		String textColor = "#111111";
		int textMargin = 10;
		Paint textPainter = null;
		if ( null != symbolMap ) {
			width = symbolMap.hasKey( "width" ) ? symbolMap.getInt( "width" ) : width;
			height = symbolMap.hasKey( "height" ) ? symbolMap.getInt( "height" ) : height;
			if ( symbolMap.hasKey( "text" ) ) {
				text = symbolMap.getString( "text");
				textColor = symbolMap.hasKey( "textColor" ) ? symbolMap.getString( "textColor") : textColor;
				textMargin = symbolMap.hasKey( "textMargin" ) ? symbolMap.getInt( "textMargin") : textMargin;
				textPainter = CanvasAdapter.newPaint();
				textPainter.setStyle( Paint.Style.STROKE );
				textPainter.setStrokeWidth( symbolMap.hasKey( "textStrokeWidth" ) ? symbolMap.getInt( "textStrokeWidth" ) : 3 );
				textPainter.setTextSize( 30 );
				textPainter.setColor( Color.parseColor( textColor ) );
				textWidth = ( (int) textPainter.getTextWidth( text ) + 2 * textMargin );
				textHeight = ( (int) textPainter.getTextHeight( text ) + 2 * textMargin );
				width = Math.max( textWidth, width );
				height = Math.max( textHeight, height );
			}
			fillColor = symbolMap.hasKey( "fillColor" ) ? symbolMap.getString( "fillColor") : fillColor;
			fillColor = fillColor != null && fillColor.startsWith( "#" ) ? fillColor : null;
			strokeColor = symbolMap.hasKey( "strokeColor" ) ? symbolMap.getString( "strokeColor") : strokeColor;
			strokeColor = strokeColor != null && strokeColor.startsWith( "#" ) ? strokeColor : null;
			strokeWidth = symbolMap.hasKey( "strokeWidth" ) ? symbolMap.getInt( "strokeWidth") : strokeWidth;
			bitmap = symbolMap.hasKey( "filePath" ) ? loadMarkerImage(
				context,
				symbolMap.getString( "filePath" ),
				width,
				height,
				contentResolver,
				promise
			) : bitmap;
		}

		Bitmap bitmapPoi = CanvasAdapter.newBitmap( width, height, 0 );
		Canvas markerCanvas = CanvasAdapter.newCanvas();
		markerCanvas.setBitmap( bitmapPoi );

		if ( null != bitmap ) {
			markerCanvas.drawBitmapScaled( bitmap );
		}
		if ( null != fillColor ) {
			markerCanvasDrawCircle( markerCanvas, width, height, fillColor, Paint.Style.FILL,null );
		}
		if ( null != strokeColor ) {
			markerCanvasDrawCircle( markerCanvas, width, height, strokeColor, Paint.Style.STROKE, strokeWidth );
		}
		// Fallback
		if ( null == bitmap && fillColor == null && strokeColor == null ){
			markerCanvasDrawCircle( markerCanvas, width, height,"#ff0000", Paint.Style.FILL, null );
			markerCanvasDrawCircle( markerCanvas, width, height, "#000000", Paint.Style.STROKE, strokeWidth );
		}
		// text
		if ( text != null ) {
			Bitmap textBitmap = CanvasAdapter.newBitmap(textWidth + textMargin, textHeight + textMargin, 0 );
			Canvas textCanvas = CanvasAdapter.newCanvas();
			textCanvas.setBitmap( textBitmap );
			textCanvas.drawText( text, textMargin, textHeight - textMargin, textPainter );
			float textPositionX = symbolMap.hasKey( "textPositionX" )
				? (float) symbolMap.getDouble( "textPositionX" )
				: width * 0.5f - ( textWidth * 0.5f );
			float textPositionY = symbolMap.hasKey( "textPositionY" )
				? (float) symbolMap.getDouble( "textPositionY" )
				: 0;
			markerCanvas.drawBitmap( textBitmap, textPositionX, textPositionY );
		}
		return bitmapPoi;
	}

	protected Bitmap loadMarkerImage( Context context, String filePath, int width, int height, ContentResolver contentResolver, Promise promise ) {
		Bitmap bitmap = null;
		if ( ! filePath.isEmpty() ) {
			FileInputStream fis = null;
			try {
				if ( filePath.startsWith( "content://" ) ) {
					Uri fileUri= Uri.parse( filePath );
					DocumentFile dir = DocumentFile.fromSingleUri(context, fileUri );
					if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
						promise.reject( "Error", "filePath does not exist or is not a file. " + filePath ); return null;
					}
					if ( ! Utils.hasScopedStoragePermission( context, filePath, false ) ) {
						promise.reject( "Error", "No scoped storage read permission for filePath" + filePath ); return null;
					}
					fis = ( FileInputStream ) contentResolver.openInputStream( fileUri );
				} else if ( filePath.startsWith( "/" ) ) {
					File file = new File( filePath );
					if( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
						promise.reject( "Error", "file does not exist or is not a file. " + filePath ); return null;
					}
					fis = new FileInputStream( file );
				}
				if ( fis == null ) {
					promise.reject( "Error", "Unable to load mapFile: " + filePath ); return null;
				} else {
					bitmap = filePath.endsWith( ".svg" )
						? CanvasAdapter.decodeSvgBitmap( fis, width, height, 100 )
						: CanvasAdapter.decodeBitmap( fis );
				}
			} catch ( IOException e ) {
				e.printStackTrace();
				promise.reject( "Error", "Unable to read file" + filePath ); return null;
			}
		}
		return bitmap;
	}

	protected void markerCanvasDrawCircle(
		Canvas markerCanvas,
		float width,
		float height,
		String color,
		Paint.Style style,
		@Nullable Integer strokeWith
	) {
		final Paint painter = CanvasAdapter.newPaint();
		painter.setStyle( style );
		painter.setColor( Color.parseColor( color ) );
		if ( null != strokeWith ) {
			painter.setStrokeWidth( strokeWith );
		} else {
			strokeWith = 0;
		}
		markerCanvas.drawCircle(
			width * 0.5f,
			height * 0.5f,
			( (float) ( ( width - strokeWith ) + ( height - strokeWith ) ) / 2 ) * 0.5f,
			painter
		);
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
		ItemizedLayer markerLayer = layers.get( layerUuid );
		if ( markerLayer == null ) {
			promise.reject( "Error", "Unable to find markerLayer" ); return;
		}

		WritableMap params = new WritableNativeMap();

		int size = markerLayer.getItemList().size();
		if (size == 0)
			return;

		int eventX = (int) x - mapView.map().getWidth() / 2;
		int eventY = (int) y - mapView.map().getHeight() / 2;
		Viewport mapPosition = mapView.map().viewport();

		Box box = mapPosition.getBBox(null, Tile.SIZE / 2);
		box.map2mercator();
		box.scale(1E6);

		int inside = -1;

		// squared dist: 50x50 px ~ 2mm on 400dpi
		// 20x20 px on baseline mdpi (160dpi)
		double dist = (20 * CanvasAdapter.getScale()) * (20 * CanvasAdapter.getScale());

		for (int i = 0; i < size; i++) {
			if ( inside >= 0 ) {
				continue;
			}
			MarkerInterface item = markerLayer.getItemList().get(i);

			if (!box.contains(item.getPoint().longitudeE6,
				item.getPoint().latitudeE6))
				continue;

			mapPosition.toScreenPoint(item.getPoint(), mTmpPoint);

			float dx = (float) (eventX - mTmpPoint.x);
			float dy = (float) (eventY - mTmpPoint.y);

			MarkerSymbol it = item.getMarker();
			if (it == null)
				return;

			if (it.isInside(dx, dy)) {
				inside = i;
				MarkerItem markerItem = (MarkerItem) item;
				WritableMap markerParams = new WritableNativeMap();
				markerParams.putString( "uuid", markerItem.getUid().toString() );
				markerParams.putInt( "index", i );
				params = markerParams.copy();
				Utils.sendEvent(  mapFragment.getReactContext(), "MarkerItemTriggerEvent", markerParams );
			}
		}
		promise.resolve( params );
	}

	@ReactMethod
	public void removeMarker(
		int nativeNodeHandle,
		String layerUuid,
		String markerUuid,
		Promise promise
	) {
		MapView mapView = (MapView) Utils.getMapView( this.getReactApplicationContext(), nativeNodeHandle );
		if ( null == mapView ) {
			promise.reject( "Error", "Unable to find mapView" ); return;
		}
		ItemizedLayer markerLayer = layers.get( layerUuid );
		if ( markerLayer == null ) {
			promise.reject( "Error", "Unable to find markerLayer" ); return;
		}
		MarkerInterface marker = markers.get( markerUuid );
		if ( marker == null ) {
			promise.reject( "Error", "Unable to find marker" ); return;
		}
		// Remove marker from markerLayer.
		markerLayer.removeItem( marker );
		// Remove marker from markers.
		markers.remove( markerUuid );
		// Update map.
		mapView.map().clearMap();
		// Resolve uuid
		promise.resolve( markerUuid );
	}

    @ReactMethod
    public void removeLayer(int nativeNodeHandle, String uuid, Promise promise) {
		ItemizedLayer markerLayer = layers.get( uuid );
		if ( null != markerLayer ) {
			for ( MarkerInterface mi : markerLayer.getItemList() ) {
				MarkerItem markerItem = (MarkerItem) mi;
				markers.remove( markerItem.getUid().toString() );
			}
		}
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
