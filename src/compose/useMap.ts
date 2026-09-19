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
 * One entry in the JS-side scene-plan snapshot: a native layer-stack item
 * (dedicated layer or shared fragment) in desired bottom→top order.
 */
export type RegistryLayerEntry = {
	index: number;
	layerType: string | null;
	uuid: string | null;
	fragmentUuid: string | null;
	kind: 'layer' | 'fragment';
	/** Number of entries hosted by this item's fragment (1 for dedicated). */
	fragmentMemberCount: number;
};

/**
 * Summary of one fragment — a set of React components sharing a single
 * native layer (e.g. all paths inside a SharedLayer).
 */
export type FragmentSummaryEntry = {
	fragmentUuid: string;
	layerType: string;
	memberCount: number;
	resolvedCount: number;
};

/**
 * JS-side scene-plan snapshot included in the debug dump alongside the
 * native layer list.  Comparing the two shows whether the native stack
 * matches the scene's desired order.
 */
export type RegistryDebugSnapshot = {
	/** Desired native layer count (resolved items only). */
	orderLength: number;
	/** Resolved entry count across all fragments. */
	resolvedCount: number;
	fragmentCount: number;
	scopeCount: number;
	layers: RegistryLayerEntry[];
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
	const { nativeNodeHandle: contextHandle, scene } =
		useContext(MapHandleContext);
	const nativeNodeHandle =
		nativeNodeHandleOverride === undefined
			? contextHandle
			: nativeNodeHandleOverride;

	// Keep a ref to the latest scene so getDebugLayerDump (inside the memoized
	// object) can always read the current plan without adding `scene` to the
	// useMemo dependency array (which would defeat memoization).
	const sceneRef = useRef(scene);
	sceneRef.current = scene;

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

			// Build a JSON-safe snapshot of the scene plan — the desired
			// layer stack the presenter syncs to native.
			const plan = sceneRef.current.plan();
			const registryLayers: RegistryLayerEntry[] = plan.layers.map(
				(layer, index) => {
					const fragment =
						layer.kind === 'fragment'
							? plan.fragments.find((f) => f.uuid === layer.uuid)
							: undefined;
					return {
						index,
						layerType: layer.layerType ?? null,
						uuid: layer.uuid,
						fragmentUuid:
							layer.kind === 'fragment' ? layer.uuid : null,
						kind: layer.kind,
						fragmentMemberCount: fragment
							? fragment.entryUids.length
							: 1,
					};
				}
			);

			const fragmentSummary: FragmentSummaryEntry[] = plan.fragments.map(
				(fragment) => ({
					fragmentUuid: fragment.uuid,
					layerType: fragment.layerType,
					memberCount: fragment.entryUids.length,
					resolvedCount: fragment.resolvedEntryUids.length,
				})
			);

			let resolvedCount = 0;
			for (const fragment of plan.fragments) {
				resolvedCount += fragment.resolvedEntryUids.length;
			}

			const registrySnapshot: RegistryDebugSnapshot = {
				orderLength: plan.layers.length,
				resolvedCount,
				fragmentCount: plan.fragments.length,
				scopeCount: plan.scopes.length,
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
