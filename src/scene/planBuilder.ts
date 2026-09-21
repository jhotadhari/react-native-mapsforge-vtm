/**
 * Pure plan builder: (walk sequence, entry declarations, resolved uuids)
 * → immutable LayerPlan. Contains every layer-stack ordering rule as a
 * pure function — no React, no side effects, no incremental state.
 */

import type {
	AnchorDescriptor,
	EntryDeclaration,
	FragmentPlan,
	LayerPlan,
	PlannedLayer,
	ScopeInfo,
} from './types';
import { fragmentUuidFor, runUuidFor } from './ids';

type Item =
	| { kind: 'dedicated'; anchor: AnchorDescriptor; walkIndex: number }
	| {
			kind: 'run';
			layerType: string;
			scopeUid: string | undefined;
			walkIndex: number;
			memberUids: string[];
	  }
	| { kind: 'owner'; anchor: AnchorDescriptor; walkIndex: number };

type OwnerExpansion = {
	fragmentUuid: string;
	layerType: string;
	entryUids: string[];
	resolvedEntryUids: string[];
};

export const buildPlan = (
	walk: AnchorDescriptor[],
	entries: Map<string, EntryDeclaration>,
	uuids: Map<string, string>
): LayerPlan => {
	const scopeInfos = new Map<string, ScopeInfo>();
	// Scope → enclosing scope (nesting), from the scope anchors' own
	// scopeUid (the anchor of an inner scope is rendered inside the outer
	// scope's provider, so its descriptor carries the outer uid).
	const scopeParentOf = new Map<string, string>();
	walk.forEach((anchor, index) => {
		if (anchor.kind === 'scope') {
			scopeInfos.set(anchor.uid, {
				uid: anchor.uid,
				order: anchor.scopeOrder,
				anchorIndex: index,
			});
			if (anchor.scopeUid !== undefined) {
				scopeParentOf.set(anchor.uid, anchor.scopeUid);
			}
		}
	});

	const items = partitionIntoItems(walk);

	// Nearest explicit order up the scope chain (own scope first). Nested
	// unordered scopes inherit the enclosing scope's order so an ordered
	// outer block stays contiguous (fixes the block-split where an inner
	// unordered scope pulled its members to their own anchor position).
	const orderInChainOf = (scopeUid: string): number | undefined => {
		let current: string | undefined = scopeUid;
		while (current !== undefined) {
			const info = scopeInfos.get(current);
			if (info && info.order !== undefined) {
				return info.order;
			}
			current = scopeParentOf.get(current);
		}
		return undefined;
	};

	// blockStart: explicit scope order (inherited through nesting) overrides
	// tree position; default is pure tree position (S1/S2 — unordered scopes
	// sit exactly where their members are in the committed walk).
	const blockStartOf = (item: Item): number => {
		const scopeUid = scopeUidOf(item);
		if (scopeUid !== undefined) {
			const order = orderInChainOf(scopeUid);
			if (order !== undefined) {
				return order;
			}
		}
		return item.walkIndex;
	};

	const sorted = [...items].sort((a, b) => {
		return blockStartOf(a) - blockStartOf(b) || a.walkIndex - b.walkIndex;
	});

	const layers: PlannedLayer[] = [];
	const fragments: FragmentPlan[] = [];
	const runKeysByAnchor = new Map<string, string>();
	const seenFragmentUuids = new Set<string>();

	for (const item of sorted) {
		if (item.kind === 'dedicated') {
			const uuid = uuids.get(item.anchor.uid);
			if (uuid) {
				layers.push({
					uuid,
					kind: 'layer',
					layerType: item.anchor.layerType,
					anchorUid: item.anchor.uid,
				});
			}
			continue;
		}

		if (item.kind === 'run') {
			const fragmentUuid = runUuidFor(item.memberUids[0]!);
			// Every member resolves to the run's fragment uuid — this map is
			// the scene-authoritative source standalone components create
			// their native entries under (approach b).
			for (const memberUid of item.memberUids) {
				runKeysByAnchor.set(memberUid, fragmentUuid);
			}
			const resolvedEntryUids = item.memberUids.filter((uid) =>
				uuids.has(uid)
			);
			fragments.push({
				uuid: fragmentUuid,
				layerType: item.layerType,
				entryUids: [...item.memberUids],
				resolvedEntryUids,
			});
			if (
				resolvedEntryUids.length > 0 &&
				!seenFragmentUuids.has(fragmentUuid)
			) {
				seenFragmentUuids.add(fragmentUuid);
				layers.push({
					uuid: fragmentUuid,
					kind: 'fragment',
					layerType: item.layerType,
					anchorUid: item.memberUids[0],
				});
			}
			continue;
		}

		// kind === 'owner'
		const expansions = expandOwner(item, entries, uuids);
		for (const expansion of expansions) {
			if (
				expansion.entryUids.length === 0 &&
				item.anchor.layerType === undefined
			) {
				// SharedLayer wrapper without declared entries yet — the
				// anchor still reserves the position; the fragment appears
				// in the plan once entries declare + resolve.
				continue;
			}
			fragments.push({
				uuid: expansion.fragmentUuid,
				layerType: expansion.layerType,
				entryUids: expansion.entryUids,
				resolvedEntryUids: expansion.resolvedEntryUids,
			});
			// A LayerMarker-style owner (layerType defined) owns a real native
			// fragment layer that exists as soon as its own createLayer
			// resolves — even with zero marker children. It must enter the
			// plan then, so its create order hint (planWithResolved) and the
			// reorder list both include it. SharedLayer wrappers (layerType
			// undefined) still require at least one resolved entry.
			const ownerResolved = item.anchor.layerType !== undefined;
			if (
				(expansion.resolvedEntryUids.length > 0 || ownerResolved) &&
				!seenFragmentUuids.has(expansion.fragmentUuid)
			) {
				seenFragmentUuids.add(expansion.fragmentUuid);
				layers.push({
					uuid: expansion.fragmentUuid,
					kind: 'fragment',
					layerType: expansion.layerType,
					fragmentId: item.anchor.fragmentId,
				});
			}
		}
	}

	return {
		layers,
		fragments,
		scopes: [...scopeInfos.values()].sort(
			(a, b) => a.anchorIndex - b.anchorIndex
		),
		runKeysByAnchor,
	};
};

