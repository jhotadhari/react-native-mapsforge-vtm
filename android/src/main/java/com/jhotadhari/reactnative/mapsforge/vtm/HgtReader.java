package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Build;

import com.facebook.react.bridge.ReadableMap;

import org.mapsforge.map.layer.hills.AThreadedHillShading;
import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.HillShadingUtils.HillShadingThreadPool;
import org.mapsforge.map.layer.hills.HillShadingUtils.SilentFutureTask;
import org.mapsforge.map.layer.hills.LazyFuture;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class HgtReader reads data from SRTM HGT files. Currently this class is restricted to a resolution of 3 arc seconds.
 *
 * Mostly copy of, all credits to them:
 * 	- org.mapsforge.map.layer.hills.HgtCache by @usrusr, @devemux86, @Sublimis https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java
 * 		to index the hgt files. And multithreading.
 * 	- hhtznr.josm.plugins.elevation.data.SRTMTile by Harald Hetzner https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/02ed9e66ed0db1857eabbfbe2a61032588372958/src/hhtznr/josm/plugins/elevation/data/SRTMTile.java
 * 		to read elevation from the DEM. And interpolate the elevation between the 4 closest points.
 * 	- Inspired by org.openstreetmap.josm.plugins.elevation.HgtReader by Oliver Wieland https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java
 * 		to parse the hgt files.
 */
public class HgtReader {

	// Should be lower-case
	public static final String ZipFileExtension = "zip";
	// Should be lower-case
	public static final String HgtFileExtension = "hgt";

	final DemFolder demFolder;

	private List<String> problems = new ArrayList<>();

	public static final String ThreadPoolName = "ReactNativeMapsforgeVtmHgtReader";

	private static final AtomicReference<HillShadingThreadPool> ThreadPool = new AtomicReference<>(null);

	final Deque<SilentFutureTask> myTasks = new ConcurrentLinkedDeque<>();

	private LazyFuture<Map<TileKey, HgtFileInfo>> hgtFiles;

	/**
	 * The geographic vertical and horizontal dimensions of an SRTM tile: 1°.
	 */
	private static final int SRTM_EXTENT = 1; // degree

	/**
	 * The number of rows and columns of elevation data points in an SRTM3 file.
	 *
	 * See https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
	 */
	public static final int SRTM3_TILE_LENGTH = 1201;

	protected final List<TileKey> hgtFileInfoWithData = new ArrayList<TileKey>();
	protected int hgtFileInfoPurgeThreshold;
	protected boolean interpolation;

	protected FixedWindowRateLimiter rateLimiter;

	public HgtReader( DemFolder demFolder, boolean interpolation, int rateLimiterWindowSize, int hgtFileInfoPurgeThreshold ) {
		this.demFolder = demFolder;
		this.hgtFileInfoPurgeThreshold = hgtFileInfoPurgeThreshold;
		this.interpolation = interpolation;
		rateLimiter = new FixedWindowRateLimiter( rateLimiterWindowSize, 1 );

		createThreadPoolMaybe();

		while (false == myTasks.isEmpty()) {
			myTasks.pollFirst().get();
		}

		indexHgtFiles();
	}

	public void indexOnThread() {
		hgtFiles.withRunningThread();
	}

	public void updateRateLimiterWindowSize( int rateLimiterWindowSize ) {
		rateLimiter = new FixedWindowRateLimiter( rateLimiterWindowSize, 1 );
	}
	public void updateHgtFileInfoPurgeThreshold( int hgtFileInfoPurgeThreshold ) {
		this.hgtFileInfoPurgeThreshold = hgtFileInfoPurgeThreshold;
	}
	public void updateInterpolation( boolean interpolation ) {
		this.interpolation = interpolation;
	}

