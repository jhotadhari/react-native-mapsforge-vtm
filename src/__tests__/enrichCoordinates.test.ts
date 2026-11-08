/**
 * Tests for enrichCoordinatesWithElevation — tile grouping, fallback
 * handling, and the optional missing-elevation interpolation
 * (`interpolateMissing` / `interpolateMaxGapMeters`).
 *
 * All tests use a synthetic world where:
 * - The 1°×1° tile at lng ∈ [1, 2) has no HGT data (hasData false),
 *   unless overridden.
 * - Elevation is 1000 m for lng < 1 and 2000 m for lng >= 2, unless
 *   overridden.
 * - `isTileCached` always reports cached, so the fence passes
 *   immediately and the collect phase drives every value.
 */

/**
 * Internal dependencies
 */
import { enrichCoordinatesWithElevation } from '../enrichCoordinates';
import type { ElevationAPI } from '../enrichCoordinates';

interface ApiConfig {
	hasData: (lng: number, lat: number) => boolean;
	alt?: (lng: number, lat: number) => number | null;
}

const makeApi = (config: ApiConfig): ElevationAPI =>
	({
		getAltitudeAtPosition: jest.fn(async (lng: number, lat: number) => {
			if (!config.hasData(lng, lat)) return null;
			return config.alt ? config.alt(lng, lat) : 1000;
		}),
		hasDataAtPosition: jest.fn(async (lng: number, lat: number) =>
			config.hasData(lng, lat)
		),
		isTileCached: jest.fn(async () => true),
	}) as unknown as ElevationAPI;

/** Tile at lng ∈ [1, 2) has no data; 1000 m west of it, 2000 m east. */
const standardApi = (): ElevationAPI =>
	makeApi({
		hasData: (lng) => Math.floor(lng) !== 1,
		alt: (lng) => (lng < 1 ? 1000 : 2000),
	});

describe('enrichCoordinatesWithElevation', () => {
	it('leaves fallback elevations untouched by default', async () => {
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[
				1.5,
				0,
				0,
			], // no-data tile
			[
				2.5,
				0,
				0,
			],
			[
				3.0,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, standardApi());

		expect(coords[0]![2]).toBe(1000);
		expect(coords[1]![2]).toBe(1000);
		expect(coords[2]![2]).toBe(0); // fallback
		expect(coords[3]![2]).toBe(2000);
		expect(coords[4]![2]).toBe(2000);
	});

	it('linearly interpolates a bounded missing run along the track', async () => {
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[
				1.5,
				0,
				0,
			], // no-data tile → missing
			[
				2.5,
				0,
				0,
			],
			[
				3.0,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
		});

		// Anchors are coords[1] (1000 m) and coords[3] (2000 m); the
		// missing coord sits exactly halfway along the track.
		expect(coords[1]![2]).toBe(1000);
		expect(coords[2]![2]).toBeCloseTo(1500, 6);
		expect(coords[3]![2]).toBe(2000);
	});

	it('carries the first valid elevation into a leading run', async () => {
		const coords: number[][] = [
			[
				1.5,
				0,
				0,
			], // no-data tile → missing
			[
				2.5,
				0,
				0,
			],
			[
				3.0,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
		});

		expect(coords[0]![2]).toBe(2000);
		expect(coords[1]![2]).toBe(2000);
	});

	it('carries the last valid elevation into a trailing run', async () => {
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[
				1.5,
				0,
				0,
			], // no-data tile → missing
		];
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
		});

		expect(coords[1]![2]).toBe(1000);
		expect(coords[2]![2]).toBe(1000);
	});

	it('keeps the fallback when the anchor gap exceeds the cap', async () => {
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[
				1.5,
				0,
				0,
			], // no-data tile → missing
			[
				2.5,
				0,
				0,
			],
			[
				3.0,
				0,
				0,
			],
		];
		// Anchor distance is ~222 km (2° at the equator) > 100 km cap.
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
			interpolateMaxGapMeters: 100000,
		});

		expect(coords[2]![2]).toBe(0);
	});

	it('leaves a fully missing track at the fallback value', async () => {
		const coords: number[][] = [
			[
				1.5,
				0,
				0,
			],
			[
				1.6,
				0,
				0,
			],
			[
				1.7,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
		});

		expect(coords[0]![2]).toBe(0);
		expect(coords[1]![2]).toBe(0);
		expect(coords[2]![2]).toBe(0);
	});

	it('interpolates void pixels in data tiles', async () => {
		const api = makeApi({
			hasData: () => true,
			alt: (lng) => {
				if (lng >= 2 && lng < 3) return null; // void pixel
				return lng < 1 ? 1000 : 2000;
			},
		});
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[
				2.5,
				0,
				0,
			], // void → missing
			[
				3.0,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, api, {
			interpolateMissing: true,
		});

		// 80 % along the track from the 1000 m anchor to the 2000 m one.
		expect(coords[2]![2]).toBeCloseTo(1800, 6);
	});

	it('interpolates even when fallbackElevation is null', async () => {
		const coords: number[][] = [
			[
				0.0,
				0,
				0,
			],
			[
				0.5,
				0,
				0,
			],
			[1.5, 0], // no-data tile, no third element at all
			[
				2.5,
				0,
				0,
			],
			[
				3.0,
				0,
				0,
			],
		];
		await enrichCoordinatesWithElevation(coords, standardApi(), {
			interpolateMissing: true,
			fallbackElevation: null,
		});

		expect(coords[2]![2]).toBeCloseTo(1500, 6);
	});
});
