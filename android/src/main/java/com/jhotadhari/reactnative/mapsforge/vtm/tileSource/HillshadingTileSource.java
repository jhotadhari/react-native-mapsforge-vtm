package com.jhotadhari.reactnative.mapsforge.vtm.tileSource;

import android.util.Log;

//import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.util.MercatorProjection;
//import org.mapsforge.map.android.graphics.AndroidCanvas;
//import org.mapsforge.map.android.graphics.AndroidCanvas;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidHillshadingBitmap;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.renderer.HillshadingContainer;
import org.mapsforge.map.layer.renderer.ShapeContainer;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.Point;
import org.oscim.layers.tile.MapTile;
import org.oscim.map.Viewport;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.QueryResult;
import org.oscim.tiling.TileSource;
//import org.slf4j.Logger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
//import java.util.logging.Logger;

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

		public HillshadingTileDataSource( HillshadingTileSource hillshadingTileSource ) {
			mTileSource = hillshadingTileSource;

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

			short magnitude = 90;

			QueryResult res = QueryResult.FAILED;
			try {

				float effectiveMagnitude = Math.min( Math.max( 0f, magnitude * hillsCfg.getMaginuteScaleFactor() ), 255f) / 255f;
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

//
//				Log.d("testtest tile.mapSize", String.valueOf(tile.mapSize));


//				Log.d( "testtest", "" );
//				Log.d( "testtest tile", "x: " + String.valueOf( tile.tileX ) + " y: " + String.valueOf( tile.tileY ) + " zoomLevel: " + String.valueOf( tile.zoomLevel ) );


				int shadingLngStep = 1;
				int shadingLatStep = 1;
				for ( int shadingLeftLng = (int) Math.floor( maptileLeftLng ) ; shadingLeftLng <= maptileRightLng; shadingLeftLng += shadingLngStep ) {
					for ( int shadingBottomLat = (int) Math.floor( maptileBottomLat ); shadingBottomLat <= maptileTopLat; shadingBottomLat += shadingLatStep ) {
						int shadingRightLng = shadingLeftLng + 1;
						int shadingTopLat = shadingBottomLat + 1;



//						Log.d("testtest shadingLeftLng", String.valueOf( shadingLeftLng ) + " " + String.valueOf(shadingTopLat) );

						AndroidHillshadingBitmap shadingTile = null;
						try {
							shadingTile = (AndroidHillshadingBitmap) hillsCfg.getShadingTile( shadingBottomLat, shadingLeftLng, pxPerLat, pxPerLng );
						} catch (Exception e) {
							log.debug( e.getMessage() );
							continue;
						}

						double shadingPixelOffset = 0d;


						final int padding = shadingTile.getPadding();
						final int shadingInnerWidth = shadingTile.getWidth() - 2 * padding;
						final int shadingInnerHeight = shadingTile.getHeight() - 2 * padding;


//						Log.d("testtest padding", String.valueOf( padding ) );
//						Log.d("testtest shadingInnerWidth", String.valueOf( shadingInnerWidth ) );
//						Log.d("testtest shadingInnerHeight", String.valueOf( shadingInnerHeight ) );


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
						if (shadingTopLat > maptileTopLat) { // map tile ends in shading tile
							shadingSubrectTop = padding + shadingInnerHeight * ((shadingTopLat - maptileTopLat) / shadingLatStep);
						} else if (maptileTopLat > shadingTopLat) {
							maptileSubrectTop = MercatorProjection.latitudeToPixelY(shadingTopLat + (shadingPixelOffset / shadingInnerHeight), tile.mapSize) - origin.y;
						}
						if (shadingBottomLat < maptileBottomLat) { // map tile ends in shading tile
							shadingSubrectBottom = padding + shadingInnerHeight - shadingInnerHeight * ((maptileBottomLat - shadingBottomLat) / shadingLatStep);
						} else if (maptileBottomLat < shadingBottomLat) {
							maptileSubrectBottom = MercatorProjection.latitudeToPixelY(shadingBottomLat + (shadingPixelOffset / shadingInnerHeight), tile.mapSize) - origin.y;
						}
						if (shadingLeftLng < maptileLeftLng) { // map tile ends in shading tile
							shadingSubrectLeft = padding + shadingInnerWidth * ((maptileLeftLng - shadingLeftLng) / shadingLngStep);
						} else if (maptileLeftLng < shadingLeftLng) {
							maptileSubrectLeft = MercatorProjection.longitudeToPixelX(shadingLeftLng + (shadingPixelOffset / shadingInnerWidth), tile.mapSize) - origin.x;
						}
						if (shadingRightLng > maptileRightLng) { // map tile ends in shading tile
							shadingSubrectRight = padding + shadingInnerWidth - shadingInnerWidth * ((shadingRightLng - maptileRightLng) / shadingLngStep);
						} else if (maptileRightLng > shadingRightLng) {
							maptileSubrectRight = MercatorProjection.longitudeToPixelX(shadingRightLng + (shadingPixelOffset / shadingInnerHeight), tile.mapSize) - origin.x;
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

						// Render ShapeContainer to TileBitmap
						Canvas canvas = AndroidGraphicFactory.INSTANCE.createCanvas();
						TileBitmap bitmap = AndroidGraphicFactory.INSTANCE.createTileBitmap( tile.SIZE, true );
						canvas.setBitmap( bitmap );
						HillshadingContainer hillshadingContainer = (HillshadingContainer) hillShape;
						canvas.shadeBitmap(
							hillshadingContainer.bitmap,
							hillshadingContainer.hillsRect,
							hillshadingContainer.tileRect,
							hillshadingContainer.magnitude
						);

						// Convert org.mapsforge.core.graphics.TileBitmap to org.oscim.backend.canvas.Bitmap
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						bitmap.compress( bytes );
						org.oscim.backend.canvas.Bitmap bbitmap = CanvasAdapter.decodeBitmap( new ByteArrayInputStream( bytes.toByteArray() ) );

						if ( ! bbitmap.isValid() ) {
							log.debug("{} invalid bitmap", tile);
						} else {
							sink.setTileImage( bbitmap );
							res = QueryResult.SUCCESS;
						}
					}
				}

			} catch ( Throwable t ) {
				log.error( t.toString(), t );
			} finally {
				sink.completed( res );
			}
		}

		@Override
		public void dispose() {}

		@Override
		public void cancel() {}

	}
}
