# Code Review: Layer-Ordering Rewrite Phases 1–4

Range reviewed: `8100019..HEAD` (74 files, 7 parallel read-only subagents grouped by directory).
Review marks updated to `eb35439`.

- **4 critical, 23 minor, 11 suggestion findings.**
- No fixes applied (no `--fix`); all findings interleaved into the phased plan below.
- See `fix-batch-1.md` for the current execution plan and `layer-ordering-rewrite.md` for the roadmap.

## Critical

| # | File | Lines | Summary |
|---|---|---|---|
| 1 | `LayerPath.tsx` / `Marker.tsx` / `LayerShape.tsx` | ~99 / ~100 / ~138 | Standalone type-run members self-key `run:<own-uid>` but the plan keys runs by the *first* member — members 2..N get unmanaged native layers, never reordered, and the type-run collapse is silently dead. Latent in the examples (all single-member runs); straymap routing segments would trigger it. |
| 2 | `MarkerLayerManager.java` | 674–701 | `applyEntryPriorities` mutates the live `ItemizedLayer` item list unsynchronized on the modules thread — UI-thread hit-test races can crash. |
| 3 | `MapsforgeVtmView.java` | 77–95 | Move signal only covers direct children, is dead while detached, never re-walks on re-attach, and can leak the view via the anonymous listener. |
| 4 | `src/index.tsx` + deleted `useLayerOrder.ts` | — | Removal of the documented extension API (`useLayerOrder`, `createLayerOrderRegistry`, `LayerOrderRegistry`) breaks ext packages; docs/`AGENTS.md` still describe them. Deliberate breaking change, unshipped as-is. |

## Minor

