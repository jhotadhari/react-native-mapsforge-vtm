package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Build;

import com.facebook.react.bridge.ReadableMap;

import org.mapsforge.map.layer.hills.DemFile;
import org.mapsforge.map.layer.hills.DemFolder;
import org.mapsforge.map.layer.hills.LazyFuture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class HgtReader reads data from SRTM HGT files. Currently this class is restricted to a resolution of 3 arc seconds.
 *
 * Mostly copy of
 * 	- org.mapsforge.map.layer.hills.HgtCache https://github.com/mapsforge/mapsforge/blob/e45c41dc46cdfdf0770d687adee8f6d051511f5e/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java
 * 		to index the hgt files.
 * 	- org.openstreetmap.josm.plugins.elevation.HgtReader https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java
 * 		to read the altitude from a file.
 */
public class HgtReader {

	final DemFolder demFolder;

	private List<String> problems = new ArrayList<>();

	private LazyFuture<Map<TileKey, HgtFileInfo>> hgtFiles;

	private static final int SRTM_EXTENT = 1; // degree

	protected List<TileKey> hgtFileInfoWithData = new ArrayList<TileKey>();

	protected FixedWindowRateLimiter rateLimiter;

	public HgtReader( DemFolder demFolder, int rateLimiterWindowSize ) {
		this.demFolder = demFolder;
		rateLimiter = new FixedWindowRateLimiter( rateLimiterWindowSize, 1 );
		indexHgtFiles();
	}

	public void updateRateLimiterWindowSize( int rateLimiterWindowSize ) {
		rateLimiter = new FixedWindowRateLimiter( rateLimiterWindowSize, 1 );
	}

