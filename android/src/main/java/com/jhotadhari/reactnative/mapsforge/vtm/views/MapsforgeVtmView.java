package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressLint( "ViewConstructor" )
public class MapsforgeVtmView extends LinearLayout {

	private MapFragment mapFragment;

	private double width;	// dp
	private double height;	// dp

	/**
	 * Track whether the JS side has explicitly set width / height props.
	 * When false, getDimension / getWidthInDp / getHeightInDp fall back to
	 * the actual measured view dimensions so the component can participate
	 * in Yoga flex layout (e.g. {@code flex: 1}) without explicit JS props.
	 */
	private boolean hasExplicitWidth = false;
	private boolean hasExplicitHeight = false;
	private ReadableArray center;	// Position: [ lng, lat, alt? ]
	private float zoomLevel;
	private float zoomMin;
	private float zoomMax;
	private boolean moveEnabled;
	private boolean tiltEnabled;
	private boolean rotationEnabled;
	private boolean zoomEnabled;
	private double tilt;
	private double minTilt;
	private double maxTilt;
	private double bearing;
	private double minBearing;
	private double maxBearing;
	private double roll;
	private double minRoll;
	private double maxRoll;
	private boolean emitsMapUpdateEvents;
	private ReadableMap gnssFilter;

	public MapsforgeVtmView( ThemedReactContext context ) { super(context); }

	/**
	 * Hierarchy listener on the wrapper View (this view's parent) — the only
	 * native signal that catches anchor reordering without any React commit
	 * touching a library component (identity-preserved element moves).
	 * Fires onAnchorsChanged, which the JS presenter debounces into a walk.
	 *
	 * Coverage: direct-child VtmAnchorViews of the wrapper (and their
	 * descendants — anchors are the leaf markers of every ordering-relevant
	 * component), while this view is attached. Detached-phase moves fire no
	 * events, so {@link #onAttachedToWindow()} re-walks on (re)attach.
	 *
	 * <p>Multiple {@code MapsforgeVtmView}s can share one wrapper (multi-map).
	 * A {@code ViewGroup} has a single hierarchy-listener slot, so each view
	 * registers its own delegate into a per-wrapper {@link CompositeHierarchyListener};
	 * the composite chains any pre-existing (foreign) listener and all registered
	 * delegates, and the foreign listener is restored when the last view leaves.
	 */
	/** The wrapper the listener is installed on — cached so teardown never
	 * depends on {@code getParent()} (null mid-detach). */
	@Nullable
	private ViewGroup wrapperView;
	/** This view's own delegate registered in the wrapper's composite listener. */
	@Nullable
	private ViewGroup.OnHierarchyChangeListener ownDelegate;
	/** Tracks first attach so the initial (spurious) anchors-changed emit is skipped. */
	private boolean everAttached = false;

	/**
	 * Per-wrapper composite listener registry, keyed by the wrapper ViewGroup.
	 *
	 * <p>All access runs on the UI thread (install from
	 * {@link #onAttachedToWindow()}, remove from {@link #onDetachedFromWindow()}
	 * and {@link #destroy()}). The concurrent types are defensive, not a
	 * thread-safety guarantee. Entries are purged when the last delegate
	 * leaves; on abnormal teardown (a view attached but never detached nor
	 * destroyed) the entry — and via its delegates, the view — can outlive
	 * the map, which is the residual cost of the single-slot ViewGroup API.
	 */
	private static final ConcurrentHashMap<ViewGroup, CompositeHierarchyListener> compositeListeners =
		new ConcurrentHashMap<>();

