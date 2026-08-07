package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.ItemizedLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.MarkerLayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeLayerMarkerSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ReactModule( name = LayerMarker.NAME )
public class LayerMarker extends NativeLayerMarkerSpec {

	public static final String NAME = "LayerMarker";
	private static final String TAG = "LayerMarker";



	// Rendering a marker symbol's Bitmap (canvas allocation, text measurement/drawing, image
	// decode) is the expensive part of creating a marker -- and many markers/layers very often
	// share an identical symbol definition (e.g. every marker using the same default symbol), so
	// it's cached here keyed by the symbol's fully-resolved drawing params. Static/shared across
	// all LayerMarker instances since the same symbol definition is just as reusable across
	// different maps/layers as within one.
	private static final Map<MarkerBitmapParams, Bitmap> bitmapCache = new ConcurrentHashMap<>();

	// Every param getMarkerBitmap actually reads, after defaulting -- used both as the bitmap
	// cache key and to drive the actual drawing, so the two can never drift apart independently.
	// textPositionX/textPositionY are null when neither symbolMap nor symbolConstants provide
	// one, meaning getMarkerBitmap derives a default from the other (already-keyed) params.
	protected static final class MarkerBitmapParams {
		final int width;
		final int height;
		final String fillColor;
		final String strokeColor;
		final String text;
		final String filePath;
		final int strokeWidth;
		final int textMargin;
		final String textColor;
		final int textSize;
		final String fontFamily;
		final String fontStyle;
		final Float textPositionX;
		final Float textPositionY;

		MarkerBitmapParams(
			int width, int height, String fillColor, String strokeColor, String text, String filePath,
			int strokeWidth, int textMargin, String textColor, int textSize, String fontFamily,
			String fontStyle, Float textPositionX, Float textPositionY
		) {
			this.width = width;
			this.height = height;
			this.fillColor = fillColor;
			this.strokeColor = strokeColor;
			this.text = text;
			this.filePath = filePath;
			this.strokeWidth = strokeWidth;
			this.textMargin = textMargin;
			this.textColor = textColor;
			this.textSize = textSize;
			this.fontFamily = fontFamily;
			this.fontStyle = fontStyle;
			this.textPositionX = textPositionX;
			this.textPositionY = textPositionY;
		}

