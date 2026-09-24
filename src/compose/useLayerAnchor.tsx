/**
 * useLayerAnchor — renders a VtmAnchorView marker for a layer component,
 * fragment owner (SharedLayer / LayerMarker) or ReindexScope wrapper, and
 * registers its descriptor with the SceneSync.
 *
 * The anchor is the ONLY ordering mechanism a layer-stack component needs:
 * the committed-tree walk reads anchor uids in tree order and feeds the
 * scene. No render-phase ordering state exists anywhere.
 */

import {
	useContext,
	useEffect,
	useMemo,
	useRef,
	type ReactElement,
} from 'react';
import VtmAnchorView from '../NativeViews/VtmAnchorViewNativeComponent';
import MapHandleContext from '../context/MapHandleContext';
import ReindexContext from '../context/ReindexContext';
import type { AnchorDescriptor, AnchorKind } from '../scene/types';

// Module-load prefix keeps uids unique across Fast Refresh — without it a
// refreshed module restarts its counter and collides with uids from the
// previous generation (React state survives the refresh).
const uidPrefix = `${Date.now().toString(36)}_${Math.random()
	.toString(36)
	.slice(2, 8)}_`;
let anchorUidCounter = 0;

export type UseLayerAnchorOptions = {
	kind: AnchorKind;
	/** When false the anchor is neither rendered nor registered. */
	active?: boolean;
	layerType?: string;
	/** Owner id for kind 'fragment' (SharedLayer wrapper id / LayerMarker uid). */
	fragmentId?: string;
	/** True when the layer type is hosted by a shared fragment manager. */
	shared?: boolean;
	/** Explicit ordering priority, kind 'scope' only. */
	scopeOrder?: number;
};

const ANCHOR_STYLE = { width: 0, height: 0 } as const;

const useLayerAnchor = ({
	kind,
	active = true,
	layerType,
	fragmentId,
	shared,
	scopeOrder,
}: UseLayerAnchorOptions): {
	uid: string;
	element: ReactElement | null;
} => {
	const { sync } = useContext(MapHandleContext);
	const reindexScope = useContext(ReindexContext);

	const uidRef = useRef<string | null>(null);
	if (uidRef.current === null) {
		uidRef.current = `${uidPrefix}${kind}_${anchorUidCounter++}`;
	}
	const uid = uidRef.current;

	const scopeUid = reindexScope !== null ? reindexScope.scopeUid : undefined;
	// Fragment anchors default their fragmentId to their own uid (LayerMarker
	// pattern); SharedLayer wrappers pass an explicit instance id.
	const resolvedFragmentId =
		kind === 'fragment' ? (fragmentId ?? uid) : fragmentId;

	const descriptor: AnchorDescriptor = {
		uid,
		kind,
		...(layerType !== undefined && { layerType }),
		...(resolvedFragmentId !== undefined && {
			fragmentId: resolvedFragmentId,
		}),
		...(shared !== undefined && { shared }),
		...(scopeOrder !== undefined && { scopeOrder }),
		...(scopeUid !== undefined && { scopeUid }),
	};

	// Commit-phase registration — never during render.
	useEffect(() => {
		if (!active) {
			return;
		}
		sync.registerAnchor(descriptor);
		return () => {
			sync.unregisterAnchor(uid);
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [
		active,
		sync,
		uid,
		kind,
		layerType,
		fragmentId,
		shared,
		scopeOrder,
		scopeUid,
	]);

	const element = useMemo(
		() =>
			active ? (
				<VtmAnchorView
					uid={uid}
					pointerEvents="none"
					style={ANCHOR_STYLE}
				/>
			) : null,
		[active, uid]
	);

	return { uid, element };
};

export default useLayerAnchor;
