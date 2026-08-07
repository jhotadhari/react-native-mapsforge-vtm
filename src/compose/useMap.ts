/**
 * External dependencies
 */
import { useContext, useMemo, useRef } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';
import { createMapHandle } from './createMapHandle';
import NativeMapContainer, {
	type GetPositionResponse,
	type GetDebugLayerDumpResponse,
} from '../NativeModules/NativeMapContainer';
import type { Position } from '../types';

/**
 * One of vtm's org.oscim.utils.animation.Easing.Type names, lowercased.
 */
export type EasingType =
	| 'linear'
	| 'sine_inout'
	| 'sine_in'
	| 'sine_out'
	| 'expo_out'
	| 'quad_inout'
	| 'cubic_inout'
	| 'quart_inout'
	| 'quint_inout';

export type MapPositionTarget = {
	center?: Position;
	zoomLevel?: number;
	bearing?: number;
	tilt?: number;
	roll?: number;
};

export type AnimationOptions = {
	// Milliseconds. Defaults differ per method -- see each one below.
	duration?: number;
	easing?: EasingType;
};

export type FitBoundsOptions = AnimationOptions & {
	paddingPx?: number;
};

export type { GetPositionResponse };

// ---------------------------------------------------------------------------
// Debug layer dump types
// ---------------------------------------------------------------------------

/**
 * One entry in the JS-side registry snapshot, representing a single mounted
 * layer component in React document order.  Symbols are not serializable, so
 * each entry is keyed by its position index.
 */
export type RegistryLayerEntry = {
	index: number;
	layerType: string | null;
	uuid: string | null;
	fragmentUuid: string | null;
};

/**
 * Summary of one native fragment — a set of React components that share a
 * single native layer (e.g. all paths inside a SharedLayer).  When
 * memberCount === 1 the fragment is dedicated (one component = one native
 * layer); when > 1 the components are grouped.
 */
export type FragmentSummaryEntry = {
	fragmentUuid: string;
	layerType: string;
	memberCount: number;
	memberIndices: number[];
};

/**
 * JS-side registry snapshot included in the debug dump alongside the native
 * layer list.  Comparing the two helps spot discrepancies: a layer in the
 * registry but not in nativeLayers = creation failed silently; a native layer
 * with no matching registry entry = a leak.
 */
export type RegistryDebugSnapshot = {
	orderLength: number;
	sentinelCount: number;
	resolvedCount: number;
	generation: number;
	sharedLayerActive: boolean;
	fragmentIndices: Record<string, number>;
	layers: RegistryLayerEntry[];
	/** Components grouped by their shared fragment UUID — one entry per native fragment. */
	fragmentSummary: FragmentSummaryEntry[];
};

/**
 * The full debug dump returned by getDebugLayerDump() -- native ground truth
 * plus the JS-side component hierarchy.
 */
export type DebugLayerDump = GetDebugLayerDumpResponse & {
	registry: RegistryDebugSnapshot;
};

const requireHandle = (nativeNodeHandle: null | number): number => {
	if (!nativeNodeHandle) {
		throw new Error(
			'useMap: nativeNodeHandle is not set yet -- the map view has not been created.'
		);
	}
	return nativeNodeHandle;
};

/**
 * Imperative map control -- panning, zooming, rotating/tilting, animated camera moves and
 * bounds-fitting -- all implemented as thin wrappers around two native primitives,
 * NativeMapContainer.animateTo (itself a thin wrapper around vtm's own
 * org.oscim.map.Animator, which already handles Mercator-correct interpolation and easing) and
 * NativeMapContainer.getPosition. Every method here resolves once the underlying native
 * animation actually finishes (or, for a duration of 0, once the jump is applied), so calls can
 * be awaited and chained.
 *
 * MapHandleContext is only provided to MapContainer's own `children`, so calling useMap() from
 * a component that isn't nested inside <MapContainer> (e.g. a toolbar rendered as its sibling)
 * would otherwise always see a null handle. Passing `nativeNodeHandleOverride` lets such a
 * caller use MapContainer's existing nativeNodeHandle/setNativeNodeHandle "lift the state up"
 * props instead of needing its own bridge component just to reach the context.
 */
