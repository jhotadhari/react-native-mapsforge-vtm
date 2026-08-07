# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.8.0] - 2026-08-07

This release is a complete rewrite of the library against the **React Native New Architecture**
(Fabric + TurboModules). Every component, hook, and native module has been rebuilt from the
ground up. The new design replaces the old bridge-based `NativeModules` + `NativeEventEmitter`
pattern with codegen-generated Fabric components and TurboModules, context-driven layer ordering
instead of `cloneElement` prop injection, and a serialized UI-thread mutation queue that
eliminates the threading races of the old architecture.

Performance is dramatically improved: shared-layer batching collapses hundreds of paths or
markers into a single GPU draw call, marker creation uses batch bridge calls (N markers in
one round-trip), and the new native shared-value bridge delivers map position updates at a true
60 fps with zero JavaScript bridge crossings. The API surface has been streamlined — imperative
map control lives in the `useMap()` hook, map events arrive as direct Fabric props, and a new
`react-native-mapsforge-vtm/reanimated` subpackage provides worklet-based overlay positioning.

### Added

- **React Native New Architecture** — full Fabric + TurboModules support. The library now requires
  React Native 0.76+ with the New Architecture enabled.
- **`LayerPathJts`** — dedicated-layer JTS path component. One native layer per JS component for
  guaranteed render order, with `addGreatCircle()` for great-circle arcs, built-in
  Douglas-Peucker generalization via `Style.generalization`, JTS `LineString` input, and
  per-layer gesture hit-testing (`onPress`, `onLongPress`, `onDoubleTap`).
- **`LayerShape`** — geometric shape overlays. Draw JTS polygons (with holes), circles (center +
  radius in km), rectangles, hexagons, and points with full `GeometryStyleJts` styling (fill,
  stroke, transparency, stipple) and gesture callbacks.
- **`SharedLayer`** — explicit shared-layer grouping component. Collapses multiple `LayerPath` or
  `Marker` children into one native draw call for maximum performance at scale.
- **`ReindexScope`** — sentinel-based render-order placeholders. Ensures async children (lazy-loaded
  markers, data-driven paths) insert at the correct position in the layer stack, preserving the
  invariant that native layer rendering strictly follows React component tree order.
- **`useMap()` hook** — unified imperative map control. Provides `animateTo()`, `getPosition()`,
  `fitBounds()`, `triggerEvent()` (per-layer), and `getDebugLayerDump()` for introspection.
  Replaces the old `MapContainer` ref methods.
- **`useMapPosition()`** from `react-native-mapsforge-vtm/reanimated` — 60 fps shared-value map
  position tracking. Returns reanimated `SharedValue`s for center, zoom, bearing, and tilt that
  update on every render-thread frame. Worklet consumers read with zero bridge crossings.
- **`useMapOverlay()`** from `react-native-mapsforge-vtm/reanimated` — worklet-based Mercator
  projection. Converts lat/lng coordinates to screen positions on the UI thread for smooth
  overlay animations. Includes `toScreenPosition()` and `fromScreenPosition()` utilities.
- **Native shared-value bridge** — bypasses the JavaScript bridge entirely. vtm's render thread
  writes position data directly into C++ `Synchronizable` primitives via `MapPositionWriter`;
  a worklet poller reads them each frame and updates standard `SharedValue` objects. Zero
  bridge crossings at every stage.
- **`MapHandleContext` + `useLayerOrder()`** — context-driven layer ordering. Each layer component
  registers its position via a stable render-order cursor, debouncing a single native
  `reorderLayers` call when the resolved order changes. Replaces the old `cloneElement` /
  static `isMapLayer` tree walk.
- **`useNativeLayerLifecycle()`** — unified create/remove lifecycle. Tracks a `null → false → uuid`
  state machine for every layer component, with centralized `reportNativeError()` and teardown
  race protection via `mountedRef`.
- **Extension points** — `MapHandleContext`, `createLayerOrderRegistry`, `useLayerOrder`,
  `useNativeLayerLifecycle`, and their types are exported as a stable public API for building
  third-party layer-type libraries (e.g. `react-native-mapsforge-vtm-ext-grib`).
