# Plan: Phase 5 — LayerStackController (absolute-plan applier)

Status: **implemented + device-verified** (commits below).
Companion docs: `layer-ordering-rewrite.md` (roadmap), `layer-ordering-rewrite-code-review.md`
(findings), `fix-batch-1.md` (done).

## Implemented (commits)

1. `60cb00b` — `LayerStackController` class (knownLayers, applyPlan with LIS + CLEAR_EVENT,
   removeAll, removeLayerSync, verify self-check with deduped mismatch log); MapMutationQueue
   reduced to the concurrency shell; LIS test retargeted; 12 new controller tests
2. `46cd71e` — `positionByUuid`/`BASE_POSITION`/incremental insertion deleted; adds append;
   deprecated sync `LayerHelper.addLayer`/`removeLayer`/`getLayerIndexInMapLayers` DELETED
   (no BC — lockstep in Phase 8); `LayerZoomBoundsHelper` async overrides; modules migrated;
   vestigial `positionIndex` removed from all 9 JS specs
3. `9dd44d0` — create calls carry the desired absolute order: `LayerScene.planWithResolved`,
   `layerUuids` in all create params (JS specs + native `Utils.rMapGetStringList`),
   `MapMutationQueue.AddLayer.desiredOrder`, flush applies the LAST plan-bearing mutation
   in the batch — atomic create+apply. **Deviation:** removals deliberately do NOT carry
   plans (the removed layer disappears in the same flush; remaining-layer reorder is
   SceneSync's job).
4. `64dbce0` — self-check surfaced in `getDebugLayerDump`
   (`appliedMatchesExpected`/`expectedUuids`/`appliedUuids`)

### Device-gate follow-ups (found during verification)

- `1ea8df2` — **found by the gate**: the run re-key race sent priorities for the new
  fragment before the recreate landed; the managers logged ZOMBIE and resolved anyway, so
  the JS presenter committed state and the priorities were dropped forever. Fix:
  `applyEntryPriorities` returns false on the zombie path and the modules REJECT — the
  presenter keeps the state uncommitted and re-sends on the next mutation (the recreate's
  create resolution is one).
- `68532fc` — the now-expected transient ZOMBIE log downgraded to debug level (still
  greppable, device gates stay clean).

## Device verification (19261FDEE000YM, Android 13, fresh Metro + pm clear)

| Check | Result |
|---|---|
| layer-order-verification fresh | JS 5 / Native 5, run fragment 1/2, prefixed uids ✓ |
| type-run add ×2 → remove-first → move-last-to-front | 3 paths, 5/5, run re-keyed, still 1 fragment ✓ |
| SharedLayer OFF / toggles back | 8/8 → 5/5 ✓ |
| MANYLAYERS stress test | 3/3 ✓ |
| SHARED LAYER GROUPING swap | 2 fragments ✓ |
| MULTI-MAP SYNC | Map A renders; Map B = documented known issue ✓ |
| MARKERS / MANY SHAPES | render clean ✓ |
| LayerStackController mismatch logs | 0 everywhere ✓ |
| W-level ZOMBIE warnings | 0 (re-key zombie self-healing at debug level) ✓ |
| LogBox teardown error | same pre-existing navigation-teardown noise as batch-1 gate (unrelated) ✓ |

## Goal

Delete the incremental `positionIndex`/`positionByUuid` insertion machinery and make the
native side a dumb absolute-plan applier: JS sends the complete desired layer order with
every create/remove (**atomic create+apply** — a new layer is placed at its final position in
the same flush that adds it), and the native side verifies the applied stack after each plan
application (**self-check**).

## Design overview

```
JS                                       Native (UI-thread flush)
─ SceneSync (reorderLayers, unchanged) ─┐
─ components' create/remove enqueue ────┼──> MapMutationQueue (serialization shell)
   with layerUuids = target plan ───────┘       │  1. removals
                                               │  2. adds (append)
                                               │  3. LayerStackController.applyPlan(target)
                                               │     (LIS minimal moves, last plan wins)
                                               │  4. updateMap() once
                                               │  5. LayerStackController.verify(target) → self-check
```

- `LayerStackController` = **new class** (decision confirmed) owning `knownLayers`,
  `previouslyReorderedUuids`, plan application (LIS reorder + CLEAR_EVENT logic), add/remove
  registry bookkeeping, `removeLayerSync` (teardown), and `verify()` (self-check).
- `MapMutationQueue` shrinks to the concurrency shell: pending queue, batch cap (25),
  `flushScheduled` coalescing, futures, `destroy()`/reject. All stack state delegates to the
  controller.
- Plan = `List<String>` of fragment/dedicated uuids in bottom→top order, exactly what
  `SceneSync.reorderLayers` already sends. The **last** plan-bearing mutation in a batch wins.

## Native changes

### 1. New `LayerStackController` (android …/vtm/LayerStackController.java)
- `ConcurrentHashMap<String, Layer> knownLayers` (readable from any thread — keeps
  `LayerHelper.getLayer/getLayers` working), `Set<String> previouslyReorderedUuids`.
- `applyPlan(List<String> orderedUuids)` — moved from `MapMutationQueue.flush()` step 3:
  resolve uuids → CLEAR_EVENT for newly-tracked layers → `reorderMinimalMoves` (moved
  verbatim, incl. the first-element guard that never inserts before `GestureLayer`/vtm-internal
  layers).
- `register(layer, uuid)`, `unregister(uuid)`, `removeLayerSync(uuid)`, `getKnownLayers()`.
- `verify(target) -> {matches, expectedUuids, appliedUuids}` — compares JS-managed layers in
  `map.layers()` against target; log a "LayerStackController mismatch" warning only when the
  mismatch signature differs from the previous one (no log spam); store last result for the
  debug dump.

### 2. `MapMutationQueue`
- Delete: `positionByUuid`, `AddLayer.positionIndex`, flush step-2 incremental scan,
  `knownLayers`/`previouslyReorderedUuids` fields (move to controller).
- `enqueueAddLayer(Layer, String uuid, @Nullable List<String> desiredOrder)`;
  `enqueueRemoveLayer(String uuid, @Nullable List<String> desiredOrder)`;
  `enqueueReorderLayers(...)` unchanged. Adds append; the flush applies the batch's last
  desired plan.
- `destroy()` clears controller state too. `getPendingCount()` stays.

### 3. `LayerManager` + per-type managers
- Constructor drops `basePositionIndex`; delete `BASE_POSITION` from `PathLayerManager` /
  `MarkerLayerManager` / `ShapeLayerManager`.
- `ensureSharedLayer(fragmentUuid, desiredOrder)` — forwards to `enqueueAddLayer`.

### 4. `LayerHelper` / `LayerZoomBoundsHelper` — deprecated sync API DELETED
Decision confirmed: we own straymap + all ext packages, no backwards compatibility needed
(lockstep update in Phase 8).
- **Delete** `LayerHelper.addLayer` / `removeLayer` sync methods and
  `getLayerIndexInMapLayers` — the direct `map.layers()` mutation path ceases to exist; the
  queue becomes the only mutation path.
- `addLayerAsync(layer, params, uuid, desiredOrder)` — drops `positionIndex` parsing.
  `removeLayerAsync(params, desiredOrder)`.
- `LayerZoomBoundsHelper` reworked to async overrides: `addLayerAsync(...)` attaches the zoom
  listener in `future.thenAccept(...)` (once the layer is in `knownLayers`);
  `removeLayerAsync(...)` unbinds the listener first. Its 6 consumers migrate:
  LayerScalebar, LayerHillshading, LayerMapsforge ×3, LayerBitmapTile, LayerMBTilesBitmap
  (LayerPathJts already async). `LayerScalebar.removeLayer` migrates too.

### 5. Module create/remove pass-through
- Dedicated modules (above list): read optional `layerUuids` from create/remove params, forward.
- Batch modules (LayerPath.createLayers/removeLayers, LayerShape, LayerMarker.createMarkers/
  removeMarkers): read `layerUuids` from batch params, thread to
  `ensureSharedLayer`/removals.

### 6. `getDebugLayerDump` (MapContainer.java ~:519)
Add `appliedMatchesExpected`, `expectedUuids`, `appliedUuids` from the controller's last
verify.

## JS changes

### 7. `LayerScene.planWithResolved(key: string): LayerPlan`
Pure, uncached: builds a plan as if `uuids` contains `key` (the plan only tests `has()`, so the
value is irrelevant). Returns the exact target stack including the not-yet-resolved fragment at
its correct tree position. ~10 lines + tests (type-run member, SharedLayer child, owner gating).

### 8. Components compute the target list at enqueue time
(deterministic-uuid contract makes this exact)
- Standalone `LayerPath`/`LayerShape`/`Marker`: `layerUuids =
  scene.planWithResolved(anchorUid).layers.map(l => l.uuid)`.
- SharedLayer children: key = entryUid from `useLayerEntry`.
- `LayerMarker` group: key = its anchorUid.
- Dedicated layers (LayerBitmapTile etc.): key = anchorUid.
- All create params gain `layerUuids?: ReadonlyArray<string>`; removals too.

### 9. Codegen specs
`NativeLayerPath/Shape/Marker.ts` + dedicated-layer specs: add
`layerUuids?: ReadonlyArray<string>` to create/remove params → `yarn prepare` regenerates.

### 10. Batch queues
`EntryBatchQueue` items forward `layerUuids`. `SceneSync` itself is unchanged (its
`reorderLayers` remains the pure-reorder path; create-carried plans make the add+apply atomic
and render it redundant for the common case).

## Self-check semantics

- Runs only after a plan application, on the UI thread, O(n).
- Mismatch = JS-managed layers in `map.layers()` (excluding vtm-internal) don't equal the
  target plan.
- Deduped warning log + surfaced in `getDebugLayerDump` (exercised by the many-layers 🐛 Dump
  button and dev tools).

## Deletion checklist (what dies in this phase)

`positionByUuid`, `positionIndex` params in all layer specs, `BASE_POSITION` ×3,
`PathLayerManagerTest.basePosition_isOneBelowMaxInt`, flush step-2 scan, `LayerHelper` sync
add/remove + direct mutation path, `LayerZoomBoundsHelper` sync overrides,
`getLayerIndexInMapLayers`.

## Tests

- New `LayerStackControllerTest` (Robolectric): applyPlan minimal moves;
  first-element-never-before-GestureLayer; verify() match/mismatch; removeLayerSync.
- `MapMutationQueueTest`: rewrite `positionIndexOrdering` → `desiredOrder_placesAddAtomically`
  (add with plan lands at final position in ONE flush); plan last-wins across add+reorder in
  one batch; remove+plan; destroy clears controller.
- `MapMutationQueueLISTest`: unchanged (helper moved).
- `PathLayerManagerTest`: drop `BASE_POSITION` test; batch create with `layerUuids`.
- JS: `LayerScene.planWithResolved` tests; batch-queue `layerUuids` forwarding; existing
  `SceneSync` tests stay green.

## Device gate

Full matrix: type-run repro (add/remove-first/move, still 1 fragment), toggles 5/5 + 8/8,
MANYLAYERS 3/3, shared-layer-grouping swap, markers, many-shapes, **multi-map** (queue is
per-handle — regression-prone), logcat clean (incl. no LayerStackController mismatches),
visual: no z-order flash while tiles/layers load.

## Commit sequence

1. `feat(stack): LayerStackController — absolute-plan applier with self-check`
   (controller + queue refactor + tests)
2. `refactor(stack): drop positionByUuid, BASE_POSITION and incremental insertion`
   (managers, LayerHelper/LayerZoomBoundsHelper, modules, sync-API deletion)
3. `feat(js): create/remove carry the desired layer order`
   (planWithResolved, components, queues, specs, tests)
4. `feat(debug): stack self-check surfaced in getDebugLayerDump`
5. `docs(plans): phase 5 results` after the device gate

## Phase 8 lockstep note

The deleted `LayerHelper.addLayer`/`removeLayer` sync API goes on the lockstep checklist:
straymap, ext-path-color-ramp, ext-grib must not call it (they're updated in the same
release).

Then **fix batch 2** (per plan): #14 (createLayers destroyed-map error synthesis), #15
(per-item response inside error capture), #16 (marker tie-break determinism), #19 (hoist
syncGestureSupport out of per-item loops), plus a re-review of the Phase 5 diff.
