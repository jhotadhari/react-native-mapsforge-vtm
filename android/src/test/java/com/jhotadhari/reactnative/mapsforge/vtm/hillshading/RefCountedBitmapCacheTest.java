package com.jhotadhari.reactnative.mapsforge.vtm.hillshading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mapsforge.core.graphics.HillshadingBitmap;
import org.mapsforge.core.model.BoundingBox;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RefCountedBitmapCacheTest {

	static class FakeBitmap implements HillshadingBitmap {
		final long size;
		final AtomicInteger recycleCount = new AtomicInteger();
		final Object mutex = new Object();

		FakeBitmap( long size ) { this.size = size; }

		boolean recycled() { return recycleCount.get() > 0; }
		@Override public long getSizeBytes() { return size; }
		@Override public BoundingBox getAreaRect() { return null; }
		@Override public int getPadding() { return 1; }
		@Override public Object getMutex() { return mutex; }
		@Override public void compress( OutputStream o ) {}
		@Override public void decrementRefCount() {}
		@Override public int getHeight() { return 1; }
		@Override public int getWidth() { return 1; }
		@Override public void incrementRefCount() {}
		@Override public boolean isDestroyed() { return recycled(); }
		@Override public void scaleTo( int w, int h ) {}
		@Override public void setBackgroundColor( int c ) {}
	}

	private final List<FakeBitmap> created = new ArrayList<>();
	private final IdentityHashMap<HillshadingBitmap, Integer> recycleCalls = new IdentityHashMap<>();

	private RefCountedBitmapCache<String> cache( int min, int max, long maxBytes ) {
		return new RefCountedBitmapCache<>( min, max, maxBytes, bitmap -> {
			synchronized ( recycleCalls ) {
				recycleCalls.merge( bitmap, 1, Integer::sum );
			}
			( (FakeBitmap) bitmap ).recycleCount.incrementAndGet();
		} );
	}

	/** Lazy like mapsforge's LazyFuture: computed by the first get(), size known only afterwards. */
	private RefCountedBitmapCache.FutureFactory<String> factory( final long size ) {
		return key -> {
			FutureTask<HillshadingBitmap> f = new FutureTask<>( (Callable<HillshadingBitmap>) () -> {
				FakeBitmap b = new FakeBitmap( size );
				synchronized ( created ) { created.add( b ); }
				return b;
			} ) ;
			return new Future<HillshadingBitmap>() {
				public boolean cancel( boolean b ) { return false; }
				public boolean isCancelled() { return false; }
				public boolean isDone() { return f.isDone(); }
				public HillshadingBitmap get() throws InterruptedException, java.util.concurrent.ExecutionException { f.run(); return f.get(); }
				public HillshadingBitmap get( long t, TimeUnit u ) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException { f.run(); return f.get( t, u ); }
			};
		};
	}

	@Before
	public void setUp() {
		created.clear();
		recycleCalls.clear();
	}

	@Test
	public void sizeIsAccountedOnceCalculated_andCapHolds() throws Exception {
		RefCountedBitmapCache<String> c = cache( 1, 100, 250 );
		for ( String k : new String[] { "a", "b", "c", "d" } ) {
			c.acquire( k, factory( 100 ), 100 ).close();
		}
		// 4 x 100 bytes against a 250 cap: only 2 may stay, the two oldest are recycled.
		assertEquals( 200, c.totalBytes() );
		assertEquals( 2, c.entryCount() );
		assertTrue( created.get( 0 ).recycled() );
		assertTrue( created.get( 1 ).recycled() );
		assertFalse( created.get( 2 ).recycled() );
		assertFalse( created.get( 3 ).recycled() );
	}

	@Test
	public void leasedBitmapIsNeverRecycled_untilReleased() throws Exception {
		RefCountedBitmapCache<String> c = cache( 0, 100, 50 );
		RefCountedBitmapCache.Lease held = c.acquire( "a", factory( 100 ), 100 );
		FakeBitmap a = (FakeBitmap) held.bitmap();
		// Everything else gets evicted over the cap, but the leased entry stays and is intact.
		for ( String k : new String[] { "b", "c" } ) {
			c.acquire( k, factory( 100 ), 100 ).close();
		}
		assertFalse( a.recycled() );
		assertEquals( 1, c.entryCount() );
		held.close();
		// Released while over the cap: recycled now, exactly once.
		assertTrue( a.recycled() );
		assertEquals( 1, a.recycleCount.get() );
		held.close(); // idempotent
		assertEquals( 1, a.recycleCount.get() );
		assertEquals( 0, c.entryCount() );
		assertEquals( 0, c.totalBytes() );
	}

	@Test
	public void sharedEntry_recycledOnlyAfterLastLease() throws Exception {
		RefCountedBitmapCache<String> c = cache( 0, 100, 10 );
		RefCountedBitmapCache.Lease l1 = c.acquire( "a", factory( 100 ), 100 );
		RefCountedBitmapCache.Lease l2 = c.acquire( "a", factory( 100 ), 100 );
		assertSame( l1.bitmap(), l2.bitmap() );
		assertEquals( 1, created.size() );
		l1.close();
		assertFalse( created.get( 0 ).recycled() );
		l2.close();
		assertEquals( 1, created.get( 0 ).recycleCount.get() );
	}

	@Test
	public void clearKeepsLeased_closeDropsAfterRelease() throws Exception {
		RefCountedBitmapCache<String> c = cache( 1, 100, 1000 );
		c.acquire( "a", factory( 10 ), 10 ).close();
		RefCountedBitmapCache.Lease held = c.acquire( "b", factory( 10 ), 10 );
		c.clear();
		assertTrue( created.get( 0 ).recycled() );
		assertFalse( created.get( 1 ).recycled() );
		c.close();
		assertFalse( created.get( 1 ).recycled() );
		held.close();
		assertTrue( created.get( 1 ).recycled() );
		// After close nothing is retained.
		c.acquire( "c", factory( 10 ), 10 ).close();
		assertTrue( created.get( 2 ).recycled() );
		assertEquals( 0, c.entryCount() );
	}

	@Test
	public void nullBitmapYieldsNullLease_andCountsNoBytes() throws Exception {
		RefCountedBitmapCache<String> c = cache( 0, 100, 10 );
		RefCountedBitmapCache.Lease l = c.acquire( "x", k -> java.util.concurrent.CompletableFuture.completedFuture( null ), 0 );
		assertNull( l );
		// The empty entry stays as a cheap negative cache (like upstream), but holds no lease and no bytes.
		assertEquals( 0, c.totalBytes() );
	}

	@Test
	public void concurrentUse_neverRecyclesInUse_andNoDoubleRecycle() throws Exception {
		final RefCountedBitmapCache<String> c = cache( 1, 1000, 350 );
		final AtomicInteger violations = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool( 8 );
		final CountDownLatch done = new CountDownLatch( 8 );
		for ( int t = 0; t < 8; t++ ) {
			final int seed = t;
			pool.execute( () -> {
				try {
					java.util.Random r = new java.util.Random( seed );
					for ( int i = 0; i < 2000; i++ ) {
						RefCountedBitmapCache.Lease l = c.acquire( "k" + r.nextInt( 12 ), factory( 100 ), 100 );
						if ( ( (FakeBitmap) l.bitmap() ).recycled() ) {
							violations.incrementAndGet();
						}
						if ( r.nextInt( 50 ) == 0 ) {
							c.clear();
						}
						if ( ( (FakeBitmap) l.bitmap() ).recycled() ) {
							violations.incrementAndGet();
						}
						l.close();
					}
				} catch ( Throwable e ) {
					violations.incrementAndGet();
				} finally {
					done.countDown();
				}
			} );
		}
		assertTrue( done.await( 60, TimeUnit.SECONDS ) );
		pool.shutdown();
		assertEquals( 0, violations.get() );
		synchronized ( recycleCalls ) {
			for ( Integer n : recycleCalls.values() ) {
				assertEquals( 1, n.intValue() );
			}
		}
		// No leases left: the cap holds.
		assertTrue( c.totalBytes() <= 350 );
		c.close();
		assertEquals( 0, c.totalBytes() );
		synchronized ( created ) {
			for ( FakeBitmap b : created ) {
				assertTrue( b.recycled() );
			}
		}
	}
}