| # | File | Lines | Summary |
|---|---|---|---|
| 1 | `SceneSync.ts` | 54 | max-wait timer re-runs after debounce already flushed — double executions per burst |
| 2 | `SceneSync.ts` | 149 | `enumerateAnchors` failure swallowed, no retry — transient early-mount failures leave the scene permanently unsynced |
| 3 | `SceneSync.ts` | 201 | persistent `reorderLayers` failure → unbounded retry loop, no backoff/cap |
| 4 | `SceneSync.ts` | 164 | `PriorityAllocator` state advances before native success — failed priority updates permanently dropped (comment overstates recovery) |
| 5 | `SceneSync.ts` | 123 | `destroy()` one-way latch; React StrictMode mount→cleanup→mount kills the presenter |
| 6 | `SceneSync.ts` | 130 | no in-flight guard on concurrent walks — stale walk can overwrite a fresher one |
| 7 | `useLayerEntry.ts` / `useSceneUuidBinding.ts` | 63 / 16 | no unmount detach — `scene.uuids` + command log grow unbounded |
| 8 | `LayerScene.ts` | 36 | unbounded command log; `applyWalk` retains caller arrays forever |
| 9 | `SharedLayer.tsx` / `useLayerAnchor.tsx` / `useLayerEntry.ts` | 21 / 23 / 13 | bare module counters collide across Fast Refresh (old random-prefix guard lost) |
| 10 | `EntryBatchQueue.ts` | 187 | max-wait timer callback never resets `maxWaitTimer` — safety net dead code (pre-existing pattern, now tripled) |
| 11 | `PathBatchQueue.ts` / `ShapeBatchQueue.ts` | 23 | optional `response` cast without guard — undefined resolves produce zombie entries |
| 12 | `PathBatchQueue.ts` etc. | 26 | `drainQueue` rejects pending removes at teardown → spurious "Map view destroyed" `onError` |
| 13 | `planBuilder.ts` / `ReindexScope.tsx` | 55 / 27 | nested scopes: outer `order` doesn't propagate — block splits; `o2`-vs-`i1` tree-order violation inside nested blocks |
| 14 | `modules/LayerPath.java` | 231 | batch `createLayers` lacks the map-destroyed synthesis `removeLayers` has — whole frame of creates rejects on teardown races |
| 15 | `PathLayerManager.java` | 399 | per-item response building outside error capture — failure rejects the whole batch after entries were created (zombies) |
| 16 | `MarkerLayerManager.java` | 681 | rebuild sort tie-break returns 0 — equal-priority markers get non-deterministic order |
| 17 | all three managers | ~656 | assignments don't verify the entry belongs to `fragmentUuid` — cross-fragment `positionIndex` mutation |
| 18 | `PathLayerManager.java` | 393 | `errors[i] = e.getMessage()` can be null — failed item reported as success (phantom uuid) |
| 19 | `PathLayerManager.java` / `ShapeLayerManager.java` | 384 | batch `create`/`remove` call `syncGestureSupport()` per item — O(n²) batches |
| 20 | `PathLayerManager.java` | 170 | `"__vtm_shared_path__0"` magic string in 4 places (shape manager has the constant, path doesn't) |
| 21 | `VtmAnchorViewManager.java` | 66 | recursive `collect` can StackOverflow on deep trees → walk rejects, no retry |
| 22 | `MapsforgeVtmView.java` | 90 | `setOnHierarchyChangeListener` clobbers any pre-existing wrapper listener |
| 23 | `useLayerEntry.ts` | 63 | attach effect not gated on `active` — standalone layers double-mutate the scene |

## Suggestions

| # | File | Lines | Summary |
|---|---|---|---|
| 1 | `planDiff.ts` | 31 | `addedUuids`/`removedUuids` implied by `layerOrderChanged` — dead conditions |
| 2 | `PriorityAllocator.ts` | 102 | front insertions produce unbounded negative priorities (no renumber floor) |
| 3 | `LayerScene.ts` | 37 / 49 | `applyWalk` doesn't copy the array; `declareEntry` logs the entry without its `declarationSeq` |
| 4 | `useLayerAnchor.tsx` | 66 | descriptor effect uses field-list deps (eslint-disable) — `useMemo` the descriptor instead |
| 5 | `useMap.ts` | 85 | `RegistryDebugSnapshot` removed fields break straymap's `LayerDebugDumpButton` (cross-repo contract) |
| 6 | `LayerMarker.tsx` | 180 | `injectVtmSortIndex` runs even when children are discarded (`!uuid`) |
| 7 | `Marker.tsx` / `ReindexScope.tsx` | 82 / 35 | `markerLayerUuid !== false` unreachable; `scopeOrder` in context unused |
| 8 | `MapContainer.java` | 3 | unused `android.view.View` import |
| 9 | managers/modules | — | batch scaffolding triplicated — extract a shared helper (mirror the JS `EntryBatchQueue`) |
| 10 | `MarkerLayerManager.java` | 638 | "Inlist.push() reversal" comment describes an architecture that no longer exists |
| 11 | tests | — | coverage gaps: `@Ignore`d `removeMarkers` test with rewritten assertions (dead), batch response payloads never asserted, no failure/retry/maxWait SceneSync tests, no nested-scope+order test, hardcoded fragment uuids in tests |

## Interleaving into the phased plan

### Fix batch 1 — before Phase 5 (correctness foundation)
CRITICAL #1 (approach **b**: scene-authoritative fragment uuids; **e** rejected), #2, #3;
MINOR #1–13, #17, #18, #20–23; suggestion #10 (comment), #11 (nested-scope test, partial).
Reasoning: everything here is either isolated, or the foundation Phase 5's self-check will exercise.
CRITICAL #1's create-flow change is JS-only and orthogonal to Phase 5's native position work.

### Phase 5 — `LayerStackController`
Absolute plans, delete `positionByUuid`/`BASE_POSITION`/incremental insertion, atomic
create+apply (create carries the desired list), post-apply self-check.
Expected to obsolete part of CRITICAL #1's manifestation and possibly MINOR #14.

### Fix batch 2 — after Phase 5
MINOR #14, #15, #16, #19 (native batch robustness — adjacent to Phase 5's touched code,
or potentially obsolete), plus a **re-review** of Phase 5 against remaining findings.

### Phase 6 — debug refinement
SUGGESTION #5 (restore/extend `RegistryDebugSnapshot` compat with straymap), debug tree entries.

### Phase 7 — docs
CRITICAL #4 (extension API docs → `useLayerAnchor`/`useLayerEntry`/`registerEntryPriorityHandler`),
CHANGELOG breaking-changes note, `AGENTS.md` rewrite, SUGGESTION #7 (dead code cleanup).

### Phase 8 — lockstep consumers
CRITICAL #4 release coordination (straymap + ext packages updated in the same release).

### Deferred / optional
SUGGESTION #1–4, #6, #8, #9 (polish — pick up opportunistically in batches 1/2 where trivial;
#8 trivially in batch 1 or 2), SUGGESTION #11 (remaining test gaps spread across batches).

---

# Code Review #2: Phase 5 + Fix Batches 1–2 + example flicker fix

Range reviewed: `eb35439..HEAD` (`f4f035a`). 68 non-plan files, 11 parallel read-only
subagents (native controller, managers, helpers, modules, views, tests, examples,
NativeModules specs, components, compose hooks/queues, scene).

Review marks updated to `f4f035a`.

