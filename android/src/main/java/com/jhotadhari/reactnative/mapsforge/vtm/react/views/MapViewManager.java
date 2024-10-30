// replace with your package
package com.jhotadhari.reactnative.mapsforge.vtm.react.views;

import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.annotations.ReactPropGroup;

import java.util.Map;

public class MapViewManager extends ViewGroupManager<FrameLayout> {

	public static final String REACT_CLASS = "MapViewManager";
	public final int COMMAND_CREATE = 1;
	protected MapFragment mapFragment;

	private double propWidth;
	private double propHeight;
	private double propWidthForLayoutSize;
	private double propHeightForLayoutSize;

	private ReadableMap propCenter;

	private boolean propMoveEnabled;
	private boolean propRotationEnabled;
	private boolean propZoomEnabled;
	private boolean propTiltEnabled;

	private int propZoomLevel;
	private int propMinZoom;
	private int propMaxZoom;

	private float propTilt;
	private float propMinTilt;
	private float propMaxTilt;

	private float propBearing;
	private float propMinBearing;
	private float propMaxBearing;

	private float propRoll;
	private float propMinRoll;
	private float propMaxRoll;

	private String propHgtDirPath;
	private ReadableMap propResponseInclude;

	ReactApplicationContext reactContext;

	public ReactApplicationContext getReactContext() {
		return reactContext;
	}

	public MapViewManager( ReactApplicationContext reactContext ) {
		this.reactContext = reactContext;
	}

	@Override
	public String getName() {
		return REACT_CLASS;
	}

	/**
	 * Return a FrameLayout which will later hold the Fragment
	  */
	@Override
	public FrameLayout createViewInstance( ThemedReactContext reactContext ) {
		return new FrameLayout( reactContext );
	}

	/**
	 * Map the "create" command to an integer
	  */
	@Nullable
	@Override public Map<String, Integer> getCommandsMap() {


//		Log.d("testtest", "1".asString() ) ;

		return MapBuilder.of("create", COMMAND_CREATE);
	}

	@Override
	public void receiveCommand(
		@NonNull FrameLayout root,
		int commandId,
		@Nullable ReadableArray args
	) {
		receiveCommand(root, String.valueOf( commandId ), args );
	}

	/**
	 * Handle "create" command (called from JS) and call createFragment method
	 */
	@Override
	public void receiveCommand(
		@NonNull FrameLayout root,
		String commandId,
		@Nullable ReadableArray args
	) {
		super.receiveCommand(root, commandId, args);

		int reactNativeViewId = args.getInt(0);
		int commandIdInt = Integer.parseInt(commandId);

		switch ( commandIdInt ) {
			case COMMAND_CREATE:
				createFragment( root, reactNativeViewId );
					break;
				default: {}
		}
	}

	@ReactPropGroup( names = {
		"width",
		"height",
		"widthForLayoutSize",
		"heightForLayoutSize"
	} )
	public void setReactPropsDimensions(FrameLayout view, int index, double value) {
		if (index == 0) {
			propWidth = value;
		}
		if (index == 1) {
			propHeight = value;
		}
		if (index == 2) {
			propWidthForLayoutSize = value;
		}
		if (index == 3) {
			propHeightForLayoutSize = value;
		}
	}

	@ReactProp( name="center" )
	public void setReactPropCenter( FrameLayout view, ReadableMap value ) {
		propCenter = value;
	}

	@ReactPropGroup( names = {
		"moveEnabled",
		"rotationEnabled",
		"zoomEnabled",
		"tiltEnabled",
	} )
	public void setReactPropsInteractionsEnabled( FrameLayout view, int index, int value ) {	// boolean doesn't work, let's use int

		if ( index == 0 ) {
			propMoveEnabled = value == 1;
		}
		if ( index == 1 ) {
			propRotationEnabled = value == 1;
		}
		if ( index == 2 ) {
			propZoomEnabled = value == 1;
		}
		if ( index == 3 ) {
			propTiltEnabled = value == 1;
		}
	}

