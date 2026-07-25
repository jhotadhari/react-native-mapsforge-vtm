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
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

/**
 * Registers an Android {@link LocationListener} with the configured
 * {@code minDistanceMeters} / {@code minTimeMs} (passed directly to
 * {@link LocationManager#requestLocationUpdates}), applies an accuracy guard,
 * optionally resolves DEM altitude via {@link ElevationReader}, and calls
 * back the host view with a {@code WritableMap} ready to emit as a
 * {@code onGnssPosition} event.
 *
 * <p>Filtering on/off: create a new instance and call {@link #start()} when
 * the {@code gnssFilter} prop is set; call {@link #stop()} when the prop is
 * cleared or the fragment is destroyed.
 */
public class GnssManager {

    /** Callback invoked on every qualifying GNSS position. */
    public interface Callback {
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
    private final String altitudeSource;  // "dem-only" | "gnss-only" | "dem-preferred" | "gnss-preferred"
    private final int demRetryMs;
    private final ElevationReader elevationReader;
    private final Callback callback;

    private boolean started;
    @Nullable private Location lastEmittedLocation;

    // -------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------

    /**
     * Parse a {@link GnssManager} config from the JS {@code gnssFilter} prop.
     *
     * @param locationManager  Android LocationManager (from context).
     * @param gnssFilter       The raw ReadableMap from JS.  May be null
     *                         (treated as "stop").
     * @param elevationReader  May be null — when absent, DEM altitude
     *                         resolution is silently skipped.
     * @param callback         Called on each qualifying position.
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
                : "gps";
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

        // Altitude sub-map — optional, defaults to dem-preferred with 500ms retry.
        String altitudeSource = "dem-preferred";
        int demRetryMs = 500;
        if (gnssFilter.hasKey("altitude")) {
            ReadableMap altMap = gnssFilter.getMap("altitude");
            if (altMap != null) {
                if (altMap.hasKey("source")) {
                    altitudeSource = altMap.getString("source");
                }
                if (altMap.hasKey("demRetryMs")) {
                    demRetryMs = altMap.getInt("demRetryMs");
                }
            }
        }

        return new GnssManager(
                locationManager, provider, minTimeMs, minDistanceMeters,
                minAccuracyMeters, altitudeSource, demRetryMs,
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
            int demRetryMs,
            @Nullable ElevationReader elevationReader,
            @NonNull Callback callback
    ) {
        this.locationManager = locationManager;
        this.provider = provider;
        this.minTimeMs = minTimeMs;
        this.minDistanceMeters = minDistanceMeters;
        this.minAccuracyMeters = minAccuracyMeters;
        this.altitudeSource = altitudeSource;
        this.demRetryMs = demRetryMs;
        this.elevationReader = elevationReader;
        this.callback = callback;
    }

    // -------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            // Accuracy guard — drop positions too coarse for the user's
            // configured threshold.  Android's own distance/time filter
            // already applied by requestLocationUpdates.
            if (location.getAccuracy() > minAccuracyMeters) return;

            double lng = location.getLongitude();
            double lat = location.getLatitude();

            // Resolve altitude per source preference.
            Short demAlt = null;
            if (elevationReader != null) {
                boolean wantDem = "dem-only".equals(altitudeSource)
                        || "dem-preferred".equals(altitudeSource)
                        || "gnss-preferred".equals(altitudeSource);
                if (wantDem && elevationReader.hasDataFor(lng, lat)) {
                    demAlt = retryGetElevation(lng, lat);
                }
            }

            Double altitude = null;
            switch (altitudeSource) {
                case "dem-only":
                    altitude = demAlt != null ? demAlt.doubleValue() : null;
                    break;
                case "gnss-only":
                    altitude = location.hasAltitude()
                            ? location.getAltitude() : null;
                    break;
                case "dem-preferred":
                    altitude = demAlt != null
                            ? demAlt.doubleValue()
                            : (location.hasAltitude() ? location.getAltitude() : null);
                    break;
                case "gnss-preferred":
                    altitude = location.hasAltitude()
                            ? location.getAltitude()
                            : (demAlt != null ? demAlt.doubleValue() : null);
                    break;
                default:
                    altitude = demAlt != null ? demAlt.doubleValue() : null;
                    break;
            }

            lastEmittedLocation = location;

            WritableMap payload = Arguments.createMap();
            payload.putDouble("lng", lng);
            payload.putDouble("lat", lat);
            if (altitude != null) {
                payload.putDouble("altitude", altitude);
            } else {
                payload.putNull("altitude");
            }
            payload.putDouble("bearing", location.hasBearing()
                    ? location.getBearing() : -1);
            payload.putDouble("accuracy", location.getAccuracy());
            payload.putDouble("speed", location.hasSpeed()
                    ? location.getSpeed() : 0);
            payload.putDouble("timestamp", location.getTime());

            callback.onGnssPosition(payload);
        }

        @Override public void onProviderEnabled(@NonNull String p) { }
        @Override public void onProviderDisabled(@NonNull String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    // -------------------------------------------------------------------
    // DEM retry
    // -------------------------------------------------------------------

    /**
     * Polls {@link ElevationReader#getElevation(double, double)} with a
     * short backoff loop until the HGT tile is cached or the timeout expires.
     *
     * @return the elevation in metres, or {@code null} if the tile never
     *         loaded within {@link #demRetryMs}.
     */
    @Nullable
    private Short retryGetElevation(double lng, double lat) {
        if (elevationReader == null) return null;

        // Fast path — cache hit.
        Short val = elevationReader.getElevation(lng, lat);
        if (val != null) return val;

        // Cache miss — poll with backoff.
        long deadline = System.currentTimeMillis() + demRetryMs;
        int delay = 50;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(delay); } catch (InterruptedException e) { break; }
            val = elevationReader.getElevation(lng, lat);
            if (val != null) return val;
            delay = Math.min(200, delay * 2);
        }
        return null;
    }

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    /** Start receiving location updates.  Idempotent. */
    public void start() {
        if (started) return;
        try {
            locationManager.requestLocationUpdates(
                    provider, minTimeMs, minDistanceMeters,
                    locationListener, Looper.getMainLooper()
            );
            started = true;
        } catch (SecurityException e) {
            // Location permission not granted — the app should have
            // requested it.  Emit an error and leave started=false.
            WritableMap err = Arguments.createMap();
            err.putString("errorMsg", "Location permission not granted: " + e.getMessage());
            callback.onGnssPosition(err);
        }
    }

    /** Stop receiving location updates.  Idempotent. */
    public void stop() {
        if (!started) return;
        locationManager.removeUpdates(locationListener);
        started = false;
        lastEmittedLocation = null;
    }
}
