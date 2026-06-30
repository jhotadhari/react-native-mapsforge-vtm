/**
 * External dependencies
 */
import { useContext } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';

/**
 * Signals that the current render pass should reindex already-registered
 * layers in the native layer stack to match their current document order.
 *
 * Call this during render (before returning JSX children) in any component
 * inside {@code <MapContainer>} whose children may have been reordered
 * without MapContainer itself re-rendering — for example, a Redux-connected
 * layer list where the parent component does not subscribe to the layer order.
 *
 * Under the hood this bumps the registry's generation counter so that
 * {@code useLayerOrder} repositions already-registered layers on this same
 * render pass. The debounced {@code scheduleSync} then sends a native
 * {@code reorderLayers} call only when the UUID list actually changed.
 *
 * Safe to call unconditionally on every render — the repositioning logic is
 * a no-op when the document order hasn't changed, and the debounced sync
 * skips native calls when the UUID list is unchanged.
 */
const useLayerReindex = () => {
	const { registry } = useContext(MapHandleContext);
	registry.generation++;
	// Reset the cursor so the first child in this render pass anchors at
	// position 0 rather than using the stale cursor from the previous pass.
	// Without this, the repositioning logic in useLayerOrder computes
	// expectedIndex relative to whatever layer rendered last in the prior
	// pass, converging to the old order — the reindex is silently a no-op.
	registry.cursor = undefined;
};

export default useLayerReindex;
