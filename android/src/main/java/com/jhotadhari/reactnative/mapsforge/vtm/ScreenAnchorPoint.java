package com.jhotadhari.reactnative.mapsforge.vtm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReadableMap;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.map.Viewport;

/**
 * A point of a layer entry that is pinned to a position on the map's screen
 * instead of a geographic coordinate. The geographic coordinate is resolved
 * from the current viewport (per frame, via {@link ScreenAnchorRegistry}) so
 * the point stays glued to its screen position during pan/zoom/rotate — the
 * escape hatch that lets one end of a native vtm layer follow the viewport
 * without any JS round trip.
 *
 * <p>Screen positions are expressed as fractions of the viewport (unit
 * {@code "fraction"}, default) or device-independent px (unit {@code "dp"}),
 * top-left origin. {@code fromScreenPoint} expects physical px, so dp values
 * are scaled by the display density at resolve time.</p>
 */
public class ScreenAnchorPoint {

	/** Index into the resolved point list (paths); unused (-1) elsewhere. */
	public int index = -1;

	/** Horizontal position: fraction of viewport width (unit "fraction") or dp (unit "dp"). */
	public float x = 0f;

	/** Vertical position: fraction of viewport height (unit "fraction") or dp (unit "dp"). */
	public float y = 0f;

	/** {@code "fraction"} (default) or {@code "dp"}. */
	@NonNull
	public String unit = "fraction";

	/** The currently resolved geographic coordinate (updated per frame). */
	public double lng = Double.NaN;
	public double lat = Double.NaN;

	public ScreenAnchorPoint() {
	}

	@Nullable
	public static ScreenAnchorPoint fromReadableMap( @Nullable ReadableMap map ) {
		if ( map == null ) {
			return null;
		}
		ScreenAnchorPoint point = new ScreenAnchorPoint();
		if ( map.hasKey( "index" ) ) {
			point.index = map.getInt( "index" );
		}
		point.x = (float) map.getDouble( "x" );
		point.y = (float) map.getDouble( "y" );
		if ( map.hasKey( "unit" ) ) {
			point.unit = map.getString( "unit" );
		}
		return point;
	}

	/** True when the geographic coordinate was resolved at least once. */
	public boolean isResolved() {
		return !Double.isNaN( lng ) && !Double.isNaN( lat );
	}

	/**
	 * Resolves the current geographic coordinate for this anchor from the map
	 * viewport.
	 *
	 * @return true when the resolved coordinate differs from the previous one
	 */
	public boolean resolveAndCheckChanged( @NonNull MapView mapView ) {
		double oldLng = lng;
		double oldLat = lat;
		resolve( mapView );
		return oldLng != lng || oldLat != lat;
	}

	/**
	 * Resolves the current geographic coordinate for this anchor from the map
	 * viewport. No-op when the viewport has no size yet (the first real frame
	 * event will resolve it).
	 */
	public void resolve( @NonNull MapView mapView ) {
		int widthPx = mapView.getWidth();
		int heightPx = mapView.getHeight();
		if ( widthPx <= 0 || heightPx <= 0 ) {
			return;
		}
		Viewport viewport = mapView.map().viewport();
		float density = mapView.getResources().getDisplayMetrics().density;
		float px = "dp".equals( unit ) ? x * density : x * widthPx;
		float py = "dp".equals( unit ) ? y * density : y * heightPx;
		GeoPoint geo = viewport.fromScreenPoint( px, py );
		this.lng = geo.getLongitude();
		this.lat = geo.getLatitude();
	}
}
