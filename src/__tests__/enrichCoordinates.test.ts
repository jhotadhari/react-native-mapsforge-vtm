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

/**
 * Batching: a simulated native LRU cache (capacity set via
 * `setCacheCapacity`, default 10) that loads a tile on the first
 * `getAltitudeAtPosition` miss, like the native preload would.
 */
const makeLruApi = (
	onAltitude?: (lng: number, lat: number) => void
): {
	api: ElevationAPI;
	capacities: number[];
	maxResident: () => number;
} => {
	let capacity = 10;
	const capacities: number[] = [];
	const cache: string[] = []; // LRU order, oldest first
	let maxResident = 0;
	const key = (lng: number, lat: number): string =>
		`${Math.floor(lng)},${Math.floor(lat)}`;
	const touch = (k: string): boolean => {
		const i = cache.indexOf(k);
		if (i === -1) return false;
		cache.splice(i, 1);
		cache.push(k);
		return true;
	};
	const trim = (): void => {
		while (cache.length > capacity) cache.shift();
	};
	const api = {
		hasDataAtPosition: jest.fn(async () => true),
		setCacheCapacity: jest.fn(async (n: number) => {
			capacities.push(n);
			capacity = n;
			trim();
		}),
		isTileCached: jest.fn(async (lng: number, lat: number) =>
			touch(key(lng, lat))
		),
		getAltitudeAtPosition: jest.fn(async (lng: number, lat: number) => {
			onAltitude?.(lng, lat);
			const k = key(lng, lat);
			if (!touch(k)) {
				// Miss: "preload" (completes immediately), return null.
				cache.push(k);
				trim();
				maxResident = Math.max(maxResident, cache.length);
				return null;
			}
			return 100 + Math.floor(lng);
		}),
	} as unknown as ElevationAPI;
	return { api, capacities, maxResident: () => maxResident };
};

/** `tiles` tiles along lng, 3 coordinates each, interleaved in track order. */
const makeTrack = (tiles: number): number[][] => {
	const coords: number[][] = [];
	for (let pass = 0; pass < 3; pass++) {
		for (let t = 0; t < tiles; t++) {
			coords.push([
				t + 0.2 + pass * 0.2,
				10.5,
				0,
			]);
		}
	}
	return coords;
};

describe('enrichCoordinatesWithElevation batching', () => {
	it('enriches every coordinate without raising capacity beyond the batch bound', async () => {
		const { api, capacities, maxResident } = makeLruApi();
		const coords = makeTrack(40);
		await enrichCoordinatesWithElevation(coords, api, {
			batchSize: 4,
			maxCacheCapacity: 200,
		});

		for (const c of coords) {
			expect(c[2]).toBe(100 + Math.floor(c[0]!));
		}
		// Raised once to batchSize + default (14), restored to 10.
		expect(Math.max(...capacities)).toBeLessThanOrEqual(4 + 10);
		expect(capacities[capacities.length - 1]).toBe(10);
		expect(maxResident()).toBeLessThanOrEqual(14);
	});

	it('defaults to a batch size of 4', async () => {
		const { api, capacities } = makeLruApi();
		await enrichCoordinatesWithElevation(makeTrack(12), api);
		expect(Math.max(...capacities)).toBe(14);
	});

	it('processes at most batchSize tiles per batch and reports progress', async () => {
		const progress: number[] = [];
		const { api } = makeLruApi();
		await enrichCoordinatesWithElevation(makeTrack(9), api, {
			batchSize: 3,
			onProgress: (f) => progress.push(f),
		});
		expect(progress).toHaveLength(3);
		expect(progress[progress.length - 1]).toBeCloseTo(1, 6);
	});

	it('respects maxCacheCapacity as an upper bound', async () => {
		const { api, capacities } = makeLruApi();
		const coords = makeTrack(10);
		await enrichCoordinatesWithElevation(coords, api, {
			batchSize: 8,
			maxCacheCapacity: 3,
		});
		expect(capacities[0]).toBe(3);
		expect(Math.max(...capacities.slice(0, -1))).toBe(3);
		for (const c of coords) {
			expect(c[2]).toBe(100 + Math.floor(c[0]!));
		}
	});

	it('keeps the raised capacity with keepCacheCapacity', async () => {
		const { api, capacities } = makeLruApi();
		await enrichCoordinatesWithElevation(makeTrack(6), api, {
			keepCacheCapacity: true,
		});
		expect(capacities).toEqual([14]);
	});

	it('stops mid-batch when aborted and leaves later tiles untouched', async () => {
		const controller = new AbortController();
		let seen = 0;
		const { api } = makeLruApi(() => {
			// Abort a few calls into the second batch's collect phase.
			if (++seen === 40) controller.abort();
		});
		const coords = makeTrack(20);
		await enrichCoordinatesWithElevation(coords, api, {
			batchSize: 4,
			signal: controller.signal,
		});

		const enriched = coords.filter((c) => c[2] !== 0);
		expect(enriched.length).toBeGreaterThan(0);
		expect(enriched.length).toBeLessThan(coords.length);
		// Tiles of later batches were never touched.
		const lastTile = coords.filter((c) => Math.floor(c[0]!) >= 16);
		expect(lastTile.every((c) => c[2] === 0)).toBe(true);
		// Capacity is still restored.
		expect(api.setCacheCapacity).toHaveBeenLastCalledWith(10);
	});
});
