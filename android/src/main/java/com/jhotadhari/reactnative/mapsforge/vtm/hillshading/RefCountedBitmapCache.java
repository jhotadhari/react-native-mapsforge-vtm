package com.jhotadhari.reactnative.mapsforge.vtm.hillshading;

import org.mapsforge.core.graphics.HillshadingBitmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Byte-capped LRU cache of {@link HillshadingBitmap}s with reference counting and
 * real (eager) recycling of evicted bitmaps.
 *
 * <p>Replaces mapsforge's {@code HgtCache.Lru}, which (a) evicts without recycling --
 * on Android the pixels are native memory that is only released when the GC finalizes
 * the Java bitmap object -- and (b) counts an entry's size as 0 until it is calculated
 * and never corrects it afterwards, so the byte cap does not hold.
 *
 * <h3>Safety model</h3>
 * Every user obtains a {@link Lease} via {@link #acquire} and must {@link Lease#close()} it
 * once it no longer reads the bitmap (draws it, or merges it into a neighbour).
 * <ul>
 *   <li>An entry is only evicted while its lease count is 0, and eviction happens under the
 *       same lock that {@code acquire} uses to find entries and increment the count. An
 *       evicted entry is removed from the map first, so nobody can obtain a lease on it
 *       afterwards. Hence a bitmap is recycled only when no thread can still read it, and
 *       exactly once (guarded by {@code recycled}).</li>
 *   <li>Pinned entries (lease count &gt; 0) are skipped by eviction and cleaned up by the
 *       trim that runs when their last lease is released. The byte cap can therefore be
 *       exceeded temporarily by the bitmaps that are actually in use.</li>
 *   <li>Futures are awaited outside the lock.</li>
 * </ul>
 */
public final class RefCountedBitmapCache<K> {

	public interface Recycler {
		void recycle( HillshadingBitmap bitmap );
	}

	public interface FutureFactory<K> {
		Future<HillshadingBitmap> create( K key );
	}

	/** Handle to a bitmap in the cache. Close exactly when done reading; closing twice is a no-op. */
	public static final class Lease implements AutoCloseable {
		private final Runnable release;
		private final HillshadingBitmap bitmap;
		private boolean closed = false;

		private Lease( Runnable release, HillshadingBitmap bitmap ) {
			this.release = release;
			this.bitmap = bitmap;
		}

		public HillshadingBitmap bitmap() {
			return bitmap;
		}

		@Override
		public void close() {
			synchronized ( this ) {
				if ( closed ) {
					return;
				}
				closed = true;
			}
			release.run();
		}
	}

	private static final class Entry<K> {
		final K key;
		final Future<HillshadingBitmap> future;
		int refs = 0;
		boolean sized = false;
		boolean removed = false;
		boolean recycled = false;
		long accountedBytes = 0;
		HillshadingBitmap bitmap = null;

		Entry( K key, Future<HillshadingBitmap> future ) {
			this.key = key;
			this.future = future;
		}
	}

	private final Object lock = new Object();
	// Access-ordered: iteration order is least-recently-acquired first.
	private final LinkedHashMap<K, Entry<K>> map = new LinkedHashMap<>( 16, 0.75f, true );
	private final int minCount;
	private final int maxCount;
	private final long maxBytes;
	private final Recycler recycler;
	private long totalBytes = 0;
	private boolean closed = false;

	public RefCountedBitmapCache( int minCount, int maxCount, long maxBytes, Recycler recycler ) {
		this.minCount = minCount;
		this.maxCount = maxCount;
		this.maxBytes = maxBytes;
		this.recycler = recycler;
	}

	/**
	 * @return a lease on the (possibly freshly calculated) bitmap, or null if the future yielded no bitmap.
	 * Blocks while the bitmap is being calculated.
	 */
	public Lease acquire( K key, FutureFactory<K> factory, long estimateBytes ) throws InterruptedException, ExecutionException {
		final Entry<K> entry;
		synchronized ( lock ) {
			Entry<K> existing = map.get( key );
			if ( null == existing ) {
				existing = new Entry<>( key, factory.create( key ) );
				map.put( key, existing );
			}
			existing.refs++;
			entry = existing;
		}

		boolean handedOut = false;
		try {
			if ( ! entry.future.isDone() ) {
				// Make room for the upcoming bitmap (the cap otherwise only counts completed entries).
				recycleAll( evictFor( estimateBytes ) );
			}

			final HillshadingBitmap bitmap = entry.future.get();

			final List<HillshadingBitmap> toRecycle;
			synchronized ( lock ) {
				if ( null != bitmap ) {
					entry.bitmap = bitmap;
					if ( ! entry.sized && ! entry.removed ) {
						// Correct the size now that the real value is known, then re-run the size management.
						entry.accountedBytes = Math.max( 0, bitmap.getSizeBytes() );
						totalBytes += entry.accountedBytes;
					}
				}
				entry.sized = true;
				toRecycle = trim();
			}
			recycleAll( toRecycle );

			if ( null == bitmap ) {
				return null;
			}
			handedOut = true;
			return new Lease( new Runnable() {
				@Override
				public void run() {
					release( entry );
				}
			}, bitmap );
		} finally {
			if ( ! handedOut ) {
				release( entry );
			}
		}
	}

	private void release( Entry<K> entry ) {
		final List<HillshadingBitmap> toRecycle;
		synchronized ( lock ) {
			entry.refs--;
			toRecycle = trim();
		}
		recycleAll( toRecycle );
	}

	/** Evicts (and recycles) all entries that are not currently leased. Leased ones stay. */
	public void clear() {
		final List<HillshadingBitmap> toRecycle = new ArrayList<>();
		synchronized ( lock ) {
			evictWhile( toRecycle, true );
		}
		recycleAll( toRecycle );
	}

	/** Like clear(), and from now on entries are dropped as soon as their last lease is released. */
	public void close() {
		final List<HillshadingBitmap> toRecycle = new ArrayList<>();
		synchronized ( lock ) {
			closed = true;
			evictWhile( toRecycle, true );
		}
		recycleAll( toRecycle );
	}

	public long totalBytes() {
		synchronized ( lock ) {
			return totalBytes;
		}
	}

	public int entryCount() {
		synchronized ( lock ) {
			return map.size();
		}
	}

	// ---- lock must be held below ----

	private boolean overLimit() {
		return closed || map.size() > maxCount || ( map.size() > minCount && totalBytes > maxBytes );
	}

	private List<HillshadingBitmap> trim() {
		final List<HillshadingBitmap> out = new ArrayList<>();
		evictWhile( out, false );
		return out;
	}

	private void evictWhile( List<HillshadingBitmap> out, boolean all ) {
		final Iterator<Entry<K>> it = map.values().iterator();
		while ( it.hasNext() && ( all || overLimit() ) ) {
			final Entry<K> e = it.next();
			if ( e.refs > 0 ) {
				continue;
			}
			it.remove();
			markRemoved( e, out );
		}
	}

	private List<HillshadingBitmap> evictFor( long bytes ) {
		final List<HillshadingBitmap> out = new ArrayList<>();
		synchronized ( lock ) {
			final Iterator<Entry<K>> it = map.values().iterator();
			while ( it.hasNext() && bytes + totalBytes > maxBytes ) {
				final Entry<K> e = it.next();
				if ( e.refs > 0 ) {
					continue;
				}
				it.remove();
				markRemoved( e, out );
			}
		}
		return out;
	}

	private void markRemoved( Entry<K> e, List<HillshadingBitmap> out ) {
		e.removed = true;
		totalBytes -= e.accountedBytes;
		e.accountedBytes = 0;
		if ( null != e.bitmap && ! e.recycled ) {
			e.recycled = true;
			out.add( e.bitmap );
		}
	}

	// ---- no lock needed ----

	private void recycleAll( List<HillshadingBitmap> bitmaps ) {
		for ( HillshadingBitmap b : bitmaps ) {
			try {
				recycler.recycle( b );
			} catch ( Throwable ignored ) {
			}
		}
	}
}
