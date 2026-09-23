/**
 * The layer scene: the single source of truth for layer-stack ordering.
 *
 * The scene is mutated ONLY from the React commit phase (useLayoutEffect) —
 * never during render. React renders may be partial, skipped or discarded;
 * the scene only ever sees committed state, which is exactly why none of the
 * historical ordering bugs (stale cursors, sentinel churn, generation
 * stamps) can occur.
 *
 * Every mutation bumps a monotonic version counter (so consumers can detect
 * "the scene changed" cheaply) and produces a fresh immutable LayerPlan
 * (Memento) on demand. Listeners (Observer pattern) are notified after each
 * mutation.
 */

import type { AnchorDescriptor, EntryDeclaration, LayerPlan } from './types';
import { buildPlan } from './planBuilder';
import { fragmentUuidFor } from './ids';

/** Upper bound on distinct planWithResolved memo keys before a blunt reset. */
const MAX_RESOLVED_PLAN_MEMO = 64;

export class LayerScene {
	private walk: AnchorDescriptor[] = [];
	private entries = new Map<string, EntryDeclaration>();
	private uuids = new Map<string, string>();
	private listeners = new Set<() => void>();
	private declarationSeq = 0;
	private mutationVersion = 0;
	private planDirty = true;
	private cachedPlan: LayerPlan = {
		layers: [],
		fragments: [],
		scopes: [],
		runKeysByAnchor: new Map(),
		fragmentUuids: new Set(),
	};
	/** Entry uid → its deterministic fragment uuid (populated on declare). */
	private entryFragment = new Map<string, string>();
	/** planWithResolved memo: cacheKey (fragment/run/anchor) → {version, plan}. */
	private resolvedPlanMemo = new Map<
		string,
		{ version: number; plan: LayerPlan }
	>();
	/** True while a batched listener-notification microtask is pending. */
	private notifyScheduled = false;

	/** Replaces the committed anchor sequence (fresh walk result). */
	applyWalk(sequence: AnchorDescriptor[]): void {
		// Defensive copy — the caller must not be able to mutate the live walk.
		this.walk = [...sequence];
		this.mutated();
	}

	/** Declares or updates an entry (drawable) inside a fragment. */
	declareEntry(entry: EntryDeclaration): void {
		this.declarationSeq++;
		this.entries.set(entry.uid, {
			...entry,
			declarationSeq: this.declarationSeq,
		});
		this.entryFragment.set(
			entry.uid,
			fragmentUuidFor(entry.fragmentId, entry.layerType)
		);
		this.mutated();
	}

	undeclareEntry(uid: string): void {
		if (this.entries.delete(uid)) {
			this.entryFragment.delete(uid);
			this.mutated();
		}
	}

	/** Records a resolved native uuid (dedicated layer, entry, or owner layer). */
	attachUuid(key: string, uuid: string): void {
		this.uuids.set(key, uuid);
		this.mutated();
	}

	detachUuid(key: string): void {
		if (this.uuids.delete(key)) {
			this.mutated();
		}
	}

	/** The current immutable plan (bottom → top). Cached until mutated. */
	plan(): LayerPlan {
		if (this.planDirty) {
			this.cachedPlan = buildPlan(this.walk, this.entries, this.uuids);
			this.planDirty = false;
		}
		return this.cachedPlan;
	}

	/**
	 * Pure, uncached plan as if {@code key} had a resolved uuid — the atomic-add
	 * order hint. Memoized per fragment/run (S23): every entry of the same
	 * fragment (and every member of the same type-run) yields an identical plan,
	 * so one build per fragment per mutation-version serves the whole create
	 * burst, collapsing the create-phase O(N²) to ~O(N log N).
	 */
	planWithResolved(key: string): LayerPlan {
		const cacheKey =
			this.entryFragment.get(key) ??
			this.plan().runKeysByAnchor.get(key) ??
			key;
		const memo = this.resolvedPlanMemo.get(cacheKey);
		if (memo !== undefined && memo.version === this.mutationVersion) {
			return memo.plan;
		}
		const virtualUuids = new Map(this.uuids);
		virtualUuids.set(key, 'virtual');
		const plan = buildPlan(this.walk, this.entries, virtualUuids);
		// Bound the memo: distinct cache keys (fragment/run/anchor uids) are
		// never reused, so a long-lived map with mount/unmount churn would
		// otherwise accumulate stale O(N) plan snapshots until clear(). A blunt
		// reset at the cap is fine — realistic maps have far fewer live keys,
		// and the next burst simply rebuilds once per fragment.
		if (this.resolvedPlanMemo.size >= MAX_RESOLVED_PLAN_MEMO) {
			this.resolvedPlanMemo.clear();
		}
		this.resolvedPlanMemo.set(cacheKey, {
			version: this.mutationVersion,
			plan,
		});
		return plan;
	}

	/**
	 * O(1) membership check over the cached plan's fragment uuids — a cheap
	 * alternative to `plan().fragments.some(f => f.uuid === uuid)` for the
	 * React bindings (see `useSceneFragmentReady`).
	 */
	hasFragment(uuid: string): boolean {
		return this.plan().fragmentUuids.has(uuid);
	}

	subscribe(listener: () => void): () => void {
		this.listeners.add(listener);
		return () => {
			this.listeners.delete(listener);
		};
	}

	/** Monotonic mutation counter — bumps on every commit-phase mutation. */
	version(): number {
		return this.mutationVersion;
	}

	/**
	 * Resets all state. Used on map teardown so a re-arm (StrictMode or
	 * re-mount) starts from a genuinely clean baseline rather than a stale
	 * walk/entries/uuids. Does NOT notify listeners — this is a teardown op,
	 * and `destroy()` always follows it with a fresh `applyWalk` (which does
	 * notify), so any still-mounted subscriber re-reads on the next walk.
	 */
	clear(): void {
		this.walk = [];
		this.entries.clear();
		this.uuids.clear();
		this.declarationSeq = 0;
		this.mutationVersion++;
		this.planDirty = true;
		this.entryFragment.clear();
		this.resolvedPlanMemo.clear();
	}

	private mutated(): void {
		this.mutationVersion++;
		this.planDirty = true;
		// Coalesce listener notification into a single microtask flush (S23):
		// a commit-phase burst of N mutations (declareEntry/attachUuid per entry)
		// notifies the N subscribers once instead of N times, collapsing the
		// O(N²) subscriber-notification storm to ~O(N). mutationVersion still
		// bumps synchronously per mutation, so plan/memo invalidation stays
		// exact — only the (async) listener fan-out is batched. Uses
		// Promise.resolve().then (real microtask, jest-fake-timer-safe) like
		// EntryBatchQueue.
		if (this.notifyScheduled) {
			return;
		}
		this.notifyScheduled = true;
		Promise.resolve().then(() => {
			this.notifyScheduled = false;
			this.listeners.forEach((listener) => listener());
		});
	}
}
