package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import com.jhotadhari.reactnative.mapsforge.vtm.ScreenAnchorPoint;

import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeometryBuffer;
import org.oscim.core.MapPosition;
import org.oscim.core.MercatorProjection;
import org.oscim.core.Point;
import org.oscim.core.Tile;
import org.oscim.renderer.BucketRenderer;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.MapRenderer;
import org.oscim.renderer.bucket.LineBucket;
import org.oscim.theme.styles.LineStyle;
import org.oscim.utils.geom.TileClipper;

/**
 * Per-frame line renderer for {@code LayerScreenLine}: the line's geometry
 * is rebuilt on the GL thread in {@link #update(GLViewport)} from the
 * frame's own sync viewport, so screen-anchored points track the screen
 * with ZERO latency — no worker thread, no spatial index, no bridge
 * crossings. Unlike {@code LayerPath}'s anchored points (which recompile
 * asynchronously on a worker thread and trail by 1-2 frames), this
 * renderer can never lag the map.
 *
 * <p>Coordinate convention mirrors {@code AbstractVectorLayer}: endpoints
 * are converted to map coordinates (screen anchors via the sync
 * viewport's {@code fromScreenPoint} — rotation/bearing-correct) and then
 * to viewport-relative local coordinates scaled by
 * {@code Tile.SIZE / UNSCALE_COORD}; the geometry is clamped through a
 * {@link TileClipper} so far-away endpoints can't overflow the fixed-point
 * vertex format. The inherited {@code render()} applies the standard
 * {@code setMatrix} transform.</p>
 */
public class ScreenLineRenderer extends BucketRenderer {

	private static final double UNSCALE_COORD = 4;
	private static final int MAX_CLIP = (int) (Short.MAX_VALUE / MapRenderer.COORD_SCALE);

	private static final double[] EMPTY_DOUBLES = new double[0];
	private static final ScreenAnchorPoint[] EMPTY_ANCHORS = new ScreenAnchorPoint[0];

	private final GeometryBuffer mGeom = new GeometryBuffer(8, 4);
	private final TileClipper mClipper = new TileClipper(-MAX_CLIP, -MAX_CLIP, MAX_CLIP, MAX_CLIP);
	private final Point mTmpPoint = new Point();

	// Endpoints — written by the manager thread, read per frame on the
	// GL thread (volatile reference swaps; arrays are never mutated in
	// place once published).
	private volatile double[] mLngs = EMPTY_DOUBLES;
	private volatile double[] mLats = EMPTY_DOUBLES;
	private volatile ScreenAnchorPoint[] mAnchors = EMPTY_ANCHORS;
	private volatile int mPointCount = 0;

	// Paint.
	private volatile int mColor = 0xff000000;
	private volatile float mWidthDp = 2f;
	private volatile Paint.Cap mCap = Paint.Cap.ROUND;

	/**
	 * Replaces the resolved point list. {@code anchors} is index-parallel:
	 * a non-null entry marks that point as screen-anchored (its lng/lat
	 * slots are ignored).
	 */
	public void setPoints(
		double[] lngs,
		double[] lats,
		ScreenAnchorPoint[] anchors,
		int pointCount
	) {
		this.mLngs = lngs;
		this.mLats = lats;
		this.mAnchors = anchors;
		this.mPointCount = pointCount;
	}

	public void setPaint(int color, float widthDp, Paint.Cap cap) {
		this.mColor = color;
		this.mWidthDp = widthDp;
		this.mCap = cap;
	}

	@Override
	public synchronized void update(GLViewport v) {
		int pointCount = mPointCount;
		if (pointCount < 2) {
			buckets.clear();
			return;
		}

		mMapPosition.copy(v.pos);
		mMapPosition.setScale(mMapPosition.scale / UNSCALE_COORD);

		float density = CanvasAdapter.getScale();
		float widthPx = mWidthDp * density;
		double tileScale = Tile.SIZE * v.pos.scale / UNSCALE_COORD;

		double[] lngs = mLngs;
		double[] lats = mLats;
		ScreenAnchorPoint[] anchors = mAnchors;

		mGeom.clear();
		mGeom.startLine();
		for (int i = 0; i < pointCount; i++) {
			ScreenAnchorPoint anchor = i < anchors.length ? anchors[i] : null;
			double mapX;
			double mapY;
			if (anchor != null) {
				float px = "dp".equals(anchor.unit) ? anchor.x * density : anchor.x * v.getWidth();
				float py = "dp".equals(anchor.unit) ? anchor.y * density : anchor.y * v.getHeight();
				v.fromScreenPoint(px, py, mTmpPoint);
				mapX = mTmpPoint.x;
				mapY = mTmpPoint.y;
			} else {
				mapX = MercatorProjection.longitudeToX(lngs[i]);
				mapY = MercatorProjection.latitudeToY(lats[i]);
			}
			mGeom.addPoint(
				(float) ((mapX - v.pos.x) * tileScale),
				(float) ((mapY - v.pos.y) * tileScale)
			);
		}

		// Clip to the fixed-point coordinate range before building the
		// bucket (far-away geo endpoints would otherwise overflow).
		if (!mClipper.clip(mGeom)) {
			buckets.clear();
			return;
		}

		buckets.clear();
		LineBucket lineBucket = buckets.getLineBucket(0);
		lineBucket.line = LineStyle.builder()
			.reset()
			.color(mColor)
			.cap(mCap)
			.fixed(true)
			.strokeWidth(widthPx)
			.build();
		lineBucket.addLine(mGeom);
		compile();
	}
}
