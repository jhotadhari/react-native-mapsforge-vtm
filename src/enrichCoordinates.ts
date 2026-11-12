/**
 * Batch elevation enrichment for coordinate arrays.
 *
 * Implements a batched three-phase flow that works within the constraints
 * of the native ElevationReader's background PRELOAD_EXECUTOR and
 * limited LRU cache:
 *
 *   1. Group coordinates by 1°×1° SRTM tile.
 *   2. Filter to tiles that have HGT files on disk (hasDataAtPosition).
 *   3. Optionally raise the LRU cache capacity — just enough for one
 *      batch (`batchSize` tiles) on top of the default, never for the
 *      whole tile set.
 *   4. Process in batches (windows) of `batchSize` tiles:
 *      a. Phase 1 — trigger: fire preloads for all tiles in the window.
 *      b. Phase 2 — fence: poll every tile in the window via isTileCached
 *         until all are cached, re‑triggering preloads for any that
 *         failed.  The fence times out after an adaptive window‑size‑based
 *         duration that accounts for I/O throughput.
 *      c. Phase 3 — collect: read all tiles from cache (sub‑ms each).
 *   5. Restore the LRU cache capacity (unless keepCacheCapacity is set).
 *   6. Optionally interpolate missing elevations (interpolateMissing):
 *      runs of coordinates that received the fallback are filled from
 *      their nearest valid neighbours — linear along‑track interpolation
 *      for bounded runs, carry‑nearest for leading/trailing runs.
 *
 * Coordinates are grouped by tile for efficient I/O; after the fence
 * proves every tile is cached, each coordinate gets its own bilinearly-
 * interpolated elevation via getAltitudeAtPosition — all sub‑millisecond
 * cache hits.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * The bridge functions the library needs to perform elevation enrichment.
 * Each consumer (e.g. straymap) wires its own native handle and provides
 * these three primitives.
 */
export interface ElevationAPI {
	/**
	 * Raw native call — returns the bilinearly‑interpolated elevation in
	 * metres at (lng, lat), or `null` when the HGT tile is not yet cached.
	 * A cache miss triggers an immediate background preload of the tile
	 * on the native ElevationReader's background executor.
	 *
	 * The library handles all retry / fencing logic — pass the raw bridge
	 * function, **not** a retry‑wrapped version.
	 */
	getAltitudeAtPosition(lng: number, lat: number): Promise<number | null>;

	/**
	 * Fast synchronous‑style check — returns `true` if an HGT file exists
	 * that covers (lng, lat), regardless of whether its data is cached.
	 * Does **not** trigger any I/O or preloading.
	 *
	 * When this returns `false` the tile is ocean / missing data — the
	 * library skips it entirely so no retry cycles are wasted.
	 */
	hasDataAtPosition(lng: number, lat: number): Promise<boolean>;

	/**
	 * Temporarily adjust the LRU cache capacity of the native
	 * ElevationReader.  Called before enrichment starts (raised to
	 * `min(maxCacheCapacity, batchSize + 10)`) and in `finally` (restored to 10)
	 * unless {@link EnrichCoordinatesOptions.keepCacheCapacity}
	 * is `true`.
	 *
	 * Optional — when absent the window size is capped at the
	 * ElevationReader's compile‑time default (10 tiles).
	 */
	setCacheCapacity?(capacity: number): Promise<void>;

	/**
	 * Returns `true` if the HGT tile covering (lng, lat) is currently
	 * loaded in the LRU cache.  Never triggers a preload.  Unlike
	 * getAltitudeAtPosition this is unambiguous — `false` always
	 * means "not cached", never "cached but void pixel".
	 *
	 * Required for the fence to work correctly.  When absent the
	 * fence falls back to getAltitudeAtPosition sampling (which can
	 * be fooled by void pixels in tiles with very little land).
	 */
	isTileCached?(lng: number, lat: number): Promise<boolean>;
}

