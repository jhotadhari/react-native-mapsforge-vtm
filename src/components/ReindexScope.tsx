/**
 * Wraps children in a named scope that orders as one block.
 *
 * The scope renders a sibling anchor that permanently marks the block's tree
 * position (replacing the old sentinel machinery — the anchor IS the
 * placeholder, with zero lifecycle management). Descendant layer anchors
 * carry the scope uid via context so the scene can group them.
 *
 * `order` optionally overrides the block position across sibling scopes;
 * without it the block sits at its tree position (default semantics).
 */

import { useMemo, type ReactNode } from 'react';
import ReindexContext from '../context/ReindexContext';
import useLayerAnchor from '../compose/useLayerAnchor';

export type ReindexScopeProps = {
	children?: ReactNode;
	/**
	 * Optional priority for ordering across sibling ReindexScope instances.
	 * Lower values = earlier in the layer stack = lower z-index.
	 */
	order?: number;
};

const ReindexScope = ({ children, order }: ReindexScopeProps) => {
	// `order` flows into the plan via the scope's own anchor descriptor
	// (useLayerAnchor → AnchorDescriptor.scopeOrder → planBuilder), NOT through
	// context — the context only carries `scopeUid` so descendant anchors can
	// group themselves under this scope.
	const { uid, element } = useLayerAnchor({
		kind: 'scope',
		scopeOrder: order,
	});

	const contextValue = useMemo(
		() => ({
			scopeUid: uid,
		}),
		[uid]
	);

	return (
		<ReindexContext.Provider value={contextValue}>
			{element}
			{children}
		</ReindexContext.Provider>
	);
};

export default ReindexScope;
