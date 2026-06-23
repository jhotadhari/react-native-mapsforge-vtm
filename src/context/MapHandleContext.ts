/**
 * External dependencies
 */
import { createContext } from 'react';

/**
 * Internal dependencies
 */
import NativeMapContainer from '../NativeModules/NativeMapContainer';

export type LayerOrderRegistry = {
	order: symbol[];
	uuids: Map<symbol, string>;
	// Id of whichever layer instance rendered immediately before "now" within the current
	// render pass. MapContainer resets this to undefined at the start of every one of its own
	// renders; see useLayerOrder for how it's used to anchor newly mounted layers.
	cursor: undefined | symbol;
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
 * component's own createLayer() call resolves). Whenever either changes, `scheduleSync` recomputes
 * the full native layer order and -- only if it actually changed -- pushes it to the native side.
 */
export const createLayerOrderRegistry = (): LayerOrderRegistry => {
	const order: symbol[] = [];
	const uuids = new Map<symbol, string>();
	let lastAppliedUuids: string[] = [];

	// Many sibling layers can each resolve their own uuid within milliseconds of one
	// another (e.g. a burst of layers mounting together) -- without batching, every single
	// resolution would fire its own native reorderLayers call. Coalescing into one
	// macrotask flush collapses a burst into a single native call carrying the final order,
	// instead of one call per resolved uuid.
	let flushScheduled = false;
	let pendingNativeNodeHandle: null | number = null;
	const flush = () => {
		flushScheduled = false;
		const nativeNodeHandle = pendingNativeNodeHandle;
		if (!nativeNodeHandle) {
			return;
		}
		const orderedUuids = order
			.map((id) => uuids.get(id))
			.filter((uuid): uuid is string => !!uuid);
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
			console.log('ERROR', err);
		});
	};

	return {
		order,
		uuids,
		cursor: undefined,
		scheduleSync: (nativeNodeHandle) => {
			pendingNativeNodeHandle = nativeNodeHandle;
			if (flushScheduled) {
				return;
			}
			flushScheduled = true;
			setTimeout(flush, 0);
		},
	};
};

const noopRegistry: LayerOrderRegistry = {
	order: [],
	uuids: new Map(),
	cursor: undefined,
	scheduleSync: () => {},
};

const MapHandleContext = createContext<MapHandleContextValue>({
	nativeNodeHandle: null,
	registry: noopRegistry,
});

export default MapHandleContext;