export interface EnrichCoordinatesOptions {
	/**
	 * Called after each batch completes with the overall fraction
	 * (0‑1).  Suitable for driving a progress bar.
	 */
	onProgress?: (fraction: number) => void;

	/**
	 * When set, enrichment stops at the next batch boundary (or at the
	 * next fence poll) after the
	 * signal is aborted.  The coordinates enriched so far are returned —
	 * it does not roll back.
	 */
	signal?: AbortSignal;

	/**
	 * When `true` the elevated cache capacity is NOT restored to the
	 * compile‑time default (10) after enrichment completes.  Use this
	 * when you expect to run enrichment again soon — keeping the tiles
	 * warm avoids redundant disk reads.
	 *
	 * Default: `false` (cache is restored).
	 */
	keepCacheCapacity?: boolean;

	/**
	 * Upper bound for the number of HGT tiles held in the LRU cache
	 * during enrichment.  The cache is raised to
	 * `min(maxCacheCapacity, batchSize + 10)` (if `setCacheCapacity`
	 * is available) before processing and restored to the
	 * compile‑time default (10) in `finally`, unless
	 * {@link EnrichCoordinatesOptions.keepCacheCapacity} is `true`.
	 *
	 * The effective batch size is also capped by this value.
	 * Tiles are memory‑mapped natively (~0 Java heap each, ~2.9 MB of
	 * page cache), so the capacity bounds address space / page cache,
	 * not the Java heap.
	 *
	 * Default: 50.
	 */
	maxCacheCapacity?: number;

	/**
	 * Number of HGT tiles preloaded, fenced and collected together.
	 * Coordinates are processed grouped by tile, `batchSize` tiles at a
	 * time, so the native cache never has to hold the whole tile set.
	 * Smaller batches keep memory flat; larger batches overlap more I/O.
	 *
	 * Default: 4.
	 */
	batchSize?: number;

	/**
	 * Elevation value assigned to coordinates whose tile has no HGT data
	 * (ocean, missing tile, outside the configured coverage).
	 *
	 * Default: `0`.  Set to `null` to leave those coordinates unchanged.
	 */
	fallbackElevation?: number | null;

	/**
	 * When `true`, coordinates that could not be enriched from HGT data
	 * (no‑data tiles, void pixels, fence timeouts) are filled by
	 * interpolating from neighbouring valid elevations instead of
	 * keeping {@link EnrichCoordinatesOptions.fallbackElevation}.
	 *
	 * Runs of consecutive missing coordinates are handled as follows:
	 * - Bounded runs (valid anchor on both sides): linear interpolation
	 *   along the track (cumulative haversine distance between the
	 *   anchors).
	 * - Leading/trailing runs (track start/end): filled with the nearest
	 *   valid elevation.
	 * - Fully‑missing tracks: left at the fallback value.
	 *
	 * Skipped when enrichment is aborted.  Default: `false`.
	 */
	interpolateMissing?: boolean;

