/**
 * External dependencies
 */
import {
	useContext,
	useEffect,
	useLayoutEffect,
	useRef,
	type ReactNode,
} from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';
import ReindexContext from '../context/ReindexContext';

/**
 * Props for {@link ReindexScope}.
 */
export type ReindexScopeProps = {
	children?: ReactNode;
	/**
	 * Optional priority for ordering across sibling ReindexScope instances.
	 * Lower values = earlier in `registry.order` = lower z-index on the map.
	 *
	 * When set, the scope's layers are positioned after layers of
	 * lower-priority scopes and before layers of higher-priority scopes,
	 * even if children mount later due to async data.
	 *
	 * Scopes without a priority are placed at the end in render-tree order
	 * (backward-compatible default).
	 */
	order?: number;
};

/**
 * Wraps children in a scope whose layers are reindexed together.
 *
 * Each <ReindexScope> creates a stable scope identifier. Descendant
 * {@code useLayerOrder} calls tag their layer symbols with this scope in
 * {@code registry.layerReindexScopes}. During a re-render, the scope finds
 * its layers at their current positions in the live {@code registry.order},
 * records the range start, and uses a two-phase protocol (render +
 * useLayoutEffect) to ensure the block stays at the correct position
 * regardless of sibling insertions or removals.
 *
 * ### Sentinel mechanism
 *
 * When children haven't mounted yet (e.g. gated on async data), the scope
 * pushes a sentinel placeholder into {@code registry.order} so sibling
 * scopes and non-scoped layers are ordered correctly, and the scope block
 * automatically lands at the right position when children eventually mount.
 *
 * ### `order` prop
 *
 * Use {@link ReindexScopeProps.order} when the scope wrapper itself mounts
 * late (not just its children). The sentinel handles the common case
 * (scope renders at initial mount, children arrive later); the `order` prop
 * handles the harder case (scope gated on async features flags etc.).
 *
 * Replaces {@code useLayerReindex()}:
 *
 * ```tsx
 * // Before:
 * const LinesMapView = () => {
 *   useLayerReindex();
 *   return <>{paths.map(p => <LayerPath key={p.id} {...p} />)}</>;
 * };
 *
 * // After:
 * const LinesMapView = () => (
 *   <ReindexScope>
 *     {paths.map(p => <LayerPath key={p.id} {...p} />)}
 *   </ReindexScope>
 * );
 * ```
 *
 * Nested ReindexScopes work correctly — each manages its own sub-range
 * independently. {@link SharedLayer} and ReindexScope are orthogonal:
 * a layer can be in any combination of the two.
 */
