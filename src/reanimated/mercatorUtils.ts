import type { SharedValue } from 'react-native-reanimated';

/**
 * Internal helpers — normalised Web Mercator coordinates (0..1 range).
 * These are zoom-independent: the tile-scale factor 256·2^zoom cancels when
 * computing the screen-pixel delta from the map centre, so no zoom term
 * appears in the public API.
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
 * Returns `null` when the map centre is not yet known or the viewport has zero
 * dimensions.
 *
 * **Limitation (v1):** Bearing and tilt are **not** accounted for. The
 * returned coordinates are correct only when the map is north-up and untilted.
 * The shared values already track `bearing` and `tilt`; a future version may
 * add the rotation-matrix + perspective-transform correction.
 *
 * @param centerSv - Shared value holding `[lng, lat]` of the map centre, or
 *   `null` if no position has been received yet.
 * @param viewportWidthSv - Shared value holding the map viewport width in dp.
 * @param viewportHeightSv - Shared value holding the map viewport height in dp.
 * @param geoPoint - The geographic coordinate to project.
 * @returns `{ x, y }` in dp (the unit used for React Native `left`/`top`
 *   styles), or `null` if the projection cannot be computed.
 *
 * @example
 * ```ts
 * // Inside a useAnimatedStyle worklet:
 * const screenPos = toScreenPosition(
 *   centerSv, viewportWidthSv, viewportHeightSv,
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
	geoPoint: { lat: number; lng: number }
): { x: number; y: number } | null {
	'worklet';
	const center = centerSv.value;
	if (!center || center.length < 2) return null;

	const vpW = viewportWidthSv.value;
	const vpH = viewportHeightSv.value;
	if (vpW <= 0 || vpH <= 0) return null;

	const centerMerc = latLngToMercator(center[1], center[0]);
	const pointMerc = latLngToMercator(geoPoint.lat, geoPoint.lng);

	const dMx = wrapMxDelta(pointMerc.mx - centerMerc.mx);
	const dMy = pointMerc.my - centerMerc.my;

	return {
		x: Math.round(vpW * (0.5 + dMx)),
		y: Math.round(vpH * (0.5 - dMy)),
	};
}

/**
 * Converts screen pixel coordinates (dp) back to a lat/lng geographic point.
 *
 * The inverse of {@link toScreenPosition}. Same assumptions apply (no
 * bearing/tilt correction). Reads shared values directly — callable from any
 * worklet or the JS thread.
 *
 * @param centerSv - Shared value holding `[lng, lat]` of the map centre.
 * @param viewportWidthSv - Shared value holding the map viewport width in dp.
 * @param viewportHeightSv - Shared value holding the map viewport height in dp.
 * @param screenPoint - The screen coordinates in dp (same unit as React Native
 *   `left`/`top` styles).
 * @returns `{ lat, lng }`, or `null` if the projection cannot be computed.
 */
export function fromScreenPosition(
	centerSv: SharedValue<[number, number] | null>,
	viewportWidthSv: SharedValue<number>,
	viewportHeightSv: SharedValue<number>,
	screenPoint: { x: number; y: number }
): { lat: number; lng: number } | null {
	'worklet';
	const center = centerSv.value;
	if (!center || center.length < 2) return null;

	const vpW = viewportWidthSv.value;
	const vpH = viewportHeightSv.value;
	if (vpW <= 0 || vpH <= 0) return null;

	const centerMerc = latLngToMercator(center[1], center[0]);

	const dMx = screenPoint.x / vpW - 0.5;
	const dMy = 0.5 - screenPoint.y / vpH;

	const pointMx = centerMerc.mx + dMx;
	const pointMy = centerMerc.my + dMy;

	return mercatorToLatLng(pointMx, pointMy);
}
