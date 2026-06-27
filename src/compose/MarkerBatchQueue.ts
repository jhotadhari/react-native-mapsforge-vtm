/**
 * MarkerBatchQueue
 *
 * Queues pending marker create/remove operations per nativeNodeHandle and
 * flushes them as batch bridge calls on the microtask boundary. This
 * collapses N individual createMarker/removeMarker bridge calls into
 * 1 createMarkers + 1 removeMarkers call.
 *
 * ## Flush timing
 *
 * - **Primary**: Promise.resolve().then() (fires at end of current JS frame,
 *   after all React effects from the current render commit have fired).
 * - **Safety max-wait**: setTimeout(16ms) so markers are never stuck waiting
 *   indefinitely if no microtask boundary arrives.
 *
 * ## Thread safety
 *
 * JS is single-threaded, so no locking is needed on the JS side.
 *
 * ## Error handling
 *
 * - Each marker in the batch resolves or rejects independently based on the
 *   per-result `error` field.
 * - A batch-level bridge failure (e.g. map destroyed mid-batch) rejects all
 *   pending markers in that batch.
 *
 * ## Lifecycle
 *
 * Queue objects are created lazily per nativeNodeHandle and garbage collected
 * when the map is destroyed and all references are released. Call
 * {@link drainQueue} on map destruction to reject all pending operations.
 */

import LayerMarkerModule from '../NativeModules/NativeLayerMarker';
import type {
	CreateMarkerParams,
	MarkerResponse,
	MarkerBatchResponse,
	RemoveMarkersResponse,
} from '../NativeModules/NativeLayerMarker';

// ── Internal types ─────────────────────────────────────────────

interface QueuedCreate {
	type: 'create';
	params: CreateMarkerParams;
	resolve: (value: MarkerResponse) => void;
	reject: (reason: unknown) => void;
}

interface QueuedRemove {
	type: 'remove';
	nativeNodeHandle: number;
	uuid: string;
	resolve: (value: string) => void;
	reject: (reason: unknown) => void;
}

interface PerHandleQueue {
	creates: QueuedCreate[];
	removes: QueuedRemove[];
	flushScheduled: boolean;
	maxWaitTimer: ReturnType<typeof setTimeout> | null;
}

// ── Queue registry (keyed by nativeNodeHandle) ─────────────────

const queues = new Map<number, PerHandleQueue>();
const MAX_WAIT_MS = 16;

function getQueue(nativeNodeHandle: number): PerHandleQueue {
	let queue = queues.get(nativeNodeHandle);
	if (!queue) {
		queue = {
			creates: [],
			removes: [],
			flushScheduled: false,
			maxWaitTimer: null,
		};
		queues.set(nativeNodeHandle, queue);
	}
	return queue;
}

// ── Flush logic ────────────────────────────────────────────────

