package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import org.oscim.layers.GenericLayer;
import org.oscim.map.Map;

/**
 * A {@link GenericLayer} whose renderer is a {@link ScreenLineRenderer}:
 * a per-frame, zero-latency polyline for {@code LayerScreenLine}.
 */
public class ScreenLineLayer extends GenericLayer {

	private final ScreenLineRenderer mScreenLineRenderer;

	public ScreenLineLayer( Map map ) {
		super( map, new ScreenLineRenderer() );
		mScreenLineRenderer = (ScreenLineRenderer) mRenderer;
	}

	public ScreenLineRenderer getScreenLineRenderer() {
		return mScreenLineRenderer;
	}
}
