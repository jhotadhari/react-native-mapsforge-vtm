# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`react-native-mapsforge-vtm` is a React Native wrapper around [mapsforge/vtm](https://github.com/mapsforge/vtm)
for offline vector map rendering from OpenStreetMap data. **Android only** — `ios/generated` codegen
stubs exist (required by the New Architecture build), but there is no real iOS implementation.

The library was rewritten against the **React Native New Architecture** (Fabric + TurboModules) in
commit `c9a6ace`, replacing an older bridge/`NativeModules` design. `MIGRATION.md` documents exactly
what changed and why (including features intentionally dropped, like `LayerPathSlopeGradient` and
GPX-file loading) — read it before assuming old-architecture patterns from outside this repo apply
here. `TODO.md` tracks open work, notably a post-rewrite dependency upgrade plan.

## Common commands

This is a Yarn workspaces monorepo (`packageManager: yarn@3.6.1` — don't use `npm`). The library lives in
the repo root (`src/`, `android/`); `example/` is a workspace app for manual testing.

```sh
yarn                  # install deps for root + example workspaces
yarn typecheck        # tsc (no emit, just checks)
yarn lint             # eslint over **/*.{js,ts,tsx} (flat config, eslint.config.mjs)
yarn format           # prettier . --write
yarn test             # jest — note: the only test file is src/__tests__/index.test.tsx and it's a stub (it.todo)
yarn clean            # del-cli android/build example/android/build example/android/app/build lib
yarn prepare          # bob build — builds lib/ (codegen + module + typescript) from src/, runs on install via "prepare"

yarn example start    # Metro for the example app
yarn example android  # build & run the example app on a connected device/emulator

yarn release <version>  # scripts/publish.sh — bumps version, validates CHANGELOG.md, tags, publishes to npm; requires `gh` CLI
```

`lefthook.yml` runs `eslint` and `tsc` on staged `*.{js,ts,jsx,tsx}` files as a pre-commit hook.
CI (`.github/workflows/ci.yml`) drives the Android build through `yarn turbo run build:android`
(`turbo.json`'s `build:android` task), caching on `yarn.lock`'s hash.

To work on native Android code, open `example/android` in Android Studio — library Java sources show up
under the `react-native-mapsforge-vtm` module (this repo is symlinked in via `example/`'s yarn workspace).

## Architecture

### One Fabric view, one TurboModule per layer

`MapContainer` (`src/components/MapContainer.tsx`) renders the single native view —
`MapsforgeVtmView`, the codegen'd Fabric component from
`src/NativeViews/MapsforgeVtmViewNativeComponent.ts`, backed by `MapsforgeVtmViewManager.java` /
`MapFragment.java` (the actual vtm `MapView` host) under
`android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/views/`. Everything nested inside it —
`LayerMapsforge`, `LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap`, `LayerPath`, `LayerPathJts`,
`LayerShape`, `LayerMarker`, `LayerScalebar` — is a plain React component (renders `null`) that talks to
its own TurboModule spec in `src/NativeModules/NativeXxx.ts` via `createLayer`/`removeLayer` calls keyed
by `nativeNodeHandle` (the map view's handle, obtained via `findNodeHandle` since Fabric views still don't
expose a handle any other way) and a `uuid` returned from `createLayer` (used later for
`removeLayer`/update calls). Each spec generates `android/generated/java/.../NativeXxxSpec.java`; the
hand-written implementation lives one level up as `android/.../modules/Xxx.java extends NativeXxxSpec`
(e.g. `LayerMarker.java extends NativeLayerMarkerSpec`).

### Wiring layers together: `MapHandleContext`, not prop injection

`MapContainer` creates a `LayerOrderRegistry` (`src/context/MapHandleContext.ts`) and provides it, along
with the map's `nativeNodeHandle`, through `MapHandleContext`. Every layer component calls
`useLayerOrder` (`src/compose/useLayerOrder.ts`), which registers the component's position in render
order (tracked by a stable `Symbol`, independent of nesting depth) and its resolved native `uuid` into
that shared registry, then debounces a single native `reorderLayers` call whenever the resolved order
actually changes. This replaced the old `cloneElement`/static-`isMapLayer` walk entirely — there is no
prop-injection wiring left in this repo. `LayerMarker` does its own one-level-down equivalent for
`Marker` children.

**Invariant: native layer rendering must strictly follow React component tree order.** A layer
declared later in JSX (e.g. a `LayerMarker` mounted after a `LayerPath`) must always render on top of
it, same as later siblings paint on top in the DOM. This currently does **not** hold under load — see
TODO.md's "Layer render order doesn't strictly follow React tree hierarchy" entry.

### Path layers: `LayerPath` vs `LayerPathJts`

The library provides two path components backed by different vtm-jts implementations:

| Aspect | `LayerPath` | `LayerPathJts` |
|---|---|---|
| Native backend | `PathLayerManager` + shared `VectorLayer` | Dedicated `org.oscim.layers.vector.PathLayer` per component |
| Architecture | **Shared-layer**: many JS components collapse into one native layer | **Dedicated-layer**: one native layer per JS component |
| Render ordering | Known bug (TODO.md #0) — shared-layer uuids not in `knownLayers` | Correct — per-component uuid IS the layer uuid |
| Performance at scale | Excellent (1 GPU draw call for all paths) | Worse (1 native layer per path) |
| Great-circle arcs | Not supported | `addGreatCircle` method |
| Douglas-Peucker generalization | External `simplify` library | Built-in via `Style.generalization` |
| JTS `LineString` input | Not supported | `setLineString(double[])` |
| Gesture hit-testing | Shared `VectorLayer` with per-drawable uuid resolution | Per-layer `contains()` + `onGesture()` |
| Best for | 50–1000+ paths, route networks, trajectory data | 1–30 paths, great circles, guaranteed z-order |

Both share the same `GeometryStyle` interface (stroke, fill, stipple, etc.) and the same gesture
callback pattern (`onPress`/`onLongPress`/`onDoubleTap`). Choose `LayerPathJts` when you need
correct render order or JTS-specific features; choose `LayerPath` when you have many paths and
the shared-layer performance matters.

### `LayerShape` — geometric shape overlays

Draws JTS geometric shapes (polygons, circles, rectangles, hexagons, points) on the map using
vtm-jts drawables. Each shape is a dedicated native `VectorLayer` with a single drawable added.
Supports full `GeometryStyleJts` styling (fill color, stroke, transparency, stipple, etc.) and
gesture callbacks.

Shape types: `polygon` (with optional holes), `circle` (center + radius in km), `rectangle`
(two corners), `hexagon` (center + radius), `point` (single position).

### Update flow for layer props

Layer components hold a `uuid` (via the shared `useNativeLayerLifecycle` hook,
`src/compose/useNativeLayerLifecycle.ts`) tracking a null → false → uuid state machine: create on
mount/whenever re-enabled, remove on unmount, with native error reporting centralized through
`reportNativeError`. Most layers recreate (remove then create) on any prop that's baked into native
construction (e.g. `mapFile`/`renderTheme` for `LayerMapsforge`) and update in place for props that
aren't (e.g. `enabledZoomMin`/`enabledZoomMax`). Native async events (map move, lifecycle, marker
press, per-layer create/remove/change, map-level tap/longPress) arrive as direct Fabric event props
(`onMapUpdate`, `onPause`, `onResume`, `onTap`, `onLongPress`, etc. on `MapContainer` itself) rather
than a global `NativeEventEmitter` — this is a
deliberate Fabric-native pattern, not a missing migration.

### Where types come from

Each layer's request/response/props types live in its own `src/NativeModules/NativeXxx.ts` codegen spec
(or alongside the component, e.g. `LayerMapsforgeProps`/`LayerMapsforgeResponse` in
`NativeLayerMapsforge.ts`) and are re-exported as a namespace from `src/index.tsx` (e.g.
`LayerMapsforgeTypes`) — when changing a layer's prop or response shape, edit the spec file, not
`src/types.ts` (which only holds the few truly shared base types: `ResponseBase`, `ErrorBase`,
`Position`).

### Native side layout

Under `android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/`:
- `modules/` — one hand-written class per layer/map-container (`LayerMarker.java`, `MapContainer.java`,
  etc.), each extending its codegen-generated `NativeXxxSpec` base class.
- `views/` — `MapFragment` (the vtm `MapView` host), `MapsforgeVtmView`, `MapsforgeVtmViewManager`
  (implements the codegen-generated Fabric manager interface).
- `layer/` — custom vtm layer subclasses (`PathLayer`, `VectorLayer`, `ItemizedLayer`,
  `GestureLayer`) used by the path/marker layers and map-level gesture detection.
- Top-level helpers: `HgtReader`/`Utils` (elevation lookups from `.hgt` DEM files), `LayerHelper`/
  `LayerZoomBoundsHelper` (zoom-based layer visibility), `RenderThemeMenuLoader` (parses
  `<stylemenu>` from render-theme XML for `useRenderStyleOptions`), `FixedWindowRateLimiter` (throttles
  native map-event emission, used by `MapFragment`).

### Threading model

All mutations to `mapView.map().layers()` (add, remove, reorder) **must** flow through
`MapMutationQueue.flush()` on the **UI thread** (Main Looper). Nothing else may call
`mapView.map().layers().add/remove` or `mapView.map().updateMap()` directly.

```
 Native Modules Thread (TurboModule)          UI Thread (Main Looper)
 ================================             =======================

 createLayer (async)  ──enqueue──>  ┌─────────────────────────────┐
 removeLayer (async)  ──enqueue──>  │  MapMutationQueue.flush()    │
 reorderLayers        ──enqueue──>  │  ─────────────────────────  │
                                    │  1. Remove stale layers      │
 animateTo()          ──dispatch──> │  2. Add new layers           │
 getPosition()        ──dispatch──> │  3. Reorder (LIS algorithm)  │
                                    │  4. updateMap() once         │
 scheduleUpdate()     ──post─────>  │  updateMap() coalesced       │
 (LayerManager +       (AtomicBool  │  (per-entry geometry changes)│
  MarkerLayerManager)   + Handler)  └─────────────────────────────┘

 LayerManager.ensureSharedLayer() blocks with future.get()
 until the shared layer is placed on the UI thread — this is
 the ONLY cross-thread blocking wait. Everything else is
 fire-and-forget from the JS side.
```

**Key rules:**
- `MapMutationQueue.flush()` is the **only** place that calls `layers().add/remove` and the batch-level `updateMap()`. It runs on the UI thread, serialized with vtm's own rendering.
- `scheduleUpdate()` (in `LayerManager`) coalesces per-entry `updateMap()` calls onto the UI thread via `AtomicBoolean` CAS + `Handler.post`. Use it instead of calling `mapView.map().updateMap()` directly.
- `MapContainer.animateTo()` / `getPosition()` dispatch to the UI thread via `UiThreadUtil.runOnUiThread` (vtm's `Animator` asserts the UI thread).
- `MapFragment.onDestroy()` must tear down `LayerManager`s **before** `mapView.onDestroy()`, or shared layers silently leak.
- `LayerHelper.addLayerAsync` / `removeLayerAsync` are the preferred API — they enqueue into `MapMutationQueue`. The deprecated `addLayer`/`removeLayer` sync methods now delegate to the async path internally.

**What still runs on the native-modules thread (read-only / non-layers-mutating):**
- `LayerHelper.getLayer()` / `getLayers()` — reads from `MapMutationQueue.getKnownLayers()` (ConcurrentHashMap, safe from any thread)
- `LayerZoomBoundsHelper.removeUpdateListener()` — calls `mapView.map().events.unbind()` (event listener management, not layer mutation)
- Marker/Path entry creation — operates on already-registered shared `Layer` objects (adds drawables/markers to `VectorLayer`/`ItemizedLayer`), not on `map.layers()`

### Map position consumption patterns

There are three tiers for reading the current map position (center, zoom, bearing, tilt) in JS,
ordered from simplest to most performant:

| Tier | API | Bridge crossings | React re-renders | When to use |
|---|---|---|---|---|
| **Callback** | `MapContainer.onMapUpdate` prop | ~25/sec (one-way native→JS) | ~25/sec | Debug overlays, one-shot reactions, anything that already calls `setState`. The event fires at most once per `mapUpdateInterval` ms (default 40). |
| **Shared values** | `useMapPosition()` from `react-native-mapsforge-vtm/reanimated` | ~25/sec (writes only) | 0 (worklet reads are UI-thread) | Smooth coordinate displays, overlay positioning, any worklet-based UI that needs to track map position at 60fps without triggering React reconciliation. Requires `react-native-reanimated >= 3.0.0` (optional peer dependency). |
| **Imperative** | `useMap().getPosition()` | 2 per call (round-trip JS→native→JS) | 0–1 per call | Button-triggered snapshots ("save current position"), non-continuous queries. Not suitable for tracking during pan/zoom — use the callback or shared values instead. |

**Callback vs shared values — they coexist.** `useMapPosition()` internally creates reanimated shared
values and returns a `handleMapUpdate` callback that you pass as the `onMapUpdate` prop. The bridge
event still fires at the same rate; the shared values receive the same writes. The win is that
worklet consumers (`useDerivedValue`, `useAnimatedStyle`, `useAnimatedProps`) read from shared values
on the UI thread — zero bridge crossings, zero React re-renders for reads.

**The trailing-edge guarantee.** `MapFragment` uses a throttle-with-trailing-edge pattern: during
continuous movement, events fire at most once per `mapUpdateInterval` ms (throttle). When movement
stops, a final flush fires after `mapUpdateInterval` ms of silence, guaranteeing the resting position
is never lost. This applies to both the `onMapUpdate` callback and the shared-values channel.
