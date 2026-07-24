package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

/**
 * JNI wrapper for the native shared-value bridge.
 *
 * <p>Map position data (lng, lat, zoom, bearing, tilt, viewport dimensions) is
 * written directly into reanimated {@code Synchronizable} primitives on the
 * vtm render thread, bypassing the JS bridge entirely.  A worklet poller on
 * the UI thread reads those synchronizables each frame and updates ordinary
 * {@code SharedValue} objects, giving overlay consumers true 60fps tracking
 * with zero bridge crossings.
 *
 * <p>When react-native-reanimated is not installed, the native library
 * ({@code libmapsforgevtm_position_writer.so}) is not built — all JNI calls
 * throw {@link UnsatisfiedLinkError}.  Callers must catch this and fall back
 * to the existing Fabric-event path.
 */
public class MapPositionWriter {

    private static final String TAG = "MapPositionWriter";
    // volatile — read from the render thread at 60fps; JMM requires
    // visibility guarantee.
    private static volatile boolean libraryLoaded = false;
    private static volatile boolean loadAttempted = false;

    /**
     * Attempts to load the native library.  Safe to call repeatedly — the
     * load is attempted only once; subsequent calls are no-ops.
     *
     * @return {@code true} if the library was loaded successfully,
     *         {@code false} if reanimated/worklets is not installed.
     */
    public static boolean ensureLibraryLoaded() {
        if (loadAttempted) {
            return libraryLoaded;
        }
        loadAttempted = true;
        try {
            System.loadLibrary("mapsforgevtm_position_writer");
            libraryLoaded = true;
            Log.d(TAG, "Native shared-value bridge loaded successfully");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "Native shared-value bridge not available "
                    + "(reanimated not installed) — using legacy Fabric event path");
            return false;
        }
    }

    // ------------------------------------------------------------------

    /**
     * Fast check - returns {@code true} when the native library was
     * loaded successfully.  Safe to call from any thread, including
     * the render thread (60fps hot path).  Does not attempt a load;
     * call {@link #ensureLibraryLoaded()} first.
     */
    public static boolean isAvailable() {
        return libraryLoaded;
    }
    // Native methods — implemented in MapPositionWriter.cpp
    // ------------------------------------------------------------------

    /**
     * Creates an empty writer entry for the given view handle.
     * Called from {@code MapsforgeVtmView.createFragment()} (UI thread).
     */
    public static native void nativeCreateWriter(int nativeNodeHandle);

    /**
     * Removes the writer entry for the given view handle.
     * Called from {@code MapFragment.onDestroy()} (UI thread).
     */
    public static native void nativeReleaseWriter(int nativeNodeHandle);

    /**
     * Writes position data to the synchronizables for the given view handle.
     * Called from {@code MapFragment.bindUpdateListener()} on every vtm
     * frame (render thread, 60fps).
     *
     * <p>When the synchronizables haven't been bound yet (JS side hasn't
     * called {@code __bindMapPositionSynchronizables}), writes are silently
     * skipped — no crash, no data corruption.
     */
    public static native void nativeSetPosition(
        int nativeNodeHandle,
        double lng,
        double lat,
        double zoom,
        double bearing,
        double tilt,
        double viewportWidth,
        double viewportHeight
    );

    /**
     * Installs the {@code __bindMapPositionSynchronizables} global function
     * on the React Native JS runtime.  Called from
     * {@code MapContainer.installMapPositionJSI()} (JS thread, synchronous
     * TurboModule).
     */
    public static native void nativeInstallJSI(long jsContext);
}
