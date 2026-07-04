import type { SharedValue } from 'react-native-reanimated';

/**
 * Web Mercator projection helpers for reanimated worklets.
 *
 * Geographic (lat/lng) ↔ normalised Mercator [0..1] ↔ screen pixels (dp).
 * Screen-pixel projection requires the current map zoom level because the
 * world-to-screen scale factor is 256·2^zoom.
 */

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

/**
 * Clamps a latitude to the Web Mercator valid range [-85.051129°, +85.051129°].
 * Values outside this range map to infinity in Mercator Y and produce NaN.
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
 *
 * Normalised range: mx ∈ [0, 1], my ∈ [0, 1].
 * mx=0 is the antimeridian (-180°), mx=1 wraps back to the antimeridian.
 * my=0 is the north pole (~85.05°), my=1 is the south pole (~-85.05°).
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
 * Wraps a longitude difference in Mercator-x space to [-0.5, +0.5] so the
 * shortest-path antimeridian crossing is always used.
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
 * Reads shared values directly — callable from any worklet or the JS thread.
 * Returns `null` when the map centre is not yet known, the viewport has zero
 * dimensions, or the zoom level is not yet available.
 *
 * Bearing and tilt are fully accounted for.  When the map is north-up and
 * untilted this produces the same result as the v1 implementation.
 *
 * @param centerSv - Shared value holding `[lng, lat]` of the map centre, or
 *   `null` if no position has been received yet.
 * @param viewportWidthSv - Shared value holding the map viewport width in dp.
 * @param viewportHeightSv - Shared value holding the map viewport height in dp.
 * @param zoomSv - Shared value holding the current zoom level.
 * @param bearingSv - Shared value holding the current map bearing in degrees
 *   (0 = north-up, clockwise).
 * @param tiltSv - Shared value holding the current map tilt in degrees
 *   (0 = top-down, increasing = tilted toward horizon).
 * @param geoPoint - The geographic coordinate to project.
 * @returns `{ x, y }` in dp (the unit used for React Native `left`/`top`
 *   styles), or `null` if the projection cannot be computed.
 *
 * @example
 * ```ts
 * // Inside a useAnimatedStyle worklet:
 * const screenPos = toScreenPosition(
 *   centerSv, viewportWidthSv, viewportHeightSv, zoomSv,
 *   bearingSv, tiltSv,
 *   { lat: 51.5074, lng: -0.1278 }
 * );
 * if (screenPos) {
 *   style.left = screenPos.x;
 *   style.top = screenPos.y;
 * }
 * ```
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

	// dMx, dMy are in normalised Mercator units [0..1].
	// Scale to world pixels at current zoom: 256 · 2^zoom.
	const dMx = wrapMxDelta(pointMerc.mx - centerMerc.mx);
	const dMy = pointMerc.my - centerMerc.my;

	const worldPx = 256 * Math.pow(2, zoom);

	// World-pixel offset from map centre (dx = east, dy = south).
	const dx = dMx * worldPx;
	const dy = dMy * worldPx;

	// Rotate by +bearing — when the map rotates clockwise under a fixed
	// viewport, geographic points trace counter-clockwise paths on screen.
	const bearingRad = bearingSv.value * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const rx = cosB * dx - sinB * dy; // screen-x before tilt
	const ry = sinB * dx + cosB * dy; // screen-y before tilt

	// Tilt foreshortening: orthographic projection of the rotated map plane.
	// The tilt axis is the horizontal screen axis, so only y is scaled.
	// cos(tilt) is exact for vtm's orthographic map rendering.
	const tiltRad = tiltSv.value * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ty = ry * cosT;

	// Translate to screen centre.  Mercator y and screen y increase in the
	// same direction (southward / downward), so no sign flip is needed.
	return {
		x: Math.round(vpW / 2 + rx),
		y: Math.round(vpH / 2 + ty),
	};
}

/**
 * Converts screen pixel coordinates (dp) back to a lat/lng geographic point.
 *
 * The inverse of {@link toScreenPosition}.  Bearing and tilt are fully
 * accounted for.  Reads shared values directly — callable from any worklet
 * or the JS thread.
 *
 * @param centerSv - Shared value holding `[lng, lat]` of the map centre.
 * @param viewportWidthSv - Shared value holding the map viewport width in dp.
 * @param viewportHeightSv - Shared value holding the map viewport height in dp.
 * @param zoomSv - Shared value holding the current zoom level.
 * @param bearingSv - Shared value holding the current map bearing in degrees.
 * @param tiltSv - Shared value holding the current map tilt in degrees.
 * @param screenPoint - The screen coordinates in dp (same unit as React Native
 *   `left`/`top` styles).
 * @returns `{ lat, lng }`, or `null` if the projection cannot be computed.
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

	// Screen offset from viewport centre.
	const sx = screenPoint.x - vpW / 2;
	const sy = screenPoint.y - vpH / 2;

	// Undo tilt: reverse the orthographic y-foreshortening.
	const tiltRad = tiltSv.value * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ry = cosT > 0.001 ? sy / cosT : sy; // guard div-by-zero at ~90° tilt

	// Undo rotation: rotate screen offset by -bearing.
	const bearingRad = bearingSv.value * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const dx = cosB * sx + sinB * ry;
	const dy = -sinB * sx + cosB * ry;

	const worldPx = 256 * Math.pow(2, zoom);

	const centerMerc = latLngToMercator(center[1], center[0]);

	const dMx = dx / worldPx;
	const dMy = dy / worldPx;

	const pointMx = centerMerc.mx + dMx;
	const pointMy = centerMerc.my + dMy;

	return mercatorToLatLng(pointMx, pointMy);
}
