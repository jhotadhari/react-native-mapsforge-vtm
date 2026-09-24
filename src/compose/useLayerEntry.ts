/**
 * useLayerEntry — declares a drawable entry (LayerPath / LayerShape / Marker
 * inside a fragment owner) to the scene in the commit phase, and keeps its
 * resolved native uuid attached.
 *
 * Entry order comes from the owner-injected vtmSortIndex; entries never
 * render anchors.
 */

import { useContext, useEffect, useLayoutEffect, useRef } from 'react';
import MapHandleContext from '../context/MapHandleContext';

// Module-load prefix keeps uids unique across Fast Refresh — without it a
// refreshed module restarts its counter and collides with uids from the
// previous generation (React state survives the refresh).
const uidPrefix = `${Date.now().toString(36)}_${Math.random()
	.toString(36)
	.slice(2, 8)}_`;
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
		uidRef.current = `${uidPrefix}entry_${entryUidCounter++}`;
	}
	const uid = uidRef.current;

	// Declared in the commit phase BEFORE paint (and before the owning
	// component's create effect) so the atomic-add order hint computed inside
	// the create callback sees the declared entry — otherwise the fragment is
	// missing from the hint on first create and lands appended (transient
	// wrong-z until the debounced SceneSync reorder heals it).
	useLayoutEffect(() => {
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
		// The attach mirrors the declaration: inactive (standalone) entries
		// must not attach their resolved uuid to an undeclared entry uid —
		// that would leave stale scene.uids entries (double mutation).
		if (!active || fragmentId === null) {
			// Detach any uuid attached in a previous (grouped) render so a
			// grouped→standalone transition doesn't leak a stale scene.uids
			// entry. Safe no-op when nothing was attached.
			scene.detachUuid(uid);
			return;
		}
		if (uuid) {
			scene.attachUuid(uid, uuid);
		} else {
			scene.detachUuid(uid);
		}
	}, [
		active,
		fragmentId,
		uuid,
		uid,
		scene,
	]);

	return uid;
};

export default useLayerEntry;
