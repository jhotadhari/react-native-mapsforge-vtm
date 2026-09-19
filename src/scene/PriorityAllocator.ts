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
 */

const STEP = 1000;

export class PriorityAllocator {
	private prioritiesByFragment = new Map<string, Map<string, number>>();

	/**
	 * Returns the entries whose native priority must change for the given
	 * ordered entry list, and stores the new assignment. Kept entries in
	 * unchanged relative order keep their priority.
	 */
	changedFor(
		fragmentUuid: string,
		orderedEntryUids: string[]
	): Map<string, number> {
		const prev = this.prioritiesByFragment.get(fragmentUuid) ?? new Map();
		const next = new Map<string, number>();
		const changed = new Map<string, number>();

		if (orderedEntryUids.length === 0) {
			this.prioritiesByFragment.set(fragmentUuid, next);
			return changed;
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
			this.prioritiesByFragment.set(fragmentUuid, next);
			return changed;
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
				return this.renumberAll(fragmentUuid, orderedEntryUids);
			}

			next.set(uid, p);
			changed.set(uid, p);
		}

		this.prioritiesByFragment.set(fragmentUuid, next);
		return changed;
	}

	private renumberAll(
		fragmentUuid: string,
		orderedEntryUids: string[]
	): Map<string, number> {
		const prev = this.prioritiesByFragment.get(fragmentUuid) ?? new Map();
		const next = new Map<string, number>();
		const changed = new Map<string, number>();
		orderedEntryUids.forEach((uid, i) => {
			const p = i * STEP;
			next.set(uid, p);
			if (prev.get(uid) !== p) {
				changed.set(uid, p);
			}
		});
		this.prioritiesByFragment.set(fragmentUuid, next);
		return changed;
	}

	/** Drops allocator state for a fragment that was destroyed. */
	forget(fragmentUuid: string): void {
		this.prioritiesByFragment.delete(fragmentUuid);
	}
}
