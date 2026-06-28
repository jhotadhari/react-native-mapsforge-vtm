# TODO

## 0. Bug: layer render order doesn't strictly follow React tree hierarchy

Found 2026-06-24 via the `many-layers` example (screenshot at high `count`, e.g. 3000 pairs): red
`LayerPath` lines visibly render on top of green `LayerMarker` markers in places, even though every
pair's JSX always puts `LayerPath` before `LayerMarker` (`example/src/examples/many-layers/index.tsx`
lines 161-162). This violates the invariant documented in CLAUDE.md's "Wiring layers together"
section — later-in-JSX layers must always paint on top of earlier ones.

Root cause (confirmed by reading `LayerOrderRegistry`/`reorderLayers`, not yet fixed): a layer's
native creation (`LayerHelper.addLayer` → `mapView.map().layers().add(layer)`, an unconditional
append) happens at whatever moment its own async `createLayer()` TurboModule call resolves — not in
React-tree order, since promises for thousands of sibling layers resolve in JNI/thread-pool order.
Correct ordering is only restored afterward by `MapContainer.reorderLayers`
(`android/.../modules/MapContainer.java:96`), which `MapHandleContext.ts`'s `scheduleSync` calls on a
16ms-debounce/250ms-max-wait. `reorderMinimalMoves` (`MapContainer.java:159`) only repositions layers
whose uuid has already resolved on the JS side (`orderedLayers`) — any layer still mid-creation is
left exactly where it landed, interleaved among already-ordered layers, until it too resolves and
joins a later reorder pass.

Needs investigation: whether the final, fully-resolved state actually self-heals (every layer
eventually becomes "tracked" and gets a correct final `reorderLayers` call) or whether something
about thousands of layers mounting at once leaves a subset permanently out of order. Repro: open
`manyLayers` example, set count to 3000+, leave it idle a few seconds, inspect whether mis-ordering
persists or only appears mid-mount.

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

## 2. Thread-safety: vtm `Map` access split across two threads

Found 2026-06-25 during code review of the `useMap()`/`animateTo` feature (commit `084638d`).
**Fixed 2026-06-27** in 5 commits (`d3e7884`..`575dcd7`):

All `mapView.map().layers()` mutations now flow through `MapMutationQueue.flush()` on the UI
thread — removals, adds, and reorders are serialized into a single batch with guaranteed ordering
(removals → adds → reorder → single `updateMap()`). Per-entry `updateMap()` calls (LayerManager
create/update, MarkerLayerManager batch ops) now go through `scheduleUpdate()` which coalesces
via `AtomicBoolean` CAS + `Handler.post` to the UI thread. `MapFragment.onDestroy` ordering was
fixed so `LayerManager.removeAll()` runs before `mapView.onDestroy()` (was silently leaking shared
layers).

Summary of changes:
- `LayerHelper.removeLayer()` → delegates to `removeLayerAsync()` → `MapMutationQueue`
- `MapContainer.reorderLayers()` → enqueues `ReorderLayers` mutation into `MapMutationQueue`
- `LayerManager.create()`/`update()` → `scheduleUpdate()` instead of direct `updateMap()`
- `MarkerLayerManager` (5 sites) → `scheduleUpdate()` instead of direct `updateMap()`
- `MapFragment.onDestroy()` → `LayerManager.removeAll` before `mapView.onDestroy`
- `MapMutationQueue.removeLayerSync()` — centralized UI-thread-only synchronous removal for teardown

**Remaining concern:** `LayerZoomBoundsHelper.updateUpdateListener()` (line 123) still calls
`mapView.map().updateMap()` directly from the native-modules thread when zoom bounds change.
This doesn't touch `layers()`, only triggers a redraw, so the risk is redundant rendering rather
than data corruption. Can be addressed if profiling shows it's a problem.

~~Why not just wrap `LayerHelper.addLayer`/`removeLayer` in `UiThreadUtil.runOnUiThread` too...~~

## 3. Reimplement `MainBaseActivity` and hardware-key support

Dropped during the New Architecture rewrite. Pre-rewrite, the library shipped
`com.jhotadhari.reactnative.mapsforge.vtm.MainBaseActivity`, an abstract `ReactActivity` subclass
that provided `dispatchKeyEvent`-based hardware-key handling (`addHardwareKeyListener` /
`removeHardwareKeyListener`). The JS-side `MapContainer` had `emitsHardwareKeyUp` /
`onHardwareKeyUp` props (volume-key → zoom binding). Both the native class and the JS props are
gone post-rewrite.

Needs reimplementation:
- `MainBaseActivity` (or equivalent) as a native base class consumers can extend
- `emitsHardwareKeyUp` / `onHardwareKeyUp` as Fabric view event props on `MapContainer`
  (mirroring `onMapUpdate` / `onPause` / `onResume`'s `NativeSyntheticEvent` pattern)

Tracked from MIGRATION_FEEDBACK_STRAYMAP.md points 5 and 7.

## 4. Bug: marker `triggerEvent` doesn't work — coordinate transformation error

Found 2026-06-28 during device testing of the thread-safety fix. The trigger example
(`example/src/examples/trigger/index.tsx`) has buttons that call `triggerEvent` on markers
and paths. The path trigger works, but the marker trigger is broken.

Root cause: `MarkerLayerManager.triggerGroupEvent()` (line 675-676) and `hitTestEntry()`
(lines 305-306) incorrectly subtract the map view's center offset from the screen coordinates
passed from JS:

```java
int eventX = (int) x - mapView.map().getWidth() / 2;
int eventY = (int) y - mapView.map().getHeight() / 2;
```

The JS side already passes absolute screen pixel coordinates (e.g. `width/2, height/2` for the
map center). Subtracting the center offset again makes `eventX`/`eventY` near zero for
center-targeted triggers, while `tmpPoint` from vtm's `Viewport.toScreenPoint()` is in absolute
screen coordinates — the `dx`/`dy` calculation then produces a huge negative offset, so
`MarkerSymbol.isInside()` always returns false.

Fix: remove the center-offset subtraction in both `triggerGroupEvent` and `hitTestEntry`.
The JS coordinates and vtm's `toScreenPoint()` both use the same absolute coordinate system
(relative to the map view's top-left corner), so no transformation is needed.

The path trigger (`LayerPath.triggerEvent` → `PathLayerManager` → `LayerManager.triggerEvent`)
does NOT have this bug — it passes coordinates through without transformation.


