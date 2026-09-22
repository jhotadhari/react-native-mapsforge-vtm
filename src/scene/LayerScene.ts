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
		this.mutated();
	}

	undeclareEntry(uid: string): void {
		if (this.entries.delete(uid)) {
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
	 * Pure, uncached: builds the plan as if {@code key} had a resolved uuid.
	 * The plan only tests presence, so the value is irrelevant. Used by
	 * components to compute the absolute target order they send with a
	 * create call — the not-yet-resolved fragment appears at its correct
	 * tree position in the result.
	 */
	planWithResolved(key: string): LayerPlan {
		const virtualUuids = new Map(this.uuids);
		virtualUuids.set(key, 'virtual');
		return buildPlan(this.walk, this.entries, virtualUuids);
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
	}

	private mutated(): void {
		this.mutationVersion++;
		this.planDirty = true;
		this.listeners.forEach((listener) => listener());
	}
}
