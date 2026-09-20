/**
 * MarkerBatchQueue — collapses N individual marker create/remove bridge
 * calls into one createMarkers + one removeMarkers call per frame, via the
 * generic entry-batching helper.
 *
 * Lifecycle: queue objects are created lazily per nativeNodeHandle and
 * garbage collected when the map is destroyed. Call {@link drainQueue} on
 * map destruction to reject all pending operations.
 */

import LayerMarkerModule from '../NativeModules/NativeLayerMarker';
import type {
	CreateMarkerParams,
	MarkerBatchResultItem,
	MarkerResponse,
} from '../NativeModules/NativeLayerMarker';
import { createEntryBatchQueue } from './EntryBatchQueue';

const queue = createEntryBatchQueue<
	CreateMarkerParams,
	MarkerResponse,
	MarkerBatchResultItem
>({
	createMany: (nativeNodeHandle, markers) =>
		LayerMarkerModule.createMarkers({ nativeNodeHandle, markers }),
	removeMany: (nativeNodeHandle, markerUuids) =>
		LayerMarkerModule.removeMarkers({ nativeNodeHandle, markerUuids }),
	resolveCreate: (result, _params, nativeNodeHandle) => ({
		uuid: result.uuid,
		index: result.index as number,
		nativeNodeHandle,
	}),
});

export const enqueueCreateMarker = queue.enqueueCreate;
export const enqueueRemoveMarker = queue.enqueueRemove;
export const drainQueue = queue.drainQueue;
