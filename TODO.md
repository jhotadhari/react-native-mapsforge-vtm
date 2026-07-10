# TODO

## 0. Bug: layer render order doesn't strictly follow React tree hierarchy

**Observable bugs fixed 2026-07-05** (`902fc47`). Two concrete bugs were causing the visible
symptoms in the `many-layers` example's dedicated mode:

1.  **`MarkerLayerManager` used `group.fragmentUuid` instead of `entry.fragmentUuid`** in
    `removeMarkers`, `updateEntry`, and `hitTestEntry`. In dedicated mode (no `SharedLayer`
    wrapper), all bare `<Marker>` components share the ROOT group but each lives in its own
    fragment — because type alternation (`path → marker → path → marker`) creates new fragments
    per alternation. Removing/updating/hit-testing via `group.fragmentUuid` always targeted the
    first marker's layer, silently failing for subsequent markers. Fixed by using
    `entry.fragmentUuid`.

2.  **Teardown race in `useNativeLayerLifecycle`**: unmounting a component while its
    `createLayer()` is in-flight (`uuid === false`) would return early from `triggerRemove`
    (no uuid to remove), and when `create` later resolved, nothing cleaned up the orphaned
    native resource. Fixed with a `mountedRef` that detects post-unmount resolution and
    removes the just-created resource.

### What was already fixed before 2026-07-05

The fragment-uuid ordering infrastructure was already in place:

- `MapHandleContext.ts:flush()` already synthesizes fragment uuids (e.g.
  `__vtm_shared_path__1`) in `orderedUuids` — not per-component uuids.
- `LayerManager.ensureSharedLayer()` already registers fragment uuids in `knownLayers`.
- `PathLayerManager.drawSegments()` already sets `drawable.setPriority(entry.positionIndex)`.
- `Marker.tsx` already uses `useLayerOrder` and passes `positionIndex` and `fragmentUuid`.
- Marker sorted insertion (`insertMarkerSorted`) already works.
- `BASE_POSITION` differs between path (`MAX_VALUE - 1`) and marker (`MAX_VALUE`) managers.

The original investigation below documents the shared-layer architecture and the fragment-uuid
design. The remaining theoretical concern (reorder timing vs `lastReorderWasEffective`) has not
been observed in practice — position-aware insertion + debounced reorder are sufficient.

<details>
<summary>Original investigation (2026-06-28)</summary>

### Architecture context

`LayerPath` and `Marker`/`LayerMarker` collapse many JS components into one shared native `Layer`
per type per map view: one shared `VectorLayer` for all paths, one shared `ItemizedLayer` for all
markers. The shared layer is created lazily by `LayerManager.ensureSharedLayer()` and registered in
`MapMutationQueue.knownLayers` via `enqueueAddLayer(sharedLayer, fragmentUuid, basePositionIndex)`.
Individual paths/markers are entries *within* the shared layer, added via `createEntry()`.

… (remaining analysis preserved for context)
</details>

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
deferred, not done). `com.caverock:androidsvg`,
`org.locationtech.jts:jts-core`, `com.squareup.okhttp3:okhttp`, and `io.github.ci-cmg:mapbox-vector-tile`
were all already at latest stable — left unchanged.

**Still open: the protobuf-java 4.x major bump**, if/when vtm's own MVT classes are confirmed
compatible with it (would need a dedicated MBTiles/MVT-layer regression test, not just a version
bump).

Also check for newer versions of:

```
androidx.documentfile:documentfile:1.0.1
com.caverock:androidsvg:1.4
org.locationtech.jts:jts-core:1.20.0
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okio:okio:3.6.0
com.google.protobuf:protobuf-java:3.24.4
io.github.ci-cmg:mapbox-vector-tile:4.0.6
```

