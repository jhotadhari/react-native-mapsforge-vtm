package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.oscim.event.Gesture;
import org.oscim.event.GestureListener;
import org.oscim.event.MotionEvent;
import org.oscim.layers.Layer;
import org.oscim.map.Map;

/**
 * A transparent vtm {@link Layer} that implements {@link GestureListener} to
 * intercept {@link Gesture.Tap} and {@link Gesture.LongPress} events BEFORE
 * the geometry layers (markers, paths, shapes, extensions) see them.
 *
 * <p>Unlike the old top-most {@code GestureLayer} (which only saw gestures
 * every geometry layer declined), this layer sits at the <b>lowest</b> index
 * and aggregates hits across <b>all</b> {@link LayerManager}s of the map view
 * into a single payload — so a tap on overlapping geometry (e.g. a waypoint
 * marker on top of a route line) reports every hit, not just the first
 * consumer. It always returns {@code false}, so the gesture still propagates
 * to the geometry layers and their existing per-layer events keep working.</p>
 *
 * <p>All other gestures — {@code Move}, {@code Press}, {@code DoubleTap},
 * etc. — pass through unhandled, leaving the standard {@code MapEventLayer}
 * behaviour intact.</p>
 */
public class HitTestLayer extends Layer implements GestureListener {

	/**
	 * Callback interface so the owning fragment can emit Fabric events
	 * without this layer needing a direct reference to the view hierarchy.
	 */
	public interface HitTestCallback {
		void onHitTest(String eventName, float x, float y, WritableArray hits);
	}

	private final HitTestCallback mCallback;
	private final int mNativeNodeHandle;

	public HitTestLayer(
		Map map,
		int nativeNodeHandle,
		@Nullable HitTestCallback callback
	) {
		super(map);
		mNativeNodeHandle = nativeNodeHandle;
		mCallback = callback;
	}

	@Override
	public boolean onGesture(Gesture g, MotionEvent e) {
		if (mCallback == null) {
			return false;
		}
		String eventName = null;
		if (g instanceof Gesture.Tap) {
			eventName = "onTap";
		} else if (g instanceof Gesture.LongPress) {
			eventName = "onLongPress";
		}
		if (eventName == null) {
			// Pass everything else — Move, Press, DoubleTap — so
			// MapEventLayer (pan/zoom/tilt/rotate) works normally.
			return false;
		}

		WritableArray hits = Arguments.createArray();
		for (LayerManager<?> manager : LayerManager.getAllInstances(mNativeNodeHandle)) {
			WritableArray managerHits = manager.hitTestAll(e.getX(), e.getY());
			for (int i = 0; i < managerHits.size(); i++) {
				ReadableMap payload = managerHits.getMap(i);
				WritableMap copy = Arguments.createMap();
				copy.merge(payload);
				copy.putString("manager", manager.getName());
				hits.pushMap(copy);
			}
		}

		mCallback.onHitTest(eventName, e.getX(), e.getY(), hits);
		// Never consume — geometry layers and their per-layer gesture
		// events continue to work as before.
		return false;
	}
}
