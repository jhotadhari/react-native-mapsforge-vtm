package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.BitmapLabelLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.android.canvas.AndroidCanvas;
import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.backend.canvas.Canvas;
import org.oscim.backend.canvas.Color;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.core.Point;
import org.oscim.layers.Layer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One {@link BitmapLabelLayer} (stock vtm {@code BitmapRenderer}) per label
 * entry: the label text + bubble are rasterized into a bitmap once per text
 * change, and the bitmap is drawn at a screen offset in the same frame as the
 * rest of the map — pixel-perfect alignment with map-rendered geometry.
 *
 * <h3>Screen anchoring + follow point</h3>
 * The label is positioned relative to a screen anchor ({@code position},
 * fraction of the viewport or dp). When {@code followPosition} is set, the
 * offset is re-computed on every map frame (via {@link ScreenAnchorRegistry})
 * so the label stays on the line between the anchor and the geographic follow
 * point:
 * <ul>
 *   <li>follow point closer than ~5dp to the anchor → label hidden</li>
 *   <li>line long enough to hold the label + {@code gapDp} gap → label
 *       centered inside the line at {@code gapDp} from the anchor</li>
 *   <li>otherwise → label placed beyond the anchor, away from the follow
 *       point, at {@code fallbackOffsetDp} (the historical overlay behavior)</li>
 * </ul>
 */
public class LabelLayerManager extends LayerManager<LabelLayerManager.LabelEntry> {

	private static final String TAG = "LabelLayerManager";

	public static final String NAME = "labels";

	/** Fallback fragment uuid when params carry none (single implicit fragment). */
	public static final String DEFAULT_FRAGMENT_UUID = "__vtm_shared_labels__0";

	/** Follow points closer than this (dp) hide the label. */
	private static final float MIN_LABEL_DISTANCE_DP = 5f;

	// ── Factory ─────────────────────────────────────────────────────────

	private static final Factory<LabelLayerManager> FACTORY = LabelLayerManager::new;

	@NonNull
	public static LabelLayerManager get(int nativeNodeHandle, @NonNull MapView mapView) {
		return LayerManager.get(nativeNodeHandle, mapView, NAME, FACTORY);
	}

	@Nullable
	public static LabelLayerManager getInstance(int nativeNodeHandle) {
		return (LabelLayerManager) LayerManager.getInstance(nativeNodeHandle, NAME);
	}

	// ── Entry type ──────────────────────────────────────────────────────

	public static class LabelEntry {
		@NonNull
		public final String entryUuid;
		@NonNull
		public final String fragmentUuid;

		@Nullable
		public String text;

		// Screen anchor (normalized bridge values).
		public float anchorX = 0.5f;
		public float anchorY = 0.5f;
		@NonNull
		public String anchorUnit = "fraction";

		/** Geographic point the label follows; null = static label. */
		@Nullable
		public GeoPoint followPoint;
		public float gapDp = 52f;
		public float fallbackOffsetDp = 48f;

		// Paint.
		public int fontSize = 12; // dp
		@NonNull
		public String textColor = "#000000";
		@Nullable
		public String backgroundColor = "#ffffff";
		public float backgroundAlpha = 1f;
		public int borderRadius = 6; // dp
		public int paddingHorizontal = 8; // dp
		public int paddingVertical = 3; // dp
		@NonNull
		public String fontFamily = "DEFAULT";
		@NonNull
		public String fontStyle = "BOLD";

		// Rendered state (px).
		@Nullable
		public Bitmap bitmap;
		public int usedWidth = 0;
		public int usedHeight = 0;
		public float offsetX = 0f;
		public float offsetY = 0f;
		public boolean visible = true;

		public LabelEntry(@NonNull String entryUuid, @NonNull String fragmentUuid) {
			this.entryUuid = entryUuid;
			this.fragmentUuid = fragmentUuid;
		}
	}

	// ── Constructor ─────────────────────────────────────────────────────

