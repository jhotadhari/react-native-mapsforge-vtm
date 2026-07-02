# TODO

## 0. Bug: layer render order doesn't strictly follow React tree hierarchy

Found 2026-06-24 via the `many-layers` example (screenshot at high `count`, e.g. 3000 pairs): red
`LayerPath` lines visibly render on top of green `Marker` symbols in places, even though every
pair's JSX always puts `LayerPath` before `Marker` (`example/src/examples/many-layers/index.tsx`
lines 171-175). This violates the invariant documented in CLAUDE.md's "Wiring layers together"
section — later-in-JSX layers must always paint on top of earlier ones.

**Investigated 2026-06-28. Root cause confirmed, not yet fixed.** The earlier analysis (async
creation append + reorderLayers catching up) was correct for the old `LayerHelper.addLayer` path,
but the actual problem is deeper and specific to the shared-layer architecture used by `LayerPath`
and `LayerMarker`/`Marker`.

### Architecture context

`LayerPath` and `Marker`/`LayerMarker` collapse many JS components into one shared native `Layer`
per type per map view: one shared `VectorLayer` for all paths (`__vtm_shared_paths__`), one shared
`ItemizedLayer` for all markers (`__vtm_shared_markers__`). The shared layer is created lazily by
`LayerManager.ensureSharedLayer()` and registered in `MapMutationQueue.knownLayers` via
`enqueueAddLayer(sharedLayer, sharedLayerUuid, basePositionIndex)`. Individual paths/markers are
entries *within* the shared layer, added via `createEntry()`.

Layers that DON'T use this shared pattern (e.g. `LayerBitmapTile`, `LayerMapsforge`, etc.) go
through `LayerHelper.addLayerAsync()` which registers each component's uuid directly in
`knownLayers`. For these, position-aware insertion (`MapMutationQueue` step 2) and `reorderLayers`
(step 3) both work correctly because the per-component uuid IS the layer's uuid in `knownLayers`.

### Three distinct root causes

1.  **Between-shared-layer ordering is non-deterministic and unfixable by `reorderLayers`**
    (critical).

    Both `PathLayerManager` and `MarkerLayerManager` use `BASE_POSITION = Integer.MAX_VALUE`
    (append). Whichever shared layer's `ensureSharedLayer()` call completes first lands first in
    `map.layers()`. This is async TurboModule resolution order, not React tree order.

    `reorderLayers` can NEVER fix this because JS sends per-component uuids (individual path entry
    uuids like `"a1b2c3..."`, individual marker group uuids like `"d4e5f6..."`). The native
    `ReorderLayers` handler looks up each uuid in `knownLayers` — but `knownLayers` only contains
    the shared-layer uuids (`__vtm_shared_paths__`, `__vtm_shared_markers__`), not per-entry uuids.
    ALL per-component uuids from `LayerPath`/`Marker` components resolve to `null` in
    `knownLayers.get()`, so `orderedLayers` is always empty and `reorderMinimalMoves` is a no-op
    for these types.

2.  **Within-PathLayer drawable ordering ignores `positionIndex`**.

    `PathLayerManager.createEntry()` calls `drawSegments()` which appends `LineDrawable`s to the
    shared `VectorLayer`. There is no positionIndex-based insertion — drawables are ordered by
    creation-completion time, not by React tree position. Contrast with
    `MarkerLayerManager.insertMarkerSorted()` which does insert by positionIndex.

3.  **`Marker` component never sends `positionIndex`** (within-ItemizedLayer ordering).

    `Marker.tsx` does NOT use `useLayerOrder` and does NOT include `positionIndex` in its
    `enqueueCreateMarker()` params. `CreateMarkerParams` doesn't even have a `positionIndex` field.
    So `resolvePositionIndex(params)` in `MarkerLayerManager` always returns `Integer.MAX_VALUE`,
    and all markers within a group are ordered by async creation order, not React tree position.
    (`LayerMarker` DOES pass `positionIndex` for group creation, but the per-marker ordering within
    that group is still unpositioned.)

### Self-healing question: NO

