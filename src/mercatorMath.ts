/**
 * Plain-JS Mercator projection utilities.
 *
 * Non-worklet counterparts to {@link reanimated/mercatorUtils.ts}.  Take raw
 * numbers instead of SharedValues, so they can be called from React hooks,
 * event handlers, and any non-worklet JS context.
 *
 * The math is identical to the worklet versions — same TILE_SIZE, same density
 * handling — so converting between screen and geographic coordinates is
 * consistent regardless of which API you use.
 */

import { PixelRatio } from 'react-native';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/**
 * vtm's Tile.SIZE.  Must match the native constant.
 * Themes can override this at runtime — callers reading from screen
 * coordinates should use the same value the renderer uses.
 */
const TILE_SIZE = 576;

/** Captured at module init. */
const DENSITY = PixelRatio.get();

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

// ---------------------------------------------------------------------------
// Core Mercator projection
// ---------------------------------------------------------------------------

/**
 * Clamps a latitude to the Web Mercator valid range [-85.051129°, +85.051129°].
 */
export function clampLat(lat: number): number {
	const max = 85.0511287798066;
	if (lat > max) return max;
	if (lat < -max) return -max;
	return lat;
}

/**
 * Converts a geographic lat/lng point to normalised Mercator coordinates.
 *
 * Normalised range: mx ∈ [0, 1], my ∈ [0, 1].
 * mx=0 is the antimeridian (-180°), mx=1 wraps back to the antimeridian.
 * my=0 is the north pole (~85.05°), my=1 is the south pole (~-85.05°).
 */
export function latLngToMercator(
	lat: number,
	lng: number
): { mx: number; my: number } {
	// Wrap lng so mx stays in [0, 1] for any input (e.g. 190° → -170°).
	let mx = (lng + 180) / 360;
	mx -= Math.floor(mx);
	const latRad = clampLat(lat) * DEG_TO_RAD;
	const mercY = Math.log(Math.tan(Math.PI / 4 + latRad / 2));
	const my = 0.5 - mercY / (2 * Math.PI);
	return { mx, my };
}

/**
 * Converts normalised Mercator coordinates back to geographic lat/lng.
 */
export function mercatorToLatLng(
	mx: number,
	my: number
): { lat: number; lng: number } {
	const lng = mx * 360 - 180;
	const mercY = (0.5 - my) * 2 * Math.PI;
	const latRad = 2 * Math.atan(Math.exp(mercY)) - Math.PI / 2;
	const lat = latRad * RAD_TO_DEG;
	return { lat, lng };
}

// ---------------------------------------------------------------------------
// Antimeridian wrapping
// ---------------------------------------------------------------------------

/**
 * Wraps a longitude difference to [-180°, +180°].
 */
export function wrapLngDelta(dLng: number): number {
	if (dLng > 180) return dLng - 360;
	if (dLng < -180) return dLng + 360;
	return dLng;
}

/**
 * Wraps a normalised Mercator mx delta to [-0.5, +0.5].
 */
export function wrapMxDelta(dMx: number): number {
	if (dMx > 0.5) return dMx - 1;
	if (dMx < -0.5) return dMx + 1;
	return dMx;
}

// ---------------------------------------------------------------------------
// Screen ↔ geographic conversion
// ---------------------------------------------------------------------------

export interface MercatorMathOptions {
	/**
	 * Tile size in pixels.  Defaults to vtm's Tile.SIZE (576).
	 * Use 256 for standard Web Mercator / OSM tile calculations.
	 */
	tileSize?: number;
	/**
	 * Device pixel ratio.  Defaults to the value captured from
	 * {@link PixelRatio.get} at module init.
	 */
	density?: number;
}

/**
 * Converts a geographic lat/lng point to screen pixel coordinates (dp).
 *
 * Uses the same math as the worklet {@link toScreenPosition}.
 * Bearing and tilt are fully accounted for.
 */