	/**
	 * Copy of org.mapsforge.map.layer.hills.HgtCache constructor
	 * See https://github.com/mapsforge/mapsforge/blob/e45c41dc46cdfdf0770d687adee8f6d051511f5e/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HgtCache.java#L146
	 */
	protected void indexHgtFiles() {

		hgtFiles = new LazyFuture<Map<TileKey, HgtFileInfo>>() {
			protected Map<TileKey, HgtFileInfo> calculate() {
				Map<TileKey, HgtFileInfo> HgtFileInfoMap = new HashMap<>();
				Matcher matcher = Pattern.compile("([ns])(\\d{1,2})([ew])(\\d{1,3})\\.(?:(hgt)|(zip))", Pattern.CASE_INSENSITIVE).matcher("");
				crawl( demFolder, matcher, HgtFileInfoMap, problems );
				return HgtFileInfoMap;
			}

			void crawl(DemFile file, Matcher matcher, Map<TileKey, HgtFileInfo> HgtFileInfoMap, List<String> problems) {
				String name = file.getName();
				if (matcher.reset(name).matches()) {
					int northsouth = Integer.parseInt(matcher.group(2));
					int eastwest = Integer.parseInt(matcher.group(4));

					int north = "n".equals(matcher.group(1).toLowerCase()) ? northsouth : -northsouth;
					int east = "e".equals(matcher.group(3).toLowerCase()) ? eastwest : -eastwest;

					long length = 0;

					if (matcher.group(6) == null) {
						length = file.getSize();
					} else {
						// zip
						ZipInputStream zipInputStream = null;
						try {
							zipInputStream = new ZipInputStream(file.openInputStream());
							String expectedHgt = name.toLowerCase().substring(0, name.length() - 4) + ".hgt";
							ZipEntry entry;
							while (null != (entry = zipInputStream.getNextEntry())) {
								if (expectedHgt.equals(entry.getName().toLowerCase())) {
									length = entry.getSize();
									break;
								}
							}
						} catch (IOException e) {
							problems.add("could not read zip file " + file.getName());
						}
						if (zipInputStream != null) {
							try {
								zipInputStream.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					long heights = length / 2;
					long sqrt = (long) Math.sqrt(heights);
					if (heights == 0 || sqrt * sqrt != heights) {
						if (problems != null)
							problems.add(file + " length in shorts (" + heights + ") is not a square number");
						return;
					}

					TileKey tileKey = new TileKey(north, east);
					HgtFileInfo existing = HgtFileInfoMap.get( tileKey );
					if (existing == null || existing.size < length) {
						HgtFileInfoMap.put(tileKey, new HgtFileInfo(file, length));
					}
				}
			}

			void crawl(DemFolder file, Matcher matcher, Map<TileKey, HgtFileInfo> HgtFileInfoMap, List<String> problems) {
				for (DemFile demFile : file.files()) {
					crawl(demFile, matcher, HgtFileInfoMap, problems);
				}
				for (DemFolder sub : file.subs()) {
					crawl(sub, matcher, HgtFileInfoMap, problems);
				}
			}
		};
	}

	protected void purgeHgtFileInfoMapData( Map<TileKey, HgtFileInfo> hgtFileInfoMap, TileKey tileKey ) {
		int threshold = 2;
		int i = 0;
		while( i < hgtFileInfoWithData.size() ) {
			TileKey tkey = hgtFileInfoWithData.get( i );
			if (
				tkey.east < tileKey.east - threshold
				|| tkey.east > tileKey.east + threshold
				|| tkey.north > tileKey.north + threshold
				|| tkey.north < tileKey.north - threshold
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
		}
	}

	/**
	 * Mostly copy of org.openstreetmap.josm.plugins.elevation.HgtReader readElevation
	 * See https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java#L148C26-L148C39
	 */
	public Short getAltitudeAtPosition( ReadableMap center ) {
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
					short[][] data = info.getData();
					if ( null != data ) {
						if ( ! hgtFileInfoWithData.contains( tileKey ) ) {
							hgtFileInfoWithData.add( tileKey );
						}
						int[] index = getIndex(lng, lat, data.length);
						altitude = data[index[0]][index[1]];
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
	 * Mostly copy of org.openstreetmap.josm.plugins.elevation.HgtReader getIndex
	 * See https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java#L200
	 */
	private static int[] getIndex( double lng, double lat, int mapSize ) {
		float fraction = ( (float) SRTM_EXTENT) / ( mapSize - 1 );
		int latitude = (int) Math.round(frac(Math.abs(lat)) / fraction);
		int longitude = (int) Math.round(frac(Math.abs(lng)) / fraction);
		if (lat >= 0) {
			latitude = mapSize - latitude - 1;
		}
		if (lng < 0) {
			longitude = mapSize - longitude - 1;
		}
		return new int[] { latitude, longitude };
	}

	/**
	 * Copy of org.openstreetmap.josm.plugins.elevation.HgtReader frac
	 * See https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java#L246
	 */
	public static double frac( double d ) {
		long iPart;
		double fPart;
		iPart = (long) d;
		fPart = d - iPart;
		return fPart;
	}

	class HgtFileInfo {
		final DemFile file;

		final long size;

		protected short[][] data = null;

		HgtFileInfo( DemFile file, long size ) {
			this.file = file;
			this.size = size;
		}

		/**
		 * Copy of org.openstreetmap.josm.plugins.elevation.HgtReader readHgtFile
		 * See https://github.com/JOSM/josm-plugins/blob/5026c5627f2cacfb2410505a869fd915211edf41/ElevationProfile/src/org/openstreetmap/josm/plugins/elevation/HgtReader.java#L102
		 */
		private static short[][] readHgtFile( InputStream fis) throws IOException {

			short[][] data = null;

			// choose the right endianness
			ByteBuffer bb = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				bb = ByteBuffer.wrap(fis.readAllBytes());
			}
			bb.order(ByteOrder.BIG_ENDIAN);
			int size = (int) Math.sqrt(bb.array().length / 2.0);
			data = new short[size][size];
			int x = 0;
			int y = 0;
			while (x < size) {
				while (y < size) {
					data[x][y] = bb.getShort(2 * (x * size + y));
					y++;
				}
				x++;
				y = 0;
			}

			return data;
		}

		public short[][] getData() {
			if ( data == null && rateLimiter.tryAcquire() ) {
				try {
					data = readHgtFile( file.openInputStream() );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
			}
			return data;
		}

		public void resetData() {
			if ( data != null ) {
				data = null;
			}
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
