# AGENTS.md

This file provides guidance to OpenCode when working with code in this repository.

## What this is

`react-native-mapsforge-vtm` is a React Native wrapper around [mapsforge/vtm](https://github.com/mapsforge/vtm)
for offline vector map rendering from OpenStreetMap data. **Android only** — `ios/generated` codegen
stubs exist (required by the New Architecture build), but there is no real iOS implementation.

The library was rewritten against the **React Native New Architecture** (Fabric + TurboModules) in
commit `c9a6ace`, replacing an older bridge/`NativeModules` design. The rewrite dropped several
features intentionally (notably `LayerPathSlopeGradient` and GPX-file loading) — don't assume
old-architecture patterns from outside this repo apply here.

The layer-ordering system was further rewritten (scene model replacing a cursor-chain registry) on
the `feature/layer-ordering-rewrite` branch; its details live in `src/scene/AGENTS.md`.

## Common commands

This is a Yarn workspaces monorepo (`packageManager: yarn@3.6.1` — don't use `npm`). The library lives in
the repo root (`src/`, `android/`); `example/` is a workspace app for manual testing.

```sh
yarn                  # install deps for root + example workspaces
yarn typecheck        # tsc (no emit, just checks)
yarn lint             # eslint over **/*.{js,ts,tsx} (flat config, eslint.config.mjs)
yarn format           # prettier . --write
yarn test             # jest — the only test file is src/__tests__/index.test.tsx and it's a stub (it.todo); don't trust it to catch regressions
yarn clean            # del-cli android/build example/android/build example/android/app/build lib
yarn prepare          # bob build — builds lib/ (codegen + module + typescript) from src/, runs on install via "prepare"

yarn example start    # Metro for the example app
yarn example android  # build & run the example app on a connected device/emulator

yarn release <version>  # release-kit — bumps version, validates CHANGELOG.md, tags, publishes to npm; requires `gh` CLI
```

`lefthook.yml` runs `eslint` and `tsc` on staged `*.{js,ts,jsx,tsx}` files as a pre-commit hook.
CI (`.github/workflows/ci.yml`) drives the Android build through `yarn turbo run build:android`
(`turbo.json`'s `build:android` task), caching on `yarn.lock`'s hash. **CI only triggers on `v**` tags** —
PRs and branch pushes do not get CI; test locally before pushing.

Prettier config (`.prettierrc`): **tabs** (not spaces), 80 char print width, plugins
`prettier-plugin-embed` and `prettier-plugin-multiline-arrays`. The `multilineArraysWrapThreshold: 2`
means arrays with 2+ elements get one-element-per-line formatting.

Dependencies already dropped (don't re-add): `simplify` (commit `39c8833`) and `savitzky-golay`
(commit `f5ade2e`).

To work on native Android code, open `example/android` in Android Studio — library Java sources show up
under the `react-native-mapsforge-vtm` module (this repo is symlinked in via `example/`'s yarn workspace).

## Architecture

### One Fabric view, one TurboModule per layer

`MapContainer` (`src/components/MapContainer.tsx`) renders the single native view —
`MapsforgeVtmView`, the codegen'd Fabric component from
`src/NativeViews/MapsforgeVtmViewNativeComponent.ts`, backed by `MapsforgeVtmViewManager.java` /
`MapFragment.java` (the actual vtm `MapView` host). Everything nested inside it —
`LayerMapsforge`, `LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap`, `LayerPath`, `LayerPathJts`,
`LayerShape`, `LayerMarker`, `LayerScalebar` — is a plain React component (renders `null`) that talks to
its own TurboModule spec in `src/NativeModules/NativeXxx.ts` via `createLayer`/`removeLayer` calls keyed
by `nativeNodeHandle` (the map view's handle, obtained via `findNodeHandle`) and a `uuid` returned from
`createLayer` (used later for `removeLayer`/update calls). Each spec generates
`android/generated/java/.../NativeXxxSpec.java`; the hand-written implementation lives one level up as
`android/.../modules/Xxx.java extends NativeXxxSpec`.

### Layer ordering — the scene model

Order is a **function of committed state, never accumulated during render**. `MapContainer` provides
`MapHandleContext` holding `{ nativeNodeHandle, scene: LayerScene, sync: SceneSync }`. Layer components
render `VtmAnchorView` anchors and declare entries into the scene via commit-phase hooks; the
committed view tree is walked to produce tree order, the scene builds an immutable plan, and
`SceneSync` applies it natively (absolute `reorderLayers` + per-fragment `applyEntryPriorities`).

**Invariant:** native layer rendering must strictly follow React component tree order. A layer
declared later in JSX renders on top.

Full details — `LayerScene`, `planBuilder` (`frag:`/`run:` fragment keys), `planDiff`,
`PriorityAllocator`, `SceneSync`, and the scene hooks — are in `src/scene/AGENTS.md`. Native layer
mutation/threading is in `android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/AGENTS.md`.

### Central lifecycle hook: `useNativeLayerLifecycle`

Every layer component (all 9 layer types + `Marker`) uses `useNativeLayerLifecycle`
(`src/compose/useNativeLayerLifecycle.ts`). It owns the `null → false → uuid` state machine, creates
on mount (or whenever re-enabled via the `enabled` gate), removes on unmount, and centralizes native
error reporting through `reportNativeError`. Two refs guard against teardown races:

- **`mountedRef`** — if the component unmounts while `createLayer()` is in-flight (`uuid === false`),
  the hook detects the post-unmount resolution and cleans up the just-created native resource.
- **`uuidRef`** — mirrors the current uuid and is updated *immediately* on promise resolution (before
  React re-render), so the unmount cleanup effect can see the real uuid and call `removeLayer`.

After a successful create/remove, the hook calls `setUuid()` (a re-render); the component's
commit-phase hooks then mutate the scene (`useSceneUuidBinding`/`useLayerEntry`), which notifies
`SceneSync` via `scene.subscribe` and schedules a debounced sync.

### Shared-layer architecture: `SharedLayer` + fragments

`<SharedLayer>` (`src/components/SharedLayer.tsx`) collapses many same-type JS layer components into
one native `Layer` (a **fragment**): 1000 `<LayerPath>` components → ~1 native layer instead of 1000.
Fragment boundaries occur at **type-run boundaries** and **scope boundaries**; fragment keys are
`frag:<owner>:<type>` (SharedLayer/LayerMarker) or `run:<anchor>` (implicit type-runs).

`<ReindexScope>` (`src/components/ReindexScope.tsx`) renders a scope anchor that permanently marks a
block's tree position (so async children land correctly) and carries the `order` prop for explicit
cross-scope priority.

### Marker batch creation

`MarkerBatchQueue` (`src/compose/MarkerBatchQueue.ts`) collapses N `createMarker`/`removeMarker`
bridge calls into 1 `createMarkers` + 1 `removeMarkers` call, flushed on the microtask boundary
(`Promise.resolve().then()`) with a 16ms safety max-wait. Call `drainQueue(nativeNodeHandle)` on map
destruction.

### Path layers: `LayerPath` vs `LayerPathJts`

| Aspect | `LayerPath` | `LayerPathJts` |
|---|---|---|
| Native backend | `PathLayerManager` + shared `VectorLayer` | Dedicated `org.oscim.layers.vector.PathLayer` per component |
| Architecture | **Shared-layer** (many JS components → one native layer) | **Dedicated-layer** (one native layer per component) |
| Performance at scale | Excellent (1 GPU draw call) | Worse (1 native layer per path) |
| Great-circle arcs | Not supported | `addGreatCircle` method |
| Douglas-Peucker generalization | Not supported | Built-in via `Style.generalization` |
| JTS `LineString` input | Not supported | No — coordinates come in as JS `Position[]` and are converted to vtm `GeoPoint`s natively |
| Gesture hit-testing | Shared `VectorLayer` with per-drawable uuid resolution | Per-layer `contains()` + `onGesture()` |
| Best for | 50–1000+ paths, route networks, trajectory data | 1–30 paths, great circles, guaranteed z-order |

Both share the same `PathPaint` interface and the same gesture callback pattern
(`onPress`/`onLongPress`/`onDoubleTap`). Choose `LayerPathJts` for great circles / JTS features or a
guaranteed per-layer z-order; choose `LayerPath` for scale.

### `LayerShape` — geometric shape overlays

Draws JTS geometric shapes (polygons, circles, rectangles, hexagons, points) via `ShapeLayerManager`
(shared-layer): same-type shapes share one native `VectorLayer`, each shape a `Drawable` within it.
Shape types: `polygon` (with holes — Java accepts one hole), `circle` (center + radius in km),
`rectangle` (two corners), `hexagon` (center + radius), `point` (single position). Supports full
`PathPaintJts` styling and gesture callbacks.

### Update flow for layer props

Layer components hold a `uuid` via `useNativeLayerLifecycle`. Most layers recreate (remove then
create) on props baked into native construction (e.g. `mapFile`/`renderTheme` for `LayerMapsforge`)
and update in place for props that aren't (e.g. `enabledZoomMin`/`enabledZoomMax`). Native async
events (map move, lifecycle, marker press, per-layer create/remove/change, map-level tap/longPress)
arrive as direct Fabric event props (`onMapUpdate`, `onPause`, `onResume`, `onTap`, `onLongPress`, …
on `MapContainer`) — a deliberate Fabric-native pattern, not a missing migration.

### Where types come from

Each layer's request/response/props types live in its own `src/NativeModules/NativeXxx.ts` codegen spec
(or alongside the component, e.g. `LayerMapsforgeProps`/`LayerMapsforgeResponse` in
`NativeLayerMapsforge.ts`) and are re-exported **directly** from `src/index.tsx`. When changing a
layer's prop or response shape, edit the spec file, not `src/types.ts` (which holds only the few truly
shared base types: `ResponseBase`, `ErrorBase`, `ErrorWithErrorMsg`, `Position`).

### Imperative map API: `createMapHandle` / `useMap` / `createMapHandleRegistry`

`createMapHandle(nativeNodeHandle)` (`src/compose/createMapHandle.ts`) is a **non-hook factory** that
builds the imperative map-control + elevation API object from a concrete view tag, so non-React code
(Redux thunks, services) can control the map. The returned object is **flat**; the Camera / Animation /
Bounds / Elevation groupings below are conceptual:

```
createMapHandle(handle)
  ├── Camera: getPosition, jumpTo, panTo, panBy, setZoom/zoomTo, zoomOut,
  │            setBearing/rotateTo, resetNorth, resetNorthPitch, setRoll
  ├── Animation: easeTo, flyTo          (animateTo is an internal helper, not exposed)
  ├── Bounds:   fitBounds/setBounds, flyToBounds, panInsideBounds, panInside
  └── Elevation: getAltitudeAtPosition, hasDataAtPosition, isTileCached,
                 setCacheCapacity, getAltitudeAtPositionRetry
```

`useMap` (`src/compose/useMap.ts`) delegates to `createMapHandle` and adds only `getDebugLayerDump()`
(which needs the JS-side `scene` to build the scene-plan snapshot).

`createMapHandleRegistry()` returns a `{ wire, unwire, getHandle, requireHandle }` singleton; the
React side wires the handle in a `useEffect`, non-React code calls `requireHandle()`.

### JS-side elevation enrichment

`enrichCoordinatesWithElevation()` (`src/enrichCoordinates.ts`) batch-enriches coordinate arrays with
SRTM elevation through a windowed three-phase flow — **Trigger** (preload all tiles in the window),
**Fence** (poll `isTileCached` until loaded, adaptive timeout), **Collect** (per-coordinate bilinear
interpolation). Mutates the input array in place and returns it.

**`ElevationAPI` bridge** (4 methods from `useMap()`): `getAltitudeAtPosition` (catches → `null`),
`hasDataAtPosition` (catches → `false`), `isTileCached?` (catches → `false`, never preloads),
`setCacheCapacity?` (**throws**).

**`EnrichCoordinatesOptions`:** `maxCacheCapacity` (default 50), `keepCacheCapacity`,
`onProgress`, `signal` (AbortSignal), `fallbackElevation`.

**`getAltitudeAtPositionRetry(lng, lat, opts?)`:** exponential-backoff retry wrapper that checks
`hasDataAtPosition` first (returns `null` immediately for ocean/missing tiles).

### Reanimated sub-package (`react-native-mapsforge-vtm/reanimated`)

Secondary entry point (`package.json` `exports`) providing `useMapPosition()`, `useMapOverlay()`,
`toScreenPosition()` / `fromScreenPosition()`. `useMapPosition()` is optional (gated by
`peerDependenciesMeta` on `react-native-reanimated`). With `activateNativeBridge(handle)`, the render
thread writes position data directly into C++ `Synchronizable` primitives via
`MapPositionWriter.nativeSetPosition()` — zero bridge crossings.

### Map position consumption patterns

Four tiers, simplest → most performant:

| Tier | API | Bridge crossings | React re-renders |
|---|---|---|---|
| Callback | `MapContainer.onMapUpdate` prop | ~25/sec | ~25/sec |
| Shared values | `useMapPosition()` | ~25/sec (writes) | 0 |
| Shared values (native) | `useMapPosition()` + `activateNativeBridge(handle)` | **0** | 0 |
| Imperative | `useMap().getPosition()` | 2 per call | 0–1 |

`MapFragment` fires events on every vtm frame; the callback path is rate-limited by Fabric event
dispatch, the native shared-value bridge bypasses it entirely.

### Mercator math utilities (non-worklet)

`src/mercatorMath.ts` — plain-JS Mercator projection (same math as the `/reanimated` worklets, tile
size 576): `clampLat`, `latLngToMercator`, `mercatorToLatLng`, `wrapLngDelta`, `wrapMxDelta`,
`toScreenPosition`, `fromScreenPosition`, `computeViewportBbox`, `lngLatToTile`, `tileToBbox`,
`snapBboxToTiles`.

### Spatial query hooks

`useMapEventInterval(eventRef, intervalMs, callback)` (`src/compose/useMapEventInterval.ts`) polls a
map-event ref at a fixed interval. `useViewportBbox(eventRef, intervalMs, opts?)`
(`src/compose/useViewportBbox.ts`) computes a tile-snapped viewport bbox (options: `snapZoomOffset`
default 4, `minSnapZoom`/`maxSnapZoom` default 0/8) and only updates state when the snapped bbox
changes — small same-tile pans produce no re-render.

### Threading model

All `mapView.map().layers()` mutations flow through `MapMutationQueue.flush()` on the UI thread
(delegating to `LayerStackController`), with two sanctioned exceptions: `removeLayerSync()` during
teardown and `MapFragment`'s internal `GestureLayer`. Full details (the ASCII diagram, key rules,
what still runs on the native-modules thread) are in
`android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/AGENTS.md`.

### ZOMBIE diagnostic system

A systematic defensive pattern against "zombie" native resources (GPU objects outliving their JS
references). The three managers (`PathLayerManager`/`MarkerLayerManager`/`ShapeLayerManager`) log
`"ZOMBIE: ..."` warnings and clear drawable references; the JS side prevents zombies via
`useNativeLayerLifecycle`'s `uuidRef` and `SceneSync.destroy()`. Full details in the android
`AGENTS.md`.

### Extension points for external layer-type libraries

The public API (`src/index.tsx`) exports stable hooks and contexts for building custom layer types
outside this repo (e.g. `react-native-mapsforge-vtm-ext-grib`):

| Export | Purpose |
|---|---|
| `MapHandleContext` | React context holding `nativeNodeHandle` + `scene` + `sync` |
| `useLayerAnchor` | Renders an anchor and registers its descriptor with the scene |
| `useLayerEntry` | Declares an entry (drawable/marker) inside a fragment owner |
| `useSceneUuidBinding` | Binds a resolved native uuid to an anchor/entry uid |
| `LayerScene` / `SceneSync` | The scene model + presenter (advanced extension integration) |
| `registerEntryPriorityHandler` | Registers the `applyEntryPriorities` bridge for a layer type |
| `useNativeLayerLifecycle` | `null → false → uuid` state machine for native resource lifecycle |
| `createMapHandle` | Non-hook factory — the imperative map-control + elevation API |
| `createMapHandleRegistry` | Singleton `wire`/`unwire` pattern for non-React code |
| `useMapEventInterval` | Poll a map-event ref at a fixed interval |
| `useViewportBbox` | Tile-snapped viewport bbox from map events |

Companion types: `CreateFlags`, `RemoveFlags`, `MapHandleContextValue`, `UseLayerAnchorOptions`.
See `docs/advanced/extending.md` and the `ext-plan` skill for the three extension patterns
(JS-only, TurboModule, vtm-shadowing).
