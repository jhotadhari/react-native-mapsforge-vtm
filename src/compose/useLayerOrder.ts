/**
 * External dependencies
 */
import { useContext, useEffect, useLayoutEffect, useRef } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';
import ReindexContext from '../context/ReindexContext';
import SharedLayerContext from '../context/SharedLayerContext';

/**
 * Registers a layer component into the shared, map-wide layer ordering registry, and keeps
 * the native layer stack's order in sync with where this component sits in the render tree --
 * across arbitrary nesting depth, and continuously across mount/unmount/reorder, not just at
 * creation time. Returns the current nativeNodeHandle and this layer's positionIndex among
 * all JS-managed layers, so callers can pass it to the native createLayer call for
 * position-aware insertion (the native side inserts at that position immediately, eliminating
 * the need for a follow-up reorderLayers pass).
 */
const useLayerOrder = (uuid: null | false | string, layerType?: string) => {
	const { nativeNodeHandle, registry } = useContext(MapHandleContext);
	const sharedScopeId = useContext(SharedLayerContext);
	const isGrouped = sharedScopeId !== null;
	const reindexScopeId = useContext(ReindexContext);

	const idRef = useRef<undefined | symbol>(undefined);
	if (!idRef.current) {
		idRef.current = Symbol();
	}
	const id = idRef.current;

	// Always holds the latest nativeNodeHandle, so the unmount effect below can resync with an
	// up to date value without having to re-run (and thus re-register) on every handle change.
	const nativeNodeHandleRef = useRef(nativeNodeHandle);
	nativeNodeHandleRef.current = nativeNodeHandle;

	// Guard to skip redundant Map write when reindex scope hasn't changed.
	const prevReindexScopeRef = useRef<symbol | null>(null);

	// Track the generation to detect new full render passes. MapContainer bumps
	// this on every one of its own renders, so a change means the entire subtree
	// is rendering in document order and already-registered layers must be
	// repositioned to match.
	const lastGenerationRef = useRef<number>(registry.generation);
	const generationChanged = lastGenerationRef.current !== registry.generation;
	lastGenerationRef.current = registry.generation;

	// Register during render: React calls component render functions in a deterministic
	// depth-first, document-order sequence regardless of nesting depth. MapContainer resets
	// registry.cursor to undefined at the start of every one of its own renders, so within any
	// single coherent render pass (adding/removing/toggling a layer always re-renders its
	// siblings too, since none of them are memoized), each not-yet-registered instance can
	// insert itself right after whichever sibling rendered immediately before it — giving it
	// the correct position even when it's a fresh remount, not just on first-ever mount.
	// Already-registered instances are repositioned when the generation changed (full render
	// pass) but never on a solo re-render (e.g. this component's own uuid resolving
	// asynchronously), preserving the key invariant that a single layer's state change can
	// never disturb sibling order.
	const previousId = registry.cursor;
	registry.cursor = id;
	const isNew = !registry.order.includes(id);
	if (isNew) {
		const previousIndex = previousId
			? registry.order.indexOf(previousId)
			: -1;
		registry.order.splice(
			previousIndex === -1 ? registry.order.length : previousIndex + 1,
			0,
			id
		);
	} else if (generationChanged) {
		// Full render pass: reposition already-registered layers to match the
		// current document order. The cursor tells us which sibling rendered
		// immediately before this one, so this layer should sit right after it.
		const currentIndex = registry.order.indexOf(id);
		// Defensive: if the id is somehow not in order despite isNew===false
		// (e.g. Concurrent Mode discarded a partial render), treat as new
		// registration instead of corrupting the array with splice(-1, 1).
		if (currentIndex === -1) {
			const previousIndex = previousId
				? registry.order.indexOf(previousId)
				: -1;
			registry.order.splice(
				previousIndex === -1
					? registry.order.length
					: previousIndex + 1,
				0,
				id
			);
		} else {
			const expectedIndex =
				previousId !== undefined
					? registry.order.indexOf(previousId) + 1
					: 0;
			if (currentIndex !== expectedIndex) {
				registry.order.splice(currentIndex, 1);
				registry.order.splice(
					currentIndex < expectedIndex
						? expectedIndex - 1
						: expectedIndex,
					0,
					id
				);
				// The uuid-mapping useEffect won't re-fire on generation
				// change (its deps don't include generation), so the native
				// reorder would never be triggered. Schedule it here directly
				// whenever a layer actually moved. The debounce coalesces
				// multiple calls from sibling layers into one native call.
				registry.scheduleSync(nativeNodeHandle);
			}
		}
	}

	// Store layer type for type-run boundary detection. For first-time renders,
	// compute a fragment uuid eagerly so callers can use it immediately. On
	// re-renders, just advance the cursor so subsequent siblings see the correct
	// previous type.
	//
	// Inside a SharedLayer (sharedScopeId non-null): use the scope ID as the
	// fragment UUID suffix, so all same-type children within a single SharedLayer
	// wrapper share one native fragment, and sibling SharedLayers each get their
	// own independent fragments.
	//
	// Outside SharedLayer (sharedScopeId null): use an incrementing per-type
	// fragment index that advances on type-run boundaries (same as before).
	if (layerType) {
		if (isNew) {
			registry.layerTypes.set(id, layerType);

			if (isGrouped) {
				// Inside SharedLayer: fragment UUID = __vtm_shared_<type>__<scopeId>
				const fragmentUuid = `__vtm_shared_${layerType}__${sharedScopeId}`;
				registry.fragmentUuids.set(id, fragmentUuid);
			} else {
				// Outside SharedLayer: use incrementing per-type fragment index.
				// Consecutive same-type layers share a fragment (index doesn't
				// change on type-match), alternating types get new fragments.
				const cursorType = registry.cursorLayerType;
				if (cursorType !== layerType) {
					const currentIdx =
						registry.fragmentIndices.get(layerType) ?? 0;
					registry.fragmentIndices.set(layerType, currentIdx + 1);
				}
				const fragIdx = registry.fragmentIndices.get(layerType) ?? 1;
				const fragmentUuid = `__vtm_shared_${layerType}__${fragIdx}`;
				registry.fragmentUuids.set(id, fragmentUuid);
			}
		}
		// Advance the cursor for non-grouped type-run detection only. Inside a
		// SharedLayer, fragment assignment uses the scope ID as suffix (not
		// fragment indices), so cursorLayerType is irrelevant — and advancing
		// it would leak the type across the SharedLayer boundary, causing the
		// next layer outside the wrapper to falsely see a type-match and skip
		// the fragment index increment.
		if (!isGrouped) {
			registry.cursorLayerType = layerType;
		}
	}

	// Tag with reindex scope so the containing ReindexScope can find this
	// layer in registry.order during its Phase 1 / Phase 2 operations.
	// Runs every render, not just isNew, so the scope association stays
	// current as long as this component is inside a ReindexScope.
	if (reindexScopeId !== null) {
		if (prevReindexScopeRef.current !== reindexScopeId) {
			prevReindexScopeRef.current = reindexScopeId;
			registry.layerReindexScopes.set(id, reindexScopeId);
		}
	} else {
		prevReindexScopeRef.current = null;
		registry.layerReindexScopes.delete(id);
	}

	// Compute the current position index among JS-managed layers. This is called during
	// render, so `order` already reflects the correct document-order position for this
	// component, even if it was just registered above.
	const positionIndex = registry.order.indexOf(id);

	// Unregister exactly once, on actual unmount -- not on every uuid change.
	// Uses useLayoutEffect so cleanup runs during commit (before ReindexScope's
	// Phase 2 useLayoutEffect), preventing zombie symbols in Phase 2.
	useLayoutEffect(() => {
		return () => {
			const index = registry.order.indexOf(id);
			if (index !== -1) {
				registry.order.splice(index, 1);
			}
			registry.uuids.delete(id);
			registry.layerTypes.delete(id);
			registry.fragmentUuids.delete(id);
			registry.layerReindexScopes.delete(id);
			registry.scheduleSync(nativeNodeHandleRef.current);
			registry.notify();
		};
	}, [id, registry]);

	// uuid resolves asynchronously after creation; keep the mapping (and a resync) up to date
	// whenever it changes, including going back to null/false right before removal.
	useEffect(() => {
		if (uuid) {
			registry.uuids.set(id, uuid);
		} else {
			registry.uuids.delete(id);
		}
		registry.scheduleSync(nativeNodeHandle);
		registry.notify();
	}, [
		id,
		registry,
		uuid,
		nativeNodeHandle,
	]);

	const fragmentUuid = layerType ? registry.fragmentUuids.get(id) : undefined;

	return { nativeNodeHandle, positionIndex, fragmentUuid };
};

export default useLayerOrder;