export function toScreenPosition(
	center: [number, number],
	zoom: number,
	viewportWidth: number,
	viewportHeight: number,
	bearing: number,
	tilt: number,
	geoPoint: { lat: number; lng: number },
	options?: MercatorMathOptions
): { x: number; y: number } | null {
	if (!center || center.length < 2) return null;
	if (zoom <= 0) return null;
	if (viewportWidth <= 0 || viewportHeight <= 0) return null;

	const tileSize = options?.tileSize ?? TILE_SIZE;
	const density = options?.density ?? DENSITY;

	const centerMerc = latLngToMercator(center[1], center[0]);
	const pointMerc = latLngToMercator(geoPoint.lat, geoPoint.lng);

	const dMx = wrapMxDelta(pointMerc.mx - centerMerc.mx);
	const dMy = pointMerc.my - centerMerc.my;

	const worldPx = tileSize * Math.pow(2, zoom);
	const scaleDp = density > 0 ? worldPx / density : worldPx;

	const dx = dMx * scaleDp;
	const dy = dMy * scaleDp;

	// Rotate by +bearing.
	const bearingRad = bearing * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const rx = cosB * dx - sinB * dy;
	const ry = sinB * dx + cosB * dy;

	// Tilt foreshortening.
	const tiltRad = tilt * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ty = ry * cosT;

	return {
		x: Math.round(viewportWidth / 2 + rx),
		y: Math.round(viewportHeight / 2 + ty),
	};
}

/**
 * Converts screen pixel coordinates (dp) back to a geographic lat/lng point.
 *
 * Uses the same math as the worklet {@link fromScreenPosition}.
 * Bearing and tilt are fully accounted for.
 */
export function fromScreenPosition(
	center: [number, number],
	zoom: number,
	viewportWidth: number,
	viewportHeight: number,
	bearing: number,
	tilt: number,
	screenPoint: { x: number; y: number },
	options?: MercatorMathOptions
): { lat: number; lng: number } | null {
	if (!center || center.length < 2) return null;
	if (zoom <= 0) return null;
	if (viewportWidth <= 0 || viewportHeight <= 0) return null;

	const tileSize = options?.tileSize ?? TILE_SIZE;
	const density = options?.density ?? DENSITY;

	// Screen offset from viewport centre.
	const sx = screenPoint.x - viewportWidth / 2;
	const sy = screenPoint.y - viewportHeight / 2;

	// Undo tilt: reverse the orthographic y-foreshortening.
	const tiltRad = tilt * DEG_TO_RAD;
	const cosT = Math.cos(tiltRad);
	const ry = cosT > 0.001 ? sy / cosT : sy;

	// Undo rotation: rotate screen offset by -bearing.
	const bearingRad = bearing * DEG_TO_RAD;
	const cosB = Math.cos(bearingRad);
	const sinB = Math.sin(bearingRad);
	const dx = cosB * sx + sinB * ry;
	const dy = -sinB * sx + cosB * ry;

	const worldPx = tileSize * Math.pow(2, zoom);
	const scaleDp = density > 0 ? worldPx / density : worldPx;

	const centerMerc = latLngToMercator(center[1], center[0]);

	const dMx = dx / scaleDp;
	const dMy = dy / scaleDp;

	const pointMx = centerMerc.mx + dMx;
	const pointMy = centerMerc.my + dMy;

	return mercatorToLatLng(pointMx, pointMy);
}

// ---------------------------------------------------------------------------
// Viewport bounding box
// ---------------------------------------------------------------------------

/**
 * Geographic bounding box (axis-aligned).
 * `[west, south, east, north]` in degrees.
 */
export type ViewportBbox = [
	number,
	number,
	number,
	number,
];

/**
 * Computes the axis-aligned geographic bounding box of the visible viewport.
 *
 * Projects the four screen corners to lat/lng and takes the min/max extent.
 * With bearing/tilt, the true visible area is a rotated trapezoid, so the
 * AABB is larger than the true visible footprint — safe for spatial culling
 * (may include a few extra off-screen items, but never misses one).
 *
 * Returns `null` when the projection cannot be computed.
 */
