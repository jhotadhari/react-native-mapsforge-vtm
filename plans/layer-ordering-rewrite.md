# Plan: Layer-Ordering System Rewrite

## Context

`react-native-mapsforge-vtm`'s layer-ordering system (cursor chain, sentinels, per-scope
generations, fragment indices, `positionIndex`/`positionByUuid` native insertion) was a grown
spaghetti monster that repeatedly produced z-order bugs ("lines jump behind the base layers" in
straymap). Each fix was a monkey patch over the previous one.

This rewrite replaces the whole system: order becomes a **function of committed state**, never
accumulated during render.

- **Branch:** `feature/layer-ordering-rewrite` (from `development`)
- **Release:** breaking 0.9.0, consumers updated in lockstep (straymap,
  ext-path-color-ramp, ext-grib — all local repos)
- **Companion plans:** `layer-ordering-rewrite-code-review.md` (review findings + interleaving),
  `fix-batch-1.md` (current execution plan)

## Agreed architecture

| Piece | From |
|---|---|
| `LayerScene` model, commit-phase-only mutations, command log, plan snapshots, presenter | Option B (OOP retained-mode model) |
| Tree-order default via committed-tree anchor walk | new (added because tree order must stay the default) |
| `order` prop as optional explicit override on `ReindexScope` | Option A (demoted to override) |
| Deterministic fragment keys (`frag:<owner>:<type>`, `run:<anchor>`) | Option D |
| Native absolute-plan applier (`LayerStackController`) | orthogonal redesign |
| Descriptor-array API | **Option C — permanently rejected by the user** |

### Two-level split

| Level | What orders it | Source of truth |
|---|---|---|
| Layer stack (~5–30 native layers: scopes, fragments, dedicated layers) | anchor walk over the committed tree, after commit | `LayerScene.plan()` |
| Drawables inside a fragment (1000s of entries) | fragment owner declares ordered entry keys (`vtmSortIndex`); walk order for type-run entries | same plan, per-fragment entry lists |

### Mechanics

- **Anchors:** `VtmAnchorView` host component (invisible 0×0 view) rendered by scopes, fragment
  owners (`SharedLayer`/`LayerMarker`) and standalone layer components. `MapContainer.enumerateAnchors`
  walks the committed view tree under the wrapper View (skipping the `MapsforgeVtmView` subtree)
  and returns ordered uids. A hierarchy-change listener emits `onAnchorsChanged` as a move signal.
- **Scene:** commit-phase mutations only (`useLayoutEffect`/`useEffect` — never render).
  `applyWalk`, `declareEntry`, `attachUuid`, `plan()`, command log, observers.
- **Presenter (`SceneSync`):** debounced walk (16ms/250ms) → `applyWalk` → `plan()` →
  `diffPlans` → absolute `reorderLayers` + `applyEntryPriorities` (single-flight, retry).
- **Entries:** sparse int drawable priorities (`PriorityAllocator`: step 1000, midpoint insert,
  renumber on exhaustion → O(changed) add/remove). Owner injection via `injectVtmSortIndex`
  (recursive through arrays + Fragments; explicit `order` prop as escape hatch).
- **Batching:** generic `EntryBatchQueue` → `createLayers`/`removeLayers` per type
  (paths, shapes, markers).

## Semantic contract

| # | Decision |
|---|---|
| S1 | Tree order = default; `ReindexScope order` = optional override |
| S2 | Unordered scopes sit at their tree position (was "appended at end") |
| S3 | `SharedLayer` fragments: one per layerType, ordered by first occurrence |
| S4 | Same-type consecutive standalone layers share **type-run fragments** (keyed by first member) |
| S5 | Entry order: owner-injected `vtmSortIndex`; explicit `order` prop wins; unindexed sort by declaration order |
| S6 | `ReindexScope` renders a sibling anchor + context provider (anchor IS the sentinel) |
| S7 | `Marker` order inside `LayerMarker` = owner injection |
| S8 | `useNativeLayerLifecycle` kept untouched |

## Roadmap

| Phase | Content | Status |
|---|---|---|
| 0 | Branch + baseline (repaired broken test baseline) | ✅ done |
| 1 | Pure scene model + unit tests | ✅ done |
| 2 | `VtmAnchorView` + walk + move signal | ✅ done |
| 3 | React bindings + component rewrites | ✅ done |
| 3.5 | Fragment-injection fix + `order` prop | ✅ done |
| 4A | Sparse priorities applied natively | ✅ done |
| 4B | Generic batching (paths+shapes, marker migration) | ✅ done |
| — | **Fix batch 1** (review findings, before Phase 5) | ✅ done — commits `d6405c1`..`c9f1987`, device-verified |
| 5 | `LayerStackController` (absolute plans, `positionByUuid` removal, atomic create+apply, self-check) | ✅ done — commits `60cb00b`..`68532fc`, device-verified |
| — | **Fix batch 2** (post-Phase-5 findings + re-review) | ✅ done — commits `786fccf`..`60d35a3`, device-verified |
| 6 | Debug refinement (fragment entry lists, expected-vs-applied dump) | ✅ done — commits `c69343d`..`64c0164`, device-verified |
| 7 | Docs rewrite (`layer-ordering.md`, `AGENTS.md`, extending.md), example verification matrix | pending |
| 8 | Lockstep consumers (straymap, ext-path-color-ramp, ext-grib) + breaking release | pending |

## Commits so far

```
eb35439 feat(batching): generic entry-batch queue for paths + shapes, marker queue migrated
742d38e feat(priorities): apply sparse drawable priorities natively (applyEntryPriorities)
110ad59 fix(entries): recursive vtmSortIndex injection through Fragments + explicit order prop
317110e feat(bindings): commit-phase scene wiring — anchors, entries, SceneSync, rewritten components
86ce908 feat(anchors): VtmAnchorView host component + committed-tree walk + move signal
32f2483 feat(scene): pure LayerScene ordering model with plan builder, diff, priority allocator
8100019 fix(tests): repair broken unit-test baseline
```

## Key files

**JS (new):** `src/scene/{types,ids,LayerScene,planBuilder,planDiff,PriorityAllocator,
priorityHandlers,SceneSync}.ts`, `src/compose/{useLayerAnchor,useLayerEntry,useSceneUuidBinding,
injectVtmSortIndex,EntryBatchQueue,PathBatchQueue,ShapeBatchQueue}.ts`,
`src/NativeViews/VtmAnchorViewNativeComponent.ts`

**JS (deleted):** `src/compose/useLayerOrder.ts`, the registry half of `MapHandleContext.ts`

**Native (new):** `views/VtmAnchorView.java`, `views/VtmAnchorViewManager.java`

**Native (changed):** `MapMutationQueue` (still pre-Phase-5), `MapContainer` module
(`enumerateAnchors`), `MapsforgeVtmView` (move signal), the three managers, the three layer modules

## Environment notes

- `node_modules/react-native-mapsforge-vtm` was a stale 0.8.1 copy shadowing the workspace —
  replaced with a symlink to the repo root (uncommitted, local-only fix)
- Metro resolves through `lib/module` (package `main`) — run `yarn prepare` before device checks
- Device: `19261FDEE000YM`; `adb reverse tcp:8081 tcp:8081` after `adb root` (kills the reverse)
- Android unit tests: `./gradlew :react-native-mapsforge-vtm:testDebugUnitTest` from `example/android`