- **`ext-plan` skill** — interactive scaffolding for extension libraries. Walks through
  architectural decisions (JS-only, TurboModule, or vtm-shadowing patterns) and generates a
  plan file plus scaffolded repo.
- **`CanvasAdapterModule`** — global scale control. Set `lineScale`, `textScale`, and `symbolScale`
  for all mapsforge layers across all `MapContainer` instances (must be called before the first
  map initializes).
- **Batch marker creation** — `createMarkers()` sends N markers in a single bridge call via
  `MarkerBatchQueue`, dramatically reducing latency for large marker sets.
- **`MapMutationQueue`** — serialized UI-thread layer mutation queue. All `layers().add/remove`
  and the batch-level `updateMap()` flow through a single `flush()` call on the Main Looper,
  eliminating the threading races of the old ad-hoc approach.
- **`ElevationReader` / `getAltitudeAtPosition()`** — thin, non-blocking elevation API. Multithreaded
  HGT file loading with interpolation support (3 arc-second DEMs). Replaces the old `HgtReader`
  with its callback-heavy view props.
- **`getDebugLayerDump()`** — layer introspection. Returns a structured snapshot of every native
  layer's fragment UUIDs, position indices, and type for debugging render-order issues.
- **`useLayerDebugInfo()` / `LayerDebugTree`** — debug visualization components for inspecting
  the live layer tree in development.
- **Flex layout support** — `MapContainer` now accepts `flex: 1` and falls back to measured view
  dimensions when no explicit `width`/`height` is set.
- **Bearing and tilt** — included in map position data, overlay projection, and map events.
- **`RenderThemeMenuLoader`** — parses `<stylemenu>` from render-theme XML for
  `useRenderStyleOptions()`.
- **Trailing-edge flush** — map position events fire at the render thread's native frame rate
  (~60 fps) with a trailing-edge guarantee (the latest position is always delivered).
- **Comprehensive documentation** — rewritten README, new `docs/advanced/extending.md`,
  `docs/advanced/performance.md` and inline code comments.
- **`FixedWindowRateLimiter`** — throttles native map-event emission to avoid flooding the
  Fabric event channel.

### Changed

- **Breaking:** Complete rewrite against React Native New Architecture. The library requires
  React Native 0.76+ with `newArchEnabled=true`. The old bridge-based architecture is no
  longer supported.
- **Breaking:** Layer ordering uses context-based `MapHandleContext` + `useLayerOrder` instead
  of `cloneElement` prop injection. The `reactTreeIndex` prop is gone — ordering follows
  React component tree position automatically.
- **Breaking:** Map events delivered as direct Fabric event props on `MapContainer`
  (`onMapUpdate`, `onTap`, `onLongPress`, `onPause`, `onResume`) instead of a global
  `NativeEventEmitter`. Each `MapContainer` receives only its own events.
- **Breaking:** `useMapEvents()` hook removed. Use the `onMapUpdate` prop on `MapContainer`
  for callback-based consumption, or `useMapPosition()` from the reanimated subpackage for
  shared-value consumption.
- **Breaking:** `useMapLayersCreated()` hook removed. Extension authors should use
  `useNativeLayerLifecycle()` instead.
- **Breaking:** `usePromiseQueueState()`, `promiseQueue`, `usePrevious()`, and `useRefState()`
  removed — these were internal implementation details never intended as public API.
- **Breaking:** `onMapEvent` prop renamed to `onMapUpdate`.
- **Breaking:** `mapUpdateInterval` prop removed. Events now fire at the render thread's
  native frame rate (~60 fps) with trailing-edge flush.
- **Breaking:** `responseInclude` prop removed. The event shape is now fixed and simplified.
- **Breaking:** `emitsMapEvents` prop removed — map events always emit.
- **Breaking:** `emitsHardwareKeyUp` / `onHardwareKeyUp` props removed. Hardware key handling
  is no longer built into the map view.
