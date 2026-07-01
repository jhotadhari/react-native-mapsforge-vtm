package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.annotation.SuppressLint;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

@SuppressLint( "ViewConstructor" )
public class MapsforgeVtmView extends LinearLayout {

	private MapFragment mapFragment;

	private double width;	// dp
	private double height;	// dp
	private ReadableArray center;	// Position: [ lng, lat, alt? ]
	private int zoomLevel;
	private int zoomMin;
	private int zoomMax;
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
	private ReadableMap responseInclude;
	private int mapUpdateInterval;
	private boolean emitsMapUpdateEvents;

	public MapsforgeVtmView( ThemedReactContext context ) { super(context); }

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
				break;
			case "height":
				this.height = dimension;
				break;
		}
	}

	public double getDimension( String key, String unit ) {
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

	public void setZoomLevel( int zoomLevel ) {
		this.zoomLevel = zoomLevel;
		if ( null != mapFragment ) {
			mapFragment.updateZoomLevel();
		}
	}

	public int getZoomLevel() {
		return zoomLevel;
	}

	public void setZoomBounds( String key, int value ) {
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

	public int getZoomBounds( String bound ) {
		return switch ( bound ) {
			case "min" -> zoomMin;
			case "max" -> zoomMax;
			default -> 0;
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

	public void setResponseInclude( ReadableMap responseInclude ) {
		this.responseInclude = responseInclude;
	}

	public ReadableMap getResponseInclude() {
		return responseInclude;
	}

	public void setMapUpdateInterval( int mapUpdateInterval ) {
		this.mapUpdateInterval = mapUpdateInterval;
		if ( null != mapFragment ) {
			mapFragment.updateRateLimiterRate();
		}
	}

	public int getMapUpdateInterval() {
		return mapUpdateInterval;
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

	public void createFragment() {
		if ( null == mapFragment ) {
			mapFragment = new MapFragment();
			setupLayout( this );
			FragmentActivity activity = (FragmentActivity) getReactContext().getCurrentActivity();
			if ( activity != null ) {
				activity.getSupportFragmentManager().beginTransaction()
					.replace( this.getId(), mapFragment, String.valueOf( this.getId() ) )
					.commit();
			}
		}
	}

	public void setupLayout(ViewGroup view) {
		Choreographer.getInstance().postFrameCallback( new Choreographer.FrameCallback() {
			@Override
			public void doFrame(long frameTimeNanos) {
				manuallyLayoutChildren(view);
				view.getViewTreeObserver().dispatchOnGlobalLayout();
				Choreographer.getInstance().postFrameCallback( this );
			}
		} );
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
