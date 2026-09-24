package com.jhotadhari.reactnative.mapsforge.vtm;

import static org.junit.Assert.assertEquals;

import android.view.View;
import android.widget.FrameLayout;

import com.jhotadhari.reactnative.mapsforge.vtm.views.VtmAnchorView;
import com.jhotadhari.reactnative.mapsforge.vtm.views.VtmAnchorViewManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith( RobolectricTestRunner.class )
@Config( sdk = 33 )
public class VtmAnchorWalkTest {

	private VtmAnchorView anchor( String uid ) {
		VtmAnchorView anchor = new VtmAnchorView( RuntimeEnvironment.getApplication() );
		anchor.setUid( uid );
		return anchor;
	}

	@Test
	public void collectAnchorUids_returnsCommittedTreeOrder() {
		FrameLayout root = new FrameLayout( RuntimeEnvironment.getApplication() );
		FrameLayout inner = new FrameLayout( RuntimeEnvironment.getApplication() );

		root.addView( anchor( "a" ) );
		inner.addView( anchor( "b" ) );
		inner.addView( anchor( "c" ) );
		root.addView( inner );
		root.addView( anchor( "d" ) );

		List<String> uids = VtmAnchorViewManager.collectAnchorUids( root, null );
		assertEquals( List.of( "a", "b", "c", "d" ), uids );
	}

	@Test
	public void collectAnchorUids_skipsExcludedSubtree() {
		FrameLayout root = new FrameLayout( RuntimeEnvironment.getApplication() );
		FrameLayout excluded = new FrameLayout( RuntimeEnvironment.getApplication() );
		excluded.addView( anchor( "hidden" ) );

		root.addView( anchor( "a" ) );
		root.addView( excluded );
		root.addView( anchor( "b" ) );

		List<String> uids = VtmAnchorViewManager.collectAnchorUids( root, excluded );
		assertEquals( List.of( "a", "b" ), uids );
	}

	@Test
	public void collectAnchorUids_ignoresNonAnchorViews() {
		FrameLayout root = new FrameLayout( RuntimeEnvironment.getApplication() );
		root.addView( new View( RuntimeEnvironment.getApplication() ) );
		root.addView( anchor( "a" ) );
		root.addView( new FrameLayout( RuntimeEnvironment.getApplication() ) );

		List<String> uids = VtmAnchorViewManager.collectAnchorUids( root, null );
		assertEquals( List.of( "a" ), uids );
	}

	@Test
	public void collectAnchorUids_skipsNullUidAnchors() {
		FrameLayout root = new FrameLayout( RuntimeEnvironment.getApplication() );
		root.addView( new VtmAnchorView( RuntimeEnvironment.getApplication() ) );
		root.addView( anchor( "a" ) );

		List<String> uids = VtmAnchorViewManager.collectAnchorUids( root, null );
		assertEquals( List.of( "a" ), uids );
	}
}