export function computeViewportBbox(
	center: [number, number],
	zoom: number,
	viewportWidth: number,
	viewportHeight: number,
	bearing: number,
	tilt: number,
	options?: MercatorMathOptions
): ViewportBbox | null {
	const corners = [
		{ x: 0, y: 0 },
		{ x: viewportWidth, y: 0 },
		{ x: viewportWidth, y: viewportHeight },
		{ x: 0, y: viewportHeight },
	];

	const points: { lat: number; lng: number }[] = [];
	for (const c of corners) {
		const pt = fromScreenPosition(
			center,
			zoom,
			viewportWidth,
			viewportHeight,
			bearing,
			tilt,
			c,
			options
		);
		if (!pt) return null;
		points.push(pt);
	}

	let minLng = Infinity;
	let maxLng = -Infinity;
	let minLat = Infinity;
	let maxLat = -Infinity;

	for (const p of points) {
		if (p.lng < minLng) minLng = p.lng;
		if (p.lng > maxLng) maxLng = p.lng;
		if (p.lat < minLat) minLat = p.lat;
		if (p.lat > maxLat) maxLat = p.lat;
	}

	return [
		minLng,
		minLat,
		maxLng,
		maxLat,
	];
}

// ---------------------------------------------------------------------------
// Tile-grid helpers (Web Mercator / OSM tile scheme)
// ---------------------------------------------------------------------------

/**
 * Returns the Web Mercator tile coordinates for a geographic point at the
 * given zoom level.  Standard OSM / Google tile scheme.
 */
export function lngLatToTile(
	lng: number,
	lat: number,
	zoom: number
): { x: number; y: number } {
	const n = Math.pow(2, zoom);
	// Wrap lng so tile x stays in [0, n) for any input.
	let mx = (lng + 180) / 360;
	mx -= Math.floor(mx);
	const x = mx * n;
	// Clamp to valid Web Mercator range — tan() / 1/cos() diverge
	// beyond ±85.05°, producing NaN tile coordinates.
	const latRad = (clampLat(lat) * Math.PI) / 180;
	const y =
		((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) /
			2) *
		n;
	return { x, y };
}

/**
 * Returns the geographic bounding box of a single tile.
 */
export function tileToBbox(tx: number, ty: number, zoom: number): ViewportBbox {
	const n = Math.pow(2, zoom);
	const west = (tx / n) * 360 - 180;
	const east = ((tx + 1) / n) * 360 - 180;
	const northRad = Math.atan(Math.sinh(Math.PI * (1 - (2 * ty) / n)));
	const southRad = Math.atan(Math.sinh(Math.PI * (1 - (2 * (ty + 1)) / n)));
	const north = (northRad * 180) / Math.PI;
	const south = (southRad * 180) / Math.PI;
	return [
		west,
		south,
		east,
		north,
	];
}

/**
 * Snaps a geographic bbox to Web Mercator tile boundaries at `tileZoom`.
 * Returns the smallest bbox that fully contains the input and is aligned
 * to tile edges.
 *
 * Small pans within the same tile(s) produce the same snapped bbox, so
 * query keys stay stable and no unnecessary work fires on minor pans.
 */
export function snapBboxToTiles(
	bbox: ViewportBbox,
	tileZoom: number
): ViewportBbox {
	const z = Math.max(0, Math.round(tileZoom));
	const nw = lngLatToTile(bbox[0], bbox[3], z); // west, north
	const se = lngLatToTile(bbox[2], bbox[1], z); // east, south

	const minTx = Math.floor(Math.min(nw.x, se.x));
	const maxTx = Math.floor(Math.max(nw.x, se.x));
	const minTy = Math.floor(Math.min(nw.y, se.y));
	const maxTy = Math.floor(Math.max(nw.y, se.y));

	const topLeft = tileToBbox(minTx, minTy, z);
	const bottomRight = tileToBbox(maxTx, maxTy, z);

	return [
		topLeft[0],
		bottomRight[1],
		bottomRight[2],
		topLeft[3],
	];
}