const ReindexScope = ({ children, order }: ReindexScopeProps) => {
	const { nativeNodeHandle, registry } = useContext(MapHandleContext);
	const parentScopeId = useContext(ReindexContext);

	// ── Stable scope symbol ─────────────────────────────────────────
	const scopeSymbolRef = useRef<symbol | undefined>(undefined);
	if (!scopeSymbolRef.current) {
		scopeSymbolRef.current = Symbol('reindex');
	}
	const scopeSymbol = scopeSymbolRef.current;

	// ── Sentinel (placeholder when children haven't mounted yet) ─────
	const sentinelRef = useRef<symbol | undefined>(undefined);

	// ── Range start (persists between Phase 1 and Phase 2) ──────────
	// -1 means "first mount, no existing scope layers found."
	const rangeStartRef = useRef<number>(-1);

	// ════════════════════════════════════════════════════════════════
	// PHASE 1: RENDER
	// ════════════════════════════════════════════════════════════════

	// 0. Register / unregister scope priority (runs every render)
	if (order !== undefined) {
		registry.scopePriorities.set(scopeSymbol, order);
	} else {
		registry.scopePriorities.delete(scopeSymbol);
	}

	// 0b. Sentinel lifecycle — push or remove placeholder in `order`
	const hasSentinel =
		sentinelRef.current !== undefined &&
		registry.order.includes(sentinelRef.current);

	// 1. Collect existing scope-tagged layers from LIVE order
	const scopeSymbols: symbol[] = [];
	for (const id of registry.order) {
		if (registry.layerReindexScopes.get(id) === scopeSymbol) {
			scopeSymbols.push(id);
		}
	}

	const hasChildren = scopeSymbols.length > 0;

	if (hasChildren && hasSentinel) {
		// Children just mounted — remove the sentinel placeholder.
		const sIdx = registry.order.indexOf(sentinelRef.current!);
		registry.order.splice(sIdx, 1);
		registry.sentinels.delete(sentinelRef.current!);
		registry.sentinelScopes.delete(sentinelRef.current!);
		sentinelRef.current = undefined;
	}

	if (!hasChildren) {
		// Push or reposition sentinel so sibling scopes and non-scoped
		// layers see the correct relative order even before this scope's
		// children exist.
		if (parentScopeId === null) {
			// Determine where the sentinel should go.
			let insertAfterIdx = -1;

			if (order !== undefined) {
				// Walk order to find the position relative to other
				// scopes' layers, respecting declared priorities.
				let lastLowerIdx = -1;
				for (let i = 0; i < registry.order.length; i++) {
					const sym = registry.order[i]!;
					let symScope = registry.layerReindexScopes.get(sym);
					// Sentinels don't have real layer symbols, so
					// layerReindexScopes may not map them. Look up the
					// sentinel's owning scope directly instead.
					if (symScope === undefined && registry.sentinels.has(sym)) {
						symScope = registry.sentinelScopes.get(sym);
					}
					if (symScope !== undefined) {
						const symOrder = registry.scopePriorities.get(symScope);
						if (symOrder !== undefined && symOrder < order) {
							lastLowerIdx = i;
						}
					}
				}
				insertAfterIdx = lastLowerIdx;
			} else {
				// No order prop: use the cursor position (render-tree
				// order). This is the default backward-compat path.
				if (registry.cursor !== undefined) {
					insertAfterIdx = registry.order.indexOf(registry.cursor);
				}
			}

			if (!hasSentinel) {
				// Create a new sentinel at the cursor position
				// (initial render or first mount of a late scope).
				sentinelRef.current = Symbol('reindex-sentinel');
				registry.order.splice(
					insertAfterIdx === -1
						? order !== undefined
							? 0
							: registry.order.length
						: insertAfterIdx + 1,
					0,
					sentinelRef.current
				);
				registry.sentinels.add(sentinelRef.current);
				registry.sentinelScopes.set(sentinelRef.current, scopeSymbol);
			} else if (order !== undefined) {
				// Reposition existing sentinel for order-prop-driven
				// priority changes. Without an order prop, the initial
				// position (from MapContainer's first render pass) is
				// always correct — never reposition it.
				const curIdx = registry.order.indexOf(sentinelRef.current!);
				const targetIdx =
					insertAfterIdx === -1
						? 0
						: insertAfterIdx >= curIdx
							? insertAfterIdx
							: insertAfterIdx + 1;
				if (curIdx !== targetIdx) {
					registry.order.splice(curIdx, 1);
					registry.order.splice(targetIdx, 0, sentinelRef.current!);
				}
			}
			// else: sentinel exists, no order prop — leave it at its
			// initial position (placed correctly during the first
			// render pass). Repositioning would use a stale cursor
			// from a partial re-render and break the ordering.

			// Set cursor to the sentinel so any non-scoped layers
			// rendered after this scope insert after it.
			registry.cursor = sentinelRef.current!;
			// cursorLayerType stays as-is — the sentinel isn't a
			// real layer so it shouldn't affect fragment indices.
		}
		// Nested inside parent scope: leave cursor alone. The outer
		// scope (or a sentinel above us) already set it.
	}

	// 2. Determine rangeStart and set cursor for children
	if (hasChildren) {
		// ── Existing scope block found ───────────────────────────
		const firstSymbol = scopeSymbols[0]!;
		const firstScopeIdx = registry.order.indexOf(firstSymbol);
		rangeStartRef.current = firstScopeIdx;

		// Set cursor to the element just before the scope block,
		// so children reposition to firstScopeIdx, firstScopeIdx+1, ...
		if (firstScopeIdx > 0) {
			const anchorSymbol = registry.order[firstScopeIdx - 1];
			registry.cursor = anchorSymbol;

			// Different scope (or no scope): force a new fragment so
			// same-type layers across scope boundaries don't share
			// native draw-call batches. Within the same scope, walk
			// back to find the nearest preceding element that HAS a
			// layerType — dedicated-layer anchors (BitmapTile,
			// Scalebar) don't set one and would cause spurious
			// fragment-index increments.
			const anchorScope = registry.layerReindexScopes.get(anchorSymbol!);
			if (anchorScope !== scopeSymbol) {
				registry.cursorLayerType = undefined;
			} else {
				let anchorType: string | undefined;
				for (let i = firstScopeIdx - 1; i >= 0; i--) {
					const t = registry.layerTypes.get(registry.order[i]!);
					if (t) {
						anchorType = t;
						break;
					}
				}
				registry.cursorLayerType = anchorType;
			}
		} else {
			registry.cursor = undefined;
			registry.cursorLayerType = undefined;
		}
	} else {
		// ── No children (cursor already positioned at sentinel) ────
		rangeStartRef.current = -1;

		if (parentScopeId !== null) {
			// Inside an outer ReindexScope: leave cursor alone.
			// The outer scope already set it for its block.
		}
		// Top-level without children: cursor already set to sentinel
		// above. Nothing more to do here.
	}

	// 3. Rebuild fragment indices so new shared-type layers added
	//    during a partial re-render (MapContainer not re-rendering)
	//    see correct continuation indices. Mirrors MapContainer's
	//    rebuild logic.
	{
		let lastType: string | undefined;
		registry.fragmentIndices.clear();
		for (const id of registry.order) {
			// Skip sentinels — they aren't real layers.
			if (registry.sentinels.has(id)) {
				continue;
			}
			const t = registry.layerTypes.get(id);
			if (t) {
				if (lastType !== t) {
					const idx = registry.fragmentIndices.get(t) ?? 0;
					registry.fragmentIndices.set(t, idx + 1);
				}
				lastType = t;
			}
		}
	}

	// 4. Bump generation so children's useLayerOrder repositioning fires
	// REMOVED: registry.generation++ — bumping generation from a Redux-triggered
	// partial re-render causes unrelated layers (in other scopes) to reposition
	// using a cursor set by this scope's children, destroying the layer order.
	// MapContainer already bumps generation on full render passes.

	// ════════════════════════════════════════════════════════════════
	// PHASE 2: useLayoutEffect
	// Runs synchronously after commit, before paint — within the
	// debounce window of scheduleSync (16ms trailing). Children's
	// scheduleSync calls from render have started their timers but
	// haven't fired yet, so the flush reads the corrected order.
	//
	// Phase 2 verifies that the scope block is at the position
	// recorded in Phase 1. If a sibling ReindexScope's Phase 2
	// shifted the block in the same commit, we accept the current
	// position and update the recording. The cursor chain from
	// Phase 1 handles internal ordering — Phase 2 only does
	// block-level verification and first-mount recording.
	// ════════════════════════════════════════════════════════════════
	useLayoutEffect(() => {
		// 1. Collect scope-tagged layers (just placed by children)
		const currentScopeSymbols: symbol[] = [];
		for (const id of registry.order) {
			if (registry.layerReindexScopes.get(id) === scopeSymbol) {
				currentScopeSymbols.push(id);
			}
		}

		if (currentScopeSymbols.length === 0) {
			return; // All children unmounted (sentinel still in place)
		}

		// Remove sentinel if children mounted in this same render
		// (Phase 1 could not detect children before they rendered).
		if (
			sentinelRef.current !== undefined &&
			registry.order.includes(sentinelRef.current)
		) {
			const sIdx = registry.order.indexOf(sentinelRef.current);
			registry.order.splice(sIdx, 1);
			registry.sentinels.delete(sentinelRef.current);
			registry.sentinelScopes.delete(sentinelRef.current);
			sentinelRef.current = undefined;
		}
		// 2. Determine current position
		const currentFirstIdx = registry.order.indexOf(currentScopeSymbols[0]!);

		// 3. Verify or record position
		if (rangeStartRef.current >= 0) {
			// Existing scope: if the block was shifted by a sibling
			// ReindexScope's Phase 2 in the same commit, accept the
			// current position and update the recording for Phase 1
			// of the next render.
			if (currentFirstIdx !== rangeStartRef.current) {
				rangeStartRef.current = currentFirstIdx;
			}
		} else {
			// First mount: record where children landed
			rangeStartRef.current = currentFirstIdx;
		}

		// 4. Always schedule sync (flush dedup skips no-ops)
		registry.scheduleSync(nativeNodeHandle);
	});

	// ── Cleanup ─────────────────────────────────────────────────────
	useEffect(() => {
		registry.notify();
		return () => {
			// Remove sentinel from order and sentinels set.
			if (
				sentinelRef.current !== undefined &&
				registry.order.includes(sentinelRef.current)
			) {
				const sIdx = registry.order.indexOf(sentinelRef.current);
				registry.order.splice(sIdx, 1);
				registry.sentinels.delete(sentinelRef.current);
				registry.sentinelScopes.delete(sentinelRef.current);
			}
			// Remove scope priority.
			registry.scopePriorities.delete(scopeSymbol);
			registry.notify();
		};
	}, [registry, scopeSymbol]);

	return (
		<ReindexContext.Provider value={scopeSymbol}>
			{children}
		</ReindexContext.Provider>
	);
};

export default ReindexScope;