The final settled state does NOT self-heal. Since `reorderLayers` is structurally a no-op for
path/marker shared layers (their uuids are never in `knownLayers`), and there is no other mechanism
that fixes their relative order, any misordering during creation is permanent. Even if every
component's uuid eventually resolves, the `orderedUuids` list from JS never contains the shared
layer uuids (`__vtm_shared_paths__`, `__vtm_shared_markers__`), so `reorderMinimalMoves` never
touches them.

### Fix approach

The shared-layer uuids must be communicated to JS and included in the `orderedUuids` list at the
correct positions. Specifically:

- Each layer type's first `createLayer` response (or a constant) should include the
  `sharedLayerUuid`.
- JS must track which render-tree positions belong to which shared layer type.
- When computing `orderedUuids` (in `MapHandleContext.ts:flush()`), insert synthetic entries
  for each shared layer at the position corresponding to its earliest component in render order.
- This way `reorderMinimalMoves` can reposition the shared layers correctly relative to each
  other and to non-shared layers.

Additionally:
- `PathLayerManager.createEntry()` needs positionIndex-based drawable insertion (like
  `MarkerLayerManager.insertMarkerSorted()`).
- `Marker` needs to use `useLayerOrder` and pass `positionIndex` through `CreateMarkerParams`
  into native `createMarkers`/`createMarker`.
- The `BASE_POSITION` constants should differ between path and marker shared layers so that
  position-aware insertion in `MapMutationQueue.flush()` step 2 gets them in the right order
  even before `reorderLayers` runs (defense in depth).

## 1. Dependency upgrade plan (post-rewrite version bump)

Drafted 2026-06-23, right after the New Architecture rewrite landed (commit `c9a6ace`). Follow these
steps in order — each one should land as its own commit/PR so a regression is easy to bisect, given
how much native surface (NDK/vtm JNI) this touches.

**Still blocked, not done:**
- `eslint`/`@eslint/js` stuck on `^9.22.0` — ESLint 10 crashes under
  `@react-native/eslint-config`'s pinned `@typescript-eslint/*@^8.36.0`, whose `ScopeManager` doesn't
  implement `addGlobals()` yet. Upstream gap; re-attempt once `@react-native/eslint-config` or
  `@typescript-eslint` ships ESLint 10 support.
- `jest`/`@types/jest` stuck — `@react-native/jest-preset` itself still depends on
  `jest-environment-node@^29.7.0`, which lacks the `clearMocksOnScope` method Jest 30 needs. Re-attempt
  once `@react-native/jest-preset` bumps that dependency past 29.x.

### Last step: everything else in `android/build.gradle`

**Partially done, folded into Step 3's commit (`fdeef21`) 2026-06-24** — bumped the trivial ones:
`androidx.documentfile` `1.0.1` → `1.1.0`, `com.squareup.okio` `3.6.0` → `3.12.0` (transitive-only,
no direct usage in this repo), `com.google.protobuf:protobuf-java` `3.24.4` → `3.25.8` (stayed within
the 3.x line deliberately — the 4.x major bump changes generated-code APIs and `vtm-mvt`/
`vtm-android-mvt`'s own bundled MVT-parsing classes aren't verified against a 4.x runtime, so that's
deferred, not done). `com.goebl:simplify`, `io.vacco.savitzky-golay`, `com.caverock:androidsvg`,
`org.locationtech.jts:jts-core`, `com.squareup.okhttp3:okhttp`, and `io.github.ci-cmg:mapbox-vector-tile`
were all already at latest stable — left unchanged.

**Still open: the protobuf-java 4.x major bump**, if/when vtm's own MVT classes are confirmed
compatible with it (would need a dedicated MBTiles/MVT-layer regression test, not just a version
bump).

Also check for newer versions of:

```
com.goebl:simplify:1.0.0
io.vacco.savitzky-golay:savitzky-golay:1.0.1
androidx.documentfile:documentfile:1.0.1
com.caverock:androidsvg:1.4
org.locationtech.jts:jts-core:1.20.0
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okio:okio:3.6.0
com.google.protobuf:protobuf-java:3.24.4
io.github.ci-cmg:mapbox-vector-tile:4.0.6
```

