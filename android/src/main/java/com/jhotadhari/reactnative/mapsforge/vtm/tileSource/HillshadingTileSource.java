package com.jhotadhari.reactnative.mapsforge.vtm.tileSource;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidHillshadingBitmap;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.renderer.HillshadingContainer;
import org.mapsforge.map.layer.renderer.ShapeContainer;
import org.oscim.android.canvas.AndroidBitmap;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.Point;
import org.oscim.layers.tile.MapTile;
import org.oscim.map.Viewport;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.QueryResult;
import org.oscim.tiling.TileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class HillshadingTileSource extends TileSource {

	private final String mHgtDirPath;

	public HillshadingTileSource( String hgtDirPath ) {
		this( hgtDirPath, Viewport.MIN_ZOOM_LEVEL, Viewport.MAX_ZOOM_LEVEL );
	}

	public HillshadingTileSource( String hgtDirPath, int zoomMin, int zoomMax ) {
		super( zoomMin, zoomMax );
		mHgtDirPath = hgtDirPath;
	}

	public String getHgtDirPath() {
		return mHgtDirPath;
	}
	@Override
	public ITileDataSource getDataSource() {
		return new HillshadingTileDataSource(this );
	}

	@Override
	public OpenResult open() {
		return OpenResult.SUCCESS;
	}

	@Override
	public void close() {
		getDataSource().dispose();
	}

	protected class HillshadingTileDataSource implements ITileDataSource {
		private static final Logger log = LoggerFactory.getLogger( HillshadingTileDataSource.class );
		protected final HillshadingTileSource mTileSource;
		protected final HillsRenderConfig hillsCfg;
		protected final short mMagnitude;

		public HillshadingTileDataSource( HillshadingTileSource hillshadingTileSource ) {
			mTileSource = hillshadingTileSource;
			mMagnitude = 90;

			MemoryCachingHgtReaderTileSource hgtReaderTileSource = new MemoryCachingHgtReaderTileSource(
				new DemFolderFS( getDemFolder( mTileSource.getHgtDirPath() ) ),
				new DiffuseLightShadingAlgorithm( 30f ),
//				new SimpleShadingAlgorithm( -1d, 1d ),
				AndroidGraphicFactory.INSTANCE
			);

			hgtReaderTileSource.setEnableInterpolationOverlap( true );
			hillsCfg = new HillsRenderConfig( hgtReaderTileSource );
			hillsCfg.indexOnThread();
		}

		private static File getDemFolder( String hgtDirPath ) {
			File demFolder = new File( hgtDirPath );
			if ( demFolder.exists() && demFolder.isDirectory() && demFolder.canRead() ) {
				return demFolder;
			}
			return null;
		}

		@Override
		public void query( final MapTile tile, final ITileDataSink sink ) {
			QueryResult res = QueryResult.FAILED;

			// Mostly copy of org.mapsforge.map.rendertheme.renderinstruction.Hillshading render method.
			// See https://github.com/mapsforge/mapsforge/blob/e45c41dc46cdfdf0770d687adee8f6d051511f5e/mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/renderinstruction/Hillshading.java#L57
			try {

				float effectiveMagnitude = Math.min( Math.max( 0f, mMagnitude * hillsCfg.getMaginuteScaleFactor() ), 255f) / 255f;
				byte zoomLevel = tile.zoomLevel;
				if ( zoomLevel > mTileSource.getZoomLevelMax() || zoomLevel < mTileSource.getZoomLevelMin() )	// ??? from react props
					return;

				Point origin = tile.getOrigin();
				double maptileTopLat = MercatorProjection.pixelYToLatitude( (long) origin.y, tile.mapSize );
				double maptileLeftLng = MercatorProjection.pixelXToLongitude( (long) origin.x, tile.mapSize );

				double maptileBottomLat = MercatorProjection.pixelYToLatitude((long) origin.y + tile.SIZE, tile.mapSize );
				double maptileRightLng = MercatorProjection.pixelXToLongitude((long) origin.x + tile.SIZE, tile.mapSize );

				double mapTileLatDegrees = maptileTopLat - maptileBottomLat;
				double mapTileLngDegrees = maptileRightLng - maptileLeftLng;
				double pxPerLat = ( tile.SIZE / mapTileLatDegrees );
				double pxPerLng = ( tile.SIZE / mapTileLngDegrees );

				if ( maptileRightLng < maptileLeftLng ) {
					maptileRightLng += tile.mapSize;
				}

				SortedMap<Integer, Map<Integer, Bitmap>> androidBitmapsLngs = new TreeMap<Integer, Map<Integer, Bitmap>>();
				int shadingLngStep = 1;
				int shadingLatStep = 1;
				for ( int shadingLeftLng = (int) Math.floor( maptileLeftLng ) ; shadingLeftLng <= maptileRightLng; shadingLeftLng += shadingLngStep ) {
					SortedMap<Integer, Bitmap> androidBitmapsLats = new TreeMap<>( Collections.reverseOrder() );
					for ( int shadingBottomLat = (int) Math.floor( maptileBottomLat ); shadingBottomLat <= maptileTopLat; shadingBottomLat += shadingLatStep ) {
						int shadingRightLng = shadingLeftLng + 1;
						int shadingTopLat = shadingBottomLat + 1;

						AndroidHillshadingBitmap shadingTile = null;
						try {
							shadingTile = (AndroidHillshadingBitmap) hillsCfg.getShadingTile( shadingBottomLat, shadingLeftLng, pxPerLat, pxPerLng );
						} catch ( Exception e ) {
							log.debug( e.getMessage() );
							continue;
						}

						final int padding = shadingTile.getPadding();
						final int shadingInnerWidth = shadingTile.getWidth() - 2 * padding;
						final int shadingInnerHeight = shadingTile.getHeight() - 2 * padding;

						// shading tile subset if it fully fits inside map tile
						double shadingSubrectTop = padding;
						double shadingSubrectLeft = padding;

						double shadingSubrectRight = shadingSubrectLeft + shadingInnerWidth;
						double shadingSubrectBottom = shadingSubrectTop + shadingInnerHeight;

						// map tile subset if it fully fits inside shading tile
						double maptileSubrectLeft = 0;
						double maptileSubrectTop = 0;
						double maptileSubrectRight = tile.SIZE;
						double maptileSubrectBottom = tile.SIZE;

						// find the intersection between map tile and shading tile in earth coordinates and determine the pixel
						if ( shadingTopLat > maptileTopLat ) { // map tile ends in shading tile
							shadingSubrectTop = padding + shadingInnerHeight * ( ( shadingTopLat - maptileTopLat ) / shadingLatStep );
						} else if ( maptileTopLat > shadingTopLat ) {
							maptileSubrectTop = MercatorProjection.latitudeToPixelY(shadingTopLat, tile.mapSize ) - origin.y;
						}

						if (shadingBottomLat < maptileBottomLat) { // map tile ends in shading tile
							shadingSubrectBottom = padding + shadingInnerHeight - shadingInnerHeight * ((maptileBottomLat - shadingBottomLat) / shadingLatStep);
						} else if (maptileBottomLat < shadingBottomLat) {
							maptileSubrectBottom = MercatorProjection.latitudeToPixelY(shadingBottomLat, tile.mapSize) - origin.y;
						}

						if (shadingLeftLng < maptileLeftLng) { // map tile ends in shading tile
							shadingSubrectLeft = padding + shadingInnerWidth * ((maptileLeftLng - shadingLeftLng) / shadingLngStep);
						} else if (maptileLeftLng < shadingLeftLng) {
							maptileSubrectLeft = MercatorProjection.longitudeToPixelX(shadingLeftLng, tile.mapSize) - origin.x;
						}

						if (shadingRightLng > maptileRightLng) { // map tile ends in shading tile
							shadingSubrectRight = padding + shadingInnerWidth - shadingInnerWidth * ((shadingRightLng - maptileRightLng) / shadingLngStep);
						} else if (maptileRightLng > shadingRightLng) {
							maptileSubrectRight = MercatorProjection.longitudeToPixelX(shadingRightLng, tile.mapSize) - origin.x;
						}

						Rectangle hillsRect = new Rectangle(
							shadingSubrectLeft,
							shadingSubrectTop,
							shadingSubrectRight,
							shadingSubrectBottom
						);
						Rectangle maptileRect = new Rectangle(
							maptileSubrectLeft,
							maptileSubrectTop,
							maptileSubrectRight,
							maptileSubrectBottom
						);
						ShapeContainer hillShape = new HillshadingContainer(
							shadingTile,
							effectiveMagnitude,
							hillsRect,
							maptileRect
						);

						try {
							// Render ShapeContainer to mapsforge TileBitmap
							Canvas mfCanvas = AndroidGraphicFactory.INSTANCE.createCanvas();
							TileBitmap mfTileBitmap = AndroidGraphicFactory.INSTANCE.createTileBitmap( tile.SIZE, true );
							mfCanvas.setBitmap( mfTileBitmap );
							HillshadingContainer hillshadingContainer = (HillshadingContainer) hillShape;
							mfCanvas.shadeBitmap(
								hillshadingContainer.bitmap,
								hillshadingContainer.hillsRect,
								hillshadingContainer.tileRect,
								hillshadingContainer.magnitude
							);
							// Crop mapsforge TileBitmap to shaded part and convert to android.graphics.Bitmap.
							Bitmap androidBitmapCropped = Bitmap.createBitmap(
								convertMfTileBitmapToAndroidBitmap( mfTileBitmap ),
								(int) maptileRect.left,							// start x
								(int) maptileRect.top,							// start y
								(int) ( maptileRect.right - maptileRect.left ),	// width
								(int) ( maptileRect.bottom - maptileRect.top )	// height
							);
							// Store bitmap part.
							androidBitmapsLats.put( shadingBottomLat, androidBitmapCropped );
						} catch ( IOException e ) {
							log.debug( e.getMessage() );
						}
					}
					androidBitmapsLngs.put( shadingLeftLng, androidBitmapsLats );
				}

				// Init resulting android.graphics.Bitmap to hold all the shaded puzzle pieces.
				Bitmap androidBitmapResult = Bitmap.createBitmap( tile.SIZE, tile.SIZE, Bitmap.Config.ARGB_8888 );
				android.graphics.Canvas androidCanvasResult = new android.graphics.Canvas( androidBitmapResult );
				// Loop shaded parts and puzzle them together.
				int left = 0;
				for ( Integer lng : androidBitmapsLngs.keySet() ) {
					Map<Integer, Bitmap> lats = androidBitmapsLngs.get( lng );
					int lastWidth = 0;
					int top = 0;
					for ( Integer lat : lats.keySet() ) {
						Bitmap bitmapPart = lats.get( lat );
						androidCanvasResult.drawBitmap(
							bitmapPart,
							left, 		// left
							top, 		// top
							null
						);
						top += bitmapPart.getHeight();
						lastWidth = bitmapPart.getWidth();
					}
					left += lastWidth;
				}
				// Convert to vtmCanvasBitmap and set it to sink.
				AndroidBitmap vtmCanvasBitmapResult = androidBitmapToVtmCanvasBitmap( androidBitmapResult );
				if ( ! vtmCanvasBitmapResult.isValid() ) {
					log.debug("{} invalid bitmap", tile);
				} else {
					sink.setTileImage( vtmCanvasBitmapResult );
					res = QueryResult.SUCCESS;
				}

			} catch ( Throwable t ) {
				log.error( t.toString(), t );
			} finally {
				sink.completed( res );
			}
		}

		public static android.graphics.Bitmap convertMfTileBitmapToAndroidBitmap( org.mapsforge.core.graphics.TileBitmap mfTileBitmap ) throws IOException {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			mfTileBitmap.compress( bytes );
			bytes.close();
			byte[] bytesArray  = bytes.toByteArray();
			Bitmap androidBitmap = BitmapFactory.decodeByteArray( bytesArray, 0, bytesArray.length );
			return androidBitmap;
		}

		public static AndroidBitmap androidBitmapToVtmCanvasBitmap( android.graphics.Bitmap androidBitmap ) throws IOException {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			androidBitmap.compress( Bitmap.CompressFormat.PNG, 100, bytes );
			bytes.close();
			AndroidBitmap vtmCanvasBitmap = (AndroidBitmap) CanvasAdapter.decodeBitmap( new ByteArrayInputStream( bytes.toByteArray() ) );
			return vtmCanvasBitmap;
		}

		public static AndroidBitmap convertMfTileBitmapToVtmCanvasBitmap( org.mapsforge.core.graphics.TileBitmap mfTileBitmap ) throws IOException {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			mfTileBitmap.compress( bytes );
			AndroidBitmap vtmCanvasBitmap = (AndroidBitmap) CanvasAdapter.decodeBitmap( new ByteArrayInputStream( bytes.toByteArray() ) );
			return vtmCanvasBitmap;
		}

		public static android.graphics.Bitmap vtmCanvasBitmapToAndroidBitmap( AndroidBitmap vtmCanvasBitmap ) throws IOException {
			byte[] pngBytes  = vtmCanvasBitmap.getPngEncodedData();
			Bitmap androidBitmap = BitmapFactory.decodeByteArray( pngBytes, 0, pngBytes.length );
			return androidBitmap;
		}

		@Override
		public void dispose() {}

		@Override
		public void cancel() {}

	}
}
