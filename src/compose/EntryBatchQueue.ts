/**
 * Generic entry-batching queue — collapses N individual entry create/remove
 * bridge calls into one createMany + one removeMany call per frame.
 *
 * Instantiated per layer type (paths, shapes, markers) with the two batch
 * calls and a per-item result resolver. The queue is keyed by
 * nativeNodeHandle; flushed on the microtask boundary with a 16ms safety
 * max-wait. JS is single-threaded, so no locking is needed.
 */

export type BatchResultItem = {
	uuid: string;
	error?: string;
	[key: string]: unknown;
};

export type EntryBatchQueueSpec<
	TItem,
	TCreateResult,
	TBatchResult extends { uuid: string; error?: string } = BatchResultItem,
> = {
	createMany: (
		nativeNodeHandle: number,
		items: TItem[]
	) => Promise<{ results: ReadonlyArray<TBatchResult> }>;
	removeMany: (
		nativeNodeHandle: number,
		uuids: string[]
	) => Promise<{ results: ReadonlyArray<{ uuid: string; error?: string }> }>;
	resolveCreate: (
		result: TBatchResult,
		params: TItem,
		nativeNodeHandle: number
	) => TCreateResult;
};

export type EntryBatchQueue<TItem, TCreateResult> = {
	enqueueCreate: (params: TItem) => Promise<TCreateResult>;
	enqueueRemove: (nativeNodeHandle: number, uuid: string) => Promise<string>;
	drainQueue: (nativeNodeHandle: number) => void;
};

interface QueuedCreate<TItem, TCreateResult> {
	type: 'create';
	params: TItem;
	resolve: (value: TCreateResult) => void;
	reject: (reason: unknown) => void;
}

interface QueuedRemove {
	type: 'remove';
	nativeNodeHandle: number;
	uuid: string;
	resolve: (value: string) => void;
	reject: (reason: unknown) => void;
}

interface PerHandleQueue<TItem, TCreateResult> {
	creates: QueuedCreate<TItem, TCreateResult>[];
	removes: QueuedRemove[];
	flushScheduled: boolean;
	maxWaitTimer: ReturnType<typeof setTimeout> | null;
}

const MAX_WAIT_MS = 16;

export const createEntryBatchQueue = <
	TItem extends { nativeNodeHandle: number },
	TCreateResult,
	TBatchResult extends { uuid: string; error?: string } = BatchResultItem,
>(
	spec: EntryBatchQueueSpec<TItem, TCreateResult, TBatchResult>
): EntryBatchQueue<TItem, TCreateResult> => {
	const queues = new Map<number, PerHandleQueue<TItem, TCreateResult>>();

	const getQueue = (
		nativeNodeHandle: number
	): PerHandleQueue<TItem, TCreateResult> => {
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
	};

	const flush = (nativeNodeHandle: number): void => {
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

		if (pendingCreates.length > 0) {
			spec.createMany(
				nativeNodeHandle,
				pendingCreates.map((op) => op.params)
			)
				.then(({ results }) => {
					const len = Math.min(pendingCreates.length, results.length);
					for (let i = 0; i < len; i++) {
						const op = pendingCreates[i]!;
						const result = results[i]!;
						if (result.error) {
							op.reject(new Error(result.error));
							continue;
						}
						try {
							op.resolve(
								spec.resolveCreate(
									result,
									op.params,
									nativeNodeHandle
								)
							);
						} catch (resolveError) {
							// A resolver that throws (e.g. missing response
							// payload) must reject THIS op — otherwise the
							// op's promise would hang forever and the entry
							// would become a zombie.
							op.reject(resolveError);
						}
					}
					for (let i = len; i < pendingCreates.length; i++) {
						pendingCreates[i]!.reject(
							new Error(
								'Entry creation result missing in batch response'
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

		if (pendingRemoves.length > 0) {
			spec.removeMany(
				nativeNodeHandle,
				pendingRemoves.map((op) => op.uuid)
			)
				.then(({ results }) => {
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
								'Entry removal result missing in batch response'
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
	};

	const scheduleFlush = (nativeNodeHandle: number): void => {
		const queue = getQueue(nativeNodeHandle);
		if (queue.flushScheduled) {
			return;
		}
		queue.flushScheduled = true;

		// Primary flush: end of the current JS frame via microtask — all
		// effects from the current commit have fired by then.
		Promise.resolve().then(() => flush(nativeNodeHandle));

		// Safety max-wait: armed once per batch cycle.
		if (queue.maxWaitTimer === null) {
			queue.maxWaitTimer = setTimeout(() => {
				const q = queues.get(nativeNodeHandle);
				if (q) {
					// Consume the timer reference BEFORE checking pending
					// work — leaving it set would deaden the safety net
					// for every future cycle.
					q.maxWaitTimer = null;
					if (q.creates.length > 0 || q.removes.length > 0) {
						flush(nativeNodeHandle);
					}
				}
			}, MAX_WAIT_MS);
		}
	};

	return {
		enqueueCreate: (params: TItem) =>
			new Promise<TCreateResult>((resolve, reject) => {
				const queue = getQueue(params.nativeNodeHandle);
				queue.creates.push({
					type: 'create',
					params,
					resolve,
					reject,
				});
				scheduleFlush(params.nativeNodeHandle);
			}),
		enqueueRemove: (nativeNodeHandle: number, uuid: string) =>
			new Promise<string>((resolve, reject) => {
				const queue = getQueue(nativeNodeHandle);
				queue.removes.push({
					type: 'remove',
					nativeNodeHandle,
					uuid,
					resolve,
					reject,
				});
				scheduleFlush(nativeNodeHandle);
			}),
		drainQueue: (nativeNodeHandle: number) => {
			const queue = queues.get(nativeNodeHandle);
			if (!queue) {
				return;
			}
			const error = new Error('Map view destroyed');
			for (const op of queue.creates) {
				op.reject(error);
			}
			// Pending removes resolve (not reject): native teardown already
			// destroyed the shared layers the entries lived in, so the
			// remove is effectively complete — rejecting would surface a
			// spurious "Map view destroyed" onError during unmount.
			for (const op of queue.removes) {
				op.resolve(op.uuid);
			}
			if (queue.maxWaitTimer !== null) {
				clearTimeout(queue.maxWaitTimer);
			}
			queues.delete(nativeNodeHandle);
		},
	};
};
