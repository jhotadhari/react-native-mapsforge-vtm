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
	// Monotonically increasing counter bumped by MapContainer on each of its own renders.
	// useLayerOrder compares its last-seen generation against this to detect full render
	// passes (where already-registered layers must be repositioned) vs solo re-renders
	// (where they must not be disturbed).
	generation: number;
	// Id of whichever layer instance rendered immediately before "now" within the current
	// render pass. MapContainer resets this to undefined at the start of every one of its own
	// renders; see useLayerOrder for how it's used to anchor newly mounted layers.
	cursor: undefined | symbol;
	// Type of the layer at `cursor`, used for type-run boundary detection so that
	// interleaved shared-layer types (e.g. Path, Marker, Path) each get their own
	// fragment. Reset by MapContainer alongside `cursor`.
	cursorLayerType: undefined | string;
	// Per-type fragment counter (e.g. path→2, marker→1). Used by the eager
	// render-time assignment in useLayerOrder.
	fragmentIndices: Map<string, number>;
	// Per-component fragment uuid (keyed by the component's stable Symbol id).
	// Computed eagerly during render by useLayerOrder and consumed directly by flush().
	fragmentUuids: Map<symbol, string>;
	// Per-component layer type string (e.g. 'path', 'marker', 'mapsforge', etc.).
	// Populated by useLayerOrder during render.
	layerTypes: Map<symbol, string>;
	// Per-layer reindex scope assignment.
	// layer symbol → ReindexScope's stable scope symbol.
	// Populated by useLayerOrder when inside a ReindexContext provider.
	layerReindexScopes: Map<symbol, symbol>;
	// True when at least one <SharedLayer> wrapper rendered in the current pass.
	// Set by SharedLayer during render, reset by MapContainer each pass.
	// Exists purely for debug display — useLayerOrder reads SharedLayerContext instead.
	sharedLayerActive: boolean;
	scheduleSync: (nativeNodeHandle: null | number) => void;
	// Cancels pending debounced flush timers. Call when the map view is
	// destroyed to prevent stale nativeNodeHandle calls that would produce
	// cosmetic console errors (reportNativeError) after teardown.
	destroy: () => void;
	// Debug/devtools subscription — called whenever the registry mutates so the debug
	// hook (useLayerDebugInfo) can re-read state via useSyncExternalStore.
	listeners: Set<() => void>;
	subscribe: (callback: () => void) => () => void;
	notify: () => void;
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
	const layerReindexScopes = new Map<symbol, symbol>();
	let lastAppliedUuids: string[] = [];
	let lastReorderWasEffective = false;

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
	let destroyed = false;
	const flush = () => {
		const nativeNodeHandle = pendingNativeNodeHandle;
		if (nativeNodeHandle === null) {
			return;
		}
		// Build orderedUuids from fragment uuids (computed eagerly during render
		// by useLayerOrder for shared-layer types) and per-component uuids (for
		// dedicated-layer types). Fragment uuids start with __vtm_shared_ so we
		// can distinguish them from per-component uuids without a hardcoded list.
		const orderedUuids: string[] = [];
		const seenUuids = new Set<string>();

		for (const id of order) {
			const fragmentUuid = fragmentUuids.get(id);
			if (fragmentUuid) {
				// Shared-layer type: use the fragment uuid
				if (!seenUuids.has(fragmentUuid)) {
					seenUuids.add(fragmentUuid);
					orderedUuids.push(fragmentUuid);
				}
			} else {
				// Dedicated-layer type or still-resolving: use per-component uuid
				const uuid = uuids.get(id);
				if (uuid && !seenUuids.has(uuid)) {
					seenUuids.add(uuid);
					orderedUuids.push(uuid);
				}
			}
		}
		const unchanged =
			orderedUuids.length === lastAppliedUuids.length &&
			orderedUuids.every((uuid, i) => uuid === lastAppliedUuids[i]);
		// Skip only when the uuid list hasn't changed AND the last reorder
		// actually had layers to move. If the last reorder was a no-op (e.g.
		// it fired before shared layers existed in knownLayers), re-fire now
		// that layers may have been created.
		if (unchanged && lastReorderWasEffective) {
			return;
		}
		lastAppliedUuids = orderedUuids;
		lastReorderWasEffective = true;
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

	const listeners = new Set<() => void>();

	return {
		order,
		uuids,
		generation: 0,
		cursor: undefined,
		cursorLayerType: undefined,
		fragmentIndices,
		fragmentUuids,
		layerTypes,
		layerReindexScopes,
		listeners,
		sharedLayerActive: false,
		subscribe: (callback: () => void) => {
			listeners.add(callback);
			return () => {
				listeners.delete(callback);
			};
		},
		notify: () => {
			listeners.forEach((cb) => cb());
		},
		scheduleSync: (nativeNodeHandle) => {
			if (destroyed) {
				return;
			}
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
		destroy: () => {
			destroyed = true;
			pendingNativeNodeHandle = null;
			if (debounceTimer) {
				clearTimeout(debounceTimer);
				debounceTimer = null;
			}
			if (maxWaitTimer) {
				clearTimeout(maxWaitTimer);
				maxWaitTimer = null;
			}
		},
	};
};

const noopRegistry: LayerOrderRegistry = {
	order: [],
	uuids: new Map(),
	generation: 0,
	cursor: undefined,
	cursorLayerType: undefined,
	fragmentIndices: new Map(),
	fragmentUuids: new Map(),
	layerTypes: new Map(),
	layerReindexScopes: new Map(),
	sharedLayerActive: false,
	listeners: new Set(),
	subscribe: () => () => {},
	notify: () => {},
	scheduleSync: () => {},
	destroy: () => {},
};

const MapHandleContext = createContext<MapHandleContextValue>({
	nativeNodeHandle: null,
	registry: noopRegistry,
});

export default MapHandleContext;
