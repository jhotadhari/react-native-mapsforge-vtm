/**
 * Internal dependencies
 */
import NativeMapContainer, {
	type GetPositionResponse,
} from '../NativeModules/NativeMapContainer';
import type { Bbox } from '../NativeModules/NativeLayerPath';
import type { Position } from '../types';
import type {
	AnimationOptions,
	FitBoundsOptions,
	MapPositionTarget,
} from './useMap';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const requireBbox = (
	bounds: Bbox
): { west: number; south: number; east: number; north: number } => {
	if (bounds.length !== 4) {
		throw new Error(
			`bounds must be [ west, south, east, north ] (length 4), got length ${bounds.length}.`
		);
	}
	const [
		west,
		south,
		east,
		north,
	] = bounds;
	return { west: west!, south: south!, east: east!, north: north! };
};

const getRetryDelay = (attempt: number): number =>
	Math.min(500, Math.max(100, 10 * Math.pow(2, attempt)));

// ---------------------------------------------------------------------------
// Public
// ---------------------------------------------------------------------------

/**
 * Creates the full imperative map-control + elevation API object from a
 * concrete (non-null) {@code nativeNodeHandle}.
 *
 * This is the shared foundation used by both {@link useMap} (the React
 * hook) and {@link createMapHandle} (the non-hook factory).  It contains
 * every method that doesn't need React context (i.e. everything except
 * {@code getDebugLayerDump}, which requires the JS-side layer registry).
 *
 * @param nativeNodeHandle A resolved native view tag (already validated
 *   non-null by the caller).
 */
export function createMapHandle(nativeNodeHandle: number) {
	const animateTo = (
		target: MapPositionTarget,
		options?: AnimationOptions
	): Promise<void> =>
		NativeMapContainer.animateTo({
			nativeNodeHandle,
			...target,
			duration: options?.duration ?? 0,
			easing: options?.easing ?? 'linear',
		});

	const getPosition = (): Promise<GetPositionResponse> =>
		NativeMapContainer.getPosition({ nativeNodeHandle });

	const jumpTo = (target: MapPositionTarget): Promise<void> =>
		animateTo(target);

	const panTo = (center: Position): Promise<void> => animateTo({ center });

	const panBy = async (
		deltaLngLat: readonly [number, number]
	): Promise<void> => {
		const [
			lng,
			lat,
			alt,
		] = await getPosition().then((p) => p.center);
		return panTo(
			alt !== undefined
				? [
						lng! + deltaLngLat[0],
						lat! + deltaLngLat[1],
						alt,
					]
				: [lng! + deltaLngLat[0], lat! + deltaLngLat[1]]
		);
	};

	const setZoom = (zoomLevel: number): Promise<void> =>
		animateTo({ zoomLevel });

	const zoomOut = async (by: number = 1): Promise<void> => {
		const current = await getPosition();
		return setZoom(current.zoomLevel - by);
	};

	const setBearing = (bearing: number): Promise<void> =>
		animateTo({ bearing });

	const resetNorth = (): Promise<void> => setBearing(0);

	const resetNorthPitch = (): Promise<void> =>
		animateTo({ bearing: 0, tilt: 0 });

	const setRoll = (roll: number): Promise<void> => animateTo({ roll });

	const easeTo = (
		target: MapPositionTarget,
		options?: AnimationOptions
	): Promise<void> =>
		animateTo(target, {
			duration: options?.duration ?? 300,
			easing: options?.easing ?? 'sine_inout',
		});

	const flyTo = (
		target: MapPositionTarget,
		options?: AnimationOptions
	): Promise<void> =>
		animateTo(target, {
			duration: options?.duration ?? 1200,
			easing: options?.easing ?? 'expo_out',
		});

	const fitBounds = (
		bounds: Bbox,
		options?: FitBoundsOptions
	): Promise<void> => {
		requireBbox(bounds);
		return NativeMapContainer.animateTo({
			nativeNodeHandle,
			bounds,
			boundsPaddingPx: options?.paddingPx ?? 0,
			duration: options?.duration ?? 0,
			easing: options?.easing ?? 'linear',
		});
	};

	const flyToBounds = (
		bounds: Bbox,
		options?: FitBoundsOptions
	): Promise<void> =>
		fitBounds(bounds, {
			paddingPx: options?.paddingPx ?? 0,
			duration: options?.duration ?? 1200,
			easing: options?.easing ?? 'expo_out',
		});

	const panInsideBounds = async (bounds: Bbox): Promise<void> => {
		const { west, south, east, north } = requireBbox(bounds);
		const [lng, lat] = await getPosition().then((p) => p.center);
		const clampedLng = Math.min(Math.max(lng!, west), east);
		const clampedLat = Math.min(Math.max(lat!, south), north);
		if (clampedLng === lng && clampedLat === lat) {
			return;
		}
		return panTo([clampedLng, clampedLat]);
	};

	const panInside = (point: Position): Promise<void> => panTo(point);

	const getAltitudeAtPosition = async (
		lng: number,
		lat: number
	): Promise<number | null> => {
		try {
			const result = await NativeMapContainer.getAltitudeAtPosition({
				nativeNodeHandle,
				lng,
				lat,
			});
			return result.altitude ?? null;
		} catch (e) {
			if (__DEV__) {
				console.warn('[mapHandle] getAltitudeAtPosition failed:', e);
			}
			return null;
		}
	};

	const hasDataAtPosition = async (
		lng: number,
		lat: number
	): Promise<boolean> => {
		try {
			const result = await NativeMapContainer.hasDataAtPosition({
				nativeNodeHandle,
				lng,
				lat,
			});
			return result.hasData;
		} catch {
			return false;
		}
	};

	const isTileCached = async (lng: number, lat: number): Promise<boolean> => {
		try {
			const result = await NativeMapContainer.isTileCached({
				nativeNodeHandle,
				lng,
				lat,
			});
			return result.cached;
		} catch {
			return false;
		}
	};

	const setCacheCapacity = async (capacity: number): Promise<void> => {
		await NativeMapContainer.setCacheCapacity({
			nativeNodeHandle,
			capacity,
		});
	};

	const getAltitudeAtPositionRetry = async (
		lng: number,
		lat: number,
		opts?: { maxRetries?: number }
	): Promise<number | null> => {
		const maxRetries = opts?.maxRetries ?? 10;

		const hasData = await hasDataAtPosition(lng, lat);
		if (!hasData) return null;

		for (let attempt = 0; attempt <= maxRetries; attempt++) {
			const result = await getAltitudeAtPosition(lng, lat);
			if (result !== null) return result;
			if (attempt < maxRetries) {
				await new Promise<void>((r) =>
					setTimeout(r, getRetryDelay(attempt))
				);
			}
		}
		return null;
	};

	return {
		getPosition,
		jumpTo,
		panTo,
		panBy,
		setZoom,
		zoomTo: setZoom,
		zoomOut,
		setBearing,
		rotateTo: setBearing,
		resetNorth,
		resetNorthPitch,
		setRoll,
		easeTo,
		flyTo,
		fitBounds,
		setBounds: fitBounds,
		flyToBounds,
		panInsideBounds,
		panInside,
		getAltitudeAtPosition,
		hasDataAtPosition,
		isTileCached,
		setCacheCapacity,
		getAltitudeAtPositionRetry,
	};
}

