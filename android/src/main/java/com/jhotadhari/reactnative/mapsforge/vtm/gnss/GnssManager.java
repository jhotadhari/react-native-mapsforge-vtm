package com.jhotadhari.reactnative.mapsforge.vtm.gnss;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader;

/**
 * Wraps an Android {@link LocationListener} to deliver two GNSS position
 * streams to JS:
 *
 * <ul>
 *   <li><b>Live</b> ({@code onGnssLive}) — every fix the provider delivers
 *       (~1 Hz), guarded only against garbage accuracy (the weaker of a
 *       100&nbsp;m cap and the recording accuracy threshold, so the
 *       recording stream never starves).  Consumers that render the live
 *       position (map marker, speed/course dashboard widgets) subscribe
 *       to this stream — it stays dense even at low speeds.
 *   <li><b>Recording</b> ({@code onGnssPosition}) — only positions that
 *       qualify for track recording: accuracy within the configured
 *       {@code minAccuracyMeters} AND at least {@code minTimeMs} elapsed
 *       AND {@code minDistanceMeters} moved since the last qualifying
 *       position.  This replicates the semantics of
 *       {@link LocationManager#requestLocationUpdates(long, float,
 *       android.location.LocationListener, android.os.Looper)} (which is
 *       no longer used — the listener is registered with raw parameters
 *       so the live stream never thins to the recording cadence).
 * </ul>
 *
 * <p>Both payloads share the same shape; altitude resolution via
 * {@link ElevationReader} runs once per fix.  Filtering on/off: create a
 * new instance and call {@link #start()} when the {@code gnssFilter}
 * prop is set; call {@link #stop()} when the prop is cleared or the
 * fragment is destroyed.
 *
 * <p>DEM altitude is a fast-path cache hit — on cache miss,
 * {@link ElevationReader#getElevation(double, double)} returns null
 * immediately and triggers a background preload.  The next location
 * update (~1 s later) will get the cached value.  No blocking retry
 * loop — this runs on the main thread alongside the LocationListener.
 */
public class GnssManager {

	/** Upper accuracy bound for the live stream when the recording
	 * threshold is looser — keeps the marker from teleporting on
	 * garbage fixes. */
	private static final float LIVE_MAX_ACCURACY_METERS = 100f;

	/** Callbacks invoked per fix: live on every fix, recording only on
	 * qualifying positions. */
	public interface Callback {
		void onGnssLive(@NonNull WritableMap payload);

		void onGnssPosition(@NonNull WritableMap payload);
	}

	// -------------------------------------------------------------------
	// Parsed configuration
	// -------------------------------------------------------------------

	private final LocationManager locationManager;
	private final String provider;
	private final long minTimeMs;
	private final float minDistanceMeters;
	private final float minAccuracyMeters;
	private final String altitudeSource; // "dem-only" | "gnss-only" | "dem-preferred" | "gnss-preferred"
	private final ElevationReader elevationReader;
	private final Callback callback;

	private volatile boolean started;

	/** Last position that passed the recording gate — anchor for the
	 * internal time+distance filter (previously applied by
	 * requestLocationUpdates). */
	private Location lastPassingLocation;

	// -------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------

