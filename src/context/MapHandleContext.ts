/**
 * Map-level context handed down from MapContainer: the map view's
 * nativeNodeHandle plus the layer scene (single source of truth for layer
 * ordering) and its presenter (SceneSync).
 *
 * Nothing in this context mutates during render — the scene is only touched
 * from commit-phase effects, and SceneSync only applies plans outside React.
 */

import { createContext } from 'react';
import { LayerScene } from '../scene/LayerScene';
import { SceneSync } from '../scene/SceneSync';

export type MapHandleContextValue = {
	nativeNodeHandle: null | number;
	scene: LayerScene;
	sync: SceneSync;
};

// Inert defaults for components rendered outside a MapContainer — the scene
// stays empty and all sync operations no-op (no timers are armed until
// scheduling is requested, and every walk/sync guards on a null handle).
const inertSync = new SceneSync();

const MapHandleContext = createContext<MapHandleContextValue>({
	nativeNodeHandle: null,
	scene: inertSync.getScene(),
	sync: inertSync,
});

export default MapHandleContext;
