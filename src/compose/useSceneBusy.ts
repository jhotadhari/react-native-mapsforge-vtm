/**
 * useSceneBusy — reactive signal that the layer-scene presenter has pending
 * or in-flight ordering work (walk, sync, entry-priority commits, or unapplied
 * scene mutations).
 *
 * Consumers (e.g. an app-level loading bar) subscribe to the map's SceneSync
 * busy flag and re-render only on true↔false transitions — not on every scene
 * mutation. Render this from a component inside <MapContainer> (it reads
 * MapHandleContext).
 */

import { useCallback, useContext, useSyncExternalStore } from 'react';
import MapHandleContext from '../context/MapHandleContext';

const useSceneBusy = (): boolean => {
	const { sync } = useContext(MapHandleContext);

	const subscribe = useCallback(
		(listener: () => void) => sync.subscribeBusy(listener),
		[sync]
	);
	const getSnapshot = useCallback(() => sync.isBusy(), [sync]);

	return useSyncExternalStore(subscribe, getSnapshot);
};

export default useSceneBusy;
