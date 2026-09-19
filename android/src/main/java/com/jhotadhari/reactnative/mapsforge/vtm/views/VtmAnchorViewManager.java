package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewManagerDelegate;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.viewmanagers.VtmAnchorViewManagerInterface;
import com.facebook.react.viewmanagers.VtmAnchorViewManagerDelegate;

import java.util.ArrayList;
import java.util.List;

@ReactModule(name = VtmAnchorViewManager.NAME)
public class VtmAnchorViewManager extends SimpleViewManager<VtmAnchorView> implements VtmAnchorViewManagerInterface<VtmAnchorView> {

	public static final String NAME = "VtmAnchorView";

	private final ViewManagerDelegate<VtmAnchorView> mDelegate;

	public VtmAnchorViewManager() {
		mDelegate = new VtmAnchorViewManagerDelegate( this );
	}

	@Override
	public ViewManagerDelegate<VtmAnchorView> getDelegate() {
		return mDelegate;
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@NonNull
	@Override
	public VtmAnchorView createViewInstance( ThemedReactContext context ) {
		return new VtmAnchorView( context );
	}

	@ReactProp( name = "uid" )
	@Override
	public void setUid( VtmAnchorView view, @Nullable String value ) {
		view.setUid( value );
	}

	/**
	 * Depth-first walk over {@code root}, collecting uids of all
	 * {@link VtmAnchorView} instances in committed child order. The subtree
	 * rooted at {@code exclude} (the MapsforgeVtmView host) is skipped — it
	 * contains no anchors.
	 */
	public static List<String> collectAnchorUids( @NonNull ViewGroup root, @Nullable View exclude ) {
		List<String> uids = new ArrayList<>();
		collect( root, exclude, uids );
		return uids;
	}

	private static void collect( @NonNull ViewGroup group, @Nullable View exclude, @NonNull List<String> uids ) {
		int childCount = group.getChildCount();
		for ( int i = 0; i < childCount; i++ ) {
			View child = group.getChildAt( i );
			if ( child == exclude ) {
				continue;
			}
			if ( child instanceof VtmAnchorView ) {
				String uid = ( (VtmAnchorView) child ).getUid();
				if ( uid != null ) {
					uids.add( uid );
				}
			} else if ( child instanceof ViewGroup ) {
				collect( (ViewGroup) child, exclude, uids );
			}
		}
	}
}
