/**
 * ShapeBatchQueue — collapses N individual shape create/remove bridge calls
 * into one createLayers + one removeLayers call per frame, via the generic
 * entry-batching helper.
 */

import LayerShapeModule, {
	type CreateLayerParams,
	type LayerShapeResponse,
	type ShapeBatchResultItem,
} from '../NativeModules/NativeLayerShape';
import { createEntryBatchQueue } from './EntryBatchQueue';

const queue = createEntryBatchQueue<
	CreateLayerParams,
	LayerShapeResponse,
	ShapeBatchResultItem
>({
	createMany: (nativeNodeHandle, shapes) =>
		LayerShapeModule.createLayers({ nativeNodeHandle, shapes }),
	removeMany: (nativeNodeHandle, uuids) =>
		LayerShapeModule.removeLayers({ nativeNodeHandle, uuids }),
	resolveCreate: (result) => {
		if (!result.response?.uuid) {
			// A missing response payload (or one without a uuid) means the
			// native side failed to report the created entry — resolving with
			// undefined would produce a phantom uuid and a zombie entry.
			// Reject instead.
			throw new Error('Shape create response missing in batch result');
		}
		return result.response as LayerShapeResponse;
	},
});

export const enqueueCreateShape = queue.enqueueCreate;
export const enqueueRemoveShape = queue.enqueueRemove;
export const drainShapeQueue = queue.drainQueue;
