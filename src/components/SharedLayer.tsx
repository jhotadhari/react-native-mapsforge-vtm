/**
 * Wraps children so all same-type layer components inside collapse into one
 * shared native fragment per type (one GPU draw call per type).
 *
 * The wrapper renders one fragment anchor marking the fragment block's tree
 * position and provides its instance id via context. Entry components
 * (LayerPath, LayerShape, Marker) inside declare themselves against that id
 * with the owner-injected vtmSortIndex — the scene expands the anchor into
 * per-type fragments ordered by first occurrence.
 */

import {
	Children,
	cloneElement,
	isValidElement,
	useMemo,
	useRef,
	type ReactElement,
	type ReactNode,
} from 'react';
import SharedLayerContext from '../context/SharedLayerContext';
import useLayerAnchor from '../compose/useLayerAnchor';

let sharedLayerCounter = 0;

const SharedLayer = ({ children }: { children?: ReactNode }) => {
	const sharedIdRef = useRef<string | null>(null);
	if (sharedIdRef.current === null) {
		sharedIdRef.current = `shared_${sharedLayerCounter++}`;
	}
	const sharedId = sharedIdRef.current;

	const { element } = useLayerAnchor({
		kind: 'fragment',
		fragmentId: sharedId,
	});

	// Owner injection: direct element children get their sibling position as
	// vtmSortIndex — the entry-order source for the scene.
	const injectedChildren = useMemo(
		() =>
			Children.map(children, (child, index) =>
				isValidElement(child)
					? cloneElement(
							child as ReactElement<{ vtmSortIndex?: number }>,
							{
								vtmSortIndex: index,
							}
						)
					: child
			),
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
