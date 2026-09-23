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
const MAX_WALK_RETRIES = 10;
const WALK_RETRY_BASE_MS = 250;
const WALK_RETRY_MAX_MS = 4000;
const MAX_REORDER_RETRIES = 5;
const REORDER_RETRY_BASE_MS = 250;
const REORDER_RETRY_MAX_MS = 4000;

type Debouncer = {
	schedule: () => void;
	cancel: () => void;
	/** True while a scheduled run has not yet been consumed by the debounce
	 * or max-wait timer (i.e. a mutation arrived and the run is still
	 * pending). */
	isPending: () => boolean;
};

const createDebouncer = (run: () => void): Debouncer => {
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	let maxWaitTimer: ReturnType<typeof setTimeout> | null = null;
	// True while a scheduled run has not been consumed by either the
	// debounce or the max-wait timer — the max-wait fires only when the
	// burst is still pending, never a second time after the debounce
	// already flushed.
	let runPending = false;

	const fire = () => {
		if (debounceTimer !== null) {
			clearTimeout(debounceTimer);
			debounceTimer = null;
		}
		runPending = false;
		run();
	};

	return {
		schedule: () => {
			if (debounceTimer !== null) {
				clearTimeout(debounceTimer);
			}
			debounceTimer = setTimeout(() => {
				debounceTimer = null;
				runPending = false;
				run();
			}, DEBOUNCE_MS);
			// Armed once per burst — a sustained mutation stream can never
			// defer a flush indefinitely.
			if (maxWaitTimer === null) {
				maxWaitTimer = setTimeout(() => {
					maxWaitTimer = null;
					if (runPending) {
						fire();
					}
				}, MAX_WAIT_MS);
			}
			runPending = true;
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
			runPending = false;
		},
		isPending: () => runPending,
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
	private walkSeq = 0;
	private walkInFlight = false;
	private walkRequested = false;
	private reorderRetryTimer: ReturnType<typeof setTimeout> | null = null;
	private reorderFailures = 0;
	/** Generation counter invalidated on destroy() — a stale reorder resolution
	 * from a previous lifecycle must not clobber post-re-arm state (mirrors walkSeq). */
	private reorderSeq = 0;
	private lastAttemptVersion = -1;
	/** Fragment uuid → computed assignment awaiting native confirmation. */
	private pendingPriorityCommits = new Map<string, Map<string, number>>();
	/** Scene version whose state is fully applied to native (lastPlan). */
	private lastAppliedVersion = 0;
	/** True while ordering work is pending or in-flight (see isBusy). */
	private busy = false;
	private busyListeners = new Set<() => void>();

	private readonly walkDebouncer: Debouncer;
	private readonly syncDebouncer: Debouncer;

	constructor() {
		this.walkDebouncer = createDebouncer(this.walk);
		this.syncDebouncer = createDebouncer(this.sync);
		this.scene.subscribe(() => {
			this.syncDebouncer.schedule();
			this.refreshBusy();
		});
	}

	getScene(): LayerScene {
		return this.scene;
	}

	/**
	 * True while the presenter has ordering work to apply: a walk or sync is
	 * scheduled (debounced) or in-flight, entry priorities are awaiting native
	 * confirmation, or the scene has mutated since the last applied plan.
	 */
	isBusy(): boolean {
		return this.busy;
	}

	subscribeBusy(listener: () => void): () => void {
		this.busyListeners.add(listener);
		return () => {
			this.busyListeners.delete(listener);
		};
	}

	/** Recomputes the busy flag and notifies listeners on a true↔false
	 * transition. Called from every lifecycle point that can change it. */
	private refreshBusy(): void {
		const next =
			this.walkDebouncer.isPending() ||
			this.walkInFlight ||
			this.walkRetryTimer !== null ||
			this.syncDebouncer.isPending() ||
			this.syncInFlight ||
			this.pendingPriorityCommits.size > 0 ||
			this.scene.version() !== this.lastAppliedVersion;
		if (next !== this.busy) {
			this.busy = next;
			this.busyListeners.forEach((listener) => listener());
		}
	}

	setNativeNodeHandle(handle: number | null): void {
		const wasDestroyed = this.destroyed;
		if (handle !== null) {
			// StrictMode mount → cleanup → mount reuses the same handle —
			// re-arm the presenter so the second mount walks again.
			this.destroyed = false;
		}
		if (this.nativeNodeHandle === handle && !wasDestroyed) {
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
		this.refreshBusy();
	}

	/** Call when the map view is destroyed — cancels all pending timers. */
	destroy(): void {
		this.destroyed = true;
		this.walkDebouncer.cancel();
		this.syncDebouncer.cancel();
		// Invalidate in-flight walk results and pending requests — they
		// belong to the previous lifecycle.
		this.walkSeq++;
		this.walkRequested = false;
		this.reorderSeq++;
		// Reset in-flight/last-applied state so a StrictMode re-arm (or a
		// hung in-flight promise) can't gate the presenter permanently, and
		// the first post-re-arm sync diffs against a clean baseline.
		this.syncInFlight = false;
		this.walkInFlight = false;
		// Establish a genuinely clean baseline: clear the scene (walk/entries/
		// uuids) so the first post-re-arm sync diffs against an empty plan.
		this.scene.clear();
		this.allocator.reset();
		this.lastPlan = this.scene.plan();
		this.lastAttemptVersion = -1;
		this.walkFailures = 0;
		this.reorderFailures = 0;
		if (this.walkRetryTimer !== null) {
			clearTimeout(this.walkRetryTimer);
			this.walkRetryTimer = null;
		}
		if (this.reorderRetryTimer !== null) {
			clearTimeout(this.reorderRetryTimer);
			this.reorderRetryTimer = null;
		}
		this.pendingPriorityCommits.clear();
		this.descriptors.clear();
		// Fully applied clean baseline — nothing pending, and the scene
		// version is now current so busy resolves to false.
		this.lastAppliedVersion = this.scene.version();
		this.refreshBusy();
	}

	private walk = (): void => {
		const handle = this.nativeNodeHandle;
		if (handle === null || this.destroyed) {
			return;
		}
		if (this.walkInFlight) {
			// An enumeration is already running — remember the signal and
			// let the in-flight walk re-schedule a fresh one when it lands.
			this.walkRequested = true;
			return;
		}
		this.walkInFlight = true;
		this.refreshBusy();
		const seq = ++this.walkSeq;
		NativeMapContainer.enumerateAnchors({ nativeNodeHandle: handle })
			.then(({ anchors }) => {
				this.walkInFlight = false;
				this.settleWalkRequested();
				if (this.destroyed || seq !== this.walkSeq) {
					// Stale result (destroyed and re-armed in between) —
					// discard.
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
				this.walkInFlight = false;
				this.settleWalkRequested();
				this.refreshBusy();
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
				// Exponential backoff (mirrors the reorder retry) so a
				// cold-starting TurboModule isn't hammered at a fixed rate.
				const delay = Math.min(
					WALK_RETRY_BASE_MS * 2 ** (this.walkFailures - 1),
					WALK_RETRY_MAX_MS
				);
				this.walkRetryTimer = setTimeout(this.walk, delay);
			});
	};

	/**
	 * A walk signal arrived while an enumeration was in-flight — run a
	 * fresh walk so the scene converges on the latest committed state.
	 */
	private settleWalkRequested(): void {
		if (this.walkRequested) {
			this.walkRequested = false;
			if (!this.destroyed && this.nativeNodeHandle !== null) {
				this.scheduleWalk();
			}
		}
	}

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
		const hasLayerWork = diff.layerOrderChanged;

		if (!hasLayerWork && diff.entryPriorityComputations.size === 0) {
			this.lastPlan = plan;
			this.lastAppliedVersion = this.scene.version();
			this.refreshBusy();
			return;
		}

		this.syncInFlight = true;
		this.refreshBusy();
		const startedAt = this.scene.version();
		if (startedAt !== this.lastAttemptVersion) {
			// The scene mutated since the previous attempt — the plan is
			// fresh, reset the failure streak.
			this.reorderFailures = 0;
			this.lastAttemptVersion = startedAt;
		}

		// Entry priorities are independent of the layer stack — fire them
		// alongside the reorder. The computed assignments commit only after
		// the native side confirms, so a failed application is re-sent on
		// the next mutation instead of being dropped forever.
		this.applyEntryPriorityChanges(handle, plan, diff);

		// Capture the reorder generation: a resolution that lands after a
		// destroy()/re-arm (reorderSeq bumped) or after a newer sync started
		// must be discarded, not applied to the fresh lifecycle.
		const reorderSeq = this.reorderSeq;
		const reorderPromise = hasLayerWork
			? NativeMapContainer.reorderLayers({
					nativeNodeHandle: handle,
					layerUuids: diff.orderedUuids,
				})
			: Promise.resolve();

		reorderPromise
			.then(() => {
				// A resolution that lands after destroy()/re-arm must not
				// clobber the reset state (lastPlan baseline, in-flight flags).
				if (this.destroyed || reorderSeq !== this.reorderSeq) {
					return;
				}
				this.syncInFlight = false;
				this.reorderFailures = 0;
				this.lastPlan = plan;
				this.lastAppliedVersion = startedAt;
				// Mutations landed while the reorder was in-flight —
				// re-sync with the freshest plan.
				if (this.scene.version() !== startedAt) {
					this.syncDebouncer.schedule();
				}
				this.refreshBusy();
			})
			.catch(() => {
				if (this.destroyed || reorderSeq !== this.reorderSeq) {
					return;
				}
				// Don't update lastPlan — the next sync retries.
				this.syncInFlight = false;
				this.reorderFailures++;
				if (this.reorderFailures <= MAX_REORDER_RETRIES) {
					// Exponential backoff — retries stop after the cap
					// until the next scene mutation resets the streak.
					const delay = Math.min(
						REORDER_RETRY_BASE_MS * 2 ** (this.reorderFailures - 1),
						REORDER_RETRY_MAX_MS
					);
					if (this.reorderRetryTimer !== null) {
						clearTimeout(this.reorderRetryTimer);
					}
					this.reorderRetryTimer = setTimeout(this.sync, delay);
				}
				this.refreshBusy();
			});
	};

	private applyEntryPriorityChanges(
		handle: number,
		plan: LayerPlan,
		diff: PlanDiff
	): void {
		for (const [
			fragmentUuid,
			computation,
		] of diff.entryPriorityComputations) {
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
			this.pendingPriorityCommits.set(fragmentUuid, computation.next);
			handler({
				nativeNodeHandle: handle,
				fragmentUuid,
				assignments: [...computation.changed.entries()].map(
					([uuid, priority]) => ({ uuid, priority })
				),
			})
				.then(() => {
					// Commit only when no newer computation superseded
					// this one — stale maps must never clobber fresher
					// state.
					if (
						this.pendingPriorityCommits.get(fragmentUuid) ===
						computation.next
					) {
						this.allocator.commitFor(
							fragmentUuid,
							computation.next
						);
						this.pendingPriorityCommits.delete(fragmentUuid);
					}
					this.refreshBusy();
				})
				.catch(() => {
					// Best-effort: without a commit the allocator still
					// holds the previous state, so the next mutation
					// re-sends the same changes.
					if (
						this.pendingPriorityCommits.get(fragmentUuid) ===
						computation.next
					) {
						this.pendingPriorityCommits.delete(fragmentUuid);
					}
					this.refreshBusy();
				});
		}

		// Drop pending commits for fragments that left the plan — their
		// allocator state was forgotten, a late resolution must not
		// resurrect it.
		const fragmentUuids = new Set(plan.fragments.map((f) => f.uuid));
		for (const fragmentUuid of [
			...this.pendingPriorityCommits.keys(),
		]) {
			if (!fragmentUuids.has(fragmentUuid)) {
				this.pendingPriorityCommits.delete(fragmentUuid);
			}
		}
		this.refreshBusy();
	}
}
