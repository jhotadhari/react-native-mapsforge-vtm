/**
 * Wraps children so all same-type layer components inside collapse into one
 * shared native fragment per type (one GPU draw call per type).
 *
 * The wrapper renders one fragment anchor marking the fragment block's tree
 * position and provides its instance id via context. Entry components
 * (LayerPath, LayerShape, Marker) inside declare themselves against that id
 * with the owner-injected vtmSortIndex — the scene expands the anchor into
 * per-type fragments ordered by first occurrence.
 *
 * Sort-index injection traverses arrays and Fragments; wrapper components
 * should forward {@code vtmSortIndex} to the entries they render, or pass an
 * explicit {@code order} prop to the entry component instead.
 */

import { useMemo, useRef, type ReactNode } from 'react';
import SharedLayerContext from '../context/SharedLayerContext';
import useLayerAnchor from '../compose/useLayerAnchor';
import { injectVtmSortIndex } from '../compose/injectVtmSortIndex';

// Module-load prefix keeps ids unique across Fast Refresh — without it a
// refreshed module restarts its counter and collides with ids from the
// previous generation (React state survives the refresh).
const uidPrefix = `${Date.now().toString(36)}_${Math.random()
	.toString(36)
	.slice(2, 8)}_`;
let sharedLayerCounter = 0;

const SharedLayer = ({ children }: { children?: ReactNode }) => {
	const sharedIdRef = useRef<string | null>(null);
	if (sharedIdRef.current === null) {
		sharedIdRef.current = `${uidPrefix}shared_${sharedLayerCounter++}`;
	}
	const sharedId = sharedIdRef.current;

	const { element } = useLayerAnchor({
		kind: 'fragment',
		fragmentId: sharedId,
	});

	// Owner injection: element children get their sibling position as
	// vtmSortIndex — the entry-order source for the scene.
	const injectedChildren = useMemo(
		() => injectVtmSortIndex(children),
		[children]
	);

	return (
		<SharedLayerContext.Provider value={sharedId}>
			{element}
			{injectedChildren}
		</SharedLayerContext.Provider>
	);
};

export default SharedLayer;
