package com.jhotadhari.reactnative.mapsforge.vtm.views;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Invisible 0x0 marker view used only to make layer ordering visible to a
 * committed-tree walk. Never draws, never takes part in layout (forced
 * 0x0 measure), ignores pointer events.
 */
public class VtmAnchorView extends View {

	@Nullable
	private String uid;

	public VtmAnchorView( Context context ) {
		super( context );
		setWillNotDraw( true );
	}

	@Nullable
	public String getUid() {
		return uid;
	}

	public void setUid( @Nullable String uid ) {
		this.uid = uid;
	}

	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
		setMeasuredDimension( 0, 0 );
	}

	@NonNull
	@Override
	public String toString() {
		return "VtmAnchorView{uid=" + uid + "}";
	}
}