const scopeUidOf = (item: Item): string | undefined => {
	if (item.kind === 'dedicated' || item.kind === 'owner') {
		return item.anchor.scopeUid;
	}
	return item.scopeUid;
};

/**
 * Splits the walk sequence into ordering items:
 * - dedicated layers (shared=false) are individual items
 * - consecutive same-type shared anchors with the same scope form a
 *   type-run fragment (S4) — any other anchor breaks the run
 * - fragment owners (SharedLayer wrapper / LayerMarker) are items
 * - scope anchors only mark positions; they are not stack items
 */
const partitionIntoItems = (walk: AnchorDescriptor[]): Item[] => {
	const items: Item[] = [];
	let runMembers: string[] = [];
	let runType: string | undefined;
	let runScope: string | undefined;
	let runStart = -1;

	const flushRun = () => {
		if (runMembers.length > 0) {
			items.push({
				kind: 'run',
				layerType: runType!,
				scopeUid: runScope,
				walkIndex: runStart,
				memberUids: runMembers,
			});
			runMembers = [];
			runType = undefined;
			runScope = undefined;
			runStart = -1;
		}
	};

	for (let i = 0; i < walk.length; i++) {
		const anchor = walk[i]!;
		if (anchor.kind === 'scope') {
			flushRun();
			continue;
		}
		if (anchor.kind === 'fragment') {
			flushRun();
			items.push({ kind: 'owner', anchor, walkIndex: i });
			continue;
		}
		if (anchor.shared) {
			if (
				runMembers.length > 0 &&
				(runType !== anchor.layerType || runScope !== anchor.scopeUid)
			) {
				flushRun();
			}
			if (runMembers.length === 0) {
				runType = anchor.layerType;
				runScope = anchor.scopeUid;
				runStart = i;
			}
			runMembers.push(anchor.uid);
		} else {
			flushRun();
			items.push({ kind: 'dedicated', anchor, walkIndex: i });
		}
	}
	flushRun();
	return items;
};

/**
 * Expands a fragment-owner anchor into one fragment plan per hosted layer
 * type. Fragment order within the owner follows first occurrence of the
 * type among its entries (S3).
 */
const expandOwner = (
	item: Extract<Item, { kind: 'owner' }>,
	entries: Map<string, EntryDeclaration>,
	uuids: Map<string, string>
): OwnerExpansion[] => {
	const fragmentId = item.anchor.fragmentId!;

	if (item.anchor.layerType !== undefined) {
		// LayerMarker-style owner: its own native fragment layer. The native
		// identity is the deterministic fragment uuid (JS passes it as
		// fragmentUuid to createLayer; knownLayers is keyed by it). The
		// resolved group uuid only marks existence.
		if (!uuids.has(item.anchor.uid)) {
			// The native layer doesn't exist yet — the fragment only enters
			// the plan once the owner's own createLayer resolves.
			return [];
		}
		const ownedEntries = collectOwnerEntries(entries, fragmentId);
		return [
			{
				fragmentUuid: fragmentUuidFor(
					fragmentId,
					item.anchor.layerType
				),
				layerType: item.anchor.layerType,
				entryUids: ownedEntries.map((e) => e.uid),
				resolvedEntryUids: ownedEntries
					.filter((e) => uuids.has(e.uid))
					.map((e) => e.uid),
			},
		];
	}

	// SharedLayer wrapper: one fragment per declared layer type, ordered by
	// the first occurrence of the type among the entries.
	const ownedEntries = collectOwnerEntries(entries, fragmentId);
	const byType = new Map<string, EntryDeclaration[]>();
	const typeOrder: string[] = [];
	for (const entry of ownedEntries) {
		const list = byType.get(entry.layerType);
		if (!list) {
			byType.set(entry.layerType, [entry]);
			typeOrder.push(entry.layerType);
		} else {
			list.push(entry);
		}
	}

	return typeOrder.map((layerType) => {
		const typeEntries = byType.get(layerType)!;
		const fragmentUuid = fragmentUuidFor(fragmentId, layerType);
		return {
			fragmentUuid,
			layerType,
			entryUids: typeEntries.map((e) => e.uid),
			resolvedEntryUids: typeEntries
				.filter((e) => uuids.has(e.uid))
				.map((e) => e.uid),
		};
	});
};

/** Entries owned by a fragment id, ordered by (sortIndex ?? +∞, declarationSeq). */
const collectOwnerEntries = (
	entries: Map<string, EntryDeclaration>,
	fragmentId: string
): EntryDeclaration[] => {
	const owned: EntryDeclaration[] = [];
	for (const entry of entries.values()) {
		if (entry.fragmentId === fragmentId) {
			owned.push(entry);
		}
	}
	owned.sort((a, b) => {
		const aSort = a.sortIndex ?? Number.MAX_SAFE_INTEGER;
		const bSort = b.sortIndex ?? Number.MAX_SAFE_INTEGER;
		return (
			aSort - bSort || (a.declarationSeq ?? 0) - (b.declarationSeq ?? 0)
		);
	});
	return owned;
};
