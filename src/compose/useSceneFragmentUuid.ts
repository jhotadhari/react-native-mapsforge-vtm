/**
 * useSceneFragmentUuid — reads the scene-authoritative fragment uuid for a
 * standalone shared layer's anchor (type-run member) via useSyncExternalStore.
 *
 * Standalone LayerPath / LayerShape / Marker components create their native
 * entry under the run fragment uuid computed by the plan (keyed by the run's
 * first member). Waiting for this value before creating is what keeps the
 * type-run collapse alive: every member must use the SAME uuid the plan
 * ordered, never a self-keyed one.
 *
 * The store re-renders the component whenever the scene mutates (walk lands,
 * members mount/unmount), so a re-keyed run (first member removed) is picked
 * up immediately.
 */

import { useCallback, useContext, useSyncExternalStore } from 'react';
import MapHandleContext from '../context/MapHandleContext';

const useSceneFragmentUuid = (anchorUid: string | null): string | null => {
	const { scene } = useContext(MapHandleContext);

	const subscribe = useCallback(
		(listener: () => void) => scene.subscribe(listener),
		[scene]
	);
	const getSnapshot = useCallback(() => {
		if (anchorUid === null) {
			return null;
		}
		return scene.plan().runKeysByAnchor.get(anchorUid) ?? null;
	}, [scene, anchorUid]);

	return useSyncExternalStore(subscribe, getSnapshot);
};

export default useSceneFragmentUuid;
