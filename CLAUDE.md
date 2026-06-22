# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`react-native-mapsforge-vtm` is a React Native (old architecture, bridge/`NativeModules`-based) wrapper
around [mapsforge/vtm](https://github.com/mapsforge/vtm) for offline vector map rendering from OpenStreetMap
data. **Android only** — there is no iOS implementation.

**This repo is being superseded by a rewrite.** A sibling project at `../test/react-native-mapsforge-vtm-new-arch/`
is reimplementing this library against the React Native New Architecture (Fabric + TurboModules), with the
component API being redesigned freely and no backwards-compatibility constraint. Once that rewrite is far
enough along, its `src/` and `android/` will be copied wholesale into this repo (not merged component by
component) — don't try to backport New Architecture patterns into this repo piecemeal. The one thing that
won't come from the rewrite is the publishing pipeline (`scripts/publish.sh`, `CHANGELOG.md` workflow) —
that stays as-is here.

There are currently unstaged local changes (`src/Context.ts`, edits to `src/index.tsx` and
`MapContainer.tsx` adding a `MapContext`/`wrapChildren` export). This is a working hack the user relies on
in another downstream project, not a change headed for a commit — don't "clean it up" or assume it reflects
where this codebase is going. It becomes moot once the rewrite is copied over.

## Common commands

This is a Yarn workspaces monorepo (`packageManager: yarn@3.6.1` — don't use `npm`). The library lives in
the repo root (`src/`, `android/`); `example/` is a workspace app for manual testing.

```sh
yarn                  # install deps for root + example workspaces
yarn typecheck        # tsc (no emit, just checks)
yarn lint             # eslint over **/*.{js,ts,tsx}
yarn test             # jest — note: the only test file is src/__tests__/index.test.tsx and it's a stub (it.todo)
yarn clean            # del-cli android/build example/android/build example/android/app/build lib
yarn prepare          # bob build — builds lib/ (commonjs + module + typescript) from src/, runs on install via "prepare"

yarn example start    # Metro for the example app
yarn example android  # build & run the example app on a connected device/emulator

yarn release <version>  # scripts/publish.sh — bumps version, validates CHANGELOG.md, tags, publishes to npm; requires `gh` CLI
```

`lefthook.yml` runs `eslint` and `tsc` on staged `*.{js,ts,jsx,tsx}` files as a pre-commit hook.

To work on native Android code, open `example/android` in Android Studio — library Java sources show up
under the `react-native-mapsforge-vtm` module (this repo is symlinked in via `example/`'s yarn workspace).

## Architecture

### Bridge pattern: layers are NativeModules, not native views

`MapContainer` renders the single native view (`MapViewManager` / Java-side `MapFragment`, registered as
view manager `MapViewManager`). Everything nested inside it — `LayerMapsforge`, `LayerBitmapTile`,
`LayerHillshading`, `LayerMBTilesBitmap`, `LayerPath`, `LayerPathSlopeGradient`, `LayerMarker`,
`LayerScalebar` — is a plain React component (renders `null`) that talks to a per-layer `NativeModule`
(`src/nativeMapModules.ts`) via RPC-style `createLayer`/`removeLayer` calls. Each native module call is keyed
by the `nativeNodeHandle` of the map view (obtained via `findNodeHandle` since old-arch native views don't
expose a handle any other way) and a `uuid` returned from `createLayer`, used later for `removeLayer`/update
calls. One Java module class per layer under
`android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/react/modules/` (e.g.
`MapLayerMarkerModule.java`, sharing common logic via `MapLayerBase.java`).

### Wiring layers together: prop injection via `cloneElement`, not context

`MapContainer.wrapChildren` walks its children with `Children.map`/`cloneElement` and injects
`nativeNodeHandle` (and a `reactTreeIndex`, used for native-side layer ordering) into any child whose
component type has a static `isMapLayer = true` flag (e.g. `LayerMarker.isMapLayer = true`). This recurses
into nested children so wrapper components between `MapContainer` and a layer don't break the wiring.
`LayerMarker` repeats the same `cloneElement` pattern one level down for its own children (`Marker`),
injecting `layerUuid` instead of walking via context. When changing how props reach layers/markers, the
walk-and-clone logic (and the `isMapLayer` marker) is the mechanism to look at — there is no context-based
wiring in this repo (the in-progress unstaged `MapContext` change is an exception layered on top for one
downstream consumer, not the established pattern).

### Update flow for layer props

Layer components generally hold a `uuid` ref (`useRefState`) for "do I have a native layer yet," and any
prop relevant to native layer creation triggers: remove existing layer → recreate with new props → fire
`onCreate`/`onChange`. Native calls are routed through the shared `promiseQueue` (`src/promiseQueue.ts`,
backed by `queue-promise`, concurrency 400) to bound how much concurrent traffic crosses the JS/Java bridge.
Native async events (map move, lifecycle, marker press, hardware keys, per-layer create/remove/change) come
back over `NativeEventEmitter`, matched in JS by comparing `response.nativeNodeHandle` against the
component's own handle, since RN's event emitter is global, not scoped to a view instance.

### Where types come from

`src/types.ts` re-exports the props/response types defined alongside each component (e.g.
`LayerMapsforgeProps` lives in `LayerMapsforge.tsx`, re-exported from `types.ts`) — when changing a
component's prop or response shape, edit it at the component file, not in `types.ts`.

### Native side layout

Under `android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/`:
- `react/modules/` — one `NativeModule` per layer/map-container, exposed to JS via `nativeMapModules.ts`.
- `react/views/` — `MapFragment` (the actual map view, vtm `MapView` host) and `MapViewManager`.
- `layers/vector/` — custom vtm layer subclasses (`PathLayer`, `VectorLayer`) used by the path/marker layers.
- Top-level helpers: `HgtReader`/`Utils` (elevation lookups from `.hgt` DEM files), `Gradient` (slope-color
  gradients for `LayerPathSlopeGradient`), `HandleLayerZoomBounds`/`HandleGroupLayerZoomBounds` (zoom-based
  layer visibility), `HardwareKeyListener`.
