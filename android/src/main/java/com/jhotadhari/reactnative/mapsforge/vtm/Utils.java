package com.jhotadhari.reactnative.mapsforge.vtm;

import android.app.Activity;
import android.content.Context;
import android.content.UriPermission;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.android.cache.TileCache;
import org.oscim.tiling.ITileCache;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.List;

public class Utils {

	public static MapFragment getMapFragment( ReactContext reactContext, int nativeNodeHandle ) {
		try {
			FragmentActivity activity = (FragmentActivity) reactContext.getCurrentActivity();
			if ( null == activity ) {
				return null;
			}
			return (MapFragment) activity.getSupportFragmentManager().findFragmentById( (int) nativeNodeHandle );
		} catch(Exception e) {
			return null;
		}
	}

	public static MapView getMapView( ReactContext reactContext, int nativeNodeHandle ) {
		try {
			MapFragment mapFragment = getMapFragment( reactContext, nativeNodeHandle );
			if ( null == mapFragment ) {
				return null;
			}
			return (MapView) mapFragment.getMapView();
		} catch(Exception e) {
			return null;
		}
	}

	/**
	 * This method converts dp unit to equivalent pixels, depending on device density.
	 * Source https://gist.github.com/brandhill/9c947a3e2881dff66bd3
	 *
	 * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent px equivalent to dp depending on device density
	 */
	public static float convertDpToPixel(float dp, Context context){
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		return dp * (metrics.densityDpi / 160f);
	}

	/**
	 * This method converts device specific pixels to density independent pixels.
	 * Source https://gist.github.com/brandhill/9c947a3e2881dff66bd3
	 *
	 * @param px A value in px (pixels) unit. Which we need to convert into db
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent dp equivalent to px value
	 */
	public static float convertPixelsToDp(float px, Context context){
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		return px / (metrics.densityDpi / 160f);
	}

	public static boolean hasScopedStoragePermission( Context context, String string, boolean checkWritePermission ) {
		// list of all persisted permissions for our app
		List<UriPermission> uriList = context.getContentResolver().getPersistedUriPermissions();
		try {
			// Fake "document" to tree. "document" is first part of path.
			URI jUri = new URI( string );
			String[] pathArray= jUri.getPath().substring(1 ).split( "/" );
			Array.set( pathArray, 0, "tree" );
			String testString = jUri.getScheme() + "://" + jUri.getHost() + "/" + String.join( "/", pathArray );

			for ( UriPermission uriPermission : uriList ) {
				String uriString = URLDecoder.decode( uriPermission.getUri().toString() );
				if ( ( uriString.startsWith( testString ) || testString.startsWith( uriString ) ) && uriPermission.isReadPermission() && ( ! checkWritePermission || uriPermission.isWritePermission() ) ) {
					return true;
				}
			}
		} catch ( URISyntaxException e ) {
			e.printStackTrace();
		}
		return false;
	}

	// Source https://glaforge.dev/posts/2024/01/08/url-slug-or-how-to-remove-accents-in-java/
	public static String slugify( String str ) {
		return Normalizer.normalize( str , Normalizer.Form.NFD)
			.toLowerCase()									// "l'été, où es tu ?"
			.replaceAll("\\p{IsM}+", "")	// "l'ete, ou es tu ?"
			.replaceAll("\\p{IsP}+", " ")	// "l ete  ou es tu  "
			.trim()											// "l ete  ou es tu"
			.replaceAll("\\s+", "-");		// "l-ete-ou-es-tu"
	}

	public static File getCacheDirParent(
		String cacheDirBase,
		ReactApplicationContext context
	) {
		File cacheDirParent = null;
		if (
			cacheDirBase.startsWith( "/" )
			&& cacheDirBase.length() > 1 	// first char is `/`, checks if it's empty after this.
		) {
			File cacheDirBaseFile = new File( cacheDirBase );
			cacheDirParent = cacheDirBaseFile.exists() ? cacheDirBaseFile : null;
		}
		if ( null == cacheDirParent ) {
			cacheDirParent = context.getCacheDir();
		}
		return null == cacheDirParent
			? context.getCacheDir()
			: cacheDirParent;
	}

	/**
	 * Shared by LayerHillshading and LayerMBTilesBitmap, the only two layers that support an
	 * on-disk tile cache. vtm requires the cache to be set on a TileSource before that source is
	 * attached to a TileLayer.
	 *
	 * @param activity      Current Activity, needed by vtm's TileCache constructor. Null is a real,
	 *                      reachable case (e.g. an Activity recreation/destruction race) -- callers
	 *                      already catch exceptions generically and reject the promise with the
	 *                      message, so this throws rather than silently skipping the cache or NPEing
	 *                      inside TileCache's own constructor.
	 * @param cacheSize     Cache size in mb. 0 or less disables caching (returns null).
	 * @param cacheDirBase  Empty string is handled by getCacheDirParent.
	 * @param cacheDirChild Empty string falls back to dbname.
	 * @param dbname        Unique name for this cache's contents (e.g. derived from the algorithm/
	 *                      params for hillshading, or the source file for MBTiles).
	 * @return ITileCache to pass to TileSource.setCache(), or null if cacheSize <= 0.
	 */
	public static ITileCache buildTileCache(
		Activity activity,
		ReactApplicationContext reactContext,
		int cacheSize,
		String cacheDirBase,
		String cacheDirChild,
		String dbname
	) {
		if ( cacheSize <= 0 ) {
			return null;
		}
		if ( null == activity ) {
			throw new IllegalStateException( "Unable to set up tile cache: no current Activity" );
		}
		File cacheDirParent = getCacheDirParent( cacheDirBase, reactContext );
		String resolvedCacheDirChild = ! cacheDirChild.isEmpty() ? cacheDirChild : dbname;
		File cacheDirectory = new File( cacheDirParent, resolvedCacheDirChild );
		ITileCache tileCache = new TileCache( activity, cacheDirectory.toString(), dbname );
		tileCache.setCacheSize( (long) cacheSize * ( 1 << 10 ) );
		return tileCache;
	}

	public static void promiseReject( Promise promise, String errorMsg ) {
		WritableMap error = new WritableNativeMap();
		error.putString( "errorMsg", errorMsg );
		promise.reject( "error", error );
	}

	/**
	 * Check if the map has an entry for this key that is not null.
	 *
	 * @param args	ReadableMap or WritableMap
	 * @param key	The key to check for
	 * @return boolean
	 */
	public static boolean rMapHasKey( ReadableMap args, String key ) {
		return args.hasKey( key ) && ! args.isNull( key );
	}

	/**
	 * Build a geojson style `Position`, ie `[ lng, lat, alt? ]`.
	 *
	 * @param lng	Longitude
	 * @param lat	Latitude
	 * @param alt	Altitude. Omitted from the array when null.
	 * @return WritableArray
	 */
	public static WritableArray positionToWritableArray( double lng, double lat, Double alt ) {
		WritableArray position = new WritableNativeArray();
		position.pushDouble( lng );
		position.pushDouble( lat );
		if ( null != alt ) {
			position.pushDouble( alt );
		}
		return position;
	}

	public static double lngFromPosition( ReadableArray position ) {
		return position.getDouble( 0 );
	}

	public static double latFromPosition( ReadableArray position ) {
		return position.getDouble( 1 );
	}

	@Nullable
	public static Double altFromPosition( ReadableArray position ) {
		return position.size() > 2 ? position.getDouble( 2 ) : null;
	}

}