	protected LabelLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name);
	}

	// ── LayerManager contract ───────────────────────────────────────────

	@NonNull
	@Override
	protected String entryFragmentUuid(@NonNull LabelEntry entry) {
		return entry.fragmentUuid;
	}

	@NonNull
	@Override
	protected Layer createSharedLayer() {
		return new BitmapLabelLayer(mapView.map());
	}

	@NonNull
	@Override
	protected CreateResult<LabelEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		LabelEntry entry = new LabelEntry(entryUuid, fragmentUuid);
		applyParams(entry, params);

		BitmapLabelLayer layer = (BitmapLabelLayer) getSharedLayer(fragmentUuid);
		if (layer == null) {
			throw new IllegalStateException(
				"No shared BitmapLabelLayer for fragmentUuid '" + fragmentUuid
					+ "'. Known fragments: " + sharedLayerFragments.keySet());
		}

		renderBitmap(entry);
		if (entry.bitmap != null) {
			layer.getBitmapRenderer().setBitmap(entry.bitmap, entry.usedWidth, entry.usedHeight);
		}
		computePosition(entry);
		layer.getBitmapRenderer().setOffset(entry.offsetX, entry.offsetY);
		layer.setEnabled(entry.visible);

		if (entry.followPoint != null) {
			registerFrameCallback();
		}

		return new CreateResult<>(entry, null);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull LabelEntry entry) {
		// The shared layer itself is pruned by the base class once the last
		// entry of the fragment is gone — nothing per-entry to remove here.
		unregisterFrameCallback();
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull LabelEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		BitmapLabelLayer layer = (BitmapLabelLayer) getSharedLayer(entry.fragmentUuid);
		if (layer == null) {
			return new UpdateResult(null);
		}

		boolean bitmapChanged = params.hasKey("text") || params.hasKey("paint");
		applyParams(entry, params);

		if (bitmapChanged) {
			renderBitmap(entry);
			if (entry.bitmap != null) {
				layer.getBitmapRenderer().setBitmap(entry.bitmap, entry.usedWidth, entry.usedHeight);
			}
		}

		computePosition(entry);
		layer.getBitmapRenderer().setOffset(entry.offsetX, entry.offsetY);
		layer.setEnabled(entry.visible);

		if (entry.followPoint != null) {
			registerFrameCallback();
		} else {
			unregisterFrameCallback();
		}

		WritableMap responseData = new WritableNativeMap();
		responseData.putString("uuid", entry.entryUuid);
		responseData.putInt("nativeNodeHandle", nativeNodeHandle);
		return new UpdateResult(responseData);
	}

	@Nullable
	@Override
	protected WritableMap hitTestEntry(
		@NonNull LabelEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	) {
		if (!entry.visible || entry.usedWidth <= 0 || entry.usedHeight <= 0) {
			return null;
		}
		// Expand the rect by the gesture threshold so the label is tappable
		// at its edges.
		if (x >= entry.offsetX - gestureScreenDistance
			&& x <= entry.offsetX + entry.usedWidth + gestureScreenDistance
			&& y >= entry.offsetY - gestureScreenDistance
			&& y <= entry.offsetY + entry.usedHeight + gestureScreenDistance) {
			return Arguments.createMap();
		}
		return null;
	}

	// ── Per-frame updates (follow point) ────────────────────────────────

	/** True while a per-frame callback is registered with {@link ScreenAnchorRegistry}. */
	private volatile boolean frameCallbackRegistered = false;

	private final ScreenAnchorRegistry.FrameCallback frameCallback = this::updateFollowLabels;

	private boolean hasFollowEntries() {
		for (LabelEntry entry : entries.values()) {
			if (entry.followPoint != null) {
				return true;
			}
		}
		return false;
	}

	private void registerFrameCallback() {
		if (!frameCallbackRegistered) {
			frameCallbackRegistered = true;
			ScreenAnchorRegistry.register(nativeNodeHandle, mapView, frameCallback);
		}
	}

	private void unregisterFrameCallback() {
		if (frameCallbackRegistered && !hasFollowEntries()) {
			frameCallbackRegistered = false;
			ScreenAnchorRegistry.unregister(nativeNodeHandle, frameCallback);
		}
	}

	@Override
	public void remove(@NonNull String entryUuid) {
		super.remove(entryUuid);
		unregisterFrameCallback();
	}

	@Override
	protected void destroy() {
		if (frameCallbackRegistered) {
			frameCallbackRegistered = false;
			ScreenAnchorRegistry.unregister(nativeNodeHandle, frameCallback);
		}
		super.destroy();
	}

	/**
	 * Per-frame entry point (registered with {@link ScreenAnchorRegistry}):
	 * re-computes the screen offset of every follow label. Pure screen-space
	 * work (viewport projection + renderer offset) — no geometry recompile.
	 */
	private void updateFollowLabels(@NonNull MapView mapView) {
		for (LabelEntry entry : entries.values()) {
			if (entry.followPoint == null) {
				continue;
			}
			BitmapLabelLayer layer = (BitmapLabelLayer) getSharedLayer(entry.fragmentUuid);
			if (layer == null) {
				continue;
			}
			computePosition(entry);
			layer.getBitmapRenderer().setOffset(entry.offsetX, entry.offsetY);
			layer.setEnabled(entry.visible);
		}
	}

	// ── Internal helpers ────────────────────────────────────────────────

	/**
	 * Applies the param keys present in {@code params} onto the entry —
	 * absent keys keep the entry's current values, so updates are sparse.
	 */
	private void applyParams(@NonNull LabelEntry entry, @NonNull ReadableMap params) {
		if (params.hasKey("text")) {
			entry.text = params.isNull("text") ? null : params.getString("text");
		}
		if (params.hasKey("position")) {
			ReadableMap position = params.getMap("position");
			if (position != null) {
				entry.anchorX = (float) position.getDouble("x");
				entry.anchorY = (float) position.getDouble("y");
				entry.anchorUnit = position.hasKey("unit")
					? position.getString("unit")
					: "fraction";
			}
		}
		if (params.hasKey("followPosition")) {
			if (params.isNull("followPosition")) {
				entry.followPoint = null;
			} else {
				com.facebook.react.bridge.ReadableArray follow = params.getArray("followPosition");
				if (follow != null && follow.size() >= 2) {
					entry.followPoint = new GeoPoint(
						Utils.latFromPosition(follow),
						Utils.lngFromPosition(follow)
					);
				} else {
					entry.followPoint = null;
				}
			}
		}
		if (params.hasKey("gapDp")) {
			entry.gapDp = (float) params.getDouble("gapDp");
		}
		if (params.hasKey("fallbackOffsetDp")) {
			entry.fallbackOffsetDp = (float) params.getDouble("fallbackOffsetDp");
		}

		if (!params.hasKey("paint")) {
			return;
		}
		ReadableMap paint = params.getMap("paint");
		if (paint == null) {
			return;
		}
		if (paint.hasKey("fontSize")) {
			entry.fontSize = paint.getInt("fontSize");
		}
		if (paint.hasKey("textColor")) {
			entry.textColor = paint.getString("textColor");
		}
		if (paint.hasKey("backgroundColor")) {
			entry.backgroundColor = paint.isNull("backgroundColor")
				? null
				: paint.getString("backgroundColor");
		}
		if (paint.hasKey("backgroundAlpha")) {
			entry.backgroundAlpha = (float) paint.getDouble("backgroundAlpha");
		}
		if (paint.hasKey("borderRadius")) {
			entry.borderRadius = paint.getInt("borderRadius");
		}
		if (paint.hasKey("paddingHorizontal")) {
			entry.paddingHorizontal = paint.getInt("paddingHorizontal");
		}
		if (paint.hasKey("paddingVertical")) {
			entry.paddingVertical = paint.getInt("paddingVertical");
		}
		if (paint.hasKey("fontFamily")) {
			entry.fontFamily = paint.getString("fontFamily");
		}
		if (paint.hasKey("fontStyle")) {
			entry.fontStyle = paint.getString("fontStyle");
		}
	}

	/**
	 * Rasterizes the label text + bubble into a power-of-two bitmap (the
	 * BitmapRenderer uploads it as a GL texture). Only called when the text
	 * or the paint changed.
	 */
	private void renderBitmap(@NonNull LabelEntry entry) {
		String text = entry.text;
		if (text == null || text.isEmpty()) {
			entry.bitmap = null;
			entry.usedWidth = 0;
			entry.usedHeight = 0;
			return;
		}

		float density = mapView.getResources().getDisplayMetrics().density;
		int fontSizePx = (int) (entry.fontSize * density);
		int padX = (int) (entry.paddingHorizontal * density);
		int padY = (int) (entry.paddingVertical * density);

		Paint textPainter = CanvasAdapter.newPaint();
		textPainter.setStyle(Paint.Style.FILL);
		textPainter.setTextSize(fontSizePx);
		textPainter.setTypeface(getFontFamily(entry.fontFamily), getFontStyle(entry.fontStyle));
		textPainter.setColor(parseLabelColor(entry.textColor, 1f));

		String[] lines = text.split("\n", -1);
		float lineHeight = textPainter.getTextHeight("Ag");
		int maxWidth = 0;
		for (String line : lines) {
			maxWidth = Math.max(maxWidth, (int) textPainter.getTextWidth(line));
		}
		int w = maxWidth + 2 * padX;
		int h = (int) (lineHeight * lines.length) + 2 * padY;
		if (w <= 0 || h <= 0) {
			entry.bitmap = null;
			entry.usedWidth = 0;
			entry.usedHeight = 0;
			return;
		}

		Bitmap bitmap = CanvasAdapter.newBitmap(nextPowerOfTwo(w), nextPowerOfTwo(h), 0);
		Canvas canvas = CanvasAdapter.newCanvas();
		canvas.setBitmap(bitmap);

		// Rounded-rect bubble via the raw Android canvas — the oscim Canvas
		// abstraction has no rect primitive. Skipped on non-Android backends
		// (same cast pattern as the marker rotation).
		if (entry.backgroundColor != null
			&& canvas instanceof AndroidCanvas) {
			android.graphics.Paint bgPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
			bgPaint.setStyle(android.graphics.Paint.Style.FILL);
			// backgroundAlpha is folded into the parsed color (parseLabelColor).
			bgPaint.setColor(parseLabelColor(entry.backgroundColor, entry.backgroundAlpha));
			float radius = entry.borderRadius * density;
			((AndroidCanvas) canvas).canvas.drawRoundRect(0f, 0f, w, h, radius, radius, bgPaint);
		}

		float baseline = padY + lineHeight;
		for (String line : lines) {
			canvas.drawText(line, padX, baseline, textPainter);
			baseline += lineHeight;
		}

		entry.bitmap = bitmap;
		entry.usedWidth = w;
		entry.usedHeight = h;
	}

	/**
	 * Recomputes the label's screen offset (px, top-left) and visibility from
	 * the anchor, the follow point, and the bitmap dimensions.
	 */
	private void computePosition(@NonNull LabelEntry entry) {
		int widthPx = mapView.getWidth();
		int heightPx = mapView.getHeight();
		float density = mapView.getResources().getDisplayMetrics().density;
		if (widthPx <= 0 || heightPx <= 0) {
			entry.visible = false;
			return;
		}

		float ax = "dp".equals(entry.anchorUnit) ? entry.anchorX * density : entry.anchorX * widthPx;
		float ay = "dp".equals(entry.anchorUnit) ? entry.anchorY * density : entry.anchorY * heightPx;

		entry.visible = entry.text != null && !entry.text.isEmpty();
		entry.offsetX = ax - entry.usedWidth / 2f;
		entry.offsetY = ay - entry.usedHeight / 2f;

		if (entry.followPoint != null) {
			Point p = new Point();
			mapView.map().viewport().toScreenPoint(
				entry.followPoint.getLongitude(),
				entry.followPoint.getLatitude(),
				false,
				p
			);
			float dx = (float) (p.x - ax);
			float dy = (float) (p.y - ay);
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < MIN_LABEL_DISTANCE_DP * density) {
				entry.visible = false;
				return;
			}
			float ux = dx / len;
			float uy = dy / len;
			float halfW = entry.usedWidth / 2f;
			float halfH = entry.usedHeight / 2f;
			// Projected half-extent of the (horizontal) label along the
			// line direction.
			float extent = halfW * Math.abs(ux) + halfH * Math.abs(uy);
			float gapPx = entry.gapDp * density;
			if (len >= gapPx + extent) {
				// Long enough: label inside the line, gapDp from the anchor.
				entry.offsetX = (ax + ux * gapPx) - halfW;
				entry.offsetY = (ay + uy * gapPx) - halfH;
			} else {
				// Too short: beyond the anchor, away from the follow point.
				float fallbackPx = entry.fallbackOffsetDp * density;
				entry.offsetX = (ax - ux * fallbackPx) - halfW;
				entry.offsetY = (ay - uy * fallbackPx) - halfH;
			}
		}
	}

	@NonNull
	private Paint.FontFamily getFontFamily(@Nullable String fontFamily) {
		if (fontFamily == null) {
			return Paint.FontFamily.DEFAULT;
		}
		return switch (fontFamily) {
			case "DEFAULT_BOLD" -> Paint.FontFamily.DEFAULT_BOLD;
			case "MONOSPACE" -> Paint.FontFamily.MONOSPACE;
			case "SANS_SERIF" -> Paint.FontFamily.SANS_SERIF;
			case "SERIF" -> Paint.FontFamily.SERIF;
			case "THIN" -> Paint.FontFamily.THIN;
			case "LIGHT" -> Paint.FontFamily.LIGHT;
			case "MEDIUM" -> Paint.FontFamily.MEDIUM;
			case "BLACK" -> Paint.FontFamily.BLACK;
			case "CONDENSED" -> Paint.FontFamily.CONDENSED;
			default -> Paint.FontFamily.DEFAULT;
		};
	}

	@NonNull
	private Paint.FontStyle getFontStyle(@Nullable String fontStyle) {
		if (fontStyle == null) {
			return Paint.FontStyle.BOLD;
		}
		return switch (fontStyle) {
			case "BOLD_ITALIC" -> Paint.FontStyle.BOLD_ITALIC;
			case "ITALIC" -> Paint.FontStyle.ITALIC;
			case "NORMAL" -> Paint.FontStyle.NORMAL;
			default -> Paint.FontStyle.BOLD;
		};
	}

	private static int nextPowerOfTwo(int value) {
		int pot = 1;
		while (pot < value) {
			pot <<= 1;
		}
		return pot;
	}

	/**
	 * Matches CSS rgb()/rgba() colors — oscim's Color.parseColor only accepts
	 * hex, but theme colors (e.g. react-native-paper's) are rgba() strings.
	 */
	private static final Pattern RGBA_COLOR_PATTERN = Pattern.compile(
		"^rgba?\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,\\s*(\\d*\\.?\\d+)\\s*)?\\)$"
	);

	/**
	 * Matches CSS hex shorthand (#RGB / #RGBA) — neither oscim's nor
	 * Android's Color.parseColor accepts the shorthand forms, but themes
	 * (e.g. react-native-paper's '#000') emit them.
	 */
	private static final Pattern HEX_SHORTHAND_PATTERN = Pattern.compile(
		"^#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])?$"
	);

	/**
	 * Parses a label paint color: hex (#rgb/#rgba/#rrggbb/#aarrggbb) or CSS
	 * rgb()/rgba(). {@code alphaMultiplier} (0..1) is applied on top — used
	 * to fold the bubble's backgroundAlpha into the color.
	 */
	private static int parseLabelColor(@NonNull String color, float alphaMultiplier) {
		String trimmed = color.trim();

		Matcher rgbaMatcher = RGBA_COLOR_PATTERN.matcher(trimmed);
		if (rgbaMatcher.matches()) {
			int r = clampColorComponent(rgbaMatcher.group(1));
			int g = clampColorComponent(rgbaMatcher.group(2));
			int b = clampColorComponent(rgbaMatcher.group(3));
			int a = rgbaMatcher.group(4) != null
				? Math.round(Float.parseFloat(rgbaMatcher.group(4)) * 255f)
				: 255;
			a = Math.max(0, Math.min(255, Math.round(a * alphaMultiplier)));
			return Color.get(a, r, g, b);
		}

		Matcher shorthandMatcher = HEX_SHORTHAND_PATTERN.matcher(trimmed);
		if (shorthandMatcher.matches()) {
			// Expand #RGB -> #RRGGBB and #RGBA -> #AARRGGBB (alpha first —
			// the packing oscim/Android expect).
			String nibbleA = shorthandMatcher.group(4);
			String expanded = nibbleA != null
				? "#" + nibbleA + nibbleA
					+ shorthandMatcher.group(1) + shorthandMatcher.group(1)
					+ shorthandMatcher.group(2) + shorthandMatcher.group(2)
					+ shorthandMatcher.group(3) + shorthandMatcher.group(3)
				: "#" + shorthandMatcher.group(1) + shorthandMatcher.group(1)
					+ shorthandMatcher.group(2) + shorthandMatcher.group(2)
					+ shorthandMatcher.group(3) + shorthandMatcher.group(3);
			int parsed = Color.parseColor(expanded);
			return alphaMultiplier < 1f ? Color.fade(parsed, alphaMultiplier) : parsed;
		}

		int parsed = Color.parseColor(trimmed);
		return alphaMultiplier < 1f ? Color.fade(parsed, alphaMultiplier) : parsed;
	}

	private static int clampColorComponent(@Nullable String value) {
		if (value == null) {
			return 0;
		}
		return Math.max(0, Math.min(255, Integer.parseInt(value)));
	}
}
