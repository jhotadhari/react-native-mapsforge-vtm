package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import org.oscim.layers.GenericLayer;
import org.oscim.map.Map;
import org.oscim.renderer.BitmapRenderer;

/**
 * A {@link GenericLayer} whose renderer is a stock vtm {@link BitmapRenderer}:
 * draws a bitmap at a screen offset in the same frame as the rest of the map
 * — pixel-perfect alignment with map-rendered geometry, zero bridge
 * crossings. Used by {@code LabelLayerManager} for map labels.
 *
 * <p>vtm's own {@code BitmapLayer} hard-codes the renderer; this thin wrapper
 * keeps the constructor ergonomic for the manager.</p>
 */
public class BitmapLabelLayer extends GenericLayer {

	public BitmapLabelLayer( Map map ) {
		super( map, new BitmapRenderer() );
	}

	public BitmapRenderer getBitmapRenderer() {
		return (BitmapRenderer) mRenderer;
	}
}