	/**
	 * Maximum along‑track distance (metres) between the two valid
	 * anchors of a missing run for interpolation to apply.  Runs
	 * spanning a larger gap keep the fallback elevation — this prevents
	 * fabricating a plausible‑looking profile across large data gaps
	 * (e.g. DEM coverage missing for half a track).
	 *
	 * Only meaningful when
	 * {@link EnrichCoordinatesOptions.interpolateMissing} is `true`.
	 * Default: `Infinity` (interpolate any bounded run).
	 */
	interpolateMaxGapMeters?: number;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** ElevationReader compile‑time default — used when setCacheCapacity is absent. */
const DEFAULT_CACHE_CAPACITY = 10;

/** Default number of tiles per batch. */
const DEFAULT_BATCH_SIZE = 4;

/**
 * Estimated wall‑clock time for a single HGT tile load on the
 * single‑thread PRELOAD_EXECUTOR (ms).  Used to compute the adaptive
 * fence timeout.
 *
 * Tiles are memory‑mapped and pre‑faulted natively, which is faster than
 * the old ~320 ms stream read per 2.9 MB tile; 500 ms is therefore a
 * generous per‑tile estimate (headroom for slow flash / contention).
 */
const ESTIMATED_MS_PER_TILE = 500;

/** Interval between fence polls (ms). */
const FENCE_POLL_MS = 100;

/** Minimum fence timeout, regardless of tile count (ms). */
const FENCE_MIN_TIMEOUT_MS = 10000;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const tileKey = (lng: number, lat: number): string =>
	`${Math.floor(lat)},${Math.floor(lng)}`;

/**
 * Scans positions inside a cached tile for a non‑void elevation.
 * The fence already proved the tile is in the LRU cache, so every
 * call here is a sub‑millisecond cache hit.  We try the
 * representative point first, then a 3×3 grid around the centre,
 * then a 5×5 sweep across the whole tile — sufficient for tiles
 * with very little land (e.g. Caribbean islands).
 */
const findElevationInTile = async (
	api: ElevationAPI,
	tileLng: number,
	tileLat: number
): Promise<number | null> => {
	const oLng = Math.floor(tileLng);
	const oLat = Math.floor(tileLat);

	// Representative point — cheapest path, right most of the time.
	let alt = await api.getAltitudeAtPosition(tileLng, tileLat);
	if (alt !== null) return alt;

	// 3×3 grid around the tile centre (9 points, sub‑ms each).
	for (let dx = -0.1; dx <= 0.1; dx += 0.1) {
		for (let dy = -0.1; dy <= 0.1; dy += 0.1) {
			if (dx === 0 && dy === 0) continue;
			alt = await api.getAltitudeAtPosition(
				oLng + 0.5 + dx,
				oLat + 0.5 + dy
			);
			if (alt !== null) return alt;
		}
	}

	// Wide 5×5 sweep across the whole tile (25 points).
	for (let dx = 0; dx < 5; dx++) {
		for (let dy = 0; dy < 5; dy++) {
			const slng = oLng + (dx + 0.5) / 5;
			const slat = oLat + (dy + 0.5) / 5;
			alt = await api.getAltitudeAtPosition(slng, slat);
			if (alt !== null) return alt;
		}
	}

	return null;
};

const delay = (ms: number): Promise<void> =>
	new Promise<void>((r) => setTimeout(r, ms));

/**
 * Great‑circle distance in metres between two `[lng, lat]` points.
 */
const haversineMeters = (a: number[], b: number[]): number => {
	const R = 6371000;
	const toRad = (deg: number): number => (deg * Math.PI) / 180;
	const dLat = toRad(b[1]! - a[1]!);
	const dLng = toRad(b[0]! - a[0]!);
	const lat1 = toRad(a[1]!);
	const lat2 = toRad(b[1]!);
	const h =
		Math.sin(dLat / 2) ** 2 +
		Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
	return 2 * R * Math.asin(Math.sqrt(h));
};

/**
 * Fills runs of missing elevations (indices in `missing`) in place by
 * interpolating from the nearest valid neighbours.  See
 * {@link EnrichCoordinatesOptions.interpolateMissing} for the exact
 * rules.
 */
const interpolateMissingElevations = (
	coords: number[][],
	missing: Set<number>,
	maxGapMeters?: number
): void => {
	const n = coords.length;
	if (n < 2 || missing.size === 0) return;

	// Cumulative along‑track distance per coordinate.
	const cumDist: number[] = [0];
	for (let i = 1; i < n; i++) {
		cumDist.push(
			cumDist[i - 1]! + haversineMeters(coords[i - 1]!, coords[i]!)
		);
	}

	const cap = maxGapMeters ?? Infinity;

	let runStart = -1;
	for (let i = 0; i <= n; i++) {
		const isMissing = i < n && missing.has(i);
		if (isMissing && runStart === -1) {
			runStart = i;
			continue;
		}
		if (!isMissing && runStart !== -1) {
			const runEnd = i - 1;
			const left = runStart - 1;
			const right = i;
			const altLeft = left >= 0 ? coords[left]![2] : undefined;
			const altRight = right < n ? coords[right]![2] : undefined;

			if (altLeft != null && altRight != null) {
				// Bounded run — linear interpolation along the track.
				const gapMeters = cumDist[right]! - cumDist[left]!;
				if (gapMeters <= cap) {
					for (let j = runStart; j <= runEnd; j++) {
						const t =
							gapMeters > 0
								? (cumDist[j]! - cumDist[left]!) / gapMeters
								: 0;
						coords[j]![2] = altLeft + (altRight - altLeft) * t;
					}
				}
			} else if (altLeft != null) {
				// Trailing run — carry the last valid elevation.
				for (let j = runStart; j <= runEnd; j++) {
					coords[j]![2] = altLeft;
				}
			} else if (altRight != null) {
				// Leading run — carry the first valid elevation.
				for (let j = runStart; j <= runEnd; j++) {
					coords[j]![2] = altRight;
				}
			}
			runStart = -1;
		}
	}
};

interface TileGroup {
	lng: number;
	lat: number;
	coords: number[][];
	indices: number[];
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Enriches `coords` with elevation data from SRTM HGT files via the
 * provided {@link ElevationAPI} bridge.
 *
 * Coordinates are grouped by 1°×1° tile so each unique tile is queried
 * only once.  Tiles without HGT coverage (ocean, missing data) are
 * skipped.  After the fence proves every tile is cached, each coordinate
 * gets its own bilinearly‑interpolated elevation via per‑coordinate
 * {@link ElevationAPI.getAltitudeAtPosition} calls (all cache hits).
 *
 * Uses a batched three‑phase flow so the operation works correctly
 * even when the native LRU cache is smaller than the tile set.  Each
 * batch (`batchSize` tiles, default 4) fits entirely in the cache,
 * guaranteeing 100 % cache‑hit rate in the collect phase, and the
 * cache capacity is never raised beyond `batchSize` + the default.
 *
 * With {@link EnrichCoordinatesOptions.interpolateMissing} the
 * coordinates that received the fallback elevation (or were left
 * unchanged with `fallbackElevation: null`) are afterwards filled by
 * interpolating from neighbouring valid elevations — see the option
 * docs for the exact rules.
 *
 * Mutates `coords` **in place** (sets `c[2]` on every coordinate) and
 * also returns the same array for chaining.
 *
 * @example
 * ```typescript
 * import { enrichCoordinatesWithElevation } from 'react-native-mapsforge-vtm';
 *
 * const coords = [ [lng, lat, 0], [lng, lat, 0], ... ];
 * await enrichCoordinatesWithElevation(coords, {
 *     getAltitudeAtPosition: rawNativeFn,
 *     hasDataAtPosition,
 *     setCacheCapacity,
 * }, {
 *     onProgress: (f) => console.log(`${Math.round(f * 100)}%`),
 *     maxCacheCapacity: 50,
 * });
 * // coords now enriched with elevation in c[2]
 * ```
 */
export const enrichCoordinatesWithElevation = async (
	coords: number[][],
	api: ElevationAPI,
	options?: EnrichCoordinatesOptions
): Promise<number[][]> => {
	// ------------------------------------------------------------------
	// 1. Group coordinates by 1°×1° SRTM tile
	// ------------------------------------------------------------------
	const tileMap = new Map<string, TileGroup>();
	for (let i = 0; i < coords.length; i++) {
		const coord = coords[i]!;
		const key = tileKey(coord[0]!, coord[1]!);
		let entry = tileMap.get(key);
		if (!entry) {
			entry = {
				lng: coord[0]!,
				lat: coord[1]!,
				coords: [],
				indices: [],
			};
			tileMap.set(key, entry);
		}
		entry.coords.push(coord);
		entry.indices.push(i);
	}

	const allTiles = Array.from(tileMap.values());
	if (allTiles.length === 0) return coords;

	// ------------------------------------------------------------------
	// 2. Filter to tiles that have HGT data.
	//    Process in parallel chunks of 10 to avoid overwhelming the bridge
	//    while still being faster than fully serial for large tile sets.
	// ------------------------------------------------------------------
	const tilesWithData: TileGroup[] = [];
	const tilesWithoutData: TileGroup[] = [];
	const CHUNK_SIZE = 10;
	const fallback = options?.fallbackElevation ?? 0;
	/** Indices into `coords` that received the fallback (interpolation candidates). */
	const missing = new Set<number>();
	for (let i = 0; i < allTiles.length; i += CHUNK_SIZE) {
		if (options?.signal?.aborted) return coords;
		const chunk = allTiles.slice(i, i + CHUNK_SIZE);
		const results = await Promise.all(
			chunk.map(async (tile) => {
				if (options?.signal?.aborted) return null;
				const hasData = await api.hasDataAtPosition(tile.lng, tile.lat);
				return { tile, hasData };
			})
		);
		for (const r of results) {
			if (!r) continue;
			if (r.hasData) {
				tilesWithData.push(r.tile);
			} else {
				tilesWithoutData.push(r.tile);
			}
		}
	}

	// Apply fallback to coordinates in tiles without HGT data.
	for (const tile of tilesWithoutData) {
		for (let i = 0; i < tile.coords.length; i++) {
			missing.add(tile.indices[i]!);
			if (fallback !== null) {
				tile.coords[i]![2] = fallback;
			}
		}
	}

	if (tilesWithData.length === 0) return coords;

	// ------------------------------------------------------------------
	// 3. Determine batch size & optionally raise cache capacity.
	//
	//    The cache capacity MUST be >= the batch size for Phase 3 to
	//    be 100 % cache-hit.  It is raised only to batchSize + default
	//    (bounded by maxCacheCapacity), not to cover the whole tile
	//    set.  We probe setCacheCapacity first — if it throws (not
	//    wired, native reject, etc.) we fall back to the compile-time
	//    default so early tiles aren't evicted before Phase 3 reads
	//    them.
	// ------------------------------------------------------------------
	const maxCapacity = options?.maxCacheCapacity ?? 50;
	const batchSize = Math.max(
		1,
		Math.floor(options?.batchSize ?? DEFAULT_BATCH_SIZE)
	);
	if (maxCapacity <= 0) {
		console.warn(
			'enrichCoordinatesWithElevation: maxCacheCapacity must be >= 1, got ' +
				maxCapacity +
				'. Clamping to 1.'
		);
	}
	let effectiveCacheCapacity = DEFAULT_CACHE_CAPACITY;

	if (typeof api.setCacheCapacity === 'function') {
		const wanted = Math.max(
			1,
			Math.min(maxCapacity, batchSize + DEFAULT_CACHE_CAPACITY)
		);
		try {
			await api.setCacheCapacity!(wanted);
			effectiveCacheCapacity = wanted;
		} catch {}
	}

	const windowSize = Math.min(
		tilesWithData.length,
		batchSize,
		Math.max(1, effectiveCacheCapacity)
	);

	const totalWindows = Math.ceil(tilesWithData.length / windowSize);

	try {
		// --------------------------------------------------------------
		// 4. Process each window
		// --------------------------------------------------------------
		let enrichedCount = 0;
		for (let w = 0; w < totalWindows; w++) {
			if (options?.signal?.aborted) break;

			const windowStart = w * windowSize;
			const window = tilesWithData.slice(
				windowStart,
				windowStart + windowSize
			);

			// Phase 1 — trigger preloads for every tile in this window.
			// Await all so the native executor queue is fully populated
			// before the fence is queued.
			await Promise.allSettled(
				window.map(({ lng, lat }) =>
					api.getAltitudeAtPosition(lng, lat)
				)
			);

			// Phase 2 — fence: wait until EVERY tile in the window
			// is cached.  We can't rely on just the last tile — it may
			// have been pre‑cached by the centre‑altitude display or a
			// prior route calculation, giving a false‑positive fence
			// while earlier tiles are still queued.
			const useIsCached = typeof api.isTileCached === 'function';
			const fenceTimeoutMs = Math.max(
				FENCE_MIN_TIMEOUT_MS,
				window.length * ESTIMATED_MS_PER_TILE + 2000
			);

			const uncached = new Set(window.map((_, i) => i));

			for (
				let elapsed = 0;
				elapsed < fenceTimeoutMs && uncached.size > 0;
				elapsed += FENCE_POLL_MS
			) {
				if (options?.signal?.aborted) break;
				for (const idx of Array.from(uncached)) {
					const tile = window[idx]!;
					const cached = useIsCached
						? await api.isTileCached!(tile.lng, tile.lat)
						: (await findElevationInTile(
								api,
								tile.lng,
								tile.lat
							)) !== null;
					if (cached) {
						uncached.delete(idx);
					} else {
						// Re‑trigger the preload — the Phase‑1
						// attempt may have failed (I/O error).
						// getAltitudeAtPosition queues a new
						// preload if the tile isn't already
						// in‑flight.
						await api.getAltitudeAtPosition(tile.lng, tile.lat);
						await delay(FENCE_POLL_MS);
						break;
					}
				}
			}

			// Aborted during the fence: tiles may be uncached, so skip
			// the collect phase instead of stamping fallbacks.
			if (options?.signal?.aborted) break;

			// Phase 3 — collect.  The fence guarantees every tile
			// is cached, so all getAltitudeAtPosition calls are
			// sub‑millisecond cache hits with full bilinear
			// interpolation.  Each coordinate gets its own elevation
			// rather than sharing a single tile‑representative value.
			for (const { coords: tileCoords, indices } of window) {
				for (let ci = 0; ci < tileCoords.length; ci++) {
					if (options?.signal?.aborted) break;
					const c = tileCoords[ci]!;
					const alt = await api.getAltitudeAtPosition(c[0]!, c[1]!);
					if (alt !== null) {
						c[2] = alt;
					} else {
						missing.add(indices[ci]!);
						if (fallback !== null) {
							c[2] = fallback;
						}
					}
				}
				enrichedCount += tileCoords.length;
			}

			// Count coords already assigned fallback in tiles without
			// data so progress reaches 1.0 even with partial coverage.
			const fallbackCount = tilesWithoutData.reduce(
				(sum, t) => sum + t.coords.length,
				0
			);
			options?.onProgress?.(
				Math.min(
					(enrichedCount + fallbackCount) /
						Math.max(coords.length, 1),
					1
				)
			);
		}
	} finally {
		// Restore cache capacity unless the caller explicitly opted
		// to keep it elevated (e.g. for a series of enrichments).
		if (
			!options?.keepCacheCapacity &&
			typeof api.setCacheCapacity === 'function' &&
			effectiveCacheCapacity !== DEFAULT_CACHE_CAPACITY
		) {
			try {
				await api.setCacheCapacity!(DEFAULT_CACHE_CAPACITY);
			} catch {
				// Best-effort.
			}
		}
	}

	// ------------------------------------------------------------------
	// 5. Optional interpolation of missing elevations.  Runs in the
	//    original track order are filled from their nearest valid
	//    neighbours.  Skipped when enrichment was aborted — partial
	//    data makes the anchors unreliable.
	// ------------------------------------------------------------------
	if (options?.interpolateMissing && !options?.signal?.aborted) {
		interpolateMissingElevations(
			coords,
			missing,
			options.interpolateMaxGapMeters
		);
	}

	return coords;
};