// ---------------------------------------------------------------------------
// Registry — simple wire/unwire bridge for non-React code
// ---------------------------------------------------------------------------

/**
 * Creates a registry that holds a {@link createMapHandle} result, wired
 * from a React component (via {@code wire}) and consumed from non-React
 * code (thunks, services) via {@code requireHandle} or
 * {@code getHandle}.
 *
 * @example
 * ```typescript
 * // Shared singleton:
 * export const mapRegistry = createMapHandleRegistry();
 *
 * // In AppView (React):
 * useEffect(() => {
 *     if (handle) mapRegistry.wire(handle);
 *     return () => mapRegistry.unwire();
 * }, [handle]);
 *
 * // In a thunk:
 * const h = mapRegistry.requireHandle();
 * await enrichCoordinatesWithElevation(coords, h);
 * ```
 */
export function createMapHandleRegistry() {
	let _handle: ReturnType<typeof createMapHandle> | null = null;

	return {
		/** Wire (or re-wire) the registry with a new native node handle. */
		wire(nativeNodeHandle: number) {
			_handle = createMapHandle(nativeNodeHandle);
		},

		/** Unwire and release the handle. */
		unwire() {
			_handle = null;
		},

		/**
		 * Returns the current handle, or {@code null} if not yet wired.
		 * Use when the caller can degrade gracefully.
		 */
		getHandle() {
			return _handle;
		},

		/**
		 * Returns the current handle, or throws if not yet wired.
		 * Use when a handle is required to proceed.
		 */
		requireHandle() {
			if (!_handle) {
				throw new Error(
					'MapHandleRegistry: handle not wired — the map may not have mounted yet.'
				);
			}
			return _handle;
		},
	};
}

/** Type of the object returned by {@link createMapHandleRegistry}. */
export type MapHandleRegistry = ReturnType<typeof createMapHandleRegistry>;
