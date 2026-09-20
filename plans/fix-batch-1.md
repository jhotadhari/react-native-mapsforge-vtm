# Plan: Fix Batch 1 (pre-Phase-5 correctness foundation)

Status: **planned — implementation pending.**
Companion docs: `layer-ordering-rewrite.md` (roadmap), `layer-ordering-rewrite-code-review.md`
(finding details).

## A. CRITICAL #1 — scene-authoritative fragment uuids (approach b)

**Problem:** standalone type-run members self-key `run:<own-uid>` at create time while the plan
keys runs by the first member — members 2..N get unmanaged native layers and the type-run
collapse is dead. **Approach (e) (native merging) explicitly rejected** — the native side has no
run knowledge; (b) needs zero native changes (`ensureSharedLayer` already dedupes by uuid).

| File | Change |
|---|---|
| `src/scene/types.ts` | `LayerPlan.runKeysByAnchor: Map<anchorUid, fragmentUuid>` |
| `src/scene/planBuilder.ts` | build the map in `buildPlan` (every run member → `runUuidFor(firstUid)`); cache rides on `plan()`; **also MINOR #13** here: nested-scope order propagation — `blockStartOf` walks up the scope chain (unordered inner scope inherits the outer scope's position/order); fix the `o2`-vs-`i1` block-ordering test to intended semantics |
| `src/compose/useSceneFragmentUuid.ts` (new) | `useSyncExternalStore` over `scene.subscribe`; snapshot = map lookup (string \| null) |
| `src/components/LayerPath.tsx`, `LayerShape.tsx`, `Marker.tsx` | standalone path: `enabled` gains `resolvedFragmentUuid !== null`; `create` uses the resolved uuid (drop `runUuidFor(anchorUid)`); recreate-on-key-change effect (used-uuid ref + `triggerRemove → triggerCreate`, mapsforge pattern) |
| Tests | `planBuilder`: map correctness, single-member self-mapping, first-member-removal re-key, nested-scope+order; **Robolectric collapse test**: `createPaths` with 2 items sharing one uuid → 1 entry in `sharedLayerFragments`, 2 drawables in the mock layer |

**Dependency:** implement together with **D-2** (walk-failure retry) — gated creation makes a
dropped walk load-bearing.

## B. CRITICAL #2 — marker rebuild synchronization

`MarkerLayerManager.applyEntryPriorities`: build the ordered list locally, apply under
`synchronized (layer) { remove + re-add }` — serializes against vtm's own synchronized API.

## C. CRITICAL #3 (+ MINOR #22) — hierarchy-listener lifecycle

`MapsforgeVtmView`:
- cache the wrapper `ViewGroup` in a field at install; clear via the cache in **both**
  `onDetachedFromWindow()` and `destroy()` (no `getParent()` dependency → no leak)
- emit `onAnchorsChanged` from `onAttachedToWindow` (re-walk heals detached-phase moves)
- save/restore any pre-existing wrapper listener (#22)
- extend the comment: coverage = direct-child anchors, attached-only

## D. SceneSync hardening (MINOR #1–6)

| # | Fix |
|---|---|
| 1 | `createDebouncer`: pending-burst flag — max-wait fires only if the debounce hasn't flushed |
| 2 | `walk()` catch → reschedule with small backoff while `!destroyed` |
| 3 | reorder failure → capped retries with backoff; stop after N consecutive failures until the next scene mutation |
| 4 | split `PriorityAllocator.changedFor` into **compute (pure) + commit**; commit only after native success; keep pending per-fragment changes for retry |
| 5 | `destroy()` re-arms: `setNativeNodeHandle(non-null)` resets `destroyed = false` (StrictMode-safe) |
| 6 | walk in-flight guard with sequence number — stale resolutions discarded |

Tests: extend `SceneSync.test.ts` — maxWait single-fire, enumerate rejection retry, reorder
retry cap, allocator-commit-on-failure, destroy re-arm.

## E. Queue fixes (MINOR #10–12)

- `EntryBatchQueue`: timer callback resets `maxWaitTimer = null` before the guard (#10)
- `PathBatchQueue`/`ShapeBatchQueue` `resolveCreate`: throw descriptive error when `response`
  missing (#11 — prevents zombie entries)
- `drainQueue`: **resolve** pending removes (native teardown already destroyed the shared
  layers — no spurious "Map view destroyed" `onError`); keep rejecting creates (#12)

## F. Small JS fixes (MINOR #9, #23)

- Fast-Refresh-safe prefixes (`Date.now()_random`) for `sharedLayerCounter`,
  `anchorUidCounter`, `entryUidCounter` (#9)
- `useLayerEntry`: attach effect gated on `active && fragmentId !== null` (#23)

## G. Small native fixes (MINOR #17, #18, #20, #21)

- all three managers: skip assignments where `!entry.fragmentUuid.equals(fragmentUuid)` (#17)
- batch error capture: `errors[i] = msg != null ? msg : e.getClass().getSimpleName()` (#18)
- `PathLayerManager.DEFAULT_FRAGMENT_UUID` constant, used in all 4 spots (#20)
- `VtmAnchorViewManager.collectAnchorUids`: iterative work-stack traversal (#21)

## H. Example extension — `layer-order-verification`

New **"Type-run (standalone same-type)"** section, always outside the
SharedLayer/ReindexScope wrappers (unaffected by the toggles):
- two adjacent standalone `LayerPath`s rendered between `<LayerBitmapTile/>` and `{children}`
  → 2-member type-run
- controls: **"Add path"** (append → N+1), **"Remove first"** (re-key), **"Move last to
  front"** (reorder)
- asserts: debug tree run row shows `1/N` growing/shrinking; `getDebugLayerDump` native count
  = 1 for the run through all operations; no warnings; existing toggles still 4/4 and 7/7

## Commits (each gated: `yarn typecheck` / `yarn lint` / `yarn test` + gradle unit tests)

1. `fix(scene): scene-authoritative fragment uuids, run collapse restored, nested-scope ordering` (A + D-2 + #13)
2. `fix(native): synchronize marker rebuild, listener lifecycle, small native fixes` (B, C, G)
3. `fix(sync): SceneSync debounce/retry/commit hardening` (D 1,3,4,5,6)
4. `fix(queues): max-wait, response guards, teardown drain, uid prefixes` (E, F)
5. `test(examples): type-run repro controls in layer-order-verification` (H)

## Final device gate

`yarn prepare` first (Metro serves from `lib/module`), then: consecutive-pair matrix on
`layer-order-verification` + existing examples (4/4, 7/7, many-layers 3/3, many-shapes,
swapOrder, markers) + logcat clean.

## Deferred (unchanged)

- **Batch 2 (post-Phase 5):** MINOR #14, #15, #16, #19 + re-review of findings Phase 5 may obsolete
- **Phase 6–8:** CRITICAL #4, SUGGESTION #5, docs, remaining suggestions