- **5 critical, 23 minor, ~30 suggestion findings** (plus pre-existing notes).
- No fixes applied (no `--fix`). Findings below feed the Phase 6 plan.

## Critical

| # | File | Lines | Summary |
|---|---|---|---|
| C1 | `MapMutationQueue.java` / `LayerStackController.java` | 359 / 269 | `removeLayerSync` mutates `layers()` directly and is reachable from the TurboModule thread via `LayerManager.ensureSharedLayer`'s rollback path (not just UI-thread teardown). Violates the "only `flush()` touches `layers()`" invariant. |
| C2 | `MapsforgeVtmView.java` | 91 | `previousListener` captured but never invoked — the wrapper's pre-existing hierarchy listener is silenced for the whole install window. |
| C3 | `MapsforgeVtmView.java` | 90 | Multiple `MapsforgeVtmView`s under one wrapper clobber each other's listener; non-LIFO teardown restores a stale snapshot, disabling other views' move signals (multi-map). |
| C4 | `MarkerLayerManager.java` | 533 | `createMarkers` insertion loop mutates the shared `mItemList` with no layer monitor — contradicts the `synchronized (layer)` added to `applyEntryPriorities`; UI-thread hit-test race → `ConcurrentModificationException`/wrong-index. |
| C5 | `LayerScene.ts` | 114 | Command log grows unbounded (retains full `sequence` arrays) and doubles as the "did the scene mutate" signal (`commandLog().length`), so it can't be dropped/capped without breaking `SceneSync`. Long sessions leak. |

## Minor

| # | File | Lines | Summary |
|---|---|---|---|
| M1 | `LayerStackController.java` | 157 | `applyPlan` early-returns on empty resolved set without updating `previouslyReorderedUuids`/`lastVerifyResult` (stale dedup + stale dump). |
| M2 | `LayerStackController.java` | 244 | `lastLoggedMismatch` never reset on a successful verify — a recurring mismatch is permanently suppressed. |
| M3 | `LayerStackController.java` | 132 | `removeAll`/`removeLayerSync` don't remove uuids from `previouslyReorderedUuids` — a re-added fragment uuid is treated as already-seen. |
| M4 | `MarkerLayerManager.java` | 250 | single-create `createEntry` dereferences `getSharedLayer(fragmentUuid)` with no null guard (batch path has one). |
| M5 | `MarkerLayerManager.java` | 731 | tie-break comment ("earlier-created on top") unverified against vtm 0.29.0 render order; stale `Inlist.push()` reversal javadoc. |
| M6 | `PathLayerManager.java`/`ShapeLayerManager.java` | 62 / 65 | hardcoded `__vtm_shared_path__0`/`__vtm_shared_shape__0` fallbacks no longer match the JS `frag:` id scheme; singular/plural inconsistency. |
| M7 | `LayerHelper.java` | 125 | `removeLayerResolving` reads `params.getString("uuid")` eagerly — missing-uuid produces a worse/empty message and dead-codes `removeLayerAsync`'s own validation. |
| M8 | `Utils.java` | 233 | `rMapGetStringList` does `getArray` with no type guard — non-array value throws. |
| M9 | `MapsforgeVtmView.java` | 135 | reflection fallback swallows all exceptions and still clobbers the original listener with `null`. |
| M10 | `MapsforgeVtmView.java` | 149 | `emitAnchorsChanged()` fires on initial attach before anchors/JS subscriber exist. |
| M11 | `PathLayerManagerTest.java` | 767 | `createPaths_fragmentEnsureFailure_*` never asserts the per-item error in the returned batch response (false positive). |
| M12 | `*LayerManagerTest.java` | — | hardcoded fragment uuids duplicated across dozens of test sites; a scheme rename breaks them silently. |
| M13 | `layer-order-verification/index.tsx` | 488 | `nextRunIdRef.current++` mutates a ref inside the `setRunPaths` updater (impure updater). |
| M14 | `layer-order-verification/index.tsx` | 409 | `runPathElements` rebuilt every render (~25 Hz) — inconsistent with the `useMemo` sibling; wrap in `useMemo(..., [runPaths])`. |
| M15 | `LayerManager.java` | 455 | `resolvePositionIndex()` + managers still read the removed `positionIndex` key (dead append-default path; misleading javadoc). |
| M16 | `LayerPath/LayerShape/Marker.tsx` | ~117 | `layerUuids` computed before `useLayerEntry.declareEntry` runs → fragment omitted from the atomic-add order hint on first create (transient wrong-z, healed ~16 ms). |
| M17 | `LayerMarker.tsx` | 78 | `planWithResolved(anchorUid)` never places the owner's own fragment into `layerUuids` (marker layer's create order hint always incomplete). |
| M18 | `useLayerEntry.ts` | 73 | grouped→standalone early-return never calls `detachUuid` — stale `scene.uuids` entry leaks into command log. |
| M19 | `PathBatchQueue.ts`/`ShapeBatchQueue.ts` | 24 | guard checks `!result.response`, not `!result.response?.uuid` — present-but-empty response yields a phantom uuid (Marker queue does it right). |
| M20 | `LayerScene.ts` | 42 | `applyWalk` stores/records the caller's array by reference (no defensive copy). |
| M21 | `PriorityAllocator.ts` | 113 | front/back inserts grow priorities with no renumber floor/ceiling — unbounded drift, eventual int32 overflow. |
| M22 | `SceneSync.ts` | 230 | walk retry uses fixed 250 ms delay with no backoff (inconsistent with reorder exponential backoff). |
| M23 | `SceneSync.ts` | 156 | `destroy()` doesn't reset in-flight flags / last-applied state — a hung in-flight promise gates a StrictMode re-arm permanently. |

