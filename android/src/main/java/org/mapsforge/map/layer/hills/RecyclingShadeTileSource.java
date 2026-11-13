package org.mapsforge.map.layer.hills;

import com.jhotadhari.reactnative.mapsforge.vtm.hillshading.RefCountedBitmapCache;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.HillshadingBitmap;
import org.mapsforge.core.util.Constants;

import java.util.concurrent.ExecutionException;

/**
 * Fixed-configuration {@link ShadeTileSource} backed by a {@link RecyclingHgtCache}. Counterpart of
 * {@link MemoryCachingHgtReaderTileSource} (which cannot be reconfigured to build a different HgtCache).
 * Use {@link #acquireHillshadingBitmap} instead of {@link #getHillshadingBitmap}.
 */
public class RecyclingShadeTileSource implements ShadeTileSource {

	private final ShadingAlgorithm algorithm;
	private final RecyclingHgtCache cache;

	/**
	 * @param cacheMaxBytes &lt;= 0 selects mapsforge's default (1/10 of the max Java heap).
	 */
	public RecyclingShadeTileSource( DemFolder demFolder, ShadingAlgorithm algorithm, GraphicFactory graphicsFactory, long cacheMaxBytes ) {
		this.algorithm = algorithm;
		final long maxBytes = cacheMaxBytes > 0 ? cacheMaxBytes : Constants.MAX_MEMORY_MB * 1000 * 1000 / 10;
		this.cache = new RecyclingHgtCache(
			demFolder,
			graphicsFactory,
			MemoryCachingHgtReaderTileSource.PaddingSizeDefault,
			algorithm,
			1,
			360 * 180,
			maxBytes
		);
	}

	public RefCountedBitmapCache.Lease acquireHillshadingBitmap( int latitudeOfSouthWestCorner, int longitudeOfSouthWestCorner, int zoomLevel, double pxPerLat, double pxPerLon, int color ) throws ExecutionException, InterruptedException {
		return cache.acquireHillshadingBitmap( latitudeOfSouthWestCorner, longitudeOfSouthWestCorner, zoomLevel, pxPerLat, pxPerLon, color );
	}

	public int getPadding() {
		return cache.padding;
	}

	public void clearMemory() {
		cache.clearMemory();
	}

	public void closeMemory() {
		cache.closeMemory();
	}

	@Override
	public void prepareOnThread() {
		cache.indexOnThread();
	}

	/** Not supported: callers could not release the bitmap. Use {@link #acquireHillshadingBitmap}. */
	@Override
	public HillshadingBitmap getHillshadingBitmap( int latitudeOfSouthWestCorner, int longituedOfSouthWestCorner, int zoomLevel, double pxPerLat, double pxPerLon, int color ) {
		throw new UnsupportedOperationException( "use acquireHillshadingBitmap" );
	}

	@Override
	public void applyConfiguration( boolean allowParallel ) {
		if ( allowParallel ) {
			cache.indexOnThread();
		}
	}

	@Override
	public ShadingAlgorithm getAlgorithm() {
		return algorithm;
	}

	@Override
	public void setAlgorithm( ShadingAlgorithm algorithm ) {
		throw new UnsupportedOperationException( "fixed configuration" );
	}

	@Override
	public boolean isZoomLevelSupported( int zoomLevel, int lat, int lon ) {
		return cache.isZoomLevelSupported( zoomLevel, lat, lon );
	}

	@Override
	public void interruptAndDestroy() {
		cache.interruptAndDestroy();
	}
}
