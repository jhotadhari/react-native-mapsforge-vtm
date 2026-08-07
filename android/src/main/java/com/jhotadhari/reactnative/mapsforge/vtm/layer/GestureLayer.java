package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import org.oscim.event.Gesture;
import org.oscim.event.GestureListener;
import org.oscim.event.MotionEvent;
import org.oscim.layers.Layer;
import org.oscim.map.Map;

/**
 * A transparent vtm {@link Layer} that implements {@link GestureListener} to
 * intercept unconsumed {@link Gesture.Tap} and {@link Gesture.LongPress}
 * events at the map level.
 *
 * <p>This layer is intentionally added at the highest index in
 * {@code map.layers()}, so it only receives gestures that marker and path
 * layers returned {@code false} for (i.e. the tap missed their geometry).
 * Recognised gestures are consumed (returns {@code true}) so they do not
 * propagate further. All other gestures — {@code Move}, {@code Press},
 * {@code DoubleTap}, etc. — pass through unhandled (returns {@code false}),
 * leaving the standard {@code MapEventLayer} behaviour intact.</p>
 */
public class GestureLayer extends Layer implements GestureListener {

	/**
	 * Callback interface so the owning fragment can emit Fabric events
	 * without this layer needing a direct reference to the view hierarchy.
	 */
	public interface GestureCallback {
		void onGesture(String eventName, float x, float y);
	}

	private final GestureCallback mCallback;

	public GestureLayer(Map map, GestureCallback callback) {
		super(map);
		mCallback = callback;
	}

	@Override
	public boolean onGesture(Gesture g, MotionEvent e) {
		if (mCallback == null) {
			return false;
		}
		if (g instanceof Gesture.Tap) {
			mCallback.onGesture("onTap", e.getX(), e.getY());
			return true;
		} else if (g instanceof Gesture.LongPress) {
			mCallback.onGesture("onLongPress", e.getX(), e.getY());
			return true;
		}
		// Pass everything else — Move, Press, DoubleTap — so
		// MapEventLayer (pan/zoom/tilt/rotate) works normally.
		return false;
	}
}