## Suggestions

| # | File | Lines | Summary |
|---|---|---|---|
| S1 | `LayerStackController.java` | 164 | CLEAR_EVENT loop reverse-searches `knownLayers` per layer (O(n²)); use `resolvedUuids.get(i)`. |
| S2 | `LayerStackController.java` | 63 | `VerifyResult` mutable lists handed to cross-thread readers — wrap `Collections.unmodifiableList`. |
| S3 | `LayerStackController.java` | 193 | self-check can't see JS-managed layers absent from the plan — add a present-but-not-in-plan diagnostic count. |
| S4 | `MapContainer.java` | 135 | stale "Resolve uuids on the calling thread" comment. |
| S5 | `MapMutationQueue.java` | 309 | "last plan-bearing mutation wins" can let a stale `AddLayer.desiredOrder` override a fresher `ReorderLayers`. |
| S6 | three managers | — | triplicated batch scaffolding → shared helper (mirror JS `EntryBatchQueue`). |
| S7 | `Path/ShapeLayerManager` | 486 | `syncGestureSupport()` duplicated verbatim → extract. |
| S8 | `PathLayerManager.java` | 204 | misleading `vectorLayer.update();` indentation. |
| S9 | `MarkerLayerManager.java` | 480 | assign `creationSeq` at validation time so tie-break + counter agree by construction. |
| S10 | `LayerHelper.java` | 83 | `addLayerAsync` `desiredOrder`/`layerUuids` effectively dead for all callers — fix comment or pass uuids. |
| S11 | `Utils.java` | 244 | `stringListToWritableArray` no null guard. |
| S12 | `LayerMarker.java` | 302 | add `MarkerLayerManager.DEFAULT_FRAGMENT_UUID` constant (parity with Path/Shape). |
| S13 | `LayerPathJts.java` | 305 | JTS layer drops `layerUuids`, appends then reorders — confirm acceptable transient for "guaranteed z-order". |
| S14 | `MapsforgeVtmView.java` | 126 | replace reflection with a static per-wrapper composite listener (fixes C2/C3 cleanly). |
| S15 | `LayerStackControllerTest.java` | 89 | `addAtStart` helper dead code. |
| S16 | `MapMutationQueueTest.java` | 251 | `MAX_BATCH_SIZE` capping never exercised (only 5 enqueued). |
| S17 | `MapMutationQueueTest.java` | 226 | `destroyRejectsPendingFutures` only covers `AddLayer`. |
| S18 | `layer-order-verification/index.tsx` | 489 | use a monotonic counter for run-path index/color instead of `prev.length` (post-remove collisions). |
| S19 | `docs/advanced/extending.md`, `AGENTS.md` | — | still document `positionIndex`/`useLayerOrder` (removed). Feeds Phase 7 CRITICAL #4. |
| S20 | `LayerPath/LayerShape/Marker.tsx` | 112 | `runUuidFor(anchorUid)` fallback dead — drop the import/branch. |
| S21 | `Marker.tsx` | 92 | `markerLayerUuid !== false` unreachable — tighten context type to `null | string`. |
| S22 | `LayerMarker.tsx` | 186 | `injectVtmSortIndex` runs while children discarded (`!uuid`) — gate the memo on `uuid`. |
| S23 | `LayerPath/LayerShape/Marker.tsx` | 117 | `planWithResolved` per-entry create is O(N²) for bulk creates. |
| S24 | `EntryBatchQueue.ts` | 248 | `drainQueue` resolving pending removes fires `onRemove` post-unmount — confirm/document semantics. |
| S25 | `EntryBatchQueue.test.ts` | 183 | re-arm test only covers safety-net path, not the microtask-wins path. |
| S26 | `MarkerBatchQueue.ts` | 37 | `index: result.index as number` unchecked cast. |
| S27 | `planDiff.ts` / `SceneSync.ts` | 35 / 258 | `addedUuids`/`removedUuids` implied by `layerOrderChanged` (dead branches) — prior SUGGESTION #1. |
| S28 | `SceneSync.ts` | 270 | `commandLog().length` as scene-mutated proxy is fragile — introduce explicit `version`. |
| S29 | `SceneSync.test.ts` | — | missing max-wait / stale-commit / nested-scope `planWithResolved` coverage. |
| S30 | `planBuilder.ts` / `SceneSync.ts` | 79 / 43 | `blockStartOf`/`maxWaitPending` naming no longer matches behavior. |

