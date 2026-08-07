/**
 * Internal dependencies
 */
import { createContext } from 'react';

/**
 * Context provided by <ReindexScope>.
 *
 * When non-null, descendant useLayerOrder calls tag themselves with this
 * scope symbol so the containing ReindexScope can manage their sub-range
 * within registry.order.
 *
 * Outside any ReindexScope, defaults to null (same pattern as SharedLayerContext).
 */
const ReindexContext = createContext<symbol | null>(null);

export default ReindexContext;
