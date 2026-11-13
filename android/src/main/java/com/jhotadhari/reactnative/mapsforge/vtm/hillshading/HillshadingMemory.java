package com.jhotadhari.reactnative.mapsforge.vtm.hillshading;

/**
 * Process-wide defaults for the opt-in hillshading HGT bitmap memory handling
 * (see {@link RefCountedBitmapCache}). Read when a hillshading layer is created; a layer prop overrides them.
 */
public final class HillshadingMemory {

	private static volatile boolean recycleEvictedBitmaps = false;
	private static volatile long cacheMaxBytes = 0;

	private HillshadingMemory() {
	}

	public static boolean defaultRecycleEvictedBitmaps() {
		return recycleEvictedBitmaps;
	}

	/** @return 0 for "mapsforge default" (1/10 of the max Java heap). */
	public static long defaultCacheMaxBytes() {
		return cacheMaxBytes;
	}

	public static void setDefaultRecycleEvictedBitmaps( boolean value ) {
		recycleEvictedBitmaps = value;
	}

	/** @param bytes &lt;= 0 resets to the mapsforge default. */
	public static void setDefaultCacheMaxBytes( long bytes ) {
		cacheMaxBytes = Math.max( 0, bytes );
	}
}
