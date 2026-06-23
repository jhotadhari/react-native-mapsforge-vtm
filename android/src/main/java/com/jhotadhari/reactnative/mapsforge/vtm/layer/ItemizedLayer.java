package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Map;

import java.util.List;

public class ItemizedLayer extends org.oscim.layers.marker.ItemizedLayer {

	/**
	 * Tracks the symbol new markers without their own explicit symbol should use.
	 * The renderer's own default (set once, final, in the upstream MarkerRenderer)
	 * is intentionally left untouched - we never rely on it past layer creation,
	 * since MarkerItem.getMarker() is never null for markers created through
	 * LayerMarker (see LayerMarker#createMarker). Updating this field doesn't
	 * itself change anything on screen; callers also need to push it onto the
	 * already-existing items that use it (see LayerMarker#updateLayer).
	 */
	protected MarkerSymbol mDefaultMarker;

	public ItemizedLayer( Map map, List<MarkerInterface> list, MarkerSymbol defaultMarker, OnItemGestureListener<MarkerInterface> listener ) {
		super( map, list, defaultMarker, listener );
		mDefaultMarker = defaultMarker;
	}

	public MarkerSymbol getDefaultMarker() {
		return mDefaultMarker;
	}

	public void setDefaultMarker( MarkerSymbol defaultMarker ) {
		mDefaultMarker = defaultMarker;
	}
}
