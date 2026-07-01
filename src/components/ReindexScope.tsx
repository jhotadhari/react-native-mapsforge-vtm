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
 * Replace the deprecated {@code useLayerReindex()} hook with this component:
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
const ReindexScope = ({ children }: { children?: ReactNode }) => {
	const { nativeNodeHandle, registry } = useContext(MapHandleContext);
	const parentScopeId = useContext(ReindexContext);

	// ── Stable scope symbol ─────────────────────────────────────────
	const scopeSymbolRef = useRef<symbol | undefined>(undefined);
	if (!scopeSymbolRef.current) {
		scopeSymbolRef.current = Symbol('reindex');
	}
	const scopeSymbol = scopeSymbolRef.current;

	// ── Pre-render order (for Phase 2 correction) ────────────────────
	const preRenderOrderRef = useRef<symbol[]>([]);

	// ── Range start (persists between Phase 1 and Phase 2) ──────────
	// -1 means "first mount, no existing scope layers found."
	const rangeStartRef = useRef<number>(-1);

	// ════════════════════════════════════════════════════════════════
	// PHASE 1: RENDER
	// ════════════════════════════════════════════════════════════════

	// 1. Collect existing scope-tagged layers from LIVE order
	const scopeSymbols: symbol[] = [];
	for (const id of registry.order) {
		if (registry.layerReindexScopes.get(id) === scopeSymbol) {
			scopeSymbols.push(id);
		}
	}
	preRenderOrderRef.current = scopeSymbols;

	// 2. Determine rangeStart and set cursor
	if (scopeSymbols.length > 0) {
		// ── Existing scope block found ───────────────────────────
		const firstSymbol = scopeSymbols[0]!;
		const firstScopeIdx = registry.order.indexOf(firstSymbol);
		rangeStartRef.current = firstScopeIdx;

		// Set cursor to the element just before the scope block,
		// so children reposition to firstScopeIdx, firstScopeIdx+1, ...
		if (firstScopeIdx > 0) {
			const anchorSymbol = registry.order[firstScopeIdx - 1];
			registry.cursor = anchorSymbol;
			registry.cursorLayerType = registry.layerTypes.get(anchorSymbol!);
		} else {
			registry.cursor = undefined;
			registry.cursorLayerType = undefined;
		}
	} else {
		// ── First mount (no scope-tagged layers yet) ──────────────
		rangeStartRef.current = -1;

		if (parentScopeId !== null) {
			// Inside an outer ReindexScope: leave cursor alone.
			// The outer scope already set it for its block.
		} else {
			// Top-level first mount.
			// Validate cursor: if it points to a stale symbol,
			// default to appending at end of order.
			const cursorValid =
				registry.cursor !== undefined &&
				registry.order.includes(registry.cursor);
			if (!cursorValid) {
				registry.cursor =
					registry.order.length > 0
						? registry.order[registry.order.length - 1]
						: undefined;
				registry.cursorLayerType =
					registry.cursor !== undefined
						? registry.layerTypes.get(registry.cursor)
						: undefined;
			}
		}
	}

	// 3. Bump generation so children's useLayerOrder repositioning fires
	registry.generation++;

	// ════════════════════════════════════════════════════════════════
	// PHASE 2: useLayoutEffect
	// Runs synchronously after commit, before paint — within the
	// debounce window of scheduleSync (16ms trailing). Children's
	// scheduleSync calls from render have started their timers but
	// haven't fired yet, so the flush reads the corrected order.
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
			return; // All children unmounted
		}

		// 2. Sort by pre-render order so relative order within the
		//    block is correct even when the cursor chain broke (e.g.
		//    React.memo'd children skipped render).
		const preRender = preRenderOrderRef.current;
		const preRenderSet = new Set(preRender);
		const sorted = preRender.filter((s) => currentScopeSymbols.includes(s));
		// Append new symbols not in pre-render order (first mount
		// during this render — they land at the end of the block).
		for (const s of currentScopeSymbols) {
			if (!preRenderSet.has(s)) {
				sorted.push(s);
			}
		}

		// 3. Determine current first position
		const currentFirstIdx = registry.order.indexOf(sorted[0]!);

		// 4. Determine target position
		let targetFirstIdx: number;
		if (rangeStartRef.current >= 0) {
			targetFirstIdx = rangeStartRef.current;
			// Clamp: if siblings before the block were removed,
			// the original rangeStart may exceed current order length.
			if (targetFirstIdx > registry.order.length) {
				targetFirstIdx =
					registry.order.length - currentScopeSymbols.length;
			}
		} else {
			// First mount: record where children landed for next time
			targetFirstIdx = currentFirstIdx;
			rangeStartRef.current = currentFirstIdx;
		}

		// 5. Extract and reinsert if position differs
		if (targetFirstIdx !== currentFirstIdx) {
			// Remove in reverse to preserve indices
			for (let i = sorted.length - 1; i >= 0; i--) {
				const idx = registry.order.indexOf(sorted[i]!);
				if (idx !== -1) {
					registry.order.splice(idx, 1);
				}
			}
			// Re-insert at target
			registry.order.splice(targetFirstIdx, 0, ...sorted);
		}

		// 6. Always schedule sync (flush dedup skips no-ops)
		registry.scheduleSync(nativeNodeHandle);
	});

	// ── Cleanup ─────────────────────────────────────────────────────
	useEffect(() => {
		registry.notify();
		return () => {
			registry.notify();
		};
	}, [registry]);

	return (
		<ReindexContext.Provider value={scopeSymbol}>
			{children}
		</ReindexContext.Provider>
	);
};

export default ReindexScope;
