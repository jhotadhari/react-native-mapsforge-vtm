# Plan: Fix Batch 2 (post-Phase-5 findings + re-review)

Status: **planned — implementation pending.**
Companion docs: `layer-ordering-rewrite.md` (roadmap), `layer-ordering-rewrite-code-review.md`
(findings), `phase-5-layer-stack-controller.md` (done).

## A. Known findings

### A1. #14 — destroyed-map synthesis for batch creates
`createLayers`/`createShapes`/`createMarkers` lack the graceful teardown branch their
`removeLayers` siblings have — the ensure pre-loop throws → whole-batch reject, or silent
success on a destroyed map with a leaked re-created manager.
Fix: per-fragment ensure wrapped in try/catch (failure recorded per fragment); per-item
create loop skips items whose fragment failed and fills `errors[i]`; response loop emits
per-item errors — the batch promise never rejects wholesale. **Batch methods only**
(decision confirmed); single creates keep the clean null-mapView reject.

### A2. #15 — response building outside error capture
`PathLayerManager.createPaths`' response loop can throw (`buildCreateResponse` →
`addResponseData` → `getInt("coordinates"/"bounds")` throws `NoSuchKeyException` on a
partial JS `responseInclude`) AFTER entries were created → whole-batch reject → zombies.
Fix: hardening, not error synthesis — `rMapHasKey` guards (+ defaults) in `addResponseData`
so a successfully-created entry always yields a valid response. Also defensive guards in the
marker batch's unprotected insertion/tracking loops (mid-batch throw = untracked zombies).

### A3. #16 — deterministic marker tie-break
`applyEntryPriorities`' `fragmentEntries.sort` returns 0 on equal `positionIndex`; input
order is `ConcurrentHashMap` iteration → nondeterministic.
Fix: monotonic `entrySeq` (`AtomicLong`) on `MarkerEntry`; tie-break `seq` ascending —
**earlier-created on top**, mirroring `createMarkers`' source-order tie convention
(decision confirmed; markers are always in shared `ItemizedLayer` fragments — the
layer-stack order stays plan/tree-driven, this is within-fragment order only).

### A4. #19 — hoist `syncGestureSupport`
`createPaths`/`removePaths` (path) + `createShapes`/`removeShapes` (shape) call the sync
override per item → O(n² + n·f).
Fix: batch loops call `super.create`/`super.remove` (no sync) + ONE `syncGestureSupport()`
after the loop; single-entry module calls keep the overrides.

## B. New findings (Phase 5 re-review)

### B1. (minor, must fix) `'virtual'` uuid leaks into dedicated-layer plans
`planWithResolved`'s sentinel value is pushed into the plan for dedicated layers
(`planBuilder` uses `uuids.get()` for `kind: 'dedicated'`); native registers under a random
uuid → `applyPlan` skips it, layer appends, `verify` flags a spurious mismatch on every
dedicated create (dedup key contains the fresh random uuid → never deduped).
Fix: dedicated components (`LayerBitmapTile`, `LayerMapsforge` + 3 sub-layers,
`LayerHillshading`, `LayerMBTilesBitmap`, `LayerScalebar`, `LayerPathJts`) stop sending
`layerUuids` — a dedicated layer's uuid is unknowable at enqueue time, so atomic placement
is impossible for them anyway. Remove the now-unused `layerUuids` from their specs + the
`LayerPathJts` native passthrough dead code.

### B2. (minor, accepted) Partial-plan multi-fragment batches
Last-plan-wins means siblings of the winning create in the same flush stay appended
(transient, SceneSync reorder heals ≤250 ms). Accepted; B3's verify redesign stops the
self-check from flagging it.

### B3. (must fix) `verify()` redesign — compare resolvable relative order
New semantics: `expected` = target uuids that resolve to registered layers (target order);
`applied` = map's JS-managed sequence filtered to that set; compare. Real desyncs still
detected; unknown uuids and plan-absent siblings ignored.
Also: `lastVerifyResult` → `volatile`; `previouslyReorderedUuids.addAll` → resolved uuids only.

### B4. (suggestion) stale javadoc
`MapMutationQueue.enqueueRemoveLayer` (deleted sync API), `LayerManager`
(`#ensureSharedLayer(String)` link, `"positionIndex"` mention). Docs/AGENTS.md → Phase 7.

## Accepted transient (decision confirmed)

Grouped cold-mount first entries and dedicated layers keep append-then-reorder
(SceneSync heals in ≤250 ms); the self-check no longer flags it. No hook reordering
(uuid circularity in `useLayerEntry`, still blocked by the async walk on cold mount).

## Commits

1. `fix(stack): self-check compares resolvable relative order + dedup/volatile hygiene` (B3 + tests)
2. `fix(batch): destroyed-map synthesis + response-payload guards in batch creates` (A1 + A2 + tests)
3. `fix(markers): deterministic creation-sequence tie-break` (A3 + tests)
4. `perf(batch): hoist syncGestureSupport out of per-item loops` (A4 + tests)
5. `refactor(js): dedicated layers stop carrying create plans` (B1 + LayerPathJts + specs)
6. `chore(docs): stale javadoc cleanup` (B4)
7. `docs(plans): fix batch 2 results` after the device gate

## Device gate

Full matrix (type-run ops, toggles 5/5 + 8/8, MANYLAYERS 3/3, grouping swap, markers,
many-shapes, multi-map) + zero `LayerStackController mismatch` warnings +
`getDebugLayerDump` `appliedMatchesExpected: true` after a full screen load.
