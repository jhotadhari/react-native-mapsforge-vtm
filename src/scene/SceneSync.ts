/**
 * SceneSync — the presenter that drives the LayerScene from committed data
 * and applies the resulting plan to the native side.
 *
 * Responsibilities:
 * - walk scheduling (debounced, on anchor registration changes and native
 *   onAnchorsChanged signals) → scene.applyWalk(committed sequence)
 * - plan diffing against the last applied plan (single-flight, retry on
 *   failure) → reorderLayers with the complete absolute uuid list
 * - scene mutation observation: any commit-phase mutation schedules a sync
 *
 * All native calls are fire-and-forget from the JS side; the class never
 * blocks and never mutates anything during React render.
 */

import NativeMapContainer from '../NativeModules/NativeMapContainer';
import { LayerScene } from './LayerScene';
import { PriorityAllocator } from './PriorityAllocator';
import { diffPlans, type PlanDiff } from './planDiff';
import { entryPriorityHandlers } from './priorityHandlers';
import type { AnchorDescriptor, LayerPlan } from './types';

const DEBOUNCE_MS = 16;
const MAX_WAIT_MS = 250;
const WALK_RETRY_MS = 250;
const MAX_WALK_RETRIES = 10;

type Debouncer = {
	schedule: () => void;
	cancel: () => void;
};

const createDebouncer = (run: () => void): Debouncer => {
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	let maxWaitTimer: ReturnType<typeof setTimeout> | null = null;
	// True while a scheduled run has not been consumed by either the
	// debounce or the max-wait timer — the max-wait fires only when the
	// burst is still pending, never a second time after the debounce
	// already flushed.
	let maxWaitPending = false;

	const fire = () => {
		if (debounceTimer !== null) {
			clearTimeout(debounceTimer);
			debounceTimer = null;
		}
		maxWaitPending = false;
		run();
	};

	return {
		schedule: () => {
			if (debounceTimer !== null) {
				clearTimeout(debounceTimer);
			}
			debounceTimer = setTimeout(() => {
				debounceTimer = null;
				maxWaitPending = false;
				run();
			}, DEBOUNCE_MS);
			// Armed once per burst — a sustained mutation stream can never
			// defer a flush indefinitely.
			if (maxWaitTimer === null) {
				maxWaitTimer = setTimeout(() => {
					maxWaitTimer = null;
					if (maxWaitPending) {
						fire();
					}
				}, MAX_WAIT_MS);
			}
			maxWaitPending = true;
		},
		cancel: () => {
			if (debounceTimer !== null) {
				clearTimeout(debounceTimer);
				debounceTimer = null;
			}
			if (maxWaitTimer !== null) {
				clearTimeout(maxWaitTimer);
				maxWaitTimer = null;
			}
			maxWaitPending = false;
		},
	};
};

export class SceneSync {
	private readonly scene = new LayerScene();
	private readonly allocator = new PriorityAllocator();
	private readonly descriptors = new Map<string, AnchorDescriptor>();
	private nativeNodeHandle: number | null = null;
	private lastPlan: LayerPlan = this.scene.plan();
	private syncInFlight = false;
	private destroyed = false;
	private walkRetryTimer: ReturnType<typeof setTimeout> | null = null;
	private walkFailures = 0;

	private readonly walkDebouncer: Debouncer;
	private readonly syncDebouncer: Debouncer;

	constructor() {
		this.walkDebouncer = createDebouncer(this.walk);
		this.syncDebouncer = createDebouncer(this.sync);
		this.scene.subscribe(() => this.syncDebouncer.schedule());
	}

	getScene(): LayerScene {
		return this.scene;
	}

	setNativeNodeHandle(handle: number | null): void {
		if (this.nativeNodeHandle === handle) {
			return;
		}
		this.nativeNodeHandle = handle;
		if (handle !== null) {
			this.scheduleWalk();
		}
	}

	/** Registers the anchor descriptor for a mounted component (commit phase). */
	registerAnchor(descriptor: AnchorDescriptor): void {
		this.descriptors.set(descriptor.uid, descriptor);
		this.scheduleWalk();
	}

	unregisterAnchor(uid: string): void {
		if (this.descriptors.delete(uid)) {
			this.scheduleWalk();
		}
	}

	scheduleWalk(): void {
		this.walkFailures = 0;
		this.walkDebouncer.schedule();
	}

