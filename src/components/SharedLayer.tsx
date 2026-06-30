/**
 * External dependencies
 */
import { useContext, useEffect, useRef, type ReactNode } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';
import SharedLayerContext from '../context/SharedLayerContext';

/**
 * Module-level prefix for scope IDs. Combines a timestamp and random suffix
 * so that after Fast Refresh / HMR (which re-executes module scope and resets
 * the counter), a newly-mounted SharedLayer cannot produce a scope ID that
 * collides with a still-mounted instance from before the refresh.
 */
const SCOPE_ID_PREFIX = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

/**
 * Counter for generating unique scope IDs within a single module lifetime.
 * Increments on every SharedLayer mount, so each mount gets a fresh ID (stable
 * across re-renders, but unique across instances). Combined with {@link SCOPE_ID_PREFIX},
 * scope IDs are unique even across Fast Refresh boundaries.
 */
let nextScopeId = 0;

/**
 * Wraps children in a shared-layer grouping context.
 *
 * When active, all same-type layer components inside (e.g. all {@link LayerPath}
 * children, all {@link Marker} children) collapse into a single native
 * shared-layer fragment per type, regardless of interleaving. This gives
 * correct React-tree z-order at O(1) native draw calls.
 *
 * Uses React Context ({@link SharedLayerContext}) rather than a global mutable
 * counter, so grouping is properly scoped to this wrapper's subtree. Siblings
 * rendered after this SharedLayer are unaffected — they see the default
 * {@code null} context value and receive dedicated native layers.
 *
 * {@link SharedLayer} nests correctly — each wrapper provides its own scope
 * ID, and {@link useLayerOrder} reads the nearest ancestor's context value.
 * Nested SharedLayers each get their own independent fragment set.
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

	// Stable scope ID for this wrapper instance. Uses a ref so it survives
	// re-renders but changes on remount (ensuring a fresh scope each time the
	// wrapper reappears in the tree).
	const scopeIdRef = useRef<string | null>(null);
	if (!scopeIdRef.current) {
		scopeIdRef.current = `_s${SCOPE_ID_PREFIX}_${nextScopeId++}`;
	}

	// Mark the registry so the debug overlay can report whether any
	// SharedLayer wrapper is active, regardless of where the overlay sits
	// in the tree. MapContainer resets this to false each render pass.
	registry.sharedLayerActive = true;

	useEffect(() => {
		registry.notify();
		return () => {
			registry.notify();
		};
	}, [registry]);

	return (
		<SharedLayerContext.Provider value={scopeIdRef.current}>
			{children}
		</SharedLayerContext.Provider>
	);
};

export default SharedLayer;