	@ReactPropGroup( names = {
		"zoomLevel",
		"minZoom",
		"maxZoom",
	} )
	public void setReactPropsViewportPosInt( FrameLayout view, int index, int value ) {
		// zoom
		if ( index == 0 ) {
			propZoomLevel = value;
		}
		if ( index == 1 ) {
			propMinZoom = value;
		}
		if ( index == 2 ) {
			propMaxZoom = value;
		}
	}

	@ReactPropGroup( names = {
		// tilt
		"tilt",
		"minTilt",
		"maxTilt",
		// bearing
		"bearing",
		"minBearing",
		"maxBearing",
		// roll
		"roll",
		"minRoll",
		"maxRoll",
	} )

	public void setReactPropsViewportPosFloat( FrameLayout view, int index, float value ) {
		// tilt
		if ( index == 0 ) {
			propTilt = value;
		}
		if ( index == 1 ) {
			propMinTilt = value;
		}
		if ( index == 2 ) {
			propMaxTilt = value;
		}
		// bearing
		if ( index == 3 ) {
			propBearing = value;
		}
		if ( index == 4 ) {
			propMinBearing = value;
		}
		if ( index == 5 ) {
			propMaxBearing = value;
		}
		// roll
		if ( index == 6 ) {
			propRoll = value;
		}
		if ( index == 7 ) {
			propMinRoll = value;
		}
		if ( index == 8 ) {
			propMaxRoll = value;
		}
	}

	@ReactProp( name="hgtDirPath" )
	public void setReactPropCenter( FrameLayout view, String value ) {
		propHgtDirPath = value;
	}

	@ReactProp( name="responseInclude" )
	public void setReactPropResponseInclude( FrameLayout view, ReadableMap value ) {
		propResponseInclude = value;
	}

	/**
	 * Replace React Native view with a custom fragment
	  */
	public void createFragment( FrameLayout root, int reactNativeViewId ) {
		ViewGroup parentView = ( ViewGroup ) root.findViewById( reactNativeViewId );



		mapFragment = new MapFragment(
			this,

			propWidthForLayoutSize,
			propHeightForLayoutSize,

			propCenter,

			propMoveEnabled,
			propRotationEnabled,
			propZoomEnabled,
			propTiltEnabled,

			propZoomLevel,
			propMinZoom,
			propMaxZoom,

			propTilt,
			propMinTilt,
			propMaxTilt,

			propBearing,
			propMinBearing,
			propMaxBearing,

			propRoll,
			propMinRoll,
			propMaxRoll,

			propHgtDirPath,

			propResponseInclude

		);

		setupLayout( parentView );

		FragmentActivity activity = (FragmentActivity) reactContext.getCurrentActivity();
		activity.getSupportFragmentManager()
				.beginTransaction()
				.replace(reactNativeViewId, mapFragment, String.valueOf(reactNativeViewId))
				.commit();
	}

	public void setupLayout(ViewGroup view) {
		Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
		@Override
		public void doFrame(long frameTimeNanos) {
			manuallyLayoutChildren(view);
			view.getViewTreeObserver().dispatchOnGlobalLayout();
			Choreographer.getInstance().postFrameCallback(this);
		}
		});
	}

	/**
	 * Layout all children properly
	  */
	public void manuallyLayoutChildren(ViewGroup view) {
		// propWidth and propHeight coming from react-native props
		double width = propWidth;
		double height = propHeight;
		for ( int i = 0; i < view.getChildCount(); i++ ) {
			View child = view.getChildAt( i );
			child.measure(
				View.MeasureSpec.makeMeasureSpec( (int) width, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec( (int) height, View.MeasureSpec.EXACTLY)
			);
			child.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
		}
		mapFragment.updateViewLayoutSize( propWidthForLayoutSize, propHeightForLayoutSize );
	}

}
