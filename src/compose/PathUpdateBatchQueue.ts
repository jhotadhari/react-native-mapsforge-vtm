/**
 * PathUpdateBatchQueue — collapses N individual updateCoordinates bridge calls
 * into one updateLayers call per frame (microtask flush + 16ms max-wait).
 *
 * Straymap's zoom re-simplification fires one updateCoordinates per LayerPath;
 * with thousands of lines that is thousands of bridge crossings per zoom step.
 * This queue coalesces them into a single native batch.
 */

import LayerPathModule, {
	type UpdateCoordinatesParams,
	type LayerPathResponse,
} from '../NativeModules/NativeLayerPath';

type QueuedUpdate = {
	params: UpdateCoordinatesParams & {
		nativeNodeHandle: number;
		uuid: string;
	};
	resolve: (response: LayerPathResponse) => void;
	reject: (reason: unknown) => void;
};

interface PerHandleQueue {
	pending: QueuedUpdate[];
	flushScheduled: boolean;
	maxWaitTimer: ReturnType<typeof setTimeout> | null;
}

const MAX_WAIT_MS = 16;

const queues = new Map<number, PerHandleQueue>();

const getQueue = (nativeNodeHandle: number): PerHandleQueue => {
	let queue = queues.get(nativeNodeHandle);
	if (!queue) {
		queue = {
			pending: [],
			flushScheduled: false,
			maxWaitTimer: null,
		};
		queues.set(nativeNodeHandle, queue);
	}
	return queue;
};

const flush = (nativeNodeHandle: number): void => {
	const queue = queues.get(nativeNodeHandle);
	if (!queue) {
		return;
	}
	const pending = queue.pending.splice(0, queue.pending.length);
	queue.flushScheduled = false;
	if (queue.maxWaitTimer !== null) {
		clearTimeout(queue.maxWaitTimer);
		queue.maxWaitTimer = null;
	}
	if (pending.length === 0) {
		return;
	}
	LayerPathModule.updateLayers({
		nativeNodeHandle,
		paths: pending.map((op) => op.params),
	})
		.then(({ results }) => {
			const len = Math.min(pending.length, results.length);
			for (let i = 0; i < len; i++) {
				const op = pending[i]!;
				const result = results[i]!;
				if (result.error) {
					op.reject(new Error(result.error));
					continue;
				}
				op.resolve(result.response as LayerPathResponse);
			}
			for (let i = len; i < pending.length; i++) {
				pending[i]!.reject(
					new Error('Path update result missing in batch response')
				);
			}
		})
		.catch((error: unknown) => {
			for (const op of pending) {
				op.reject(error);
			}
		});
};

const scheduleFlush = (nativeNodeHandle: number): void => {
	const queue = getQueue(nativeNodeHandle);
	if (queue.flushScheduled) {
		return;
	}
	queue.flushScheduled = true;
	Promise.resolve().then(() => flush(nativeNodeHandle));
	if (queue.maxWaitTimer === null) {
		queue.maxWaitTimer = setTimeout(() => {
			const q = queues.get(nativeNodeHandle);
			if (q) {
				q.maxWaitTimer = null;
				if (q.pending.length > 0) {
					flush(nativeNodeHandle);
				}
			}
		}, MAX_WAIT_MS);
	}
};

export const enqueueUpdatePath = (
	params: UpdateCoordinatesParams & {
		nativeNodeHandle: number;
		uuid: string;
	}
): Promise<LayerPathResponse> =>
	new Promise<LayerPathResponse>((resolve, reject) => {
		const queue = getQueue(params.nativeNodeHandle);
		queue.pending.push({ params, resolve, reject });
		scheduleFlush(params.nativeNodeHandle);
	});

export const drainPathUpdateQueue = (nativeNodeHandle: number): void => {
	const queue = queues.get(nativeNodeHandle);
	if (!queue) {
		return;
	}
	const error = new Error('Map view destroyed');
	for (const op of queue.pending) {
		op.reject(error);
	}
	if (queue.maxWaitTimer !== null) {
		clearTimeout(queue.maxWaitTimer);
	}
	queues.delete(nativeNodeHandle);
};