	/**
	 * Mostly copy of org.mapsforge.map.layer.hills.HgtCache constructor, just small adjustments to fit it into here.
	 * See https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L82
	 */
	protected void indexHgtFiles() {

		this.hgtFiles = new LazyFuture<Map<TileKey, HgtFileInfo>>() {
			final Map<TileKey, HgtFileInfo> myMap = new HashMap<>();
			protected Map<TileKey, HgtFileInfo> calculate() {
				final String regex = ".*([ns])(\\d{1,2})([ew])(\\d{1,3})\\.(?:(" + HgtFileExtension + ")|(" + ZipFileExtension + "))";
				final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
				indexFolder(demFolder, pattern, problems);
				return myMap;
			}

			void indexFolder(DemFolder folder, Pattern pattern, List<String> problems) {
				for (DemFile demFile : folder.files()) {
					// Process files concurrently
					final SilentFutureTask task = new SilentFutureTask(new Callable<Boolean>() {
						public Boolean call() {
							indexFile(demFile, pattern, problems);
							return true;
						}
					});
					postToThreadPoolOrRun(task);
					myTasks.add(task);
				}

				for (final DemFolder sub : folder.subs()) {
					// Process folders concurrently
					final SilentFutureTask task = new SilentFutureTask(new Callable<Boolean>() {
						public Boolean call() {
							indexFolder(sub, pattern, problems);
							return true;
						}
					});
					postToThreadPoolOrRun(task);
					myTasks.add(task);
				}
			}

			void indexFile(DemFile file, Pattern pattern, List<String> problems) {
				final String name = file.getName();
				final Matcher matcher = pattern.matcher(name);

				if (matcher.matches()) {
					final int northsouth = Integer.parseInt(matcher.group(2));
					final int eastwest = Integer.parseInt(matcher.group(4));

					final int north = "n".equalsIgnoreCase(matcher.group(1)) ? northsouth : -northsouth;
					final int east = "e".equalsIgnoreCase(matcher.group(3)) ? eastwest : -eastwest;

					final long length = file.getSize();
					final long heights = length / 2;
					final long sqrt = (long) Math.sqrt(heights);
					if (heights == 0 || sqrt * sqrt != heights) {
						if (problems != null)
							problems.add(file + " length in shorts (" + heights + ") is not a square number");
						return;
					}

					final TileKey tileKey = new TileKey(north, east);
					synchronized (myMap) {
						final HgtFileInfo existing = myMap.get(tileKey);
						if (existing == null || existing.getSize() < length) {
							myMap.put(tileKey, new HgtFileInfo(file, length));
						}
					}
				}
			}
		};
	}

	/**
	 * Copy of org.mapsforge.map.layer.hills.HgtCache.postToThreadPoolOrRun
	 * See https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L225
	 */
	protected static void postToThreadPoolOrRun(final Runnable code) {
		final HillShadingThreadPool threadPool = ThreadPool.get();

		if (threadPool != null) {
			threadPool.executeOrRun(code);
		}
	}

	/**
	 * Copy of org.mapsforge.map.layer.hills.HgtCache.createThreadPoolMaybe
	 * See https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L237
	 */
	private static void createThreadPoolMaybe() {
		final AtomicReference<HillShadingThreadPool> threadPoolReference = ThreadPool;

		if (threadPoolReference.get() == null) {
			synchronized (threadPoolReference) {
				if (threadPoolReference.get() == null) {
					threadPoolReference.set(createThreadPool());
				}
			}
		}
	}

	/**
	 * Copy of org.mapsforge.map.layer.hills.HgtCache.createThreadPool
	 * See https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L249
	 */
	private static HillShadingThreadPool createThreadPool() {
		final int threadCount = AThreadedHillShading.ReadingThreadsCountDefault;
		final int queueSize = Integer.MAX_VALUE;
		return new HillShadingThreadPool(threadCount, threadCount, queueSize, 5, ThreadPoolName).start();
	}

	/**
	 * Copy of org.mapsforge.map.layer.hills.HgtCache.shutdownThreadPool
	 * See https://github.com/mapsforge/mapsforge/blob/33d263b52ab4d9e96505bae40a9df8079ecc8036/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L255
	 */
	protected void shutdownThreadPool() {
		final AtomicReference<HillShadingThreadPool> threadPoolReference = ThreadPool;

		synchronized (threadPoolReference) {
			final HillShadingThreadPool threadPool = threadPoolReference.getAndSet(null);

			if (threadPool != null) {
				threadPool.shutdown();
			}
		}
	}

