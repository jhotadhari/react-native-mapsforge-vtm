/**
 * Sparse drawable-priority allocation.
 *
 * Native fragment layers sort drawables by integer priority ascending
 * (VectorLayer) or by compensated descending insertion (ItemizedLayer).
 * Assigning priorities in sparse steps (seq * STEP) lets insertions and
 * removals update only the changed entries — a 3000-entry list with a new
 * entry at the front re-prioritizes exactly one entry instead of 3000.
 *
 * When the gap between neighbours is exhausted (adjacent ints), the whole
 * fragment is renumbered once — amortized O(1) per mutation.
 *
 * Compute and commit are separate steps: {@link #computeFor} is pure and
 * derives the changed set against the last COMMITTED state; {@link #commitFor}
 * persists a computation only after the native side applied it. A failed
 * native application therefore re-sends the same changes on the next
 * mutation instead of dropping them forever.
 */

const STEP = 1000;

export type PriorityComputation = {
	/** Complete entry→priority mapping for the fragment after applying. */
	next: Map<string, number>;
	/** Entries whose native priority must change (subset of `next`). */
	changed: Map<string, number>;
};

export class PriorityAllocator {
	private prioritiesByFragment = new Map<string, Map<string, number>>();

	/**
	 * Pure: computes the assignment for the given ordered entry list without
	 * storing anything. Kept entries in unchanged relative order keep their
	 * priority.
	 */
	computeFor(
		fragmentUuid: string,
		orderedEntryUids: string[]
	): PriorityComputation {
		const prev = this.prioritiesByFragment.get(fragmentUuid) ?? new Map();
		const next = new Map<string, number>();
		const changed = new Map<string, number>();

		if (orderedEntryUids.length === 0) {
			return { next, changed };
		}

		// Detect reordering of previously-known entries: if the subsequence
		// of kept entries is not strictly increasing in priority, every
		// priority is stale — renumber the whole fragment.
		let keptPrev = -Infinity;
		let needsRenumber = false;
		for (const uid of orderedEntryUids) {
			const p = prev.get(uid);
			if (p !== undefined) {
				if (p <= keptPrev) {
					needsRenumber = true;
					break;
				}
				keptPrev = p;
			}
		}

		if (needsRenumber) {
			orderedEntryUids.forEach((uid, i) => {
				const p = i * STEP;
				next.set(uid, p);
				if (prev.get(uid) !== p) {
					changed.set(uid, p);
				}
			});
			return { next, changed };
		}

		// First pass: kept entries keep their previous priority.
		for (const uid of orderedEntryUids) {
			const p = prev.get(uid);
			if (p !== undefined) {
				next.set(uid, p);
			}
		}

		// Second pass: assign new entries midpoints between neighbours.
		for (let i = 0; i < orderedEntryUids.length; i++) {
			const uid = orderedEntryUids[i]!;
			if (next.has(uid)) {
				continue;
			}

			// Find the nearest already-assigned neighbour below and above.
			let lower: number | undefined;
			for (let j = i - 1; j >= 0; j--) {
				const p = next.get(orderedEntryUids[j]!);
				if (p !== undefined) {
					lower = p;
					break;
				}
			}
			let upper: number | undefined;
			for (let j = i + 1; j < orderedEntryUids.length; j++) {
				const p = next.get(orderedEntryUids[j]!);
				if (p !== undefined) {
					upper = p;
					break;
				}
			}

			let p: number;
			if (lower === undefined && upper === undefined) {
				p = 0;
			} else if (lower === undefined) {
				p = upper! - STEP;
			} else if (upper === undefined) {
				p = lower + STEP;
			} else if (upper - lower > 1) {
				p = Math.floor((lower + upper) / 2);
			} else {
				// Gap exhausted — renumber the whole fragment once.
				return this.renumberAll(prev, orderedEntryUids);
			}

			next.set(uid, p);
			changed.set(uid, p);
		}

		return { next, changed };
	}

	/** Persists a computed assignment — call only after native success. */
	commitFor(fragmentUuid: string, next: Map<string, number>): void {
		this.prioritiesByFragment.set(fragmentUuid, next);
	}

	private renumberAll(
		prev: Map<string, number>,
		orderedEntryUids: string[]
	): PriorityComputation {
		const next = new Map<string, number>();
		const changed = new Map<string, number>();
		orderedEntryUids.forEach((uid, i) => {
			const p = i * STEP;
			next.set(uid, p);
			if (prev.get(uid) !== p) {
				changed.set(uid, p);
			}
		});
		return { next, changed };
	}

	/** Drops allocator state for a fragment that was destroyed. */
	forget(fragmentUuid: string): void {
		this.prioritiesByFragment.delete(fragmentUuid);
	}
}