	/**
	 * A chain of hierarchy listeners: the wrapper's original (foreign) listener
	 * plus one delegate per {@code MapsforgeVtmView}. Invokes all of them so
	 * (a) the foreign listener keeps receiving events and (b) sibling map views
	 * under the same wrapper don't clobber each other.
	 */
	private static final class CompositeHierarchyListener
		implements ViewGroup.OnHierarchyChangeListener {

		@Nullable
		final ViewGroup.OnHierarchyChangeListener foreign;
		final CopyOnWriteArrayList<ViewGroup.OnHierarchyChangeListener> delegates =
			new CopyOnWriteArrayList<>();

		CompositeHierarchyListener( @Nullable ViewGroup.OnHierarchyChangeListener foreign ) {
			this.foreign = foreign;
		}

		@Override
		public void onChildViewAdded( View parent, View child ) {
			if ( foreign != null ) {
				foreign.onChildViewAdded( parent, child );
			}
			for ( ViewGroup.OnHierarchyChangeListener delegate : delegates ) {
				delegate.onChildViewAdded( parent, child );
			}
		}

		@Override
		public void onChildViewRemoved( View parent, View child ) {
			if ( foreign != null ) {
				foreign.onChildViewRemoved( parent, child );
			}
			for ( ViewGroup.OnHierarchyChangeListener delegate : delegates ) {
				delegate.onChildViewRemoved( parent, child );
			}
		}
	}

	/** Installs the move-signal listener on the wrapper, preserving any
	 * pre-existing listener. Idempotent. */
	private void installHierarchyListener( @NonNull ViewGroup wrapper ) {
		if ( wrapperView != null ) {
			return;
		}
		wrapperView = wrapper;

		CompositeHierarchyListener composite = compositeListeners.computeIfAbsent(
			wrapper,
			w -> {
				// Seed the composite with the wrapper's current listener (read
				// reflectively — there is no public getter) so it keeps firing.
				CompositeHierarchyListener created =
					new CompositeHierarchyListener( getHierarchyChangeListener( w ) );
				w.setOnHierarchyChangeListener( created );
				return created;
			}
		);

		ownDelegate = new ViewGroup.OnHierarchyChangeListener() {
			@Override
			public void onChildViewAdded( View parent, View child ) {
				if ( child instanceof VtmAnchorView ) {
					emitAnchorsChanged();
				}
			}

			@Override
			public void onChildViewRemoved( View parent, View child ) {
				if ( child instanceof VtmAnchorView ) {
					emitAnchorsChanged();
				}
			}
		};
		composite.delegates.add( ownDelegate );
	}

	/** Removes this view's delegate via the cached wrapper reference and, when
	 * it was the last one, restores the wrapper's original listener. Idempotent. */
	private void removeHierarchyListener() {
		ViewGroup wrapper = wrapperView;
		ViewGroup.OnHierarchyChangeListener delegate = ownDelegate;
		wrapperView = null;
		ownDelegate = null;
		if ( wrapper == null || delegate == null ) {
			return;
		}

		CompositeHierarchyListener composite = compositeListeners.get( wrapper );
		if ( composite != null ) {
			composite.delegates.remove( delegate );
			if ( composite.delegates.isEmpty() ) {
				compositeListeners.remove( wrapper );
				// Only restore the captured foreign listener if the wrapper
				// still holds OUR composite — a newer external listener set
				// mid-lifetime must not be clobbered by the stale seed.
				if ( getHierarchyChangeListener( wrapper ) == composite ) {
					wrapper.setOnHierarchyChangeListener( composite.foreign );
				}
			}
		}
	}

	/**
	 * Reads a ViewGroup's hierarchy-change listener. There is no public
	 * getter — the private field is the only way to seed the composite with a
	 * pre-existing listener. Best-effort: hidden-API restrictions may block the
	 * read on API 28+; we degrade to {@code null} (and log) rather than crash.
	 */
	@Nullable
	private static ViewGroup.OnHierarchyChangeListener getHierarchyChangeListener(
		@NonNull ViewGroup group
	) {
		try {
			Field field = ViewGroup.class.getDeclaredField(
				"mOnHierarchyChangeListener"
			);
			field.setAccessible( true );
			return (ViewGroup.OnHierarchyChangeListener) field.get( group );
		} catch ( Exception e ) {
			// Best-effort: the private field is hidden-API-restricted (or
			// renamed) on modern Android, so this commonly throws. Degrade to
			// a null seed — our own composite chaining still works without it.
			Log.d( "MapsforgeVtmView",
				"Could not read pre-existing hierarchy listener (best-effort): " + e );
			return null;
		}
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		if ( getParent() instanceof ViewGroup ) {
			installHierarchyListener( (ViewGroup) getParent() );
		}
		if ( everAttached ) {
			// Re-attach after a detach: anchors may have been moved while
			// detached (the wrapper fires no hierarchy events for a detached
			// tree) — re-walk so the scene heals. Skipped on first attach:
			// the initial order is established by the JS side (MapContainer's
			// handleAnchorsChanged → scheduleWalk, plus the mount-time reorder),
			// so there is no JS subscriber to consume a first-attach emit.
			emitAnchorsChanged();
		}
		everAttached = true;
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		removeHierarchyListener();
	}