	/**
	 * Mostly copy of hhtznr.josm.plugins.elevation.data.SRTMTile.getLatLonEle, just some adjustments to fit it into here.
	 * See https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/02ed9e66ed0db1857eabbfbe2a61032588372958/src/hhtznr/josm/plugins/elevation/data/SRTMTile.java#L455
	 */
	public Short getAltitudeAtPosition( ReadableMap center, boolean useRateLimiter ) {
		Short altitude = null;
		double lng = center.getDouble( "lng" );
		double lat = center.getDouble( "lat" );
		try {
			Map<TileKey, HgtFileInfo> hgtFileInfoMap = hgtFiles.get();
			if ( ! hgtFileInfoMap.isEmpty() ) {
				int lngFloor = (int) Math.floor( lng );
				int latFloor = (int) Math.floor( lat );
				TileKey tileKey = new TileKey( latFloor, lngFloor );
				if ( hgtFileInfoMap.containsKey( tileKey ) ) {
					HgtFileInfo info = hgtFileInfoMap.get( tileKey );
					if ( null != info ) {
						if ( info.dataRef.get() == null ) {
							final SilentFutureTask task = info.getDataSilentFutureTask( tileKey, useRateLimiter  );
							postToThreadPoolOrRun( task );
							myTasks.add( task );
						}
						if ( info.dataRef.get() != null ) {

							double latEleIndexD = (1.0 - (lat - tileKey.north)) * (getTileLength() - 1);
							double lngEleIndexD = (lng - tileKey.east) * (getTileLength() - 1);
							int latEleIndex = (int) Math.round(latEleIndexD);
							int lngEleIndex = (int) Math.round(lngEleIndexD);

							if ( ! interpolation ) {
								altitude = info.dataRef.get()[latEleIndex][lngEleIndex];
							} else {

								// Compute 4 indices of the raster cell the given coordinate is located in
								int indexSouth;
								int indexNorth;
								int indexWest;
								int indexEast;

								if (latEleIndex < latEleIndexD) {
									indexNorth = latEleIndex;
									indexSouth = latEleIndex + 1;
								} else {
									indexNorth = latEleIndex - 1;
									indexSouth = latEleIndex;
								}

								if (lngEleIndex <= lngEleIndexD) {
									indexWest = lngEleIndex;
									indexEast = lngEleIndex + 1;
								} else {
									indexWest = lngEleIndex - 1;
									indexEast = lngEleIndex;
								}

								double[] northWest = getRasterLatLng( indexNorth, indexWest, tileKey );
								// double[] northEast = getRasterLatLng( indexNorth, indexEast, tileKey );
								double[] southWest = getRasterLatLng( indexSouth, indexWest, tileKey );
								double[] southEast = getRasterLatLng( indexSouth, indexEast, tileKey );

								altitude = (short) interpolate(
									southWest[1],
									southEast[1],
									southWest[0],
									northWest[0],
									info.dataRef.get()[indexSouth][indexWest],
									info.dataRef.get()[indexNorth][indexWest],
									info.dataRef.get()[indexSouth][indexEast],
									info.dataRef.get()[indexNorth][indexEast],
									lng,
									lat
								);
							}
						}
					}
				}
				purgeHgtFileInfoMapData( hgtFileInfoMap, tileKey );
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return altitude;
	}

	/**
	 * Copy of hhtznr.josm.plugins.elevation.data.SRTMTile.getLatLonEle, but removed the part that needs specific josm types.
	 * See https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/02ed9e66ed0db1857eabbfbe2a61032588372958/src/hhtznr/josm/plugins/elevation/math/BiLinearInterpolation.java#L41
	 *
	 *
	 * Performs a bilinear interpolation based on generic x-y coordinates.
	 *
	 * @param x1  The 1st x coordinate where {@code x1 < x2}.
	 * @param x2  The 2nd x coordinate where {@code x2 > x1}.
	 * @param y1  The 1st y coordinate where {@code y1 < y2}.
	 * @param y2  The 2nd y coordinate where {@code y2 > y1}.
	 * @param f11 The known value of the unknown function {@code f} at point
	 *            {@code (x1, y1)}.
	 * @param f12 The known value of the unknown function {@code f} at point
	 *            {@code (x1, y2)}.
	 * @param f21 The known value of the unknown function {@code f} at point
	 *            {@code (x2, y1)}.
	 * @param f22 The known value of the unknown function {@code f} at point
	 *            {@code (x2, y2)}.
	 * @param x   The x coordinate where we would like to know the value of the
	 *            unknown function.
	 * @param y   The y coordinate where we would like to know the value of the
	 *            unknown function.
	 * @return The estimated value of the unknown function {@code f} at point
	 *         {@code (x, y)}.
	 */
	public static double interpolate(
			double x1,
			double x2,
			double y1,
			double y2,
			double f11,
			double f12,
			double f21,
			double f22,
			double x,
			double y
	) {
		if (x1 >= x2)
			throw new IllegalArgumentException("Invalid interpolation bounds: x1 = " + x1 + " >= x2 = " + x2);
		if (y1 >= y2)
			throw new IllegalArgumentException("Invalid interpolation bounds: y1 = " + y1 + " >= y2 = " + y2);
		if (x < x1)
			throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " < x1 = " + x1);
		if (x > x2)
			throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " > x2 = " + x2);
		if (y < y1)
			throw new IllegalArgumentException("Interpolation coordinate outside bounds: y = " + y + " < y1 = " + y1);
		if (y > y2)
			throw new IllegalArgumentException("Interpolation coordinate outside bounds: y = " + y + " > y2 = " + y2);
		// https://en.wikipedia.org/wiki/Bilinear_interpolation
		return 1.0 / ((x2 - x1) * (y2 - y1)) * (f11 * (x2 - x) * (y2 - y) + f21 * (x - x1) * (y2 - y)
				+ f12 * (x2 - x) * (y - y1) + f22 * (x - x1) * (y - y1));
	}

