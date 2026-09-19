/**
 * Live debug snapshot of the layer scene — expected layer-stack order,
 * fragment membership and resolved uuids, derived from the scene's plan
 * (the same source the presenter syncs to native).
 */

import { useContext, useEffect, useState } from 'react';
import MapHandleContext from '../context/MapHandleContext';
import type { LayerScene } from '../scene/LayerScene';

/**
 * Per-component debug entry describing one layer-stack item in the scene
 * plan (dedicated layer or fragment).
 */
export interface LayerDebugEntry {
	/** Position in the desired layer stack (bottom → top). */
	positionIndex: number;
	layerType: string | null;
	/** Native uuid (dedicated layer uuid or deterministic fragment uuid). */
	uuid: string | null;
	fragmentUuid: string | null;
	/** Number of entries hosted by this item's fragment (1 for dedicated). */
	fragmentMemberCount: number;
	/** True when this item is a shared fragment (memberCount > 1). */
	isShared: boolean;
	/** Plan layers are always resolved — kept for UI compatibility. */
	isResolved: boolean;
}

/**
 * Aggregate debug snapshot of the JS-managed layer tree for a single map view.
 */
export interface LayerDebugInfo {
	layers: LayerDebugEntry[];
	layerCount: number;
	sharedFragmentCount: number;
	dedicatedLayerCount: number;
	estimatedNativeLayerCount: number;
	hasGroupedFragments: boolean;
}

/**
 * Builds a snapshot from the scene's current plan. Extracted as a standalone
 * function so the hook below can call it during render.
 */
export const buildSnapshot = (scene: LayerScene): LayerDebugInfo => {
	const plan = scene.plan();

	const layers: LayerDebugEntry[] = plan.layers.map((layer, index) => {
		const fragment =
			layer.kind === 'fragment'
				? plan.fragments.find((f) => f.uuid === layer.uuid)
				: undefined;
		const memberCount = fragment ? fragment.entryUids.length : 1;
		return {
			positionIndex: index,
			layerType: layer.layerType ?? null,
			uuid: layer.uuid,
			fragmentUuid: layer.kind === 'fragment' ? layer.uuid : null,
			fragmentMemberCount: memberCount,
			isShared: memberCount > 1,
			isResolved: true,
		};
	});

	const sharedFragmentCount = plan.fragments.filter(
		(f) => f.entryUids.length > 0
	).length;

	return {
		layers,
		layerCount: layers.length,
		sharedFragmentCount,
		dedicatedLayerCount: plan.layers.filter((l) => l.kind === 'layer')
			.length,
		estimatedNativeLayerCount: plan.layers.length,
		hasGroupedFragments: plan.fragments.some((f) => f.entryUids.length > 1),
	};
};

/**
 * Subscribes to the scene and returns a live snapshot of the expected layer
 * stack. When called outside a MapHandleContext provider, returns an empty
 * snapshot.
 */
export const useLayerDebugInfo = (): LayerDebugInfo => {
	const { scene } = useContext(MapHandleContext);

	// Tick counter: incremented on every scene mutation to force a
	// re-render. The snapshot itself is derived during render — never stored,
	// so it can never be stale.
	const [tick, setTick] = useState(0);

	useEffect(() => {
		// Catch up in case a mutation landed between mount and this effect.
		setTick((t) => t + 1);

		return scene.subscribe(() => {
			setTick((t) => t + 1);
		});
	}, [scene]);

	tick;
	return buildSnapshot(scene);
};
