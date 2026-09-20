/**
 * PathBatchQueue — collapses N individual path create/remove bridge calls
 * into one createLayers + one removeLayers call per frame, via the generic
 * entry-batching helper.
 */

import LayerPathModule, {
	type CreateLayerParams,
	type LayerPathResponse,
	type PathBatchResultItem,
} from '../NativeModules/NativeLayerPath';
import { createEntryBatchQueue } from './EntryBatchQueue';

const queue = createEntryBatchQueue<
	CreateLayerParams,
	LayerPathResponse,
	PathBatchResultItem
>({
	createMany: (nativeNodeHandle, paths) =>
		LayerPathModule.createLayers({ nativeNodeHandle, paths }),
	removeMany: (nativeNodeHandle, uuids) =>
		LayerPathModule.removeLayers({ nativeNodeHandle, uuids }),
	resolveCreate: (result) => {
		if (!result.response) {
			// A missing response payload means the native side failed to
			// report the created entry — resolving with undefined would
			// produce a phantom uuid and a zombie entry. Reject instead.
			throw new Error('Path create response missing in batch result');
		}
		return result.response as LayerPathResponse;
	},
});

export const enqueueCreatePath = queue.enqueueCreate;
export const enqueueRemovePath = queue.enqueueRemove;
export const drainPathQueue = queue.drainQueue;
