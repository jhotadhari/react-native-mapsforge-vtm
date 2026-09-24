/**
 * useSceneUuidBinding — binds a component's resolved native uuid to a scene
 * key (anchor uid for dedicated layers / type-run members, entry uid for
 * fragment entries handled by useLayerEntry).
 */

import { useContext, useEffect } from 'react';
import MapHandleContext from '../context/MapHandleContext';

const useSceneUuidBinding = (
	key: string | null,
	uuid: null | false | string
): void => {
	const { scene } = useContext(MapHandleContext);

	useEffect(() => {
		if (key === null) {
			return;
		}
		if (uuid) {
			scene.attachUuid(key, uuid);
		} else {
			scene.detachUuid(key);
		}
	}, [
		key,
		uuid,
		scene,
	]);
};

export default useSceneUuidBinding;
