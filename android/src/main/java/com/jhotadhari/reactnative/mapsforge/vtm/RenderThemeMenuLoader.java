package com.jhotadhari.reactnative.mapsforge.vtm;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import org.oscim.backend.XMLReaderAdapter;
import org.oscim.theme.SAXTerminationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cheaply reads just the <stylemenu> block of a mapsforge/vtm XML render theme, without running
 * vtm's full XmlThemeBuilder -- which would compile every rendering rule in the file just to
 * expose this menu metadata, and which org.oscim.theme.ThemeLoader.load() has no way to skip.
 * Mirrors the early-exit-via-exception trick vtm's own ThemeUtils.isMapsforgeTheme() uses: a
 * minimal SAX handler that throws SAXTerminationException the instant </stylemenu> closes, before
 * the (expensive) rule/style elements that make up the rest of the file are ever reached.
 *
 * Results are cached per path, invalidated by last-modified, since the same theme is typically
 * queried repeatedly (once per consumer of useRenderStyleOptions, independent of layer creation).
 */
public class RenderThemeMenuLoader {

	public static class StyleMenuOverlay {
		public final String id;
		public final String label;
		public StyleMenuOverlay( String id, String label ) {
			this.id = id;
			this.label = label;
		}
	}

	public static class StyleMenuEntry {
		public final String id;
		public final String label;
		public final boolean isDefault;
		public final List<StyleMenuOverlay> overlays;
		public StyleMenuEntry( String id, String label, boolean isDefault, List<StyleMenuOverlay> overlays ) {
			this.id = id;
			this.label = label;
			this.isDefault = isDefault;
			this.overlays = overlays;
		}
	}

	private static class CacheEntry {
		final long lastModified;
		final List<StyleMenuEntry> entries;
		CacheEntry( long lastModified, List<StyleMenuEntry> entries ) {
			this.lastModified = lastModified;
			this.entries = entries;
		}
	}

	private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	public static List<StyleMenuEntry> load( String renderThemePath, ReactApplicationContext reactContext ) throws Exception {
		long lastModified = getLastModified( renderThemePath, reactContext );
		CacheEntry cached = cache.get( renderThemePath );
		if ( null != cached && cached.lastModified == lastModified ) {
			return cached.entries;
		}
		List<StyleMenuEntry> entries = parse( renderThemePath, reactContext );
		cache.put( renderThemePath, new CacheEntry( lastModified, entries ) );
		return entries;
	}

	public static WritableArray toWritableArray( List<StyleMenuEntry> entries ) {
		WritableArray response = new WritableNativeArray();
		for ( StyleMenuEntry entry : entries ) {
			WritableMap item = new WritableNativeMap();
			item.putString( "value", entry.id );
			item.putString( "label", entry.label );
			if ( entry.isDefault ) {
				// Named isDefault, not default -- `default` is a reserved word in the C++ JSI header
				// RN's New Architecture codegen generates for the corresponding JS struct.
				item.putBoolean( "isDefault", true );
			}
			// Matches RenderStyleOverlay[] in NativeLayerMapsforge.ts -- an array of { value, label },
			// not a flat id->label map (RenderStyleOption.overlays, not .options).
			WritableArray overlays = new WritableNativeArray();
			for ( StyleMenuOverlay overlay : entry.overlays ) {
				WritableMap overlayItem = new WritableNativeMap();
				overlayItem.putString( "value", overlay.id );
				overlayItem.putString( "label", overlay.label );
				overlays.pushMap( overlayItem );
			}
			item.putArray( "overlays", overlays );
			response.pushMap( item );
		}
		return response;
	}

	private static long getLastModified( String renderThemePath, ReactApplicationContext reactContext ) {
		if ( renderThemePath.startsWith( "content://" ) ) {
			DocumentFile doc = DocumentFile.fromSingleUri( reactContext, Uri.parse( renderThemePath ) );
			return null != doc ? doc.lastModified() : 0;
		}
		return new File( renderThemePath ).lastModified();
	}