	/** Call when the map view is destroyed — cancels all pending timers. */
	destroy(): void {
		this.destroyed = true;
		this.walkDebouncer.cancel();
		this.syncDebouncer.cancel();
		if (this.walkRetryTimer !== null) {
			clearTimeout(this.walkRetryTimer);
			this.walkRetryTimer = null;
		}
		this.descriptors.clear();
	}

	private walk = (): void => {
		const handle = this.nativeNodeHandle;
		if (handle === null || this.destroyed) {
			return;
		}
		NativeMapContainer.enumerateAnchors({ nativeNodeHandle: handle })
			.then(({ anchors }) => {
				if (this.destroyed) {
					return;
				}
				this.walkFailures = 0;
				if (this.walkRetryTimer !== null) {
					clearTimeout(this.walkRetryTimer);
					this.walkRetryTimer = null;
				}
				const sequence: AnchorDescriptor[] = [];
				for (const uid of anchors) {
					const descriptor = this.descriptors.get(uid);
					if (descriptor) {
						sequence.push(descriptor);
					}
				}
				this.scene.applyWalk(sequence);
			})
			.catch(() => {
				// enumerateAnchors failed (early mount, teardown race).
				// Retry with a small backoff — a permanently failed walk
				// would leave the scene unsynced and keep standalone
				// creation gated forever. Give up after a streak of
				// failures until the next explicit scheduleWalk.
				if (this.destroyed || this.nativeNodeHandle === null) {
					return;
				}
				this.walkFailures++;
				if (this.walkFailures > MAX_WALK_RETRIES) {
					return;
				}
				if (this.walkRetryTimer !== null) {
					clearTimeout(this.walkRetryTimer);
				}
				this.walkRetryTimer = setTimeout(this.walk, WALK_RETRY_MS);
			});
	};

	private sync = (): void => {
		if (this.syncInFlight || this.destroyed) {
			return;
		}
		const handle = this.nativeNodeHandle;
		if (handle === null) {
			return;
		}

		const plan = this.scene.plan();
		const diff = diffPlans(this.lastPlan, plan, this.allocator);
		const hasLayerWork =
			diff.layerOrderChanged ||
			diff.addedUuids.length > 0 ||
			diff.removedUuids.length > 0;

		if (!hasLayerWork && diff.entryPriorityChanges.size === 0) {
			this.lastPlan = plan;
			return;
		}

		this.syncInFlight = true;
		const startedAt = this.scene.commandLog().length;

		// Entry priorities are independent of the layer stack — fire them
		// alongside the reorder. Best-effort: on failure the allocator state
		// has already advanced, so a missed update is re-sent on the next
		// mutation (drawable order may lag one mutation for that fragment).
		this.applyEntryPriorityChanges(handle, plan, diff);

		const reorderPromise = hasLayerWork
			? NativeMapContainer.reorderLayers({
					nativeNodeHandle: handle,
					layerUuids: diff.orderedUuids,
				})
			: Promise.resolve();

		reorderPromise
			.then(() => {
				this.syncInFlight = false;
				this.lastPlan = plan;
				// Mutations landed while the reorder was in-flight —
				// re-sync with the freshest plan.
				if (this.scene.commandLog().length !== startedAt) {
					this.syncDebouncer.schedule();
				}
			})
			.catch(() => {
				// Don't update lastPlan — the next sync retries.
				this.syncInFlight = false;
				this.syncDebouncer.schedule();
			});
	};

	private applyEntryPriorityChanges(
		handle: number,
		plan: LayerPlan,
		diff: PlanDiff
	): void {
		for (const [fragmentUuid, assignments] of diff.entryPriorityChanges) {
			const fragment = plan.fragments.find(
				(f) => f.uuid === fragmentUuid
			);
			if (!fragment) {
				continue;
			}
			const handler = entryPriorityHandlers.get(fragment.layerType);
			if (!handler) {
				// Unknown layer type (third-party extension without a
				// registered handler) — skipped until it registers one.
				continue;
			}
			handler({
				nativeNodeHandle: handle,
				fragmentUuid,
				assignments: [...assignments.entries()].map(
					([uuid, priority]) => ({ uuid, priority })
				),
			}).catch(() => {
				// Best-effort — see comment at the call site.
			});
		}
	}
}