- **Breaking:** `nativeMapModules` export removed. TurboModule specs are internal; the public
  API is the React components, hooks, and types exported from the main entry point.
- **Breaking:** HGT/altitude API redesigned. The old `HgtReader` with its `hgtFileInfoPurgeThreshold`
  and `hgtInterpolation` view props is replaced by the thin `ElevationReader` + imperative
  `getAltitudeAtPosition()` method on `useMap()`.
- **Breaking:** `setToBounds()` moved to `useMap().fitBounds()`.
- **Breaking:** `triggerEvent()` moved to per-layer dispatch via `useMap()`.
- **Breaking:** Zoom level types changed from `Int32` to `Double`/`Float` throughout the
  native layer, enabling fractional zoom support.
- **Breaking:** `LayerPath` `simplificationTolerance` prop replaced by external `simplify`
  library usage (apply simplification to your coordinate arrays before passing them in).
- `LayerPath` now uses a shared `VectorLayer` architecture — many JS `LayerPath` components
  (or paths inside a `SharedLayer`) collapse into one native layer, with per-drawable
  priority for ordering. At the JS level the API is identical.
- `LayerMarker` / `Marker` now use a shared `ItemizedLayer` with sorted insertion by
  position index. The JS API is unchanged.
- Path rendering uses `PathLayer` (custom vtm layer subclass) with per-drawable priority
  for correct ordering within the shared layer.
- Threading model: all layer mutations (add, remove, reorder) are serialized through
  `MapMutationQueue.flush()` on the UI thread. `scheduleUpdate()` coalesces per-entry
  `updateMap()` calls via `AtomicBoolean` CAS + `Handler.post`.
- Example app completely rewritten with 10+ focused examples (basic, mapsforge, hillshading,
  mbtiles-bitmap, scalebar, trigger, many-layers, multi-map, reanimated-overlay,
  layer-order-verification) and a new picker-based navigation UI.
- Build tooling: uses `bob build` for codegen + TypeScript compilation. ESLint flat config
  (`eslint.config.mjs`). Prettier for formatting.
- Remaining dependencies updated where compatible (see TODO.md item 1 for details).

### Removed

- **`LayerPathSlopeGradient`** — the slope-gradient path rendering component.
- **GPX-file loading** — the bundled `DummyContent.java` GPX parser and example `.gpx`
  assets. GPX parsing is an application concern; pass parsed coordinates to `LayerPath`
  or `LayerPathJts` directly.
- **`useMapEvents()` hook** — replaced by the `onMapUpdate` Fabric event prop and
  `useMapPosition()` shared values.
- **`useMapLayersCreated()` hook** — replaced by `useNativeLayerLifecycle()`.
- **`usePromiseQueueState()` / `promiseQueue`** — internal implementation detail, no
  longer exposed.
- **`usePrevious()` / `useRefState()`** — internal utility hooks, no longer exposed.
- **`nativeMapModules` export** — the old barrel export for `NativeModules` references.
  TurboModule specs are codegen-internal.
- **`constants.ts` / `utils.ts`** — unused after the rewrite.
- **`HardwareKeyListener`** — volume-key event handling removed from the map view.
- **`MapsforgeVtmPackage`** (old bridge package) — replaced by `MapsforgeVtmViewPackage`
  (Fabric).
- **`HandleGroupLayerZoomBounds` / `HandleLayerZoomBounds`** — replaced by
  `LayerZoomBoundsHelper`.
- **`Coordinate.java` / `Gradient.java`** — legacy helper classes from the old bridge
  architecture.
- **`mapUpdateInterval` prop** — events always fire at the render thread's native rate.
- **`responseInclude` prop** — event shape is now fixed.
- **`emitsMapEvents` / `emitsHardwareKeyUp` / `onHardwareKeyUp` props** — removed.
- **`setToBounds()` ref method** — use `useMap().fitBounds()` instead.

## [0.7.0] - 2025-04-05

This release contains love for elevation data <3

### Added

