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
`MapContainer.animateTo`/`getPosition` now dispatch their bodies onto the UI thread via
`UiThreadUtil.runOnUiThread` — required because vtm's `Animator`/`ViewController` call
`org.oscim.utils.ThreadUtils.assertMainThread()` and throw otherwise. Meanwhile every layer
module's `createLayer`/`removeLayer` (via the shared `LayerHelper.addLayer`/`removeLayer`,
`android/.../LayerHelper.java:43,65`) and `MapContainer.reorderLayers` still run on RN's
native-modules thread and mutate the same `mapView.map()` (`layers()`, `updateMap()`) with no
synchronization against the new UI-thread callers. Not yet fixed — deliberately deferred (see
below).

Why not just wrap `LayerHelper.addLayer`/`removeLayer` in `UiThreadUtil.runOnUiThread` too:
`addLayer` is currently **synchronous** — it returns the uuid immediately, and every layer's
`createLayer` depends on that return value to build its own response right after.
`UiThreadUtil.runOnUiThread` is fire-and-forget (posts and returns immediately); naively wrapping
it would make `addLayer` return before the layer is actually added, breaking every layer's
create/remove flow, not just closing the race. Making it safe needs either (a) blocking the
calling thread with a `CountDownLatch` until the UI-thread work finishes — adds a cross-thread
blocking round-trip to *every* layer create/remove call, directly working against making bulk
layer creation faster — or (b) a real async rewrite of `LayerHelper`'s contract across every layer
module.

Decision: do this together with the "make creation/deletion of many layers super fast" work
(see item 0 above and `project_many_layers_perf_fix_status` in memory) rather than bolting on a
latch now that would likely need undoing once that redesign happens. The redesign should also
keep CLAUDE.md's "layer render order must strictly follow React tree order" invariant in mind —
item 0's root cause (concurrent JNI-thread-pool resolution order) and this thread-safety gap are
related: a single, deliberately-threaded layer-mutation pipeline would help both at once.

Related, same root cause (no per-`nativeNodeHandle` teardown hook exists at all): pre-existing
`LayerHelper.layersByHandle` (`LayerHelper.java:27`) and `MapContainer.previouslyOrderedLayers`
never get cleared when a map view/fragment is destroyed — they just accumulate stale entries
keyed by a `nativeNodeHandle` that will never be reused. `MapContainer.pendingAnimateTo` (added in
`084638d`) has the same gap, but with a heavier payload: each entry's `Map.UpdateListener` closure
holds a strong reference to `mapView`, so an in-flight `animateTo` whose view gets destroyed
mid-animation keeps the entire native `MapView`/`Map`/tile-cache object graph (plus the pending
`Promise` and its JS-side `await` continuation) alive indefinitely instead of being released with
the view. Worth fixing as part of the same redesign by adding a real
`MapFragment.onDestroy`-driven cleanup hook for all three maps, rather than three separate
narrow patches.

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

