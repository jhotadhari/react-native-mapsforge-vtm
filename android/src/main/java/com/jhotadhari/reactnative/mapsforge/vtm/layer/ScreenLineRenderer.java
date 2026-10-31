package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import com.jhotadhari.reactnative.mapsforge.vtm.ScreenAnchorPoint;

import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.GL;
import org.oscim.core.MercatorProjection;
import org.oscim.core.Point;
import org.oscim.renderer.GLMatrix;
import org.oscim.renderer.GLShader;
import org.oscim.renderer.GLState;
import org.oscim.renderer.GLUtils;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.LayerRenderer;
import org.oscim.renderer.MapRenderer;

import static org.oscim.backend.GLAdapter.gl;

/**
 * Per-frame line renderer for {@code LayerScreenLine}: the line's quads are
 * rebuilt on the GL thread in {@link #update(GLViewport)} from the frame's
 * own sync viewport, so screen-anchored points track the screen with ZERO
 * latency — no worker thread, no spatial index, no bridge crossings.
 * Unlike {@code LayerPath}'s anchored points (which recompile
 * asynchronously on a worker thread and trail by 1-2 frames), this
 * renderer can never lag the map.
 *
 * <p>Deliberately NOT bucket-based (no {@code LineBucket}/
 * {@code RenderBuckets}): those classes are shadowed by the
 * color-ramp extension and stripped from the vtm JAR in app builds that
 * apply {@code strip-vtm-classes.gradle}, so the core library must not
 * reference them from source. Instead this mirrors
 * {@link org.oscim.renderer.LocationRenderer}: the built-in flat-color
 * {@code base_shader} + the shared quad VBO, transformed per frame into
 * one quad per segment. Endpoints are computed in screen coordinates
 * (geo points via the sync viewport's {@code toScreenPoint} —
 * rotation/bearing-correct), each segment is clipped to the viewport
 * rect (Liang-Barsky) so far-away geo endpoints stay float-safe, and the
 * quads are drawn as triangle strips.</p>
 */
public class ScreenLineRenderer extends LayerRenderer {

	private final Point mTmpPoint = new Point();

	// Endpoints — written by the manager thread, read per frame on the
	// GL thread (volatile reference swaps; arrays are never mutated in
	// place once published).
	private volatile double[] mLngs = new double[0];
	private volatile double[] mLats = new double[0];
	private volatile ScreenAnchorPoint[] mAnchors = new ScreenAnchorPoint[0];
	private volatile int mPointCount = 0;

	// Paint.
	private volatile int mColor = 0xff000000;
	private volatile float mWidthDp = 2f;

	// Per-frame result (written in update, read in render on the same thread).
	private float[] mScreenPoints;
	/** Per segment: midX, midY, halfLength, halfWidth, angle (degrees). */
	private float[] mSegmentData;
	private int mSegmentCount;
	private boolean mVisible;

	// GL state.
	private boolean mInitialized;
	private int mShaderProgram;
	private int hVertexPosition;
	private int hMatrixPosition;
	private int uColor;

	private final GLMatrix mLineMatrix = new GLMatrix();
	private final GLMatrix mRotationMatrix = new GLMatrix();
	private final GLMatrix mScaleMatrix = new GLMatrix();

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

	public void setPaint(int color, float widthDp) {
		this.mColor = color;
		this.mWidthDp = widthDp;
	}

	@Override
	public void update(GLViewport v) {
		if (!mInitialized) {
			init();
			mInitialized = true;
		}

		int pointCount = mPointCount;
		if (pointCount < 2) {
			mVisible = false;
			setReady(false);
			return;
		}

		double[] lngs = mLngs;
		double[] lats = mLats;
		ScreenAnchorPoint[] anchors = mAnchors;

		// Resolve all points to screen coordinates (px, origin at the
		// viewport center — the sync viewport applies bearing/tilt).
		float density = CanvasAdapter.getScale();
		float halfWidthPx = mWidthDp * density / 2f;
		float vpW = v.getWidth();
		float vpH = v.getHeight();

		if (mScreenPoints == null || mScreenPoints.length < pointCount * 2) {
			mScreenPoints = new float[pointCount * 2];
		}
		float[] screenPoints = mScreenPoints;

		for (int i = 0; i < pointCount; i++) {
			ScreenAnchorPoint anchor = i < anchors.length ? anchors[i] : null;
			float sx;
			float sy;
			if (anchor != null) {
				float px = "dp".equals(anchor.unit) ? anchor.x * density : anchor.x * vpW;
				float py = "dp".equals(anchor.unit) ? anchor.y * density : anchor.y * vpH;
				sx = px - vpW / 2f;
				sy = py - vpH / 2f;
			} else {
				double mapX = MercatorProjection.longitudeToX(lngs[i]);
				double mapY = MercatorProjection.latitudeToY(lats[i]);
				v.toScreenPoint(mapX, mapY, mTmpPoint);
				sx = (float) mTmpPoint.x;
				sy = (float) mTmpPoint.y;
			}
			screenPoints[i * 2] = sx;
			screenPoints[i * 2 + 1] = sy;
		}

		// Clip each segment to the viewport rect and store its quad.
		int segmentCount = pointCount - 1;
		if (mSegmentData == null || mSegmentData.length < segmentCount * 5) {
			mSegmentData = new float[segmentCount * 5];
		}
		float[] segmentData = mSegmentData;
		int visibleSegments = 0;
		for (int i = 1; i < pointCount; i++) {
			if (clipSegment(
				segmentData,
				visibleSegments * 5,
				screenPoints[(i - 1) * 2],
				screenPoints[(i - 1) * 2 + 1],
				screenPoints[i * 2],
				screenPoints[i * 2 + 1],
				halfWidthPx,
				vpW,
				vpH
			)) {
				visibleSegments++;
			}
		}

		mSegmentCount = visibleSegments;
		mVisible = visibleSegments > 0;
		setReady(mVisible);
	}

