/**
 * External dependencies
 */
import { useContext, useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';

/**
 * Per-component debug entry describing one mounted layer component and its
 * relationship to the native layer tree.
 */
export interface LayerDebugEntry {
	/** Stable Symbol identity assigned by useLayerOrder. */
	symbol: symbol;
	/** Zero-based position in JSX document order across all managed layers. */
	positionIndex: number;
	/**
	 * Layer type string ('path', 'marker', 'shape', 'mapsforge', etc.), or
	 * null when the component called useLayerOrder without a layerType.
	 */
	layerType: string | null;
	/**
	 * Resolved native layer uuid, or null when the native createLayer call
	 * has not yet resolved (uuid is null/false during the lifecycle).
	 */
	uuid: string | null;
	/**
	 * Shared-layer fragment uuid (e.g. "__vtm_shared_path__1"), or null for
	 * components that use dedicated native layers.
	 */
	fragmentUuid: string | null;
	/** True when this component is part of a shared native layer fragment. */
	isShared: boolean;
	/** True when the native createLayer call has resolved with a real uuid. */
	isResolved: boolean;
	/**
	 * How many JS components share this entry's native fragment. For shared
	 * entries this is ≥ 2; for dedicated entries this is 1.
	 */
	fragmentMemberCount: number;
}

/**
 * Aggregate debug snapshot of the JS-managed layer tree for a single map view.
 */
export interface LayerDebugInfo {
	/** Every mounted layer component, in JSX document order. */
	layers: LayerDebugEntry[];
	/** Total number of mounted layer components. */
	layerCount: number;
	/** Number of unique native shared-layer fragments across all types. */
	sharedFragmentCount: number;
	/** Number of components that own a dedicated native layer. */
	dedicatedLayerCount: number;
	/**
	 * Estimated total native vtm Layer objects: shared fragments + dedicated
	 * layers. This is the number of GPU draw calls for layer rendering.
	 */
	estimatedNativeLayerCount: number;
	/** Current SharedLayer nesting depth (0 = no grouping active). */
	groupingDepth: number;
}

/**
 * Builds a snapshot of the registry's current state. Extracted as a standalone
 * function (rather than a closure over registry) so the hook below can call it
 * during render without worrying about stale captures.
 *
 * @param registry - The mutable registry to snapshot.
 * @returns A snapshot of the registry's current state.
 */
export const buildSnapshot = (
	registry: import('../context/MapHandleContext').LayerOrderRegistry
): LayerDebugInfo => {
	const layers: LayerDebugEntry[] = [];
	const fragmentMemberCounts = new Map<string, number>();
	let dedicatedCount = 0;

	// First pass: count members per fragment and dedicated layers.
	for (const id of registry.order) {
		const fragmentUuid = registry.fragmentUuids.get(id);
		if (fragmentUuid) {
			fragmentMemberCounts.set(
				fragmentUuid,
				(fragmentMemberCounts.get(fragmentUuid) ?? 0) + 1
			);
		} else {
			dedicatedCount++;
		}
	}

	// Second pass: build entries with fragment member counts attached.
	const seenFragmentUuids = new Set<string>();

	for (const id of registry.order) {
		const uuidValue = registry.uuids.get(id);
		const fragmentUuid = registry.fragmentUuids.get(id);
		const layerType = registry.layerTypes.get(id);

		const entry: LayerDebugEntry = {
			symbol: id,
			positionIndex: layers.length,
			layerType: layerType ?? null,
			uuid: typeof uuidValue === 'string' ? uuidValue : null,
			fragmentUuid: fragmentUuid ?? null,
			isShared: !!fragmentUuid,
			isResolved: typeof uuidValue === 'string',
			fragmentMemberCount: fragmentUuid
				? (fragmentMemberCounts.get(fragmentUuid) ?? 0)
				: 1,
		};
		layers.push(entry);

		if (fragmentUuid) {
			seenFragmentUuids.add(fragmentUuid);
		}
	}

	return {
		layers,
		layerCount: layers.length,
		sharedFragmentCount: seenFragmentUuids.size,
		dedicatedLayerCount: dedicatedCount,
		estimatedNativeLayerCount: seenFragmentUuids.size + dedicatedCount,
		groupingDepth: registry.groupingDepth,
	};
};

/**
 * Subscribes to the {@link LayerOrderRegistry} and returns a live snapshot of
 * every mounted layer component — its type, render order, native uuid, and
 * shared-layer fragment assignment.
 *
 * Instead of storing snapshots in React state (which can lag behind the source
 * of truth), the snapshot is **derived during render** from the mutable
 * registry. A lightweight subscription tick counter ensures the component
 * re-renders whenever the registry mutates (layer mount / uuid resolve /
 * unmount / SharedLayer grouping change), so {@code buildSnapshot} always
 * reads the latest data.
 *
 * When called outside a {@link MapHandleContext} provider, returns an empty
 * snapshot (layerCount = 0).
 */
export const useLayerDebugInfo = (): LayerDebugInfo => {
	const { registry } = useContext(MapHandleContext);

	// Tick counter: incremented on every registry notification to force a
	// re-render. The actual snapshot is computed during render below — never
	// stored, so it can never be stale.
	const [tick, setTick] = useState(0);

	useEffect(() => {
		// Catch up in case a mutation landed between mount and this effect.
		setTick((t) => t + 1);

		return registry.subscribe(() => {
			setTick((t) => t + 1);
		});
	}, [registry]);

	// Derive fresh during every render. Computing every render is cheap
	// (the registry always has at most a few hundred entries) and guarantees
	// the debug overlay can never display stale data. The `tick` reference
	// forces re-computation when the subscription fires; `buildSnapshot` also
	// runs on unrelated re-renders, which is harmless.
	tick;
	return buildSnapshot(registry);
};
