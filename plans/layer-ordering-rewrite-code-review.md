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