	public void emitAnchorsChanged() {
		emitMapEvent( "onAnchorsChanged", Arguments.createMap() );
	}

	@Override
	public void onLayout( boolean changed, int l, int t, int r, int b ) {
		super.onLayout( changed, l, t, r, b );
		createFragment();
	}

	public final ThemedReactContext getReactContext() {
		return (ThemedReactContext) getContext();
	}

	public void emitMapEvent( String eventName, WritableMap payload  ) {
		int surfaceId = UIManagerHelper.getSurfaceId(getReactContext());
		EventDispatcher eventDispatcher = UIManagerHelper.getEventDispatcherForReactTag( getReactContext(), getId() );
		if ( eventDispatcher != null ) {
			MapEvent event = new MapEvent(surfaceId, getId(), eventName, payload );
			eventDispatcher.dispatchEvent( event );
		}
	}


	public void setDimension( String key, double dimension ) {
		switch ( key ) {
			case "width":
				this.width = dimension;
				hasExplicitWidth = true;
				break;
			case "height":
				this.height = dimension;
				hasExplicitHeight = true;
				break;
		}
		requestLayout();
	}

	public double getDimension( String key, String unit ) {
		// When the caller needs pixels and no explicit JS prop was set,
		// use the actual measured view dimensions — this is what makes
		// flex:1 work without JS-side width/height props.
		if ( "px".equals( unit ) ) {
			if ( "width".equals( key ) && ! hasExplicitWidth ) {
				return getMeasuredWidth();
			}
			if ( "height".equals( key ) && ! hasExplicitHeight ) {
				return getMeasuredHeight();
			}
		}

		double dimension = switch ( key ) {
			case "width" -> width;
			case "height" -> height;
			default -> 0;
		};
		if ( "px".equals( unit ) ) {
			dimension = (double) Utils.convertDpToPixel( (float) dimension, getContext() );
		}
		return dimension;
	}

	/**
	 * Returns the viewport width in dp.  When an explicit JS prop was set
	 * the stored value (already dp) is returned; otherwise the actual measured
	 * width in pixels is converted to dp so flex layout works transparently.
	 */
	public double getWidthInDp() {
		return hasExplicitWidth
			? width
			: Utils.convertPixelsToDp( getMeasuredWidth(), getContext() );
	}

	/**
	 * Returns the viewport height in dp.
	 * @see #getWidthInDp()
	 */
	public double getHeightInDp() {
		return hasExplicitHeight
			? height
			: Utils.convertPixelsToDp( getMeasuredHeight(), getContext() );
	}

	public void setCenter( @Nullable ReadableArray center ) {
		if ( null != center ) {
			this.center = center;
			if ( null != mapFragment ) {
				mapFragment.updateCenter();
			}
		}
	}

	public ReadableArray getCenter() {
		return center;
	}

	public void setZoomLevel( float zoomLevel ) {
		this.zoomLevel = zoomLevel;
		if ( null != mapFragment ) {
			mapFragment.updateZoomLevel();
		}
	}

	public float getZoomLevel() {
		return zoomLevel;
	}

	public void setZoomBounds( String key, float value ) {
		switch ( key ) {
			case "min":
				zoomMin = value;
				break;
			case "max":
				zoomMax = value;
				break;
		}
		if ( null != mapFragment ) {
			mapFragment.updateZoomBounds();
		}
	}

	public float getZoomBounds( String bound ) {
		return switch ( bound ) {
			case "min" -> zoomMin;
			case "max" -> zoomMax;
			default -> 0f;
		};
	}

	public void setViewportValue( String key, double value ) {
		switch ( key ) {
			case "tilt":
				tilt = value;
				break;
			case "bearing":
				bearing = value;
				break;
			case "roll":
				roll = value;
				break;
		}
		if ( null != mapFragment ) {
			mapFragment.updateViewportValue( key );
		}
	}