		@Override
		public boolean equals( Object o ) {
			if ( this == o ) { return true; }
			if ( ! ( o instanceof MarkerBitmapParams ) ) { return false; }
			MarkerBitmapParams that = (MarkerBitmapParams) o;
			return width == that.width
				&& height == that.height
				&& strokeWidth == that.strokeWidth
				&& textMargin == that.textMargin
				&& textSize == that.textSize
				&& Objects.equals( fillColor, that.fillColor )
				&& Objects.equals( strokeColor, that.strokeColor )
				&& Objects.equals( text, that.text )
				&& Objects.equals( filePath, that.filePath )
				&& Objects.equals( textColor, that.textColor )
				&& Objects.equals( fontFamily, that.fontFamily )
				&& Objects.equals( fontStyle, that.fontStyle )
				&& Objects.equals( textPositionX, that.textPositionX )
				&& Objects.equals( textPositionY, that.textPositionY );
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				width, height, fillColor, strokeColor, text, filePath, strokeWidth, textMargin,
				textColor, textSize, fontFamily, fontStyle, textPositionX, textPositionY
			);
		}
	}

	public LayerMarker( ReactApplicationContext reactContext) {
		super(reactContext);
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected Map<String, Object> getTypedExportedConstants() {
		final Map<String, Object> constants = new HashMap<>();
		// For layer and marker.
		WritableMap symbol = new WritableNativeMap();
		symbol.putDouble( "width", 30 );
		symbol.putDouble( "height", 30 );
		symbol.putString( "filePath", null );
		symbol.putString( "fillColor", null );
		symbol.putString( "strokeColor", null );
		symbol.putInt( "strokeWidth", 5 );
		symbol.putString( "hotspotPlace", "CENTER" );
		symbol.putString( "text", null );
		symbol.putInt( "textMargin", 10 );
		symbol.putNull( "textPositionX" );
		symbol.putNull( "textPositionY" );
		symbol.putString( "textColor", "#111111" );
		symbol.putInt( "textSize", 30 );
		symbol.putString( "fontFamily", "DEFAULT" );
		symbol.putString( "fontStyle", "NORMAL" );
		constants.put( "paint", symbol );
		// For marker.
		constants.put( "title", "" );
		constants.put( "description", "" );
		constants.put( "position", null );
		// For event.
		constants.put( "strategy", "nearest" );
		return constants;
	}

	@Override
	public void triggerEvent( ReadableMap params ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
			return;
		}
		int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
		if ( ! Utils.rMapHasKey( params, "markerLayerUuid" ) ) {
			return;
		}
		String groupUuid = params.getString( "markerLayerUuid" );
		if ( ! Utils.rMapHasKey( params, "x" ) || ! Utils.rMapHasKey( params, "y" ) ) {
			return;
		}
		String strategy = Utils.rMapHasKey( params, "strategy" ) ? params.getString( "strategy" ) : (String) getConstants().get( "strategy" );
		float x = (float) params.getDouble( "x" );
		float y = (float) params.getDouble( "y" );

		MarkerLayerManager manager = MarkerLayerManager.getInstance( nativeNodeHandle );
		if ( manager == null ) {
			return;
		}
		WritableMap payload = manager.triggerGroupEvent( groupUuid, x, y, strategy );
		// TEST: Always emit to verify emitOnMarkerEvent path works
		{ WritableMap t = Arguments.createMap(); t.putString("uuid", "test-uuid"); t.putInt("index", -1); t.putString("markerLayerUuid", groupUuid); t.putString("event", "itemTrigger"); t.putInt("nativeNodeHandle", nativeNodeHandle); emitOnMarkerEvent(t); }
		if ( payload != null ) { emitOnMarkerEvent( payload ); }
	}

	@Override
	public void createLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseReject( promise,"Unable to find mapView or mapFragment" ); return;
			}

			// Resolve the default marker symbol for this group (may be null).
			ReadableMap symbolMap = Utils.rMapHasKey( params, "paint" ) ? params.getMap( "paint" ) : (ReadableMap) getConstants().get( "paint" );
			MarkerSymbol defaultSymbol = getMarkerSymbol( symbolMap, mapFragment.getActivity().getContentResolver() );

			// Delegate group creation to MarkerLayerManager.
			MarkerLayerManager manager = MarkerLayerManager.get( params.getInt( "nativeNodeHandle" ), mapView );
			manager.setEventCallback(( eventName, payload ) -> {
				if ( "onMarkerEvent".equals( eventName ) ) {
					emitOnMarkerEvent( payload );
				}
			});

			String groupUuid = manager.createGroup( defaultSymbol, params );
			promise.resolve( groupUuid );
		} catch ( Exception e ) {
			e.printStackTrace();
			emitError( e.getMessage() );
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void createMarker( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseReject( promise,"Unable to find mapView or mapFragment" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "position" ) ) {
				Utils.promiseReject( promise,"Marker does not have a position" ); return;
			}

			MarkerLayerManager manager = MarkerLayerManager.get( nativeNodeHandle, mapView );
			manager.setEventCallback(( eventName, payload ) -> {
				if ( "onMarkerEvent".equals( eventName ) ) {
					emitOnMarkerEvent( payload );
				}
			});

			// Resolve the marker's symbol.
			MarkerSymbol symbol = Utils.rMapHasKey( params, "paint" )
				? getMarkerSymbol( params.getMap( "paint" ), mapFragment.getActivity().getContentResolver() )
				: null;

			// Resolve fragment uuid for this marker.
			String fragmentUuid = Utils.rMapHasKey( params, "fragmentUuid" )
				? params.getString( "fragmentUuid" )
				: "__vtm_shared_marker__0";

			// Create the marker entry via the manager.
			String markerUuid = java.util.UUID.randomUUID().toString();
			LayerManager.CreateResult<MarkerLayerManager.MarkerEntry> result = manager.create(
				markerUuid,
				fragmentUuid,
				params,
				mapFragment,
				mapFragment.getActivity().getContentResolver(),
				getReactApplicationContext()
			);

			// Set the resolved symbol on the marker item.
			if ( symbol != null ) {
				manager.setMarkerSymbol( markerUuid, symbol );
			}

			// The manager's response data already has uuid and index.
			WritableMap responseParams = result.responseData != null
				? result.responseData
				: new WritableNativeMap();
			if ( !responseParams.hasKey( "uuid" ) ) {
				responseParams.putString( "uuid", markerUuid );
			}
			promise.resolve( responseParams );
		} catch ( Exception e ) {
			e.printStackTrace();
			emitError( e.getMessage() );
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void createMarkers( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "markers" ) ) {
				Utils.promiseReject( promise,"Undefined markers array" ); return;
			}

			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView(
				getReactApplicationContext(), nativeNodeHandle );
			MapFragment mapFragment = Utils.getMapFragment(
				getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseReject( promise,
					"Unable to find mapView or mapFragment" ); return;
			}

			MarkerLayerManager manager = MarkerLayerManager.get(
				nativeNodeHandle, mapView );
			manager.setEventCallback(( eventName, payload ) -> {
				if ( "onMarkerEvent".equals( eventName ) ) {
					emitOnMarkerEvent( payload );
				}
			});

			// Resolve all per-marker symbols upfront so the bitmapCache
			// deduplicates identical symbol definitions across the batch.
			ReadableArray markersArray = params.getArray( "markers" );
			int count = markersArray.size();
			Map<Integer, MarkerSymbol> resolvedSymbols = new HashMap<>( count );

			for ( int i = 0; i < count; i++ ) {
				ReadableMap markerParams = markersArray.getMap( i );
				if ( Utils.rMapHasKey( markerParams, "paint" ) ) {
					MarkerSymbol symbol = getMarkerSymbol(
						markerParams.getMap( "paint" ),
						mapFragment.getActivity().getContentResolver()
					);
					resolvedSymbols.put( i, symbol );
				}
			}

			// Delegate bulk creation to MarkerLayerManager.
			WritableMap response = manager.createMarkers(
				markersArray,
				mapFragment,
				mapFragment.getActivity().getContentResolver(),
				getReactApplicationContext(),
				resolvedSymbols
			);

			promise.resolve( response );
		} catch ( Exception e ) {
			e.printStackTrace();
			emitError( e.getMessage() );
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeMarker( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "uuid" ) ) {
				Utils.promiseReject( promise,"Undefined uuid" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );

			MarkerLayerManager manager = MarkerLayerManager.getInstance( nativeNodeHandle );
			if ( manager != null ) {
				manager.remove( params.getString( "uuid" ) );
			}
			// Resolve successfully even if the manager is gone (map already destroyed).
			promise.resolve( params.getString( "uuid" ) );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void removeMarkers( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "markerUuids" ) ) {
				Utils.promiseReject( promise,"Undefined markerUuids array" ); return;
			}

			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			ReadableArray markerUuids = params.getArray( "markerUuids" );

			MarkerLayerManager manager = MarkerLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				// Map already destroyed — resolve with empty results.
				WritableMap response = Arguments.createMap();
				WritableArray results = Arguments.createArray();
				for ( int i = 0; i < markerUuids.size(); i++ ) {
					WritableMap item = Arguments.createMap();
					item.putString( "uuid", markerUuids.getString( i ) );
					results.pushMap( item );
				}
				response.putArray( "results", results );
				promise.resolve( response );
				return;
			}

			WritableMap response = manager.removeMarkers( markerUuids );
			promise.resolve( response );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void updateLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseReject( promise,"Unable to find mapView or mapFragment" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "uuid" ) ) {
				Utils.promiseReject( promise,"Undefined uuid" ); return;
			}
			String groupUuid = params.getString( "uuid" );

			// Resolve the new default symbol.
			MarkerSymbol newDefault = Utils.rMapHasKey( params, "paint" )
				? getMarkerSymbol( params.getMap( "paint" ), mapFragment.getActivity().getContentResolver() )
				: null;

			MarkerLayerManager manager = MarkerLayerManager.get( nativeNodeHandle, mapView );
			manager.updateGroup( groupUuid, newDefault );

			promise.resolve( groupUuid );
		} catch( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void updateMarker( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			MapFragment mapFragment = Utils.getMapFragment( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView || null == mapFragment ) {
				Utils.promiseReject( promise,"Unable to find mapView or mapFragment" ); return;
			}
			if ( ! Utils.rMapHasKey( params, "uuid" ) ) {
				Utils.promiseReject( promise,"Undefined uuid" ); return;
			}
			String uuid = params.getString( "uuid" );

			MarkerLayerManager manager = MarkerLayerManager.get( nativeNodeHandle, mapView );

			// If symbol changed, resolve and set it.
			if ( Utils.rMapHasKey( params, "paint" ) ) {
				MarkerSymbol symbol = getMarkerSymbol(
					params.getMap( "paint" ),
					mapFragment.getActivity().getContentResolver()
				);
				manager.setMarkerSymbol( uuid, symbol );
			}

			// If position changed, update it via the manager.
			if ( Utils.rMapHasKey( params, "position" ) ) {
				manager.update( uuid, params, mapFragment, mapFragment.getActivity().getContentResolver() );
			}

			promise.resolve( uuid );
		} catch( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	protected MarkerSymbol getMarkerSymbol( ReadableMap symbolMap, ContentResolver contentResolver ) {
		// Get hotspotPlace.
		ReadableMap symbolConstants = (ReadableMap) getConstants().get( "paint" );
		String hotspotPlaceString = Utils.rMapHasKey( symbolMap, "hotspotPlace" ) ? symbolMap.getString( "hotspotPlace" ) : symbolConstants.getString( "hotspotPlace" );
		MarkerSymbol.HotspotPlace hotspotPlace = switch ( hotspotPlaceString ) {
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
			default -> MarkerSymbol.HotspotPlace.CENTER;
		};
		// Bitmap rendering (canvas allocation, text measurement/drawing, image decode) is the
		// expensive part and is purely a function of the resolved symbol params, so it's cached
		// -- markers/layers sharing an identical symbol definition (very common, e.g. a shared
		// default symbol) only pay for it once.
		MarkerBitmapParams params = resolveMarkerBitmapParams( symbolMap );
		Bitmap bitmap = bitmapCache.computeIfAbsent(
			params,
			p -> getMarkerBitmap( p, contentResolver )
		);
		return new MarkerSymbol( bitmap, hotspotPlace, false );
	}

	protected MarkerBitmapParams resolveMarkerBitmapParams( ReadableMap symbolMap ) {
		ReadableMap symbolConstants = (ReadableMap) getConstants().get( "paint" );
		int width = Utils.rMapHasKey( symbolMap, "width" ) ? symbolMap.getInt( "width" ) : symbolConstants.getInt( "width" );
		int height = Utils.rMapHasKey( symbolMap, "height" ) ? symbolMap.getInt( "height" ) : symbolConstants.getInt( "height" );
		String fillColor = Utils.rMapHasKey( symbolMap, "fillColor" ) ? symbolMap.getString( "fillColor" ) : symbolConstants.getString( "fillColor" );
		String strokeColor = Utils.rMapHasKey( symbolMap, "strokeColor" ) ? symbolMap.getString( "strokeColor" ) : symbolConstants.getString( "strokeColor" );
		String text = Utils.rMapHasKey( symbolMap, "text" ) ? symbolMap.getString( "text" ) : symbolConstants.getString( "text" );
		String filePath = Utils.rMapHasKey( symbolMap, "filePath" ) ? symbolMap.getString( "filePath" ) : symbolConstants.getString( "filePath" );
		int strokeWidth = Utils.rMapHasKey( symbolMap, "strokeWidth" ) ? symbolMap.getInt( "strokeWidth" ) : symbolConstants.getInt( "strokeWidth" );
		int textMargin = Utils.rMapHasKey( symbolMap, "textMargin" ) ? symbolMap.getInt( "textMargin" ) : symbolConstants.getInt( "textMargin" );
		String textColor = Utils.rMapHasKey( symbolMap, "textColor" ) ? symbolMap.getString( "textColor" ) : symbolConstants.getString( "textColor" );
		int textSize = Utils.rMapHasKey( symbolMap, "textSize" ) ? symbolMap.getInt( "textSize" ) : symbolConstants.getInt( "textSize" );
		String fontFamily = Utils.rMapHasKey( symbolMap, "fontFamily" ) ? symbolMap.getString( "fontFamily" ) : symbolConstants.getString( "fontFamily" );
		String fontStyle = Utils.rMapHasKey( symbolMap, "fontStyle" ) ? symbolMap.getString( "fontStyle" ) : symbolConstants.getString( "fontStyle" );
		// Plain if/else, not a nested ternary: a ternary mixing a primitive float branch with a
		// null branch forces javac to unify the expression's type as primitive float, unboxing
		// the *other* branch's boxed Float unconditionally as part of evaluating that type -- so
		// a `cond ? (float) x : (cond2 ? (float) y : null)` blows up with an NPE the instant the
		// innermost null is selected, even though it's never actually unboxed by hand here.
		Float textPositionX;
		if ( Utils.rMapHasKey( symbolMap, "textPositionX" ) ) {
			textPositionX = (float) symbolMap.getDouble( "textPositionX" );
		} else if ( Utils.rMapHasKey( symbolConstants, "textPositionX" ) ) {
			textPositionX = (float) symbolConstants.getDouble( "textPositionX" );
		} else {
			textPositionX = null;
		}
		Float textPositionY;
		if ( Utils.rMapHasKey( symbolMap, "textPositionY" ) ) {
			textPositionY = (float) symbolMap.getDouble( "textPositionY" );
		} else if ( Utils.rMapHasKey( symbolConstants, "textPositionY" ) ) {
			textPositionY = (float) symbolConstants.getDouble( "textPositionY" );
		} else {
			textPositionY = null;
		}
		return new MarkerBitmapParams(
			width, height, fillColor, strokeColor, text, filePath, strokeWidth, textMargin,
			textColor, textSize, fontFamily, fontStyle, textPositionX, textPositionY
		);
	}

	protected Paint.FontFamily getFontFamily( String fontFamily ) {
		return switch ( fontFamily ) {
			case "DEFAULT" -> Paint.FontFamily.DEFAULT;
			case "DEFAULT_BOLD" -> Paint.FontFamily.DEFAULT_BOLD;
			case "MONOSPACE" -> Paint.FontFamily.MONOSPACE;
			case "SANS_SERIF" -> Paint.FontFamily.SANS_SERIF;
			case "SERIF" -> Paint.FontFamily.SERIF;
			case "THIN" -> Paint.FontFamily.THIN;
			case "LIGHT" -> Paint.FontFamily.LIGHT;
			case "MEDIUM" -> Paint.FontFamily.MEDIUM;
			case "BLACK" -> Paint.FontFamily.BLACK;
			case "CONDENSED" -> Paint.FontFamily.CONDENSED;
			default -> Paint.FontFamily.DEFAULT;
		};
	}

	protected Paint.FontStyle getFontStyle( String fontStyle ) {
		return switch ( fontStyle ) {
			case "BOLD" -> Paint.FontStyle.BOLD;
			case "BOLD_ITALIC" -> Paint.FontStyle.BOLD_ITALIC;
			case "ITALIC" -> Paint.FontStyle.ITALIC;
			case "NORMAL" -> Paint.FontStyle.NORMAL;
			default -> Paint.FontStyle.NORMAL;
		};
	}


	protected Bitmap getMarkerBitmap(
		MarkerBitmapParams params,
		ContentResolver contentResolver
	) {
		int width = params.width;
		int height = params.height;
		String fillColor = params.fillColor;
		String strokeColor = params.strokeColor;
		String text = params.text;
		String filePath = params.filePath;
		int strokeWidth = params.strokeWidth;
		int textMargin = params.textMargin;

		// If text, setup text painter and adjust width and height.
		int textWidth = 0;
		int textHeight = 0;
		Paint textPainter = null;
		if ( null != text ) {
			// Setup textPainter.
			textPainter = CanvasAdapter.newPaint();
			textPainter.setStyle( Paint.Style.FILL );
			textPainter.setTextSize( params.textSize );
			textPainter.setTypeface(
				getFontFamily( params.fontFamily ),
				getFontStyle( params.fontStyle )
			);
			textPainter.setColor( Color.parseColor( params.textColor ) );
			// Setup text dimensions and adjust width and height to fit text.
			textWidth = ( (int) textPainter.getTextWidth( text ) + 2 * textMargin );
			textHeight = ( (int) textPainter.getTextHeight( text ) + 2 * textMargin );
			width = Math.max( textWidth, width );
			height = Math.max( textHeight, height );
		}

		Bitmap imageBitmap = loadImageBitmap(
			filePath,
			width,
			height,
			contentResolver
		);

		Bitmap markerBitmap = CanvasAdapter.newBitmap( width, height, 0 );
		Canvas markerCanvas = CanvasAdapter.newCanvas();
		markerCanvas.setBitmap( markerBitmap );

		if ( null != imageBitmap ) {
			markerCanvas.drawBitmapScaled( imageBitmap );
		}
		if ( null != fillColor && fillColor.startsWith( "#" ) ) {
			markerCanvasDrawCircle( markerCanvas, width, height, fillColor, Paint.Style.FILL,null );
		}
		if ( null != strokeColor && strokeColor.startsWith( "#" ) ) {
			markerCanvasDrawCircle( markerCanvas, width, height, strokeColor, Paint.Style.STROKE, strokeWidth );
		}
		// Fallback
		if ( null == imageBitmap && fillColor == null && ( strokeColor == null || ! strokeColor.startsWith( "#" ) ) ){
			markerCanvasDrawCircle( markerCanvas, width, height,"#ff0000", Paint.Style.FILL, null );
			markerCanvasDrawCircle( markerCanvas, width, height, "#000000", Paint.Style.STROKE, strokeWidth );
		}
		// Draw text.
		if ( text != null ) {
			Bitmap textBitmap = CanvasAdapter.newBitmap(textWidth + textMargin, textHeight + textMargin, 0 );
			Canvas textCanvas = CanvasAdapter.newCanvas();
			textCanvas.setBitmap( textBitmap );
			textCanvas.drawText( text, textMargin, textHeight - textMargin, textPainter );
			float textPositionX = null != params.textPositionX ? params.textPositionX : width * 0.5f - ( textWidth * 0.5f );
			float textPositionY = null != params.textPositionY ? params.textPositionY : 0;
			markerCanvas.drawBitmap( textBitmap, textPositionX, textPositionY );
		}
		return markerBitmap;
	}

	protected Bitmap loadImageBitmap( String filePath, int width, int height, ContentResolver contentResolver ) {
		Bitmap bitmap = null;
		if ( null != filePath && ! filePath.isEmpty() ) {
			FileInputStream fis = getFileInputStream( filePath, contentResolver );
			if ( fis != null ) {
				try ( fis ) {
					bitmap = filePath.endsWith( ".svg" )
						? CanvasAdapter.decodeSvgBitmap( fis, width, height, 100 )
						: CanvasAdapter.decodeBitmap( fis );
				} catch ( IOException e ) {
					e.printStackTrace();
					emitError( "Unable to read file: " + filePath );
				}
			}
		}
		return bitmap;
	}

	protected FileInputStream getFileInputStream( String filePath, ContentResolver contentResolver ) {
		FileInputStream fis = null;
		try {
			if ( null != filePath && ! filePath.isEmpty() ) {
				if ( filePath.startsWith( "content://" ) ) {
					Uri fileUri = Uri.parse( filePath );
					DocumentFile dir = DocumentFile.fromSingleUri( getReactApplicationContext(), fileUri );
					if ( dir == null || ! dir.exists() || ! dir.isFile() ) {
						emitError( "filePath does not exist or is not a file: " + filePath );
					} else {
						if ( ! Utils.hasScopedStoragePermission( getReactApplicationContext(), filePath, false ) ) {
							emitError( "No scoped storage read permission for filePath: " + filePath );
						} else {
							fis = ( FileInputStream ) contentResolver.openInputStream( fileUri );
						}
					}
				} else if ( filePath.startsWith( "/" ) ) {
					File file = new File( filePath );
					if( ! file.exists() || ! file.isFile() || ! file.canRead() ) {
						emitError( "File does not exist or is not a file: " + filePath );
					}
					fis = new FileInputStream( file );
				}
			}
		} catch ( IOException e ) {
			e.printStackTrace();
			emitError( "Unable to read file: " + filePath );
		}
		return fis;
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

	@Override
	public void removeLayer( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "uuid" ) || ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise,"Undefined uuid or nativeNodeHandle" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			String groupUuid = params.getString( "uuid" );

			MarkerLayerManager manager = MarkerLayerManager.getInstance( nativeNodeHandle );
			if ( manager == null ) {
				promise.resolve( groupUuid );
				return;
			}
			manager.removeGroup( groupUuid );
			promise.resolve( groupUuid );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	public void emitMarkerEvent( ReadableMap payload ) {
		emitOnMarkerEvent( payload );
	}

	protected void emitError( String errorMsg ) {
		WritableMap payload = Arguments.createMap();
		payload.putString( "errorMsg", errorMsg );
		emitOnError( payload );
	}

}

