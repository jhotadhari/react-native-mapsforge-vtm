/**
 * Plan diffing: (previous plan, next plan) → the minimal native sync
 * payload. Includes entry-priority assignments via a per-scene
 * PriorityAllocator so only changed entries are re-prioritized.
 */

import type { LayerPlan } from './types';
import { PriorityAllocator } from './PriorityAllocator';

export type PlanDiff = {
	/** The complete desired bottom→top native uuid list (resolved only). */
	orderedUuids: string[];
	layerOrderChanged: boolean;
	removedUuids: string[];
	addedUuids: string[];
	/** fragment native uuid → (entry uid → sparse priority), changed only. */
	entryPriorityChanges: Map<string, Map<string, number>>;
};

export const diffPlans = (
	prev: LayerPlan,
	next: LayerPlan,
	allocator: PriorityAllocator
): PlanDiff => {
	const prevUuids = prev.layers.map((layer) => layer.uuid);
	const nextUuids = next.layers.map((layer) => layer.uuid);

	const prevSet = new Set(prevUuids);
	const nextSet = new Set(nextUuids);

	const removedUuids = prevUuids.filter((uuid) => !nextSet.has(uuid));
	const addedUuids = nextUuids.filter((uuid) => !prevSet.has(uuid));
	const layerOrderChanged =
		prevUuids.length !== nextUuids.length ||
		prevUuids.some((uuid, i) => uuid !== nextUuids[i]);

	const entryPriorityChanges = new Map<string, Map<string, number>>();
	const nextFragmentUuids = new Set<string>();

	for (const fragment of next.fragments) {
		nextFragmentUuids.add(fragment.uuid);
		if (fragment.resolvedEntryUids.length > 0) {
			const changed = allocator.changedFor(
				fragment.uuid,
				fragment.resolvedEntryUids
			);
			if (changed.size > 0) {
				entryPriorityChanges.set(fragment.uuid, changed);
			}
		}
	}

	// Forget destroyed fragments so their allocator state doesn't leak.
	for (const fragment of prev.fragments) {
		if (!nextFragmentUuids.has(fragment.uuid)) {
			allocator.forget(fragment.uuid);
		}
	}

	return {
		orderedUuids: nextUuids,
		layerOrderChanged,
		removedUuids,
		addedUuids,
		entryPriorityChanges,
	};
};