	public double getViewportValue( String key ) {
		return switch ( key ) {
			case "tilt" -> tilt;
			case "bearing" -> bearing;
			case "roll" -> roll;
			default -> 0;
		};
	}

	public void setViewportBounds( String key, String bound, double value ) {
		switch ( key ) {
			case "tilt":
				switch ( bound ) {
					case "min":
						minTilt = value;
						break;
					case "max":
						maxTilt = value;
						break;
				}
				break;
			case "bearing":
				switch ( bound ) {
					case "min":
						minBearing = value;
						break;
					case "max":
						maxBearing = value;
						break;
				}
				break;
			case "roll":
				switch ( bound ) {
					case "min":
						minRoll = value;
						break;
					case "max":
						maxRoll = value;
						break;
				}
				break;
		}
		if ( null != mapFragment ) {
			mapFragment.updateViewportBounds( key );
		}
	}

	public double getViewportBounds( String key, String bound ) {
		return switch ( key ) {
			case "tilt" -> switch ( bound ) {
				case "min" -> minTilt;
				case "max" -> maxTilt;
				default -> 0;
			};
			case "bearing" -> switch ( bound ) {
				case "min" -> minBearing;
				case "max" -> maxBearing;
				default -> 0;
			};
			case "roll" -> switch ( bound ) {
				case "min" -> minRoll;
				case "max" -> maxRoll;
				default -> 0;
			};
			default -> 0;
		};
	}

	public void setInteractionEnabled( String key, boolean value ) {
		switch ( key ) {
			case "move":
				moveEnabled = value;
				break;
			case "tilt":
				tiltEnabled = value;
				break;
			case "rotation":
				rotationEnabled = value;
				break;
			case "zoom":
				zoomEnabled = value;
				break;
		}
		if ( null != mapFragment ) {
			mapFragment.updateInteractionEnabled();
		}
	}

	public boolean getInteractionEnabled( String key ) {
		return switch ( key ) {
			case "move" -> moveEnabled;
			case "tilt" -> tiltEnabled;
			case "rotation" -> rotationEnabled;
			case "zoom" -> zoomEnabled;
			default -> false;
		};
	}

	public void setEmitsMapUpdateEvents( boolean emitsMapUpdateEvents ) {
		this.emitsMapUpdateEvents = emitsMapUpdateEvents;
		if ( null != mapFragment ) {
			mapFragment.updateUpdateListener();
		}
	}

	public boolean getEmitsMapUpdateEvents() {
		return emitsMapUpdateEvents;
	}

	public void setGnssFilter( @Nullable ReadableMap gnssFilter ) {
		this.gnssFilter = gnssFilter;
		if ( null != mapFragment ) {
			mapFragment.updateGnssFilter();
		}
	}

	@Nullable
	public ReadableMap getGnssFilter() {
		return gnssFilter;
	}

	/**
	 * Emit a {@code onGnssPosition} event to JS.
	 * Called from {@link com.jhotadhari.reactnative.mapsforge.vtm.gnss.GnssManager}
	 * on each qualifying GNSS position.
	 */
	public void emitGnssPosition( WritableMap payload ) {
		emitMapEvent( "onGnssPosition", payload );
	}

	public void createFragment() {
		if ( null == mapFragment ) {
			mapFragment = new MapFragment();
			int handle = this.getId();
			// Register immediately so Utils.getMapFragment can find
			// this fragment even before the FragmentManager transaction
			// below executes.  This is what makes multi-map work:
			// layer creation on a second MapContainer can look up the
			// correct fragment by nativeNodeHandle without racing the
			// async commit().
			fragmentRegistry.put( handle, mapFragment );

				// Create a native shared-value writer for this map view.
				// When reanimated is not installed the native library
				// won't be built — UnsatisfiedLinkError is caught and
				// the legacy Fabric-event path continues to work.
				if ( com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
						.ensureLibraryLoaded() ) {
					com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
						.nativeCreateWriter( handle );
				}
			setupLayout( this );
			FragmentActivity activity = (FragmentActivity) getReactContext().getCurrentActivity();
			if ( activity != null ) {
				activity.getSupportFragmentManager().beginTransaction()
					.replace( handle, mapFragment, String.valueOf( handle ) )
					.commitNow();
				// commitNow() created the fragment synchronously, but the
				// new child view may not have been measured yet. Post a
				// one-shot layout so the MapView gets correct dimensions
				// before any layer tries to load tiles.
				post( () -> manuallyLayoutChildren( MapsforgeVtmView.this ) );
			}
		}
	}