	/**
	 * Parse a {@link GnssManager} config from the JS {@code gnssFilter} prop.
	 *
	 * @param locationManager Android LocationManager (from context).
	 * @param gnssFilter      The raw ReadableMap from JS.  May be null
	 *                        (treated as "stop").
	 * @param elevationReader May be null — when absent, DEM altitude
	 *                        resolution is silently skipped.
	 * @param callback        Called per fix: live always, recording on
	 *                        qualifying positions.
	 * @return a configured instance, or {@code null} if {@code gnssFilter}
	 *         is null or lacks required fields.
	 */
	@Nullable
	public static GnssManager create(
		@NonNull LocationManager locationManager,
		@Nullable ReadableMap gnssFilter,
		@Nullable ElevationReader elevationReader,
		@NonNull Callback callback
	) {
		if (gnssFilter == null) return null;

		String provider = gnssFilter.hasKey("provider")
			? gnssFilter.getString("provider")
			: "satellite";
		// Map JS provider names to Android constants.
		if ("network".equals(provider)) {
			provider = LocationManager.NETWORK_PROVIDER;
		} else {
			provider = LocationManager.GPS_PROVIDER;
		}

		long minTimeMs = gnssFilter.hasKey("minTimeSec")
			? (long) (gnssFilter.getDouble("minTimeSec") * 1000)
			: 2000L;
		float minDistanceMeters = gnssFilter.hasKey("minDistanceMeters")
			? (float) gnssFilter.getDouble("minDistanceMeters")
			: 5f;
		float minAccuracyMeters = gnssFilter.hasKey("minAccuracyMeters")
			? (float) gnssFilter.getDouble("minAccuracyMeters")
			: 20f;

		// Altitude source: read from top-level (flat) field matching the
		// TypeScript GnssFilterNativeProps shape.  Default dem-preferred.
		String altitudeSource = "dem-preferred";
		if (gnssFilter.hasKey("altitudeSource")) {
			String src = gnssFilter.getString("altitudeSource");
			if (src != null) {
				altitudeSource = src;
			}
		}

		// Validate the source string so typos don't silently produce
		// null altitudes.
		if (!"dem-only".equals(altitudeSource)
			&& !"gnss-only".equals(altitudeSource)
			&& !"dem-preferred".equals(altitudeSource)
			&& !"gnss-preferred".equals(altitudeSource)) {
			android.util.Log.w("GnssManager",
				"Unknown altitudeSource '" + altitudeSource
					+ "', falling back to dem-preferred");
			altitudeSource = "dem-preferred";
		}

		return new GnssManager(
			locationManager, provider, minTimeMs, minDistanceMeters,
			minAccuracyMeters, altitudeSource,
			elevationReader, callback
		);
	}

	private GnssManager(
		@NonNull LocationManager locationManager,
		@NonNull String provider,
		long minTimeMs,
		float minDistanceMeters,
		float minAccuracyMeters,
		@NonNull String altitudeSource,
		@Nullable ElevationReader elevationReader,
		@NonNull Callback callback
	) {
		this.locationManager = locationManager;
		this.provider = provider;
		this.minTimeMs = minTimeMs;
		this.minDistanceMeters = minDistanceMeters;
		this.minAccuracyMeters = minAccuracyMeters;
		this.altitudeSource = altitudeSource;
		this.elevationReader = elevationReader;
		this.callback = callback;
	}

	// -------------------------------------------------------------------
	// Listener
	// -------------------------------------------------------------------

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onLocationChanged(@NonNull Location location) {
			// Live accuracy guard — drop positions too coarse to render.
			// Never stricter than the recording threshold, so the
			// recording stream cannot starve.
			float liveAccuracyGuard = Math.max(
				LIVE_MAX_ACCURACY_METERS, minAccuracyMeters
			);
			if (location.getAccuracy() > liveAccuracyGuard) return;

			double lng = location.getLongitude();
			double lat = location.getLatitude();

			// Resolve DEM altitude — fast-path cache hit only.
			// On miss, ElevationReader returns null and triggers a
			// background preload; the next update gets the cached value.
			Short demAlt = null;
			if (elevationReader != null) {
				boolean wantDem = "dem-only".equals(altitudeSource)
					|| "dem-preferred".equals(altitudeSource)
					|| "gnss-preferred".equals(altitudeSource);
				if (wantDem && elevationReader.hasDataFor(lng, lat)) {
					demAlt = elevationReader.getElevation(lng, lat);
				}
			}

			// Raw GNSS altitude, independent of altitudeSource.  Consumers
			// that want the raw fix (e.g. the live altitude display) read
			// this instead of the resolved `altitude` below.
			Double gnssAltitude = location.hasAltitude()
				? location.getAltitude() : null;

			Double altitude = null;
			switch (altitudeSource) {
				case "dem-only":
					altitude = demAlt != null ? demAlt.doubleValue() : null;
					break;
				case "gnss-only":
					altitude = gnssAltitude;
					break;
				case "dem-preferred":
					altitude = demAlt != null
						? demAlt.doubleValue()
						: gnssAltitude;
					break;
				case "gnss-preferred":
					altitude = gnssAltitude != null
						? gnssAltitude
						: (demAlt != null ? demAlt.doubleValue() : null);
					break;
			}

			// Live stream: every fix.
			callback.onGnssLive(buildPayload(location, gnssAltitude, altitude));

			// Recording gate: accuracy, time AND distance since the last
			// qualifying position (replicating requestLocationUpdates).
			if (location.getAccuracy() > minAccuracyMeters) return;
			if (lastPassingLocation != null) {
				long elapsedMs = location.getTime()
					- lastPassingLocation.getTime();
				float movedMeters = location.distanceTo(lastPassingLocation);
				if (elapsedMs < minTimeMs || movedMeters < minDistanceMeters) {
					return;
				}
			}
			lastPassingLocation = location;
			// Fresh payload — a Fabric event dispatch consumes the map,
			// so the two streams must never share one WritableMap.
			callback.onGnssPosition(buildPayload(location, gnssAltitude, altitude));
		}

