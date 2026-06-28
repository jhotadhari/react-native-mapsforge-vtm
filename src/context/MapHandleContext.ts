/**
 * External dependencies
 */
import { createContext } from 'react';

/**
 * Internal dependencies
 */
import NativeMapContainer from '../NativeModules/NativeMapContainer';
import reportNativeError from '../reportNativeError';

export type LayerOrderRegistry = {
	order: symbol[];
	uuids: Map<symbol, string>;
	// Id of whichever layer instance rendered immediately before "now" within the current
	// render pass. MapContainer resets this to undefined at the start of every one of its own
	// renders; see useLayerOrder for how it's used to anchor newly mounted layers.
	cursor: undefined | symbol;
	// Type of the layer at `cursor`, used for type-run boundary detection so that
	// interleaved shared-layer types (e.g. Path, Marker, Path) each get their own
	// fragment. Reset by MapContainer alongside `cursor`.
	cursorLayerType: undefined | string;
	// Per-type fragment counter (e.g. path→2, marker→1). Used both by the eager
	// render-time assignment in useLayerOrder and by flush()'s authoritative pass.
	fragmentIndices: Map<string, number>;
	// Per-component fragment uuid (keyed by the component's stable Symbol id).
	// Set during render and recomputed authoritatively in flush().
	fragmentUuids: Map<symbol, string>;
	// Per-component layer type string (e.g. 'path', 'marker', 'mapsforge', etc.).
	// Populated by useLayerOrder during render.
	layerTypes: Map<symbol, string>;
	scheduleSync: (nativeNodeHandle: null | number) => void;
};

export type MapHandleContextValue = {
	nativeNodeHandle: null | number;
	registry: LayerOrderRegistry;
};

/**
 * Creates the registry MapContainer hands down through context. It tracks, for every mounted
 * layer component, where it sits in the render tree (`order`, populated in document order
 * regardless of nesting depth) and its resolved native uuid (`uuids`, populated once that
 * component's own createLayer() call resolves).
 *
 * With position-aware layer insertion, `scheduleSync` / `reorderLayers` serves only as a
 * safety net for edge cases (e.g. layers whose relative order changes without re-creation).
 * During normal mount/unmount, each layer's `positionIndex` in the createLayer params ensures
 * it lands in the correct position from the start.
 */
export const createLayerOrderRegistry = (): LayerOrderRegistry => {
	const order: symbol[] = [];
	const uuids = new Map<symbol, string>();
	const fragmentIndices = new Map<string, number>();
	const fragmentUuids = new Map<symbol, string>();
	const layerTypes = new Map<symbol, string>();
	let lastAppliedUuids: string[] = [];

	// Many sibling layers can each resolve their own uuid within milliseconds of one
	// another (e.g. a burst of layers mounting together) -- without batching, every single
	// resolution would fire its own native reorderLayers call. scheduleSync below debounces
	// (trailing edge, with a max-wait cap) rather than just deferring to the next macrotask, so a
	// sustained burst of thousands of resolutions -- not just ones landing in the same tick --
	// collapses into as few native calls as possible.
	const DEBOUNCE_MS = 16;
	const MAX_WAIT_MS = 250;
	let debounceTimer: null | ReturnType<typeof setTimeout> = null;
	let maxWaitTimer: null | ReturnType<typeof setTimeout> = null;
	let pendingNativeNodeHandle: null | number = null;
	const flush = () => {
		const nativeNodeHandle = pendingNativeNodeHandle;
		if (!nativeNodeHandle) {
			return;
		}
		// Recompute fragment uuids from the final order (corrects any stale
		// eager assignments from the last render pass). For shared-layer types
		// ('path', 'marker'), consecutive same-type components collapse into a
		// single fragment uuid; a type alteration creates a new fragment.
		// Dedicated-layer types (everything else) use their per-component uuid
		// directly.
		let currentRunType: string | null = null;
		let runIndex = 0;
		const seenFragmentUuids = new Set<string>();
		const orderedUuids: string[] = [];

		for (const id of order) {
			const layerType = layerTypes.get(id);
			const perComponentUuid = uuids.get(id);

			if (layerType && (layerType === 'path' || layerType === 'marker')) {
				// Shared-layer type: use fragment uuid.
				if (layerType !== currentRunType) {
					currentRunType = layerType;
					// Advance fragment index for this type.
					const idx = (fragmentIndices.get(layerType) ?? 0) + 1;
					fragmentIndices.set(layerType, idx);
					runIndex = idx;
				}
				const fragmentUuid = `__vtm_shared_${layerType}__${runIndex}`;
				fragmentUuids.set(id, fragmentUuid);

				if (!seenFragmentUuids.has(fragmentUuid)) {
					seenFragmentUuids.add(fragmentUuid);
					orderedUuids.push(fragmentUuid);
				}
			} else if (perComponentUuid) {
				// Dedicated-layer type: use per-component uuid directly.
				orderedUuids.push(perComponentUuid);
			}
		}
		const unchanged =
			orderedUuids.length === lastAppliedUuids.length &&
			orderedUuids.every((uuid, i) => uuid === lastAppliedUuids[i]);
		if (unchanged) {
			return;
		}
		lastAppliedUuids = orderedUuids;
		NativeMapContainer.reorderLayers({
			nativeNodeHandle,
			layerUuids: orderedUuids,
		}).catch((err) => {
			reportNativeError(err, null);
		});
	};

	const doFlush = () => {
		if (debounceTimer) {
			clearTimeout(debounceTimer);
			debounceTimer = null;
		}
		if (maxWaitTimer) {
			clearTimeout(maxWaitTimer);
			maxWaitTimer = null;
		}
		flush();
	};

	return {
		order,
		uuids,
		cursor: undefined,
		cursorLayerType: undefined,
		fragmentIndices,
		fragmentUuids,
		layerTypes,
		scheduleSync: (nativeNodeHandle) => {
			pendingNativeNodeHandle = nativeNodeHandle;
			// Trailing debounce: every call pushes the flush out, so a continuous burst only
			// flushes once it actually goes quiet.
			if (debounceTimer) {
				clearTimeout(debounceTimer);
			}
			debounceTimer = setTimeout(doFlush, DEBOUNCE_MS);
			// Max-wait: armed once per burst (not reset), so a sustained burst can't defer the
			// flush indefinitely -- already-resolved layers are never left unsynced for longer
			// than MAX_WAIT_MS.
			if (!maxWaitTimer) {
				maxWaitTimer = setTimeout(doFlush, MAX_WAIT_MS);
			}
		},
	};
};

const noopRegistry: LayerOrderRegistry = {
	order: [],
	uuids: new Map(),
	cursor: undefined,
	cursorLayerType: undefined,
	fragmentIndices: new Map(),
	fragmentUuids: new Map(),
	layerTypes: new Map(),
	scheduleSync: () => {},
};

const MapHandleContext = createContext<MapHandleContextValue>({
	nativeNodeHandle: null,
	registry: noopRegistry,
});

export default MapHandleContext;
