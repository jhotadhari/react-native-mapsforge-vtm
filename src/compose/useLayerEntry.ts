/**
 * useLayerEntry — declares a drawable entry (LayerPath / LayerShape / Marker
 * inside a fragment owner) to the scene in the commit phase, and keeps its
 * resolved native uuid attached.
 *
 * Entry order comes from the owner-injected vtmSortIndex; entries never
 * render anchors.
 */

import { useContext, useEffect, useRef } from 'react';
import MapHandleContext from '../context/MapHandleContext';

let entryUidCounter = 0;

export type UseLayerEntryOptions = {
	/** When false nothing is declared (component is standalone/anchored). */
	active: boolean;
	/** Owner id — SharedLayer wrapper id or LayerMarker uid; null = none. */
	fragmentId: string | null;
	layerType: string;
	/** Owner-injected position among sibling entries. */
	sortIndex?: number;
	uuid: null | false | string;
};

const useLayerEntry = ({
	active,
	fragmentId,
	layerType,
	sortIndex,
	uuid,
}: UseLayerEntryOptions): string => {
	const { scene } = useContext(MapHandleContext);

	const uidRef = useRef<string | null>(null);
	if (uidRef.current === null) {
		uidRef.current = `entry_${entryUidCounter++}`;
	}
	const uid = uidRef.current;

	useEffect(() => {
		if (!active || fragmentId === null) {
			return;
		}
		scene.declareEntry({
			uid,
			fragmentId,
			layerType,
			...(sortIndex !== undefined && { sortIndex }),
		});
		return () => {
			scene.undeclareEntry(uid);
		};
	}, [
		active,
		fragmentId,
		layerType,
		sortIndex,
		uid,
		scene,
	]);

	useEffect(() => {
		if (uuid) {
			scene.attachUuid(uid, uuid);
		} else {
			scene.detachUuid(uid);
		}
	}, [
		uuid,
		uid,
		scene,
	]);

	return uid;
};

export default useLayerEntry;