function flush(nativeNodeHandle: number): void {
	const queue = queues.get(nativeNodeHandle);
	if (!queue) {
		return;
	}

	const pendingCreates = queue.creates.splice(0, queue.creates.length);
	const pendingRemoves = queue.removes.splice(0, queue.removes.length);
	queue.flushScheduled = false;
	if (queue.maxWaitTimer !== null) {
		clearTimeout(queue.maxWaitTimer);
		queue.maxWaitTimer = null;
	}

	const hasCreates = pendingCreates.length > 0;
	const hasRemoves = pendingRemoves.length > 0;

	if (!hasCreates && !hasRemoves) {
		return;
	}

	// Flush creates.
	if (hasCreates) {
		const batchParams = {
			nativeNodeHandle,
			markers: pendingCreates.map((op) => op.params),
		};

		LayerMarkerModule.createMarkers(batchParams)
			.then((batchResponse: MarkerBatchResponse) => {
				const { results } = batchResponse;
				const len = Math.min(pendingCreates.length, results.length);
				for (let i = 0; i < len; i++) {
					const op = pendingCreates[i]!;
					const result = results[i]!;
					if (result.error) {
						op.reject(new Error(result.error));
					} else {
						op.resolve({
							uuid: result.uuid,
							index: result.index,
							nativeNodeHandle,
						});
					}
				}
				for (let i = len; i < pendingCreates.length; i++) {
					pendingCreates[i]!.reject(
						new Error(
							'Marker creation result missing in batch response'
						)
					);
				}
			})
			.catch((error: unknown) => {
				for (const op of pendingCreates) {
					op.reject(error);
				}
			});
	}

	// Flush removes.
	if (hasRemoves) {
		const removeParams = {
			nativeNodeHandle,
			markerUuids: pendingRemoves.map((op) => op.uuid),
		};

		LayerMarkerModule.removeMarkers(removeParams)
			.then((batchResponse: RemoveMarkersResponse) => {
				const { results } = batchResponse;
				const len = Math.min(pendingRemoves.length, results.length);
				for (let i = 0; i < len; i++) {
					const op = pendingRemoves[i]!;
					const result = results[i]!;
					if (result.error) {
						op.reject(new Error(result.error));
					} else {
						op.resolve(result.uuid);
					}
				}
				for (let i = len; i < pendingRemoves.length; i++) {
					pendingRemoves[i]!.reject(
						new Error(
							'Marker removal result missing in batch response'
						)
					);
				}
			})
			.catch((error: unknown) => {
				for (const op of pendingRemoves) {
					op.reject(error);
				}
			});
	}
}

function scheduleFlush(nativeNodeHandle: number): void {
	const queue = getQueue(nativeNodeHandle);
	if (queue.flushScheduled) {
		return;
	}
	queue.flushScheduled = true;

	// Primary flush: end of current JS frame via microtask.
	// All Marker effects from the current render commit will have fired
	// by the time this runs, collapsing them into a single batch.

	Promise.resolve().then(() => flush(nativeNodeHandle));

	// Safety max-wait: prevent stalls if no microtask boundary arrives.
	// Only set once per batch cycle.
	if (queue.maxWaitTimer === null) {
		queue.maxWaitTimer = setTimeout(() => {
			const q = queues.get(nativeNodeHandle);
			if (q && (q.creates.length > 0 || q.removes.length > 0)) {
				flush(nativeNodeHandle);
			}
		}, MAX_WAIT_MS);
	}
}

// ── Public API ─────────────────────────────────────────────────

/**
 * Enqueues a marker creation operation. Returns a Promise that resolves
 * with the {@link MarkerResponse} when the batch completes, or rejects if
 * the individual marker fails or the entire batch fails.
 */
export function enqueueCreateMarker(
	params: CreateMarkerParams
): Promise<MarkerResponse> {
	return new Promise<MarkerResponse>((resolve, reject) => {
		const queue = getQueue(params.nativeNodeHandle);
		queue.creates.push({
			type: 'create',
			params,
			resolve,
			reject,
		});
		scheduleFlush(params.nativeNodeHandle);
	});
}

/**
 * Enqueues a marker removal operation. Returns a Promise that resolves
 * with the removed marker's uuid when the batch completes, or rejects if
 * the individual marker fails or the entire batch fails.
 */
export function enqueueRemoveMarker(
	nativeNodeHandle: number,
	uuid: string
): Promise<string> {
	return new Promise<string>((resolve, reject) => {
		const queue = getQueue(nativeNodeHandle);
		queue.removes.push({
			type: 'remove',
			nativeNodeHandle,
			uuid,
			resolve,
			reject,
		});
		scheduleFlush(nativeNodeHandle);
	});
}

/**
 * Cleans up the queue for a given nativeNodeHandle. Called when the
 * map view is destroyed. Rejects all pending operations.
 */
export function drainQueue(nativeNodeHandle: number): void {
	const queue = queues.get(nativeNodeHandle);
	if (!queue) {
		return;
	}
	const error = new Error('Map view destroyed');
	for (const op of queue.creates) {
		op.reject(error);
	}
	for (const op of queue.removes) {
		op.reject(error);
	}
	if (queue.maxWaitTimer !== null) {
		clearTimeout(queue.maxWaitTimer);
	}
	queues.delete(nativeNodeHandle);
}
