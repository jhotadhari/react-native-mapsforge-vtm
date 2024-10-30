package com.jhotadhari.reactnative.mapsforge.vtm.layers.vector;

import org.oscim.layers.vector.geometries.LineDrawable;
import org.oscim.layers.vector.geometries.Style;
import org.oscim.map.Map;

public class PathLayer extends org.oscim.layers.vector.PathLayer {

	public PathLayer(Map map, Style style) {
		super(map, style);
	}

	public PathLayer(Map map, int lineColor, float lineWidth) {
		super(map, lineColor, lineWidth);
	}

	public PathLayer(Map map, int lineColor) {
		super(map, lineColor);
	}

	public LineDrawable getDrawable() {
		return mDrawable;
	}

}
