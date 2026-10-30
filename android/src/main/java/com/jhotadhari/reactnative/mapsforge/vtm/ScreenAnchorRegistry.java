package com.jhotadhari.reactnative.mapsforge.vtm;

import androidx.annotation.NonNull;

import org.oscim.android.MapView;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.map.Map;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-map-view registry of frame callbacks invoked on every vtm map event
 * (position/update — i.e. once per rendered frame while the map is active).
 *
 * <p>Used for geometry that must track a screen position (or a position
 * derived from the live viewport, e.g. a label following a geographic point)
 * without any JS round trip. One {@link Map.UpdateListener} is bound per
 * {@code nativeNodeHandle} while at least one callback is registered, and
 * unbound when the last callback leaves — so the per-frame cost is exactly
 * zero for maps without anchored geometry. The listener is independent of
 * {@code emitsMapUpdateEvents}: it stays bound even when the JS side opted
 * out of map update events.</p>
 *
 * <p>Threading: vtm delivers map events on the thread {@code prepareFrame()}
 * runs on (the map view's own thread — {@code MapView.post} in
 * {@code AndroidMap}). bind/unbind are therefore also posted onto the map
 * view's thread so they never race an in-flight dispatch. Registration
 * itself may happen on any thread.</p>
 */
public class ScreenAnchorRegistry {

	/** Work invoked on every map frame while the map renders. */
	public interface FrameCallback {
		void onFrame( @NonNull MapView mapView );
	}

	private static final java.util.Map<Integer, List<FrameCallback>> CALLBACKS = new ConcurrentHashMap<>();
	private static final java.util.Map<Integer, Map.UpdateListener> LISTENERS = new ConcurrentHashMap<>();
	private static final java.util.Map<Integer, MapView> MAP_VIEWS = new ConcurrentHashMap<>();

	private ScreenAnchorRegistry() {
	}

	/**
	 * Registers a frame callback for the given map view. Binding the first
	 * callback posts the shared map event listener onto the map view's thread.
	 */
	public static void register( int nativeNodeHandle, @NonNull MapView mapView, @NonNull FrameCallback callback ) {
		MAP_VIEWS.put( nativeNodeHandle, mapView );
		List<FrameCallback> list = CALLBACKS.computeIfAbsent( nativeNodeHandle, h -> new CopyOnWriteArrayList<>() );
		list.add( callback );
		bindOnMapThread( nativeNodeHandle, mapView );
	}

	/** Removes a frame callback; the last removal posts an unbind. */
	public static void unregister( int nativeNodeHandle, @NonNull FrameCallback callback ) {
		List<FrameCallback> list = CALLBACKS.get( nativeNodeHandle );
		if ( list == null ) {
			return;
		}
		list.remove( callback );
		if ( list.isEmpty() ) {
			CALLBACKS.remove( nativeNodeHandle );
			unbindOnMapThread( nativeNodeHandle );
		}
	}

	/**
	 * Full teardown for a destroyed map view. Called from
	 * {@code MapFragment.onDestroy} (UI thread — direct unbind is safe there).
	 */
	public static void clear( int nativeNodeHandle, @NonNull MapView mapView ) {
		CALLBACKS.remove( nativeNodeHandle );
		Map.UpdateListener listener = LISTENERS.remove( nativeNodeHandle );
		if ( listener != null && mapView.map() != null ) {
			mapView.map().events.unbind( listener );
		}
		MAP_VIEWS.remove( nativeNodeHandle );
	}

	private static void dispatch( int nativeNodeHandle, @NonNull MapView mapView ) {
		List<FrameCallback> list = CALLBACKS.get( nativeNodeHandle );
		if ( list == null ) {
			return;
		}
		for ( FrameCallback callback : list ) {
			callback.onFrame( mapView );
		}
	}

	private static void bindOnMapThread( int nativeNodeHandle, @NonNull MapView mapView ) {
		Map.UpdateListener listener = new Map.UpdateListener() {
			@Override
			public void onMapEvent( Event e, MapPosition mapPosition ) {
				dispatch( nativeNodeHandle, mapView );
			}
		};
		if ( LISTENERS.putIfAbsent( nativeNodeHandle, listener ) != null ) {
			// Already bound for this handle.
			return;
		}
		// vtm map events fire on the map view's own thread — bind from there
		// too (mirrors MapContainer.animateTo's UiThreadUtil usage) so a bind
		// never interleaves with an in-flight event dispatch.
		mapView.post( () -> {
			// Identity check: a stale bind must not resurrect a listener that
			// was unbound again before this post ran.
			if ( LISTENERS.get( nativeNodeHandle ) == listener && mapView.map() != null ) {
				mapView.map().events.bind( listener );
			}
		} );
	}

	private static void unbindOnMapThread( int nativeNodeHandle ) {
		Map.UpdateListener listener = LISTENERS.remove( nativeNodeHandle );
		if ( listener == null ) {
			return;
		}
		MapView mapView = MAP_VIEWS.get( nativeNodeHandle );
		if ( mapView == null ) {
			return;
		}
		mapView.post( () -> {
			if ( mapView.map() != null ) {
				mapView.map().events.unbind( listener );
			}
		} );
	}
}