	/**
	 * Copy of hhtznr.josm.plugins.elevation.data.SRTMTile.getRasterLatLon.
	 * See https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/02ed9e66ed0db1857eabbfbe2a61032588372958/src/hhtznr/josm/plugins/elevation/data/SRTMTile.java#L576C19-L576C34
	 *
	 * Computes the coordinate of a tile raster location specified by its indices.
	 *
	 * @param latIndex The index in latitude dimension.
	 * @param lngIndex The index in longitude dimension.
	 * @return The latitude-longitude coordinate that corresponds with the given
	 *         raster indices.
	 */
	public double[] getRasterLatLng(int latIndex, int lngIndex, TileKey tileKey) {
		int tileLength = getTileLength();
		// Compute the tile raster coordinates of the elevation value
		// Signed latitude increases in opposite direction of data order
		double latEleRaster = tileKey.north + (1.0 - Double.valueOf(latIndex) / (tileLength - 1));
		// Signed longitude increases in same direction as data order
		double lonEleRaster = tileKey.east + Double.valueOf(lngIndex) / (tileLength - 1);
		return new double[] { latEleRaster, lonEleRaster };
	}

	public int getTileLength() {
		return SRTM3_TILE_LENGTH;
	}

	protected void purgeHgtFileInfoMapData( Map<TileKey, HgtFileInfo> hgtFileInfoMap, TileKey tileKey ) {
		if ( hgtFileInfoMap.isEmpty() ) {
			return;
		}
		int i = 0;
		synchronized ( hgtFileInfoWithData ) {
			while( i < hgtFileInfoWithData.size() ) {
				try {
					TileKey tkey = hgtFileInfoWithData.get( i );
					if (
						tkey.east < tileKey.east - hgtFileInfoPurgeThreshold
						|| tkey.east > tileKey.east + hgtFileInfoPurgeThreshold
						|| tkey.north > tileKey.north + hgtFileInfoPurgeThreshold
						|| tkey.north < tileKey.north - hgtFileInfoPurgeThreshold
					) {
						if ( hgtFileInfoMap.containsKey( tkey ) ) {
							HgtFileInfo info = hgtFileInfoMap.get( tileKey );
							if ( null != info ) {
								info.resetData();
								hgtFileInfoWithData.remove( tkey );
								i = i - 1;
							}
						}
					}
					i = i + 1;
				} catch ( NullPointerException e ) {
					e.printStackTrace();
				}
			}
		}
	}

	class HgtFileInfo {
		final DemFile file;

		WeakReference<short[][]> dataRef;

		final long size;
		HgtFileInfo( DemFile file, long size ) {
			this.file = file;
			this.size = size;
			this.dataRef = new WeakReference<>( null );
		}

		/**
		 * Inspired by org.openstreetmap.josm.plugins.elevation.HgtReader.readHgtFile
		 * See https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java#L102
		 */
		private short[][] readHgtFile( DemFile file ) throws IOException {
			short[][] data = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				InputStream fis = file.asStream();
				WeakReference<ByteBuffer> bbRef = new WeakReference<>( ByteBuffer.wrap( fis.readAllBytes() ) );
				fis.close();
				bbRef.get().order( ByteOrder.BIG_ENDIAN );
				int size = (int) Math.sqrt( bbRef.get().array().length / 2.0 );
				data = new short[ size ][ size ];
				int x = 0;
				int y = 0;
				while ( x < size ) {
					while ( y < size ) {
						data[ x ][ y ] = bbRef.get().getShort( 2 * ( x * size + y ) );
						y++;
					}
					x++;
					y = 0;
				}
				bbRef.clear();
			}
			return data;
		}

		protected SilentFutureTask getDataSilentFutureTask( TileKey tileKey, boolean useRateLimiter ) {
			return new SilentFutureTask( new Callable<Boolean>() {
				public Boolean call() {
					if ( null == dataRef.get() ) {
						if ( ! useRateLimiter || rateLimiter.tryAcquire() ) {
							try {
								dataRef = new WeakReference<>( readHgtFile( file ) );
							} catch ( IOException e ) {
								e.printStackTrace();
							}
						}
						synchronized ( hgtFileInfoWithData ) {
							if ( null != dataRef.get() && ! hgtFileInfoWithData.contains( tileKey ) ) {
								hgtFileInfoWithData.add( tileKey );
							}
						}
					}
					return true;
				}
			} );
		}

		public void resetData() {
			if ( dataRef.get() != null ) {
				dataRef.clear();
			}
		}

		public long getSize() {
			return size;
		}
	}

	protected static final class TileKey {
		final int north;
		final int east;

		@Override
		public boolean equals( Object o ) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			TileKey tileKey = (TileKey) o;

			return north == tileKey.north && east == tileKey.east;
		}

		@Override
		public int hashCode() {
			int result = north;
			result = 31 * result + east;
			return result;
		}

		TileKey( int north, int east ) {
			this.east = east;
			this.north = north;
		}

	}

}
