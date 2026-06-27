/**
 * External dependencies
 */
import { useContext, useEffect, useRef } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';

/**
 * Registers a layer component into the shared, map-wide layer ordering registry, and keeps
 * the native layer stack's order in sync with where this component sits in the render tree --
 * across arbitrary nesting depth, and continuously across mount/unmount/reorder, not just at
 * creation time. Returns the current nativeNodeHandle and this layer's positionIndex among
 * all JS-managed layers, so callers can pass it to the native createLayer call for
 * position-aware insertion (the native side inserts at that position immediately, eliminating
 * the need for a follow-up reorderLayers pass).
 */
const useLayerOrder = (uuid: null | false | string) => {
	const { nativeNodeHandle, registry } = useContext(MapHandleContext);

	const idRef = useRef<undefined | symbol>(undefined);
	if (!idRef.current) {
		idRef.current = Symbol();
	}
	const id = idRef.current;

	// Always holds the latest nativeNodeHandle, so the unmount effect below can resync with an
	// up to date value without having to re-run (and thus re-register) on every handle change.
	const nativeNodeHandleRef = useRef(nativeNodeHandle);
	nativeNodeHandleRef.current = nativeNodeHandle;

	// Register during render: React calls component render functions in a deterministic
	// depth-first, document-order sequence regardless of nesting depth. MapContainer resets
	// registry.cursor to undefined at the start of every one of its own renders, so within any
	// single coherent render pass (adding/removing/toggling a layer always re-renders its
	// siblings too, since none of them are memoized), each not-yet-registered instance can
	// insert itself right after whichever sibling rendered immediately before it -- giving it
	// the correct position even when it's a fresh remount, not just on first-ever mount.
	// Already-registered instances are never moved here, so a later solo re-render (e.g. this
	// component's own uuid resolving asynchronously) can never disturb the order.
	const previousId = registry.cursor;
	registry.cursor = id;
	if (!registry.order.includes(id)) {
		const previousIndex = previousId
			? registry.order.indexOf(previousId)
			: -1;
		registry.order.splice(
			previousIndex === -1 ? registry.order.length : previousIndex + 1,
			0,
			id
		);
	}

	// Compute the current position index among JS-managed layers. This is called during
	// render, so `order` already reflects the correct document-order position for this
	// component, even if it was just registered above.
	const positionIndex = registry.order.indexOf(id);

	// Unregister exactly once, on actual unmount -- not on every uuid change.
	useEffect(() => {
		return () => {
			const index = registry.order.indexOf(id);
			if (index !== -1) {
				registry.order.splice(index, 1);
			}
			registry.uuids.delete(id);
			registry.scheduleSync(nativeNodeHandleRef.current);
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
	}, [
		id,
		registry,
		uuid,
		nativeNodeHandle,
	]);

	return { nativeNodeHandle, positionIndex };
};

export default useLayerOrder;