	/**
	 * Clips the segment to the viewport rect (expanded by the line width)
	 * and writes its quad (midX, midY, halfLength, halfWidth, angle) into
	 * {@code out} at {@code offset}.
	 *
	 * @return true when a visible portion was written
	 */
	private static boolean clipSegment(
		float[] out,
		int offset,
		float sx,
		float sy,
		float ex,
		float ey,
		float halfWidthPx,
		float vpW,
		float vpH
	) {
		float minX = -vpW / 2f - halfWidthPx - 1f;
		float maxX = vpW / 2f + halfWidthPx + 1f;
		float minY = -vpH / 2f - halfWidthPx - 1f;
		float maxY = vpH / 2f + halfWidthPx + 1f;

		// Liang-Barsky clip of the segment to the rect.
		float dx = ex - sx;
		float dy = ey - sy;
		float t0 = 0f;
		float t1 = 1f;
		float[] p = { -dx, dx, -dy, dy };
		float[] q = { sx - minX, maxX - sx, sy - minY, maxY - sy };
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0f) {
				if (q[i] < 0f) {
					return false; // parallel and outside
				}
			} else {
				float t = q[i] / p[i];
				if (p[i] < 0f) {
					if (t > t1) {
						return false;
					}
					if (t > t0) {
						t0 = t;
					}
				} else {
					if (t < t0) {
						return false;
					}
					if (t < t1) {
						t1 = t;
					}
				}
			}
		}

		float cx = sx + t0 * dx;
		float cy = sy + t0 * dy;
		float segX = (sx + t1 * dx) - cx;
		float segY = (sy + t1 * dy) - cy;
		float length = (float) Math.sqrt(segX * segX + segY * segY);
		if (length < 0.5f) {
			return false;
		}

		out[offset] = cx + segX / 2f;
		out[offset + 1] = cy + segY / 2f;
		out[offset + 2] = length / 2f;
		out[offset + 3] = halfWidthPx;
		out[offset + 4] = (float) Math.toDegrees(Math.atan2(segY, segX));
		return true;
	}

	@Override
	public void render(GLViewport v) {
		if (!mVisible) {
			return;
		}

		GLState.useProgram(mShaderProgram);
		GLState.test(false, false);
		GLState.blend(false);

		GLState.enableVertexArrays(hVertexPosition, GLState.DISABLED);
		MapRenderer.bindQuadVertexVBO(hVertexPosition);

		float[] segmentData = mSegmentData;
		for (int i = 0; i < mSegmentCount; i++) {
			int offset = i * 5;
			mLineMatrix.setTranslation(
				segmentData[offset],
				segmentData[offset + 1],
				0f
			);
			mRotationMatrix.setRotation(segmentData[offset + 4], 0f, 0f, 1f);
			mScaleMatrix.setScale(segmentData[offset + 2], segmentData[offset + 3], 1f);
			mLineMatrix.multiplyRhs(mRotationMatrix);
			mLineMatrix.multiplyRhs(mScaleMatrix);

			// The segment data is in center-origin screen px — the space
			// vtm's projection matrix already maps to clip space. Compose
			// mvp = proj · T(mid) · R(angle) · S(halfLen, halfWidth)
			// directly (useScreenCoordinates would apply the top-left-
			// origin translation T(-w/2, -h/2) and offset the whole line
			// by half the viewport).
			v.mvp.multiplyMM(v.proj, mLineMatrix);
			v.mvp.setAsUniform(hMatrixPosition);

			GLUtils.setColor(uColor, mColor);

			gl.drawArrays(GL.TRIANGLE_STRIP, 0, 4);
		}
	}

	private void init() {
		mShaderProgram = GLShader.loadShader("base_shader");
		if (mShaderProgram == 0) {
			return;
		}
		hVertexPosition = gl.getAttribLocation(mShaderProgram, "a_pos");
		hMatrixPosition = gl.getUniformLocation(mShaderProgram, "u_mvp");
		uColor = gl.getUniformLocation(mShaderProgram, "u_color");
	}
}
