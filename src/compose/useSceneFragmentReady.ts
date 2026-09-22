/**
 * useSceneFragmentReady — reactive check that a grouped entry's owning
 * fragment is present in the committed plan (owner anchor walked + an entry
 * of that type declared).
 *
 * Grouped entries (LayerPath/LayerShape/Marker inside a SharedLayer wrapper)
 * compute their atomic-add order hint via `scene.planWithResolved(entryUid)`,
 * but that only yields the fragment once the owner anchor has been walked
 * (useLayerAnchor registers via a passive effect, then the walk is debounced +
 * async). Gating creation on the fragment actually being in the committed plan
 * means the create effect re-runs once the walk lands, so the order hint is
 * correct on first mount instead of empty (transient wrong-z, previously
 * self-healed only by the debounced reorder).
 */

import { useCallback, useContext, useSyncExternalStore } from 'react';
import MapHandleContext from '../context/MapHandleContext';
import { fragmentUuidFor } from '../scene/ids';

const useSceneFragmentReady = (
	fragmentId: string | null,
	layerType: string
): boolean => {
	const { scene } = useContext(MapHandleContext);

	const subscribe = useCallback(
		(listener: () => void) => scene.subscribe(listener),
		[scene]
	);
	const getSnapshot = useCallback(() => {
		if (fragmentId === null) {
			// Not grouped — the fragment gate doesn't apply.
			return true;
		}
		return scene.hasFragment(fragmentUuidFor(fragmentId, layerType));
	}, [
		scene,
		fragmentId,
		layerType,
	]);

	return useSyncExternalStore(subscribe, getSnapshot);
};

export default useSceneFragmentReady;
