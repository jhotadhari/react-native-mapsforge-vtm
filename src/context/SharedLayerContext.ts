/**
 * External dependencies
 */
import { createContext } from 'react';

/**
 * Context provided by {@link SharedLayer} to scope shared-layer grouping to its
 * React subtree.
 *
 * When non-null, the value is the stable instance id of the enclosing
 * SharedLayer wrapper. Entry components (LayerPath, LayerShape, Marker) read
 * this context to determine whether they share a native layer fragment with
 * other same-type components within the same SharedLayer, and declare
 * themselves against this id in the scene.
 *
 * Outside any SharedLayer, the context defaults to {@code null} (anchored /
 * type-run fragments). Nested SharedLayers each provide their own id.
 */
const SharedLayerContext = createContext<string | null>(null);

export default SharedLayerContext;
