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
import { diffPlans } from './planDiff';
import type { AnchorDescriptor, LayerPlan } from './types';

const DEBOUNCE_MS = 16;
const MAX_WAIT_MS = 250;

type Debouncer = {
	schedule: () => void;
	cancel: () => void;
};

const createDebouncer = (run: () => void): Debouncer => {
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	let maxWaitTimer: ReturnType<typeof setTimeout> | null = null;

	const fire = () => {
		if (debounceTimer !== null) {
			clearTimeout(debounceTimer);
			debounceTimer = null;
		}
		run();
	};

	return {
		schedule: () => {
			if (debounceTimer !== null) {
				clearTimeout(debounceTimer);
			}
			debounceTimer = setTimeout(() => {
				debounceTimer = null;
				run();
			}, DEBOUNCE_MS);
			// Armed once per burst — a sustained mutation stream can never
			// defer a flush indefinitely.
			if (maxWaitTimer === null) {
				maxWaitTimer = setTimeout(() => {
					maxWaitTimer = null;
					fire();
				}, MAX_WAIT_MS);
			}
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
		this.walkDebouncer.schedule();
	}

	/** Call when the map view is destroyed — cancels all pending timers. */
	destroy(): void {
		this.destroyed = true;
		this.walkDebouncer.cancel();
		this.syncDebouncer.cancel();
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
				// Map torn down or wrapper gone — nothing to sync against.
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
		NativeMapContainer.reorderLayers({
			nativeNodeHandle: handle,
			layerUuids: diff.orderedUuids,
		})
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
}
