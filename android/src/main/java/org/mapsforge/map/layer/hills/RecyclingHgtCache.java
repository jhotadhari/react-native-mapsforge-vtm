package org.mapsforge.map.layer.hills;

import com.jhotadhari.reactnative.mapsforge.vtm.hillshading.RefCountedBitmapCache;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.HillshadingBitmap;
import org.mapsforge.map.android.graphics.AndroidBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Opt-in replacement for mapsforge's {@link HgtCache} memory handling.
 *
 * <p>Lives in mapsforge's package because {@code HgtCache.TileKey},
 * {@code HgtFileLoadFuture} and {@code HgtFileInfo} internals are package-private/protected-inner.
 * Indexing, shading and futures are inherited unchanged; only the bitmap cache
 * (see {@link RefCountedBitmapCache}) is replaced. The inherited {@code lruCache}/{@code HgtFileInfo.map}
 * soft-reference cache is bypassed entirely: it keeps evicted bitmaps alive until the GC clears the
 * soft reference.
 *
 * <p>Written against mapsforge-map 0.30.0 (the version this library depends on).
 */
public class RecyclingHgtCache extends HgtCache {

	private static final class Key {
		final int north;
		final int east;
		final long tag;

		Key( int north, int east, long tag ) {
			this.north = north;
			this.east = east;
			this.tag = tag;
		}

		@Override
		public boolean equals( Object o ) {
			if ( this == o ) {
				return true;
			}
			if ( ! ( o instanceof Key ) ) {
				return false;
			}
			final Key k = (Key) o;
			return north == k.north && east == k.east && tag == k.tag;
		}

		@Override
		public int hashCode() {
			int result = north;
			result = 31 * result + east;
			result = 31 * result + (int) ( tag ^ ( tag >>> 32 ) );
			return result;
		}
	}

	private final RefCountedBitmapCache<Key> cache;
	private final long maxBytes;

	public RecyclingHgtCache( DemFolder demFolder, GraphicFactory graphicsFactory, int padding, ShadingAlgorithm algorithm, int cacheMinCount, int cacheMaxCount, long cacheMaxBytes ) {
		super( demFolder, graphicsFactory, padding, algorithm, cacheMinCount, cacheMaxCount, cacheMaxBytes );
		this.maxBytes = cacheMaxBytes;
		this.cache = new RefCountedBitmapCache<>( cacheMinCount, cacheMaxCount, cacheMaxBytes, new RefCountedBitmapCache.Recycler() {
			@Override
			public void recycle( HillshadingBitmap bitmap ) {
				recycleBitmap( bitmap );
			}
		} );
	}

	/**
	 * @return A lease on the hillshading bitmap (close it when done reading!), or null if there's no data.
	 */
	public RefCountedBitmapCache.Lease acquireHillshadingBitmap( int northInt, int eastInt, final int zoomLevel, final double pxPerLat, final double pxPerLon, final int color ) throws InterruptedException, ExecutionException {
		final HgtFileInfo hgtFileInfo = hgtFiles.get().get( new TileKey( northInt, eastInt ) );
		if ( null == hgtFileInfo ) {
			return null;
		}

		final long outputSizeEstimate = shadingAlgorithm.getOutputSizeBytes( hgtFileInfo, padding, zoomLevel, pxPerLat, pxPerLon );
		final long cacheTag = shadingAlgorithm.getCacheTag( hgtFileInfo, padding, zoomLevel, pxPerLat, pxPerLon );

		// Prevents cache hammering, same as HgtCache.getHillshadingBitmap.
		blockingSumLimiter.add( outputSizeEstimate, maxBytes );
		try {
			return cache.acquire(
				new Key( northInt, eastInt, cacheTag ),
				new RefCountedBitmapCache.FutureFactory<Key>() {
					@Override
					public Future<HillshadingBitmap> create( Key key ) {
						return createHgtFileLoadFuture( hgtFileInfo, padding, zoomLevel, pxPerLat, pxPerLon, color );
					}
				},
				outputSizeEstimate
			);
		} finally {
			blockingSumLimiter.subtract( outputSizeEstimate );
		}
	}

	/** Recycle all cached bitmaps that are not currently in use. */
	public void clearMemory() {
		cache.clear();
	}

	/** Recycle everything not in use, and drop entries as soon as they are released from now on (layer removed). */
	public void closeMemory() {
		cache.close();
	}

	@Override
	public void interruptAndDestroy() {
		super.interruptAndDestroy();
		cache.close();
	}

	private static void recycleBitmap( HillshadingBitmap bitmap ) {
		if ( bitmap instanceof AndroidBitmap ) {
			// AndroidBitmap.decrementRefCount() would only park the pixels in a soft-reference reuse pool.
			final android.graphics.Bitmap androidBitmap = AndroidGraphicFactory.getBitmap( bitmap );
			if ( null != androidBitmap && ! androidBitmap.isRecycled() ) {
				androidBitmap.recycle();
			}
		}
	}
}
