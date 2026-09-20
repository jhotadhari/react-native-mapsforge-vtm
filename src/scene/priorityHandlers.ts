/**
 * Entry-priority handler registry — maps a fragment's layerType to the
 * native module call that applies sparse drawable priorities.
 *
 * The core registers handlers for the three built-in fragment types; third-
 * party layer-type libraries (e.g. ext-path-color-ramp) register their own
 * via {@link registerEntryPriorityHandler}.
 */

import NativeLayerMarker from '../NativeModules/NativeLayerMarker';
import NativeLayerPath from '../NativeModules/NativeLayerPath';
import NativeLayerShape from '../NativeModules/NativeLayerShape';
import type { ApplyEntryPrioritiesParams } from '../NativeModules/NativeLayerPath';

export type EntryPriorityHandler = (
	params: ApplyEntryPrioritiesParams
) => Promise<void>;

export const entryPriorityHandlers = new Map<string, EntryPriorityHandler>();

entryPriorityHandlers.set('path', (params) =>
	NativeLayerPath.applyEntryPriorities(params)
);
entryPriorityHandlers.set('shape', (params) =>
	NativeLayerShape.applyEntryPriorities(params)
);
entryPriorityHandlers.set('marker', (params) =>
	NativeLayerMarker.applyEntryPriorities(params)
);

export const registerEntryPriorityHandler = (
	layerType: string,
	handler: EntryPriorityHandler
): void => {
	entryPriorityHandlers.set(layerType, handler);
};