	/**
	 * Tear down the MapFragment (which cleans up LayerManagers,
	 * MapMutationQueue, ElevationReader, and MapPositionWriter)
	 * and remove it from the FragmentManager.  Called when React
	 * Native permanently removes this native view, e.g. when
	 * MapContainer is unmounted.
	 */
	public void destroy() {
		removeHierarchyListener();
		if ( null != mapFragment ) {
			int handle = this.getId();
			mapFragment.onDestroy();
			mapFragment = null;

			FragmentActivity activity = (FragmentActivity) getReactContext().getCurrentActivity();
			if ( activity != null ) {
				androidx.fragment.app.Fragment frag =
					activity.getSupportFragmentManager().findFragmentByTag( String.valueOf( handle ) );
				if ( frag != null ) {
					activity.getSupportFragmentManager().beginTransaction()
						.remove( frag )
						.commitNowAllowingStateLoss();
				}
			}
		}
	}

	public void setupLayout(ViewGroup view) {
		// Use a one-shot OnLayoutChangeListener instead of a continuous
		// Choreographer.FrameCallback loop.  The old continuous callback
		// called the hidden API dispatchOnGlobalLayout() every frame,
		// which caused layout thrashing between multiple MapsforgeVtmView
		// instances — each view's callback would trigger re-layout of
		// the other, continuously recreating GL surfaces and preventing
		// tiles from loading (gray map) or corrupting textures (black
		// tiles).
		view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right,
					int bottom, int oldLeft, int oldTop, int oldRight,
					int oldBottom) {
				manuallyLayoutChildren((ViewGroup) v);
			}
		});
	}

	public void manuallyLayoutChildren(ViewGroup view) {
		for ( int i = 0; i < view.getChildCount(); i++ ) {
			View child = view.getChildAt( i );
			child.measure(
				View.MeasureSpec.makeMeasureSpec( (int) getDimension( "width", "px" ), View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec( (int) getDimension( "height", "px" ), View.MeasureSpec.EXACTLY)
			);
			child.layout(
				0,
				0,
				view.getMeasuredWidth(),
				view.getMeasuredHeight()
			);
		}

		mapFragment.fixViewLayoutSize();
	}

	// ------------------------------------------------------------------
	// Fragment registry — maps nativeNodeHandle → MapFragment so layer
	// creation can find the correct fragment without relying on the async
	// FragmentManager.commit() having already executed.  Multiple
	// MapsforgeVtmViews in the same activity each get their own entry.
	// ------------------------------------------------------------------

	private static final ConcurrentHashMap<Integer, MapFragment> fragmentRegistry = new ConcurrentHashMap<>();

	/**
	 * Returns the {@link MapFragment} registered for {@code nativeNodeHandle},
	 * or {@code null} if no fragment has been registered yet.
	 */
	@Nullable
	public static MapFragment getFragment(int nativeNodeHandle) {
		return fragmentRegistry.get(nativeNodeHandle);
	}

	/**
	 * Removes the fragment registration for {@code nativeNodeHandle}.
	 * Called from {@link MapFragment#onDestroy} during teardown.
	 */
	public static void removeFragment(int nativeNodeHandle) {
		fragmentRegistry.remove(nativeNodeHandle);
	}

	private class MapEvent extends Event<MapEvent> {
		private final WritableMap payload;
		private final String eventName;

		MapEvent( int surfaceId, int viewId, String eventName, WritableMap payload) {
			super(surfaceId, viewId);
			this.payload = payload;
			this.eventName = eventName;
		}

		@NonNull
		@Override
		public String getEventName() {
			return eventName;
		}

		@Override
		public WritableMap getEventData() {
			return payload;
		}
	}
}
