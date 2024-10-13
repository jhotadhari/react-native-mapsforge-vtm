// replace with your package
package com.jhotadhari.reactnative.mapsforge.vtm;

import android.graphics.Color;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.annotations.ReactPropGroup;

import java.util.ArrayList;
import java.util.Map;

public class MapViewManager extends ViewGroupManager<FrameLayout> {

  public static final String REACT_CLASS = "MapViewManager";
  public final int COMMAND_CREATE = 1;
  private double propWidth;
  private double propHeight;
  private double propWidthForLayoutSize;
  private double propHeightForLayoutSize;

  private double propZoom;
  private double propMinZoom;
  private double propMaxZoom;
  private ArrayList propCenter;

  ReactApplicationContext reactContext;

  public MapViewManager(ReactApplicationContext reactContext) {
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
  public FrameLayout createViewInstance(ThemedReactContext reactContext) {
    return new FrameLayout(reactContext);
  }

  /**
   * Map the "create" command to an integer
   */
  @Nullable
  @Override
  public Map<String, Integer> getCommandsMap() {
    return MapBuilder.of("create", COMMAND_CREATE);
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

    switch (commandIdInt) {
      case COMMAND_CREATE:
        createFragment(root, reactNativeViewId);
        break;
      default: {}
    }
  }

  @ReactPropGroup(names = {"width", "height", "widthForLayoutSize", "heightForLayoutSize"})
  public void setReactPropsStyle(FrameLayout view, int index, double value) {
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

  @ReactPropGroup(names = {"zoom", "minZoom","maxZoom"})
  public void setReactPropsZoom(FrameLayout view, int index, double value) {
    if (index == 0) {
      propZoom = value;
    }
    if (index == 1) {
      propMinZoom = value;
    }
    if (index == 2) {
      propMaxZoom = value;
    }
  }

  @ReactProp(name="center")
  public void setReactPropCenter(FrameLayout view, ReadableArray value) {
    propCenter = value.toArrayList();
  }

  /**
   * Replace React Native view with a custom fragment
   */
  public void createFragment(FrameLayout root, int reactNativeViewId) {
    ViewGroup parentView = (ViewGroup) root.findViewById(reactNativeViewId);
    setupLayout(parentView);
    final MapFragment mapFragment = new MapFragment( reactContext, propCenter, propZoom, propMinZoom, propMaxZoom, propWidthForLayoutSize, propHeightForLayoutSize );
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
  }
}
