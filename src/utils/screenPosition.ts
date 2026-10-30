/**
 * Screen-anchored positions — the escape hatch that pins a point of a
 * native vtm layer to a position on the map's screen instead of a
 * geographic coordinate. The Java side re-resolves the geographic
 * coordinate on every map movement (see ScreenAnchorRegistry in the native
 * module), so the point stays glued to its screen position during pan,
 * zoom, and rotate without any JS round trip.
 *
 * The bridge contract is numeric ({@link ScreenPosition} with unit
 * 'fraction' | 'dp'); the string sugar ('50%', '100dp') is accepted by the
 * components and normalized here, before the TurboModule call.
 */

import type { Position as GeoJsonPosition } from 'geojson';

export type ScreenPositionUnit = 'fraction' | 'dp';

/** The typed bridge contract (mirrors the codegen-local declarations in the
 * Native* spec files). */
export interface ScreenPosition {
	/** Paths only: index into the resolved point list (geo + anchored). */
	index?: number;
	x: number;
	y: number;
	unit?: ScreenPositionUnit;
}

/** Sugar: `['50%', '50%']` or `['100dp', '24dp']`. */
export type ScreenPointString = [string, string];

/** Sugar: `{ x: 0.5, y: 0.5 }` or `{ x: 120, y: 24, unit: 'dp' }`. */
export type ScreenPointObject = {
	x: number | string;
	y: number | string;
	unit?: ScreenPositionUnit;
};

export type ScreenPointSugar = ScreenPointString | ScreenPointObject;

/** A point of a path/marker position prop: geographic or screen-anchored. */
export type ScreenOrGeoPosition = GeoJsonPosition | ScreenPointSugar;

const FRACTION_RE = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)%$/;
const DP_RE = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)dp$/;

export function isScreenPointSugar(value: unknown): value is ScreenPointSugar {
	if (Array.isArray(value)) {
		return (
			value.length === 2 &&
			typeof value[0] === 'string' &&
			typeof value[1] === 'string'
		);
	}
	if (typeof value !== 'object' || value === null) {
		return false;
	}
	const candidate = value as Partial<ScreenPointObject>;
	return (
		'x' in candidate &&
		'y' in candidate &&
		(typeof candidate.x === 'number' || typeof candidate.x === 'string') &&
		(typeof candidate.y === 'number' || typeof candidate.y === 'string')
	);
}

function parseAxis(
	value: number | string,
	explicitUnit?: ScreenPositionUnit
): { value: number; unit: ScreenPositionUnit } {
	if (typeof value === 'number') {
		if (explicitUnit) {
			return { value, unit: explicitUnit };
		}
		// Bare numbers default to viewport fractions. A value outside 0..1
		// without an explicit unit is almost certainly a pixel value passed
		// by mistake — fail loudly instead of rendering off-screen.
		if (value < 0 || value > 1) {
			throw new Error(
				`screenPosition: numeric value ${value} is outside 0..1 — pass unit: 'dp' for pixel offsets`
			);
		}
		return { value, unit: 'fraction' };
	}

	const trimmed = value.trim();
	if (FRACTION_RE.test(trimmed)) {
		if (explicitUnit && explicitUnit !== 'fraction') {
			throw new Error(
				`screenPosition: "${value}" conflicts with unit '${explicitUnit}'`
			);
		}
		return { value: parseFloat(trimmed) / 100, unit: 'fraction' };
	}
	if (DP_RE.test(trimmed)) {
		if (explicitUnit && explicitUnit !== 'dp') {
			throw new Error(
				`screenPosition: "${value}" conflicts with unit '${explicitUnit}'`
			);
		}
		return { value: parseFloat(trimmed), unit: 'dp' };
	}
	throw new Error(
		`screenPosition: unable to parse "${value}" — use '50%' or '100dp'`
	);
}

/** Normalizes sugar into the numeric bridge contract. */
export function parseScreenPoint(sugar: ScreenPointSugar): {
	x: number;
	y: number;
	unit: ScreenPositionUnit;
} {
	if (Array.isArray(sugar)) {
		const xAxis = parseAxis(sugar[0]);
		const yAxis = parseAxis(sugar[1]);
		if (xAxis.unit !== yAxis.unit) {
			throw new Error('screenPosition: x and y must use the same unit');
		}
		return { x: xAxis.value, y: yAxis.value, unit: xAxis.unit };
	}
	const xAxis = parseAxis(sugar.x, sugar.unit);
	const yAxis = parseAxis(sugar.y, sugar.unit);
	if (xAxis.unit !== yAxis.unit) {
		throw new Error('screenPosition: x and y must use the same unit');
	}
	return { x: xAxis.value, y: yAxis.value, unit: xAxis.unit };
}

/**
 * Splits a path's coordinates into the geographic coordinates and the
 * screen-anchored point descriptors sent to native. Anchored slots keep
 * their JSX order: {@code index} addresses the resolved point list the
 * native side builds by inserting the anchors into the geo coordinates.
 */
export function splitPathCoordinates(
	coordinates: readonly ScreenOrGeoPosition[]
): {
	geoCoordinates: GeoJsonPosition[];
	screenPoints: ScreenPosition[];
} {
	let resolvedIndex = 0;
	const geoCoordinates: GeoJsonPosition[] = [];
	const screenPoints: ScreenPosition[] = [];
	for (const coordinate of coordinates) {
		if (isScreenPointSugar(coordinate)) {
			const { x, y, unit } = parseScreenPoint(coordinate);
			screenPoints.push({ index: resolvedIndex, x, y, unit });
		} else {
			geoCoordinates.push(coordinate);
		}
		resolvedIndex += 1;
	}
	return { geoCoordinates, screenPoints };
}

/**
 * Normalizes a marker position: sugar becomes a placeholder geo position
 * plus the {@code screenPosition} descriptor; plain positions pass through.
 */
export function normalizeMarkerPosition(
	position: ScreenOrGeoPosition | null | undefined
): {
	position?: GeoJsonPosition;
	screenPosition?: ScreenPosition;
} {
	if (position == null) {
		return {};
	}
	if (isScreenPointSugar(position)) {
		const { x, y, unit } = parseScreenPoint(position);
		return {
			position: [0, 0],
			screenPosition: { x, y, unit },
		};
	}
	return { position };
}