- All new *Clear Asymmetry* shading algorithms are implemented as options for `LayerHillshading` (thank you @Sublimis to create them and implement them to `mapsforge`, so I can use theme here):
    - [`AdaptiveClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/AdaptiveClasyHillShading.java)
  	- [`StandardClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/StandardClasyHillShading.java)
  	- [`SimpleClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/SimpleClasyHillShading.java)
  	- [`HalfResClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HalfResClasyHillShading.java)
  	- [`HiResClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HiResClasyHillShading.java)

### Changed

- Update dependencies; Update dependency `com.github.mapsforge.vtm` to version `0.25.0`.
- The java side of the `LayerHillshading` component is entirely new. Now the component is using the new [`vtm-hillshading module`](https://github.com/mapsforge/vtm/pull/1189), implemented in `com.github.mapsforge.vtm` version `0.25.0`. Thank you @devemux86! It's much faster, stable and the generated tiles are more accurate.
- Change the cache filename for `LayerHillshading`: Remove dots and dashes. That makes previous caches obsolete.
- Add props to `MapContainer` component:
  	- `hgtInterpolation` whether to interpolate elevation or not.
  	- `hgtFileInfoPurgeThreshold` how many neighboring DEMs should be kept in memory.
- Change `HgtReader` class (to retrieve altitude at certain coordinates):
  	- Better performance. It's multithreaded now and loading new DEM (hgt) files into memory doesn't block the ui thread anymore.
  	- Added support for interpolation. The code for interpolation is copied from [JOSM Elevation Plugin by Harald Hetzner](https://github.com/hhtznr/JOSM-Elevation-Plugin/tree/main), thank you @hhtznr!.
  	- For now the `HgtReader` only supports DEMs with a resolution of 3 arc seconds. Instead of fixing this issue, I will wait that this feature will be implemented properly into mapsforge, see [#1621
  ](https://github.com/mapsforge/mapsforge/issues/1621).

### Removed

- Remove built in themes `MOTORIDER_DARK` and `OSMAGRAY`. Because they are not existing anymore in latest version of `com.github.mapsforge.vtm`.

## [0.6.0] - 2025-03-26

### Added

- New props for `LayerMapsforge`: `hasBuildings` and `hasLabels`.
- New prop for `LayerBitmapTile`: `alpha` to control opacity.
- `CanvasAdapterModule` to control `lineScale`, `textScale` and `symbolScale` of all mapsforge layers of all `MapContainer`. The methods have to be called before the first `MapContainer` is initialized.

### Changed

- Individual cache dirs for `LayerHillshading`. Added props:
    - `cacheDirBase` defaults to `/`. If `/`, java will fallback to app internal cache dir.
    - `cacheDirChild` defaults to ``. If ``, will fallback to cache dbname.
- Individual cache dirs for `LayerBitmapTile`. Added props:
    - `cacheDirBase` defaults to `/`. If `/`, java will fallback to app internal cache dir.
    - `cacheDirChild` defaults to ``. If ``, will fallback to slugify url.

## [0.5.3] - 2025-03-19

### Added

- Add prop `hgtReadFileRate` to `MapContainerModule`. To control `HgtReader` read file throttle rate.

### Changed

- `HgtReader`: Purge hgt file data if not neighbors.

### Fixed

- `LayerHillshading`: Should update on `magnitude` or `cacheSize` changes.
- `LayerBitmapTile`: property `cacheSize` was not working.
- Missing export `useMapLayersCreated`.

## [0.5.2] - 2024-12-29

### Added

- prop `onHardwareKeyUp` to `MapContainer` component. Function that gets called when certain hardware keys are pressed.
- prop `emitsHardwareKeyUp` to `MapContainer` component. Defines which hardware key events are consumed (keycodes: `KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN`). If they are consumed, these events don't bubble, `onHardwareKeyUp` js event is triggers and `MapContainer:onHardwareKeyUp` function is called.

### Changed

- Make all `MapEventResponse` properties optional.

### Fixed

- `HardwareKeyListener` consumes all key events and prevents bubbling, breaks all key events. Now it only consumes the event if prop `emitsHardwareKeyUp` contains event keycode (`KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN`).
- Missing type exports: `XmlRenderTheme`, `RenderStyleOptionsCollection`.

## [0.5.1] - 2024-11-16

### Fixed

- `LayerMBTilesBitmap` `NullPointerException`. `dataSource.getBounds()` might be null.

## [0.5.0] - 2024-11-14

### Changed

- Changed export of nativeModules. They are exported directly, not bundled as a `nativeModules` object anymore.

### Fixed

- Types export

## [0.4.0] - 2024-11-14

### Added

- Added prop `onMapEvent` to `MapContainer` component. It listens to the same event like the `useMapEvents` hook.
- Added props `enabledZoomMin` and `enabledZoomMax` to all base layer components (`LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap` and `LayerMapsforge`).

### Changed

- Added prop `emitsMapEvents` to `MapContainer` component. If `false`, the map won't emit any mapEvents and the `onMapEvent` or `useMapEvents` are useless. If `undefined`|`null` it will be `true` if `onMapEvent` prop is set. **If you want to use the `useMapEvents` hook, you have to set `emitsMapEvents` to `true`!**
- Renamed type `mapEvent` to `MapEventResponse`.
- Renamed `minZoom`|`MaxZoom` to `zoomMin`|`zoomMax` and `setMinZoom`|`setMaxZoom` to `setZoomMin`|`setZoomMax`. It was not consistent, both naming were used by different components.

## [0.3.0] - 2024-11-06

Path improvements

### Added

- Gesture events for both Path layers. Added props: `onPress`, `onLongPress` and `onDoubleTap`.
- Trigger event at any point at the map, for both Path layers. Added prop: `onTrigger`. Added method `triggerEvent` to path modules.
- Possibility to simplify paths. Added prop: `simplificationTolerance`.

### Changed

- Both path layer modules. Now they work the same way and `MapLayerPathSlopeGradientModule` extends `MapLayerPathModule`.
- Removed `strokeWidth` prop from `LayerPathSlopeGradient`. Now it uses the same `style` prop like `PathLayer`. Just that the color will be overwritten by the gradient color.

### Fixed

- The zickzacky appearance of `PathLayer`. Now it uses the same way of rendering like the `LayerPathSlopeGradient` component.

## [0.2.0] - 2024-11-06

Markers, hurrah.

### Added

- `LayerMarker` to hold `Marker` components.
    - Markers support raster image or svg symbols. Or symbols fallback to a customizable circle.
    - Markers support `press` and `longPress` events. Furthermore events can be triggered at any position on the map.

### Changed

- Responds types extend the `ResponseBase` interface.

### Fixed

- Example `PickerModalControl`, if options are empty. Conditions have to be boolean, otherwise react wants to render them as text, without `Text` component.

## [0.1.3] - 2024-10-30

Just updated README.md

## [0.1.2] - 2024-10-30

### Fixed

- Layer props: make reactTreeIndex optional. It is required but the MapContainer will mix it in

## [0.1.1] - 2024-10-30

### Fixed

- Publish script changelog

## [0.1.0] - 2024-10-30

### Added

- Better example app.
- Catch blocks to all native module methods. And `onError` function prop to all components and hooks.
- New `MapContainer` prop: `responseInclude`. We don't need to send everything always through the bridge bottleneck.
- New `MapContainer` prop: `mapEventRate`, the update rate of map events.
- `MapContainerModule` method: `setToBounds`.
- Some components respond their `bounds` and more meta data.
- New `LayerPath` prop: `style`. To parse almost all options from js to java `org.oscim.layers.vector.geometries.Style` (`texture` not supported yet).

### Changed

- Migrated all js to typeScript.
- Huge refactor.
- Some variable names and props have changed.

## [0.0.1] - 2024-10-25

First bumpy version

[Unreleased]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.3...v0.6.0
[0.5.3]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/releases/tag/v0.0.1