	private static InputStream openStream( String renderThemePath, ReactApplicationContext reactContext ) throws Exception {
		if ( renderThemePath.startsWith( "content://" ) ) {
			return reactContext.getContentResolver().openInputStream( Uri.parse( renderThemePath ) );
		}
		return new FileInputStream( renderThemePath );
	}

	private static List<StyleMenuEntry> parse( String renderThemePath, ReactApplicationContext reactContext ) throws Exception {
		final List<StyleMenuEntry> entries = new ArrayList<>();
		try ( InputStream is = openStream( renderThemePath, reactContext ) ) {
			new XMLReaderAdapter().parse( new DefaultHandler() {

				boolean inStyleMenu = false;
				String defaultLanguage = null;
				String defaultValue = null;

				String currentLayerId = null;
				boolean currentLayerEnabled = false;
				boolean currentLayerVisible = false;
				Map<String, String> currentLayerTitles = null;
				List<StyleMenuOverlay> currentLayerOverlays = null;

				final Map<String, StyleMenuEntry> byId = new LinkedHashMap<>();
				// layer id -> its own overlays, so a later `<layer parent="...">` can inherit them,
				// mirroring XmlThemeBuilder's handling of the "parent" attribute.
				final Map<String, List<StyleMenuOverlay>> overlaysById = new LinkedHashMap<>();

				@Override
				public void startElement( String uri, String localName, String qName, Attributes attributes ) throws SAXException {
					switch ( qName ) {
						case "stylemenu":
							inStyleMenu = true;
							defaultLanguage = attributes.getValue( "defaultlang" );
							defaultValue = attributes.getValue( "defaultvalue" );
							break;
						case "layer":
							if ( inStyleMenu ) {
								currentLayerId = attributes.getValue( "id" );
								currentLayerEnabled = Boolean.parseBoolean( attributes.getValue( "enabled" ) );
								currentLayerVisible = Boolean.parseBoolean( attributes.getValue( "visible" ) );
								currentLayerTitles = new HashMap<>();
								String parent = attributes.getValue( "parent" );
								currentLayerOverlays = new ArrayList<>(
									null != parent && overlaysById.containsKey( parent )
										? overlaysById.get( parent )
										: new ArrayList<>()
								);
							}
							break;
						case "name":
							if ( inStyleMenu && null != currentLayerId ) {
								currentLayerTitles.put( attributes.getValue( "lang" ), attributes.getValue( "value" ) );
							}
							break;
						case "overlay":
							if ( inStyleMenu && null != currentLayerId ) {
								String ovId = attributes.getValue( "id" );
								StyleMenuEntry overlayEntry = byId.get( ovId );
								if ( null != overlayEntry ) {
									currentLayerOverlays.add( new StyleMenuOverlay( overlayEntry.id, overlayEntry.label ) );
								}
							}
							break;
					}
				}

				@Override
				public void endElement( String uri, String localName, String qName ) throws SAXException {
					if ( "layer".equals( qName ) && inStyleMenu && null != currentLayerId ) {
						String label = currentLayerTitles.get( defaultLanguage );
						StyleMenuEntry entry = new StyleMenuEntry(
							currentLayerId,
							null != label ? label : currentLayerId,
							Objects.equals( currentLayerId, defaultValue ),
							currentLayerOverlays
						);
						byId.put( currentLayerId, entry );
						overlaysById.put( currentLayerId, currentLayerOverlays );
						// Only layers that are disabled-by-default but visible are selectable "styles" --
						// mirrors the old module's parseRenderThemeOptions filter exactly.
						if ( ! currentLayerEnabled && currentLayerVisible ) {
							entries.add( entry );
						}
						currentLayerId = null;
					} else if ( "stylemenu".equals( qName ) ) {
						throw new SAXTerminationException();
					}
				}
			}, is );
		} catch ( SAXTerminationException e ) {
			// Expected -- this is the early exit, reached right after </stylemenu>.
		}
		return entries;
	}

}