		@Override public void onProviderEnabled(@NonNull String p) { }
		@Override public void onProviderDisabled(@NonNull String p) { }
		@Override public void onStatusChanged(String p, int s, Bundle e) { }
	};

	/**
	 * Build a fresh {@link WritableMap} for one event emission.  Fabric
	 * dispatches consume the map (the native memory moves to the event),
	 * so every emission needs its own instance — never share one between
	 * the live and recording streams.
	 */
	@NonNull
	private WritableMap buildPayload(
		@NonNull Location location,
		@Nullable Double gnssAltitude,
		@Nullable Double altitude
	) {
		WritableMap payload = Arguments.createMap();
		payload.putDouble("lng", location.getLongitude());
		payload.putDouble("lat", location.getLatitude());
		if (altitude != null) {
			payload.putDouble("altitude", altitude);
		} else {
			payload.putNull("altitude");
		}
		if (gnssAltitude != null) {
			payload.putDouble("gnssAltitude", gnssAltitude);
		} else {
			payload.putNull("gnssAltitude");
		}
		payload.putDouble("bearing", location.hasBearing()
			? location.getBearing() : -1);
		payload.putDouble("accuracy", location.getAccuracy());
		payload.putDouble("speed", location.hasSpeed()
			? location.getSpeed() : 0);
		payload.putDouble("timestamp", location.getTime());
		return payload;
	}

	// -------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------

	/** Start receiving location updates.  Idempotent. */
	public void start() {
		if (started) return;
		try {
			// Raw registration — every fix reaches the listener; the
			// recording gate is applied internally (see onLocationChanged).
			locationManager.requestLocationUpdates(
				provider, 0L, 0f,
				locationListener, Looper.getMainLooper()
			);
			started = true;
		} catch (SecurityException | IllegalArgumentException e) {
			// Location permission not granted, or invalid parameters.
			// Emit an error via both callbacks and leave started=false.
			callback.onGnssLive(buildErrorPayload(e.getMessage()));
			callback.onGnssPosition(buildErrorPayload(e.getMessage()));
		}
	}

	/**
	 * Build an error payload for a failed listener start — a fresh map
	 * per emission (Fabric dispatch consumes each one).
	 */
	@NonNull
	private WritableMap buildErrorPayload(@Nullable String errorMsg) {
		WritableMap err = Arguments.createMap();
		err.putDouble("lng", 0);
		err.putDouble("lat", 0);
		err.putNull("altitude");
		err.putNull("gnssAltitude");
		err.putDouble("bearing", -1);
		err.putDouble("accuracy", 0);
		err.putDouble("speed", 0);
		err.putDouble("timestamp", System.currentTimeMillis());
		err.putString("errorMsg", errorMsg != null ? errorMsg : "");
		return err;
	}

	/** Stop receiving location updates.  Idempotent. */
	public void stop() {
		if (!started) return;
		try {
			locationManager.removeUpdates(locationListener);
		} catch (SecurityException ignored) {
			// Permission revoked between start/stop — listener is
			// already unregistered by the OS.
		}
		started = false;
		lastPassingLocation = null;
	}
}