const useMap = (nativeNodeHandleOverride?: null | number) => {
	const { nativeNodeHandle: contextHandle, registry } =
		useContext(MapHandleContext);
	const nativeNodeHandle =
		nativeNodeHandleOverride === undefined
			? contextHandle
			: nativeNodeHandleOverride;

	// Keep a ref to the latest registry so getDebugLayerDump (inside the memoized
	// object) can always read the current registry state without adding `registry`
	// to the useMemo dependency array (which would defeat memoization since the
	// registry is a new object on every MapContainer render).
	const registryRef = useRef(registry);
	registryRef.current = registry;

	return useMemo(() => {
		// Guard: requireHandle was called lazily (inside each method
		// closure) before the createMapHandle extraction.  Now that
		// createMapHandle is called eagerly in the memo, we must
		// return a stub when the handle isn't ready yet — otherwise
		// the eager requireHandle(null) throws during render, crashing
		// every component that calls useMap() before the map mounts.
		if (!nativeNodeHandle) {
			const fail = () => {
				throw new Error(
					'useMap: nativeNodeHandle is not set yet -- the map view has not been created.'
				);
			};
			const stub: Record<string, (...args: any[]) => any> = {};
			const methodNames = [
				'getPosition',
				'jumpTo',
				'panTo',
				'panBy',
				'setZoom',
				'zoomTo',
				'zoomOut',
				'setBearing',
				'rotateTo',
				'resetNorth',
				'resetNorthPitch',
				'setRoll',
				'easeTo',
				'flyTo',
				'fitBounds',
				'setBounds',
				'flyToBounds',
				'panInsideBounds',
				'panInside',
				'getAltitudeAtPosition',
				'hasDataAtPosition',
				'isTileCached',
				'setCacheCapacity',
				'getAltitudeAtPositionRetry',
				'getDebugLayerDump',
			];
			for (const name of methodNames) {
				stub[name] = fail;
			}
			return stub as ReturnType<typeof createMapHandle> & {
				getDebugLayerDump: () => Promise<DebugLayerDump>;
			};
		}
		const handle = requireHandle(nativeNodeHandle);
		const base = createMapHandle(handle);

		/**
		 * Returns a comprehensive debug dump of all layers on the map, combining
		 * native ground truth (actual vtm Layer objects, their z-indices, class
		 * names, uuids, and enabled state) with the JS-side component registry
		 * (React render order, fragment assignments, generation counter).
		 *
		 * Useful for debugging layer ordering issues, missing layers, and
		 * discrepancies between what React thinks is mounted and what's actually
		 * on the native map.
		 */
		const getDebugLayerDump = async (): Promise<DebugLayerDump> => {
			const nativeDump = await NativeMapContainer.getDebugLayerDump({
				nativeNodeHandle: handle,
			});

			// Build a JSON-safe snapshot of the JS-side registry.  Symbols can't
			// be serialized, so we iterate `order` by position index and look up
			// each entry's metadata from the registry's parallel maps.
			const reg = registryRef.current;
			const registryLayers: RegistryLayerEntry[] = reg.order
				.filter(function (id) {
					return !reg.sentinels.has(id);
				})
				.map((id, index) => ({
					index,
					layerType: reg.layerTypes.get(id) ?? null,
					uuid: reg.uuids.get(id) ?? null,
					fragmentUuid: reg.fragmentUuids.get(id) ?? null,
				}));

			const fragmentIndices: Record<string, number> = {};
			reg.fragmentIndices.forEach((value, key) => {
				fragmentIndices[key] = value;
			});

			// Group components by their fragment UUID so it's immediately
			// obvious whether SharedLayer grouping is active (few fragments
			// with many members) or not (many fragments with 1 member each).
			const fragmentSummaryMap = new Map<
				string,
				{ layerType: string; memberIndices: number[] }
			>();
			for (const layer of registryLayers) {
				if (!layer.fragmentUuid) {
					continue;
				}
				let entry = fragmentSummaryMap.get(layer.fragmentUuid);
				if (!entry) {
					entry = {
						layerType: layer.layerType ?? 'unknown',
						memberIndices: [],
					};
					fragmentSummaryMap.set(layer.fragmentUuid, entry);
				}
				entry.memberIndices.push(layer.index);
			}
			const fragmentSummary: FragmentSummaryEntry[] = Array.from(
				fragmentSummaryMap.entries()
			)
				.map(([fragmentUuid, entry]) => ({
					fragmentUuid,
					layerType: entry.layerType,
					memberCount: entry.memberIndices.length,
					memberIndices: entry.memberIndices,
				}))
				.sort((a, b) => a.memberIndices[0]! - b.memberIndices[0]!);

			const registrySnapshot: RegistryDebugSnapshot = {
				orderLength: reg.order.length,
				sentinelCount: reg.sentinels.size,
				resolvedCount: registryLayers.filter((l) => l.uuid !== null)
					.length,
				generation: reg.generation,
				sharedLayerActive: reg.sharedLayerActive,
				fragmentIndices,
				layers: registryLayers,
				fragmentSummary,
			};

			return {
				...nativeDump,
				registry: registrySnapshot,
			};
		};

		return {
			...base,
			getDebugLayerDump,
		};
	}, [nativeNodeHandle]);
};

export default useMap;
