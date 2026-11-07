package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import android.content.Context;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeGnssModuleSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.gnss.GnssManager;

import org.mapsforge.map.layer.hills.DemFolder;

/**
 * Application-scoped GNSS listener (TurboModule). Owns the
 * {@link GnssManager} lifecycle instead of the map view, so track
 * recording keeps collecting fixes while the activity surface is gone
 * (accidental swipe / background recording): the foreground service
 * keeps the process alive and this module keeps the location listener
 * alive.
 *
 * <p>Positions are emitted as device events
 * ({@code onGnssLive} / {@code onGnssPosition} — same names and payload
 * shape the view-level props used to carry). DEM altitude resolution
 * uses the shared {@code ElevationReader} registry via
 * {@link MapContainer} under this module's own key (independent of any
 * map view); without a configured DEM dir it falls back to raw GNSS
 * altitude (see {@link GnssManager}).
 */
@ReactModule( name = GnssModule.NAME )
public class GnssModule extends NativeGnssModuleSpec {

	public static final String NAME = "GnssModule";

	/** ElevationReader registry key for this module. The registry is
	 * keyed by map-view nativeNodeHandle (positive ints); the module
	 * uses a dedicated negative key so its reader is independent of any
	 * view and never collides. */
	private static final int ELEVATION_READER_KEY = -1;

	private final ReactApplicationContext reactContext;
	private GnssManager gnssManager;

	public GnssModule( ReactApplicationContext reactContext ) {
		super( reactContext );
		this.reactContext = reactContext;
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	/**
	 * Configure or stop the GNSS listener. {@code null} stops;
	 * otherwise the listener is (re)started with the given
	 * recording-gate parameters (same semantics as the removed
	 * {@code gnssFilter} view prop).
	 */
	@Override
	public void setFilter( @Nullable ReadableMap filter ) {
		if ( null != gnssManager ) {
			gnssManager.stop();
			gnssManager = null;
		}
		if ( null == filter ) {
			return;
		}
		Context ctx = reactContext.getApplicationContext();
		if ( null == ctx ) {
			return;
		}
		LocationManager lm = (LocationManager) ctx.getSystemService( Context.LOCATION_SERVICE );
		if ( null == lm ) {
			return;
		}
		ElevationReader reader = MapContainer.getElevationReader( ELEVATION_READER_KEY, reactContext );
		gnssManager = GnssManager.create( lm, filter, reader, new GnssManager.Callback() {
			@Override
			public void onGnssLive( @NonNull WritableMap payload ) {
				emit( "onGnssLive", payload );
			}

			@Override
			public void onGnssPosition( @NonNull WritableMap payload ) {
				emit( "onGnssPosition", payload );
			}
		} );
		if ( null != gnssManager ) {
			gnssManager.start();
		}
	}

	/**
	 * Configure this module's DEM source (same registry + build logic
	 * as the map's {@code setHgtDirPath}). A null/empty path tears the
	 * reader down.
	 */
	@Override
	public void setHgtDirPath( @Nullable String hgtDirPath ) {
		// removeElevationReader removes AND closes any existing reader.
		MapContainer.removeElevationReader( ELEVATION_READER_KEY, reactContext );
		if ( null == hgtDirPath || hgtDirPath.isEmpty() ) {
			return;
		}
		DemFolder demFolder = Utils.buildDemFolder( hgtDirPath, reactContext.getApplicationContext() );
		if ( null == demFolder ) {
			return;
		}
		MapContainer.putElevationReader(
			ELEVATION_READER_KEY, reactContext, new ElevationReader( demFolder )
		);
	}

	private void emit( @NonNull String eventName, @NonNull WritableMap payload ) {
		reactContext
			.getJSModule( DeviceEventManagerModule.RCTDeviceEventEmitter.class )
			.emit( eventName, payload );
	}
}
