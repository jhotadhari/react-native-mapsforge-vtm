import type { SharedValue } from 'react-native-reanimated';
import { PixelRatio } from 'react-native';

/**
 * Web Mercator projection helpers for reanimated worklets.
 *
 * Geographic (lat/lng) → normalised Mercator [0..1] → screen dp.
 *
 * vtm renders at native device-pixel resolution.  The formula converts
 * geographic offsets to dp using the actual runtime tile size and density:
 *
 *   dx_dp = dMx × TILE_SIZE × 2^zoom ÷ DENSITY
 *
 * Both constants are read once at module init time so worklets capture
 * them as simple number values.
 */

// vtm 0.28.0 Tile.SIZE is 512 at init but themes (loaded during
// MapFragment creation) override it to 576.  MapContainer.getConstants()
// runs BEFORE themes load, so we hardcode the post-theme value verified
// against vtm's own viewport.toScreenPoint() output on device.
// The onMapUpdate Fabric event also emits tileSize for debugging.
const TILE_SIZE = 576;

const DENSITY = PixelRatio.get();

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

/**
 * Clamps a latitude to the Web Mercator valid range [-85.051129°, +85.051129°].
 */
function clampLat(lat: number): number {
	'worklet';
	const max = 85.0511287798066;
	if (lat > max) return max;
	if (lat < -max) return -max;
	return lat;
}

/**
 * Converts a lat/lng geographic point to normalised Mercator coordinates.
 */
function latLngToMercator(
	lat: number,
	lng: number
): { mx: number; my: number } {
	'worklet';
	const mx = (lng + 180) / 360;
	const latRad = clampLat(lat) * DEG_TO_RAD;
	const mercY = Math.log(Math.tan(Math.PI / 4 + latRad / 2));
	const my = 0.5 - mercY / (2 * Math.PI);
	return { mx, my };
}

/**
 * Converts normalised Mercator coordinates back to lat/lng.
 */
function mercatorToLatLng(
	mx: number,
	my: number
): { lat: number; lng: number } {
	'worklet';
	const lng = mx * 360 - 180;
	const mercY = (0.5 - my) * 2 * Math.PI;
	const latRad = 2 * Math.atan(Math.exp(mercY)) - Math.PI / 2;
	const lat = latRad * RAD_TO_DEG;
	return { lat, lng };
}

/**
 * Wraps a longitude difference to [-0.5, +0.5] for antimeridian crossing.
 */
function wrapMxDelta(dMx: number): number {
	'worklet';
	if (dMx > 0.5) return dMx - 1;
	if (dMx < -0.5) return dMx + 1;
	return dMx;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Converts a lat/lng geographic point to screen pixel coordinates (dp).
 *
 * Uses the standard Web Mercator tile scheme (256 px tiles).
 * Bearing and tilt are fully accounted for.
 */
export function toScreenPosition(
	centerSv: SharedValue<[number, number] | null>,
	viewportWidthSv: SharedValue<number>,
	viewportHeightSv: SharedValue<number>,
	zoomSv: SharedValue<number>,
	bearingSv: SharedValue<number>,
	tiltSv: SharedValue<number>,
	geoPoint: { lat: number; lng: number }
): { x: number; y: number } | null {
	'worklet';
	const center = centerSv.value;
	if (!center || center.length < 2) return null;

	const zoom = zoomSv.value;
	if (zoom <= 0) return null;

	const vpW = viewportWidthSv.value;
	const vpH = viewportHeightSv.value;
	if (vpW <= 0 || vpH <= 0) return null;

	const centerMerc = latLngToMercator(center[1], center[0]);
	const pointMerc = latLngToMercator(geoPoint.lat, geoPoint.lng);

	const dMx = wrapMxDelta(pointMerc.mx - centerMerc.mx);
	const dMy = pointMerc.my - centerMerc.my;

	// vtm renders at native device-pixel resolution.  Convert from
	// device pixels to dp so the offset matches React Native layout.
	// Verified against vtm's own toScreenPoint() output on device.
	const worldPx = TILE_SIZE * Math.pow(2, zoom);
	const scaleDp = DENSITY > 0 ? worldPx / DENSITY : worldPx;

	const dx = dMx * scaleDp;
	const dy = dMy * scaleDp;

	// Rotate by +bearing.
	const bearingRad = bearingSv.value * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const rx = cosB * dx - sinB * dy;
	const ry = sinB * dx + cosB * dy;

	// Tilt foreshortening.
	const tiltRad = tiltSv.value * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ty = ry * cosT;

	return {
		x: Math.round(vpW / 2 + rx),
		y: Math.round(vpH / 2 + ty),
	};
}

/**
 * Converts screen pixel coordinates (dp) back to a lat/lng geographic point.
 *
 * The inverse of {@link toScreenPosition}.
 */
export function fromScreenPosition(
	centerSv: SharedValue<[number, number] | null>,
	viewportWidthSv: SharedValue<number>,
	viewportHeightSv: SharedValue<number>,
	zoomSv: SharedValue<number>,
	bearingSv: SharedValue<number>,
	tiltSv: SharedValue<number>,
	screenPoint: { x: number; y: number }
): { lat: number; lng: number } | null {
	'worklet';
	const center = centerSv.value;
	if (!center || center.length < 2) return null;

	const zoom = zoomSv.value;
	if (zoom <= 0) return null;

	const vpW = viewportWidthSv.value;
	const vpH = viewportHeightSv.value;
	if (vpW <= 0 || vpH <= 0) return null;

	const sx = screenPoint.x - vpW / 2;
	const sy = screenPoint.y - vpH / 2;

	const tiltRad = tiltSv.value * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ry = cosT > 0.001 ? sy / cosT : sy;

	const bearingRad = bearingSv.value * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const dx = cosB * sx + sinB * ry;
	const dy = -sinB * sx + cosB * ry;

	const worldPxInverse = TILE_SIZE * Math.pow(2, zoom);
	const scaleDpInverse =
		DENSITY > 0 ? worldPxInverse / DENSITY : worldPxInverse;

	const centerMerc = latLngToMercator(center[1], center[0]);

	const dMx = dx / scaleDpInverse;
	const dMy = dy / scaleDpInverse;

	const pointMx = centerMerc.mx + dMx;
	const pointMy = centerMerc.my + dMy;

	return mercatorToLatLng(pointMx, pointMy);
}