## Pre-existing (noted, not introduced by this range)

- `LayerMarker.java:202` — spurious `test-uuid` `onMarkerEvent` emitted on every `triggerEvent` (real defect, predates branch).
- `MarkerLayerManager` default fragment uuid singular/plural mismatch (`__vtm_shared_marker__0` vs `__vtm_shared_markers__0`) in the single-marker path.
- `LayerZoomBoundsHelper.java:93` — initial enabled state uses `getMaxZoomLevel()` instead of current zoom.
- `MapsforgeVtmView.java:69` — move signal only covers direct-child anchors (javadoc overstates).
- 4 `@Ignore`d marker tests permanently dead (`createMarkers_populatesEntries`, `_handlesErrorGracefully`, `triggerAllMarkers_*`, `removeMarkers_*`).
- `applyEntryPriorities_unknownFragmentIsNoOp` tests don't assert the `false` return.

## Interleaving

All findings feed **Phase 6 — debug + polish** (already agreed: absorb every remaining SUGGESTION).
Grouping: 6a debug (S2, S3, S4, S14 + debug tree), 6b dead code (M15, S20, S21, S27, S30 + prior #7/#8),
6c robustness (C1–C5, M1–M23 correctness items, S5, S13, S24), 6d refactor (S6, S7, S9, S10, S11, S12),
6e tests (M11, M12, S15–S17, S25, S29 + prior #11 gaps).
Pre-existing items addressed opportunistically. Docs (S19) → Phase 7 CRITICAL #4.

## Phase 6 resolution (commits `c69343d`..`f69a759`)

### Addressed

- **6a critical**: C1 (async-queue rollback), C4 (marker `mItemList` synchronization),
  C2/C3/M9/S14 (composite per-wrapper hierarchy listener), M10 (skip initial anchors-changed),
  C5/M20/S28 (remove command log → `version()` counter)
- **6b**: M1, M2, M3, M7, M8, S1, S4, S5, S10, S11
- **6c**: M4, M5, M15, S6, S7, S8, S9, S12 (M6 partially — marker fallback unified)
- **6d**: M16 (useLayoutEffect declare), M17 (owner-resolved fragment), M18, M19, M21, M22,
  M23, S22, S24, S26 (S28 done in 6a)
- **6e**: S20, S21, S27, S30, prior #7 (scopeOrder), prior #8 (unused import)
- **6f**: M13, M14, S18
- **6g**: S2 (unmodifiable VerifyResult), S3 (notInPlanCount), SUGGESTION #5 (verified already
  compatible — straymap reads only top-level dump fields)
- **6h**: M11, M12, S15, S17, pre-existing false-return assertions

### Deferred (low-risk, documented)

- **S23** `planWithResolved` O(N²) for bulk creates — perf, needs a batched plan cache (risky)
- **S16** MAX_BATCH_SIZE cap test — needs >25-mutation scenario
- **S25** EntryBatchQueue microtask-wins re-arm test
- **S29** SceneSync max-wait / stale-commit / nested-scope `planWithResolved` tests
- **M6** remaining fragment-uuid fallback reconciliation (Path/Shape singular-vs-plural NAME) —
  cosmetic; each manager is now internally consistent via `DEFAULT_FRAGMENT_UUID`
- fragment entry-list dump (aspirational Phase 6 item; expected-vs-applied + notInPlanCount
  already surfaced)

### Not in scope of Phase 6

- CRITICAL #4 (extension API removal) → Phase 7 (docs) + Phase 8 (lockstep consumers)
- S19 (stale `positionIndex`/`useLayerOrder` in docs) → Phase 7

---

# Code Review #3: Phase 6 (post-fix re-review)

Range reviewed: `f4f035a..HEAD` (`7c4e691`). 40 non-plan files, 9 parallel read-only
subagents (controller, managers/helpers, native layer/module/view, tests, examples,
JS spec/context, components, compose, scene). Review marks updated to `7c4e691`.

- **0 critical, 18 minor, ~13 suggestion findings** (plus pre-existing notes).
- No fixes applied (no `--fix`). Findings feed fix batch 3 (before Phase 7).

## Minor

| # | File | Lines | Summary |
|---|---|---|---|
| M1 | `LayerStackController.java` | 176 | empty-plan branch hardcodes `notInPlanCount = 0` (wrong for stale/partial plan) — delegate to `verify(orderedUuids)` |
| M2 | `LayerStackController.java` | 175 | redundant `lastLoggedMismatch = null` (verify() owns the reset) |
| M3 | `MarkerLayerManager.java` | 608/271 | `getItemList().indexOf(...)` reads live list outside `synchronized(layer)` |
| M4 | `MarkerLayerManager.java` | 731 | `entry.positionIndex` write outside `synchronized(layer)` (reads at 547/770 inside) |
| M5 | `LayerHelper.java` | 130 | `removeLayerResolving` re-reads uuid inside `thenRun` (cross-thread) |
| M6 | `Utils.java` | 234 | `rMapGetStringList` silently returns null for non-array (masks JS bug) |
| M7 | `MapsforgeVtmView.java` | 93 | static `compositeListeners` holds strong ViewGroup/view refs → leak risk |
| M8 | `MapsforgeVtmView.java` | 189 | restore `composite.foreign` can clobber a newer external listener |
| M9 | `LayerManager.java` | 384 | `errorMessage` returns `""` for anonymous/local classes |
| M10 | `LayerManager.java` | 465 | rollback `enqueueRemoveLayer` future ignored (silent leak on torn-down queue) |
| M11 | `PathLayerManagerTest.java` | 779 | `verify(response).putString("error",…)` asserts the wrong map (false positive) |
| M12 | `LayerManagerTest.java` | 230 | `appendPriority_isMaxValue` is tautological (no behavioral coverage) |
| M13 | `LayerPath/LayerShape/Marker.tsx` | ~114 | `runFragmentUuid!` non-null assertion relies on non-local `enabled` invariant |
| M14 | `useLayerEntry.ts` | 52 | `useLayoutEffect` declare only fixes LayerMarker, not SharedLayer (owner anchor + walk not ready) |
| M15 | `SceneSync.ts` | 170 | `destroy()` `lastPlan = scene.plan()` isn't a clean baseline (scene never cleared) |
| M16 | `SceneSync.ts` | 304 | reorder `.then`/`.catch` don't guard `destroyed` → stale resolution clobbers destroy resets |

## Suggestions

| # | File | Lines | Summary |
|---|---|---|---|
| S1 | `MapMutationQueue.java` | 310 | reorderPlan-wins-over-addPlan preference has no test coverage |
| S2 | `MarkerLayerManager.java` | 500 | `sortedLocalIndices` descending sort now vestigial (all APPEND_PRIORITY) |
| S3 | `MarkerLayerManager.java` | 536/1086 | duplicated scan+insert (createMarkers vs insertMarkerSorted) |
| S4 | `MarkerLayerManager.java` | 281/668 | removal paths don't take `synchronized(layer)` (inconsistent) |
| S5 | `MarkerLayerManager.java` | 747 | `applyEntryPriorities` holds layer lock across full rebuild+populate (hitch) |
| S6 | `LayerManager.java` | 452 | throw message uses raw `e.getMessage()` — use `errorMessage()` |
| S7 | `MapsforgeVtmView.java` | 143 | `computeIfAbsent` side effect + non-atomic restore; CHM implies false thread-safety |
| S8 | `MapsforgeVtmView.java` | 227 | first-attach emit skip — add comment documenting the JS-mount guarantee |
| S9 | `MapMutationQueueTest.java` | 248 | new destroy test omits `getInstance == null` assertion |
| S10 | `layer-order-verification/index.tsx` | 499 | monotonic `id` index grows unbounded (extent/strokeWidth) |
| S11 | `useNativeLayerLifecycle.ts` | 66 | `triggerCreate` guards captured `enabled` but calls latest `createRef.current` |
| S12 | `ReindexScope.tsx` | 32 | add comment: `order` flows via scope anchor descriptor, not context |

## Pre-existing (noted, not introduced by this range)

- `useLayerEntry`/`useSceneUuidBinding` resolved uuid never detached on unmount (unbounded `scene.uuids` growth) — same root as M15.
- `LayerScene` has no `clear()` — stale walk/entries/uuids leak across destroy/re-arm.

## Interleaving (fix batch 3, before Phase 7)

Correctness first: M15, M16 (destroy/teardown), M1+M2 (controller dedup), M3+M4
(marker synchronization), M5+M6 (helpers), M8+M7 (listener restore/leak), M13 (restore
runFragmentUuid fallback or reject), M14 (SharedLayer order-hint gate).
Then tests: M11, M12, S9, S1. Then polish: M9, M10, S2–S8, S10–S12.
Phase 7 docs unchanged; CRITICAL #4 + S19 still owned by Phase 7.

## Fix batch 3 resolution (commits `bfe62da`, `2654b77`)

### Addressed
- M1, M2 (controller empty-plan → verify(), dedup), M3, M4 (marker index/positionIndex
  under layer monitor), M5, M6 (helpers), M7, M8 (listener leak doc + restore guard),
  M9, M10, S6 (errorMessage/getName + rollback future log), M13 (restore runFragmentUuid
  fallback), M14 (SharedLayer order-hint gate via useSceneFragmentReady), M11, M12, S9, S1
  (test fixes + reorder-plan coverage), S8, S10, S12 (comments + example cap).

### Deferred (low-risk, documented)
- S2, S3, S4, S5 — MarkerLayerManager sort/scan/lock refactors (deadlock/perf-sensitive)
- S7 — MapsforgeVtmView CHM-vs-UI-thread: documented in M7 (kept CHM, access is UI-thread)
- S11 — useNativeLayerLifecycle triggerCreate/enabled coupling (core lifecycle, risky)

Device-verified: layer-order-verification (JS 5 = Native 5, SharedLayer grouping intact
with the M14 gate), manyLayers (appliedMatchesExpected: true, notInPlanCount: 0),
multi-map (Map A renders, no listener clobber). No mismatch/W-level ZOMBIE.

---

# Code Review #4: fix batch 3 (post-Phase-7)

Range reviewed: `7c4e691..27b670f` (fix batch 3 code + tests, excluding docs). 6 parallel
read-only subagents (controller+marker, helpers+views, tests, example, components, scene).
Review marks updated to `3648398`.

- **0 critical, 6 minor, ~10 suggestion findings** (plus pre-existing notes).
- Findings fed fix batch 4 (committed as `3648398`).

## Minor (fixed in `3648398`)

| # | File | Summary |
|---|---|---|
| M1 | `SceneSync.ts` | `destroyed` boolean guard reset on re-arm → stale reorder resolution clobbered state. Fixed with a `reorderSeq` generation counter (mirrors `walkSeq`). |
| M2 | `MarkerLayerManager.java` | `applyEntryPriorities` positionIndex writes inside the lock could abort mid-lock on a malformed assignment. Fixed by pre-validating assignments before the lock. |
| M3 | `MarkerLayerManager.java` | `synchronized(layer)` around `indexOf` was vestigial (vtm's hit-test doesn't lock). Fixed the overstating comment. |
| M4 | `MapsforgeVtmView.java` | M8 restore-guard conflated "reflection blocked" with "listener changed", leaving a stale empty composite. Fixed: restore when the slot reads null or still holds our composite. |
| M5 | `PathLayerManagerTest.java` | `verify(responseMap, never()).putString("error",…)` was tautological. Replaced with `putArray(eq("results"), any())`. |
| M6 | `LayerPath/Shape/Marker.tsx` | `runUuidFor(anchorUid)` fallback self-keyed non-first type-run members. Fixed with `runFragmentUuid ?? scene.plan().runKeysByAnchor.get(anchorUid) ?? runUuidFor(anchorUid)`. |

## Pre-existing (addressed)

- `PriorityAllocator` never reset on `destroy()` → entry priorities not re-applied to
  re-created fragments on StrictMode re-arm. Fixed with `PriorityAllocator.reset()` + call in `destroy()`.

## Deferred (documented, not fixed)

- `useSceneFragmentReady.getSnapshot` calls `scene.plan()` during render → O(N²) hot path
  (consider a `hasFragment()` accessor).
- vtm `ItemizedLayer.activateSelectedItems` hit-test reads `mItemList` without a monitor —
  needs a vendored `ItemizedLayer` override (out of scope).
- `LayerScene.clear()` does not notify listeners (documented; always followed by a notifying `applyWalk`).

---

# Pre-Phase-8 polish (backlog resolution)

Status: **implemented** (commits `6dd9544`, `ca8057f`, `0601964`, `0bfc3bd`, `6391e3d`).
Device smoke **passed** (Pixel 6 Pro, Android 13).

## Device smoke results

- **manyLayers · SharedLayer ON · 50**: `appliedMatchesExpected: true`, `notInPlanCount: 0`,
  2 fragments (path 50 / marker 50), `pendingMutations: 0`.
- **manyLayers · SharedLayer OFF · 50**: 100 dedicated layers, JS=Native=101, no crash/mismatch.
- **manyLayers · SharedLayer ON · 1000**: 2000 drawables → 2 fragments, `appliedMatchesExpected:
  true`, `notInPlanCount: 0`, `resolvedCount: 2000`, `pendingMutations: 0`, no ZOMBIE/crash.
- **markers hit-test**: tap Cairo → `itemSingleTapUp Cairo` (ItemizedLayer.onGesture sync override
  fires; no CME).
- **layer-order-verification**: SharedLayer OFF interleaved 6 items render in strict JSX order
  (shape → path → marker → shape → path → marker), Native=8; SharedLayer ON collapses to 3
  fragments with the marker fragment at `1/2`.

## Addressed

- **A (correctness)**: LayerMarker spurious `test-uuid` emit removed; LayerZoomBoundsHelper
  initial zoom uses `getZoomLevel()`; ItemizedLayer.onGesture synchronized override closes the
  marker hit-test CME race; useNativeLayerLifecycle guards on `enabledRef` (S11).
- **B (perf)**: LayerScene `hasFragment()` + `fragmentUuids` Set → O(1) `useSceneFragmentReady`.
- **C (marker refactors)**: S2/S3 removed the dead create-time sort+scan (entries append; real
  order via applyEntryPriorities); S5 shrinks applyEntryPriorities to an O(n) swap (sort outside
  the lock) and corrected ordering to ascending positionIndex. S4 resolved by A's onGesture override.
- **D (tests)**: S16 batch-split test **also fixed a real drain-loop off-by-one** (one mutation
  dropped per full batch); S25 microtask-wins re-arm test; S29 max-wait test; 4 @Ignore'd marker
  tests revived.
- **E (cosmetic/docs)**: M6 dead-fallback comments; MapsforgeVtmView move-signal coverage javadoc.

## Deferred (documented, not fixed)

- S23 `planWithResolved` caching for bulk creates (create-phase O(N²), lower priority than the
  render-phase fix in B).
- S29 stale-commit test (needs PriorityAllocator internal-state exposure).
- MapsforgeVtmView descendant move-signal mechanism (javadoc corrected; mechanism deferred).
- fragment entry-list dump (aspirational).

---

# Fix batch 5 — post-8d review findings

Status: **implemented** (commits `67546d2`, `0f51aa6`, `55bf1da`, `2b648fa`).

Findings from the code review of `3648398..HEAD` (Phase 8 work). Resolution:

## Addressed

- **CRITICAL #1 — marker swap race**: `applyEntryPriorities` now preserves any item added to the
  live `ItemizedLayer` after the `allMarkers` snapshot (untracked-live-item), so the
  `clear()`+`addAll()` swap can't drop a mid-create marker. Regression test added. (Latent under
  the current single-threaded native-modules dispatch, but hardened regardless.)
- **MINOR #1/#2 — busy-signal edge cases**: `refreshBusy()` now recomputes after arming the
  walk-retry timer; the scene-subscriber callback guards against post-destroy batched
  notifications re-arming the debouncer. Tests for both.
- **MINOR #3 — memo growth**: `planWithResolved` memo capped (blunt clear at 64 keys; a
  version-window sweep was rejected as it would evict mid-burst).
- **MINOR #4/#6 — update-path no-response**: `updatePaths` logs (not errors) a null update (benign
  remove/update race); the JS queue resolves a synthesized minimal response instead of `undefined`.
- **MINOR #5 — trigger CME**: `triggerGroupEvent`/`triggerAllMarkers` read a defensive copy of
  `getItemList()` under the layer monitor.
- **MINOR #7 — NPE guard**: `LayerPath.updateLayers`/`updateCoordinates` null-guard
  `mapFragment.getActivity()`.
- **SUGGESTIONs**: perf-test header comment; drain microtask identity guard; stale
  descending/sorted-insertion comments + `insertMarkerSorted`→`appendMarker` rename; duplicated
  `MapsforgeVtmView` javadoc sentence.

## Deferred

- Nothing carried over — all review findings resolved.
