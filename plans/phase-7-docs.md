# Plan: Phase 7 — Documentation rewrite

Status: **pending.**

The layer-ordering rewrite (phases 1–6, branch `feature/layer-ordering-rewrite`) replaced the
cursor-chain / `LayerOrderRegistry` / `useLayerOrder` / `positionIndex` architecture with a
scene-based model (`LayerScene` + `SceneSync` + anchors). The docs and `AGENTS.md` still
describe the old architecture — this is the S19 finding from review #2 plus the lingering
CRITICAL #4 (extension-API removal never documented).

Companion docs: `layer-ordering-rewrite.md` (roadmap), `layer-ordering-rewrite-code-review.md`
(findings), `phase-5-layer-stack-controller.md` / `fix-batch-1.md` / `fix-batch-2.md` (done).

## New architecture to document (source of truth)

- `MapHandleContext` provides `{ nativeNodeHandle, scene, sync }` (`scene` = `LayerScene`,
  `sync` = `SceneSync`) — no `registry`.
- **Anchors:** `VtmAnchorView` host component + committed-tree walk (`enumerateAnchors`)
  → `LayerScene.applyWalk(sequence)`.
- **Scene:** `LayerScene` (commit-phase mutations only, `plan()`, monotonic `version()`),
  `planBuilder` (fragment keys `frag:<owner>:<type>` / `run:<anchor>`, type-runs, owner
  expansion), `planDiff` (`layerOrderChanged` + `entryPriorityComputations`).
- **Presenter:** `SceneSync` (debounced walk + single-flight sync + exponential backoff;
  absolute `reorderLayers` + `applyEntryPriorities`).
- **Entries:** `PriorityAllocator` (sparse int priorities → native `applyEntryPriorities`);
  owner injection via `injectVtmSortIndex` / `order` prop.
- **Hooks:** `useLayerAnchor`, `useLayerEntry`, `useSceneUuidBinding`,
  `useSceneFragmentUuid`, `useSceneFragmentReady`, `useNativeLayerLifecycle`.
- **Extension API (public):** `MapHandleContext`, `MapHandleContextValue`, `useLayerAnchor`,
  `UseLayerAnchorOptions`, `useLayerEntry`, `useSceneUuidBinding`, `LayerScene`, `SceneSync`,
  `registerEntryPriorityHandler`, `useNativeLayerLifecycle`, `CreateFlags`, `RemoveFlags`,
  `useMapEventInterval`, `useViewportBbox`, `createMapHandle`, `createMapHandleRegistry`.

## Removed (breaking, 0.9.0)

- `useLayerOrder`, `createLayerOrderRegistry`, `LayerOrderRegistry` (→ `useLayerAnchor` /
  `useLayerEntry` / `useSceneUuidBinding`).
- Create-time `positionIndex` (→ sparse priorities via `applyEntryPriorities`).
- Sync `LayerHelper.addLayer` / `removeLayer` (→ `addLayerAsync` / `removeLayerAsync`).
- `SceneCommand` / `commandLog()` (→ `LayerScene.version()`).
- Fragment-uuid scheme `__vtm_shared_<type>__<index>` (→ `frag:` / `run:`).

## Files, in priority order

1. `AGENTS.md` — rewrite 4 stale sections:
   - `### Wiring layers together: MapHandleContext, not prop injection` (cursor chain,
     ~20-field registry) → scene/anchor/plan flow.
   - `### Shared-layer architecture: SharedLayer + fragments` (`__vtm_shared_*` + sentinel
     lifecycle) → `frag:`/`run:` keys + anchor-as-sentinel.
   - `### MarkerLayerManager sort direction` (`Inlist.push()` reversal + `positionIndex`) →
     `creationSeq` tie-break + `applyEntryPriorities`.
   - `### Extension points for external layer-type libraries` (`useLayerOrder`/`createLayerOrderRegistry`)
     → current exports.
   - Minor: `### Central lifecycle hook` (`registry.markNativeDirty()` → scene-based).
2. `docs/advanced/layer-ordering.md` — full rewrite around the scene model.
3. `docs/advanced/extending.md` — rewrite "Extension API (public)" + TurboModule spec example
   (drop `positionIndex`) + "Layer render ordering".
4. `docs/NAMING_TERMINOLOGY.md` — glossary updates.
5. `docs/components/{reindex-scope,shared-layer,layer-mapsforge}.md` — targeted edits.
6. `docs/api/create-map-handle.md` + `docs/hooks/use-map.md` + `docs/debug/get-debug-layer-dump.md`
   — replace "JS-side registry"/`LayerOrderRegistry` with `RegistryDebugSnapshot`.
7. `CHANGELOG.md` — detailed `[Unreleased]` Added/Changed/Removed breaking-changes entry.

## Not in scope (already current)

`README.md`, `docs/api/types.md`, `docs/debug/layer-debug-tree.md`,
`docs/debug/use-layer-debug-info.md`, the `ext-plan` skill, and the elevation / GNSS /
reanimated / mercator / spatial-query sections.

## Verification

- `grep` for `useLayerOrder|LayerOrderRegistry|positionIndex|sentinel|cursor chain|Inlist|__vtm_shared_`
  over `docs/` + `AGENTS.md` → zero hits (intentional historical notes in `TODO.md` only).
- `yarn typecheck && yarn lint`.
