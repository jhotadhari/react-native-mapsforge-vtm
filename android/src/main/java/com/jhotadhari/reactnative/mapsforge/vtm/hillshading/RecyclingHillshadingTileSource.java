package com.jhotadhari.reactnative.mapsforge.vtm.hillshading;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.RecyclingShadeTileSource;
import org.mapsforge.map.layer.hills.ShadingAlgorithm;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.hills.HillshadingTileSource;

/**
 * Opt-in counterpart of vtm's {@link HillshadingTileSource}: one shared byte-capped HGT bitmap cache per layer
 * that recycles evicted bitmaps (see {@link RefCountedBitmapCache}).
 */
public class RecyclingHillshadingTileSource extends TileSource {

	final int mMagnitude;
	final int mColor;
	final GraphicFactory mGraphicFactory;

	private final RecyclingShadeTileSource mShadeTileSource;
	private final HillsRenderConfig mHillsRenderConfig;

	/**
	 * @param cacheMaxBytes &lt;= 0 selects mapsforge's default (1/10 of the max Java heap).
	 */
	public RecyclingHillshadingTileSource( int zoomMin, int zoomMax, DemFolder demFolder, ShadingAlgorithm algorithm, int magnitude, int color, GraphicFactory graphicFactory, long cacheMaxBytes ) {
		super( zoomMin, zoomMax );
		mMagnitude = magnitude;
		mColor = color;
		mGraphicFactory = graphicFactory;
		mShadeTileSource = new RecyclingShadeTileSource( demFolder, algorithm, graphicFactory, cacheMaxBytes );
		mHillsRenderConfig = new HillsRenderConfig( mShadeTileSource );
		mHillsRenderConfig.indexOnThread();
	}

	@Override
	public ITileDataSource getDataSource() {
		return new RecyclingHillshadingTileDataSource( this, new HillshadingTileSource.TileDecoder(), mHillsRenderConfig, mShadeTileSource );
	}

	@Override
	public OpenResult open() {
		return OpenResult.SUCCESS;
	}

	/** Called when the layer is detached: recycle everything, and don't retain anything from now on. */
	@Override
	public void close() {
		mShadeTileSource.closeMemory();
	}

	/** Recycle all cached HGT bitmaps that are not currently in use (they are recalculated on demand). */
	public void clearMemory() {
		mShadeTileSource.clearMemory();
	}
}
