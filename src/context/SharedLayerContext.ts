/**
 * External dependencies
 */
import { createContext } from 'react';

/**
 * Context provided by {@link SharedLayer} to scope shared-layer grouping to its
 * React subtree.
 *
 * When non-null, the value is a unique scope identifier for this SharedLayer
 * instance. Components that use {@link useLayerOrder} read this context to
 * determine whether they should share a native layer fragment with other
 * same-type components within the same SharedLayer.
 *
 * Outside any SharedLayer, the context defaults to {@code null} (dedicated
 * native layers). Inside a SharedLayer, the value is a stable string that
 * identifies that wrapper — nested SharedLayers each provide their own scope
 * ID, so inner layers correctly share fragments within their immediate wrapper
 * while outer layers share fragments within the outer wrapper.
 */
const SharedLayerContext = createContext<string | null>(null);

export default SharedLayerContext;
