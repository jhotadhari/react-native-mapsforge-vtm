/**
 * External dependencies
 */
import { useContext, useEffect, type ReactNode } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';

/**
 * Wraps children in a shared-layer grouping context.
 *
 * When active, all same-type layer components inside (e.g. all {@link LayerPath}
 * children, all {@link Marker} children) collapse into a single native
 * shared-layer fragment per type, regardless of interleaving. This gives
 * correct React-tree z-order at O(1) native draw calls.
 *
 * {@link SharedLayer} nests correctly — each wrapper increments
 * {@link LayerOrderRegistry.groupingDepth} during render (before children
 * render), so nested wrappers accumulate depth correctly.
 *
 * {@link MapContainer} resets {@code groupingDepth = 0} at the start of every
 * render pass, so when a {@link SharedLayer} is removed from the tree the next
 * render immediately sees depth 0 — no stale value from the previous render.
 *
 * @example
 * ```tsx
 * <SharedLayer>
 *   <LayerPath coordinates={path1} />
 *   <Marker position={pos1} />
 *   <LayerPath coordinates={path2} />
 *   <Marker position={pos2} />
 * </SharedLayer>
 * // → 1 native path layer + 1 native marker layer (not 4 layers)
 * ```
 */
const SharedLayer = ({ children }: { children?: ReactNode }) => {
	const { registry } = useContext(MapHandleContext);

	// Increment during every render — MapContainer resets groupingDepth to 0
	// at the start of each pass, so when this SharedLayer is present in the
	// tree children always see depth ≥ 1, and when it's removed they see 0
	// on the very next render (no stale cleanup value).
	registry.groupingDepth++;

	useEffect(() => {
		registry.notify();
		return () => {
			registry.notify();
		};
	}, [registry]);

	return <>{children}</>;
};

export default SharedLayer;
