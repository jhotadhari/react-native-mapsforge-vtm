# Migration guide: v0.7 → next major (New Architecture)

This library is being rewritten against React Native's New Architecture (Fabric + TurboModules).
The public API is changing freely — there is no backwards-compatibility shim. This document summarizes
what changed and how to update a consumer app.

## 1. Architecture & tooling bump

- Old Architecture (Bridge) → New Architecture (Fabric + TurboModules);
  `create-react-native-library` type `view-legacy` → `fabric-view`.
- `react` 18.3.1 → 19.0.0, `react-native` 0.76.0 → 0.78.2, `react-native-builder-bob` 0.30.3 → 0.40.6,
  Node 18 → 20.19.
- Library module SDK/tooling raised: Kotlin 1.7.0 → 2.0.21, `minSdkVersion` 21 → 24,
  `target`/`compileSdkVersion` 31 → 35, NDK 21.4 → 27.1. Example app: Kotlin 1.9.24 → 2.0.21, Gradle
  8.10.2 → 8.12, `targetSdkVersion` 34 → 35, NDK 26.1 → 27.1.
- New `codegenConfig` block in `package.json`; `ios/generated` codegen stubs are now present, but
  there is still no real iOS implementation — this library remains Android-only.
- ESLint inline config (`eslintConfig` in `package.json`) → flat config (`eslint.config.mjs`,
  ESLint v9+). Prettier moved out of `package.json` into `.prettierrc`/`.prettierignore` with
  materially different rules (tabs instead of spaces, `printWidth: 80`, new plugins) — this will
  reformat any forked/vendored copy of the source on first lint/format run.
- `newArchEnabled=true` / `hermesEnabled=true` are now required in the consumer app — there is no
  old-architecture code path anymore.

## 2. Public API / JS changes

- Internal layer wiring changed from prop-injection/child-cloning (the old static `isMapLayer` flag)
  to React Context (`MapHandleContext` + `useLayerOrder`). Invisible to normal usage — only matters if
  you built a custom layer component against the old flag.
- `LayerMapsforge` no longer bundles tile + buildings + labels into one native `GroupLayer`; each is
  its own real native layer, kept contiguous via `useLayerOrder`. Toggling `hasBuildings`/`hasLabels`
  is just mounting/unmounting a child — no other API change.
- `useRenderStyleOptions` (style-menu options for `LayerMapsforge`'s render theme) is now a plain
  `Promise` keyed only by the theme file path — no longer tied to a live map/`nativeNodeHandle`, and
  far cheaper (parses only `<stylemenu>`, cached by path + last-modified, instead of compiling the
  full theme via vtm's `XmlThemeBuilder`).
- `LayerMBTilesBitmap.alpha` is now a live float in `0–1` (`BitmapTileLayer.setBitmapAlpha`), not a
  value baked into the tile source at creation time using the wrong `0–255` range.
- Map lifecycle events (`onMapUpdate` / `onPause` / `onResume`) are now direct Fabric event props on
  `MapContainer` itself, not a separate `useMapEvents()` subscription hook. This is a deliberate
  Fabric-native pattern, not an oversight.

  ⚠️ **Breaking subtlety:** `onMapUpdate` / `onPause` / `onResume` / `onError` on `MapContainer` are
  Fabric native-view event props, so your handler receives `NativeSyntheticEvent<T>` — you must read
  `event.nativeEvent`, not the event itself. This differs from every promise-based API in this library
  (`useMap()`, `useRenderStyleOptions()`), which resolve plain objects. If you're migrating handlers
  that used to receive a plain response object directly, add the `.nativeEvent` unwrap or they will
  silently always see `undefined` fields. TypeScript will catch this mismatch unless you cast it away
  — the example app's `sharedDeps.ts` and `MapInfo.tsx` show the correct unwrap pattern.

- `useMap()` — imperative map control (replaces `MapContainerModule`):

  `useMap()` replaces every old imperative `MapContainerModule` call (`zoomIn` / `zoomOut` /
  `setCenter` / `setPropsInteractionsEnabled` / `setMapCenterAnimated` / etc.). It returns methods
  (`getPosition()`, `panTo()`, `animateTo()`, …) that operate on the nearest ancestor
  `<MapContainer>`.

  - Call it from any component nested inside `<MapContainer>`; for components rendered outside the
    map tree (e.g. a sibling toolbar), use the `nativeNodeHandleOverride` parameter — lift
    `nativeNodeHandle` / `setNativeNodeHandle` up to a shared ancestor and pass it in.
  - `requireHandle()` throws synchronously when no handle is available (the map hasn't mounted yet).
    Guard call sites that fire from user interactions (e.g. a button `onPress`) until a handle
    exists, to avoid an uncaught throw crashing the event handler.

### `Location` → `Position` (GeoJSON tuple)

The old `Location { lng, lat, alt? }` object type is gone everywhere. All positional data now uses
GeoJSON `Position` tuples: `[lng, lat, alt?]`. This is one of the largest mechanical changes —
expect to touch every file that constructs, destructures, or accesses `.lng` / `.lat` / `.alt` on
positional data. Affected props and return values:

- `MapContainer`'s `center` prop and `onMapUpdate` response field
- `Marker`'s `position` prop
- `LayerPath`'s `positions` prop — **also renamed to `coordinates`** (a prop rename, not just a
  type change)
- `useMap()` return values (`getPosition()`, `animateTo()` targets, etc.)

### `triggerEvent` moved from `LayerMarker` to `MapContainer`

- `LayerMarker`'s `triggerEvent` prop is gone. Use `MapContainer`'s new `triggerEvent` prop instead.
  It fires on **all** markers on the map (not scoped to a group), with the same `x`/`y` screen
  coordinates and `strategy` (`'all'` / `'first'` / `'nearest'`, default `'nearest'`).

  ```tsx
  // Before
  const ref = useRef<LayerMarkerTriggerEvent>(null);
  <LayerMarker triggerEvent={ref}>…</LayerMarker>

  // After
  const ref = useRef<null | ((params: {
    x: number; y: number; strategy?: 'first' | 'nearest' | 'all';
  }) => void)>(null);
  <MapContainer triggerEvent={ref}>…</MapContainer>
  ```

- The old screen-space hit-test (with a broken mercator `box.contains()` guard and incorrect
  center-offset subtraction) was replaced by geo-space hit testing via `fromScreenPoint()`,
  matching the approach the working path trigger already used. This eliminates all coordinate‑space
  fragility.

- `LayerMarker` is no longer required for markers — bare `<Marker>` components work directly inside
  `<MapContainer>` without a `<LayerMarker>` wrapper. `LayerMarker` still provides per-group default
  symbol inheritance and layer‑scoped event subscriptions, but neither is needed for basic marker
  rendering or `triggerEvent`.

### Layer composition through arbitrary nesting

Layer components now wire themselves through React Context (`MapHandleContext` + `useLayerOrder`)
instead of the old static `isMapLayer` prop-injection walk. This means layers can be nested at
arbitrary depth inside wrapper components, conditional renders, `.map()`-rendered lists, etc. —
they need no special handling. The old restriction that layers had to be direct children of
`<MapContainer>` is gone.

- The following old public exports from the library root have **no replacement** — if your app
  imports any of these directly, you'll need to inline your own copy:
  - `promiseQueue`
  - `usePromiseQueueState`
  - `useRefState`
  - `usePrevious`
  - `useMapLayersCreated`
  - `constants` (`LINKING_ERROR`, `BUILT_IN_THEMES`, `MarkerHotspotPlaces`)
  - `MapLayerMapsforgeModule` and its `getRenderThemeOptions` static method — switch to
    `useRenderStyleOptions` (see above); the module itself is no longer exported.
- `LayerPathSlopeGradient` remains permanently out of scope for this library (it may become its own
  separate, single-purpose package later). Its supporting native code (`Gradient.java`, the
  `Coordinate` datetime field, `MapLayerPathSlopeGradientModule`) is gone.
- **Intentionally dropped, not coming back:** `LayerPath`'s old GPX-file loading
  (triggered when `filePath` ended in `.gpx`, via the `android-gpx-parser` dependency) has no
  native-side implementation anymore. If your app loads paths from `.gpx` files, pre-parse them to
  coordinates in JS before passing `positions` to `LayerPath`.
- The example app dropped the old `ExampleDem` and `ExampleLayerPathSlopeGradient` demos, and gained
  `mbtilesBitmap`, `hillshading`, `mapsforge`, and `manyLayers` (stress test) demos.

## 3. `CanvasAdapterModule` is back, name unchanged

- `CanvasAdapterModule` (`setTextScale` / `setLineScale` / `setSymbolScale` — global vtm
  rendering-scale controls), dropped during the New Architecture rewrite, is back with the same
  name and call shape as before: `CanvasAdapterModule.setTextScale(2)`. Unlike every other
  rewritten module (`LayerMarker`, not `LayerMarkerModule`), this one deliberately keeps the
  "Module" suffix — it's the only module exported directly to consumers; every other module backs
  a component and is never exported on its own. No call-site changes needed if your app was
  already using the pre-rewrite name. Behavior is unchanged: the scale only affects layers/themes
  parsed *after* the setters are called, so call them before mounting the
  `MapContainer`/`LayerMapsforge` whose theme you want scaled.

## 4. Bug fixes carried forward (informational only, no action needed)

- Layer reordering no longer causes map-wide tile flicker (previously a global `clearMap()` on every
  reorder; now scoped to a per-layer `onMapEvent(CLEAR_EVENT)` only for layers new to the tracked
  set).
- Layers built on `BitmapTileLayer` (hillshading, mapsforge tiles) now reliably trigger their initial
  tile load without needing an incidental follow-up map event.
- Mounting many sibling layers at once now batches into a single native `reorderLayers` call instead
  of firing one per layer.

## 5. Migration checklist

1. Bump `react-native` to ≥ 0.80.0 and `react` to ≥ 19.0.0; enable `newArchEnabled=true` /
   `hermesEnabled=true` in your app (there is no old-arch fallback anymore).
2. Search your app for imports of: `promiseQueue`, `usePromiseQueueState`, `useRefState`,
   `usePrevious`, `useMapLayersCreated`, `constants` / `BUILT_IN_THEMES` / `MarkerHotspotPlaces`,
   `MapLayerMapsforgeModule` / `getRenderThemeOptions`, `LayerPathSlopeGradient`. None of these have
   a drop-in replacement — inline your own copy if still needed (or switch to `useRenderStyleOptions`
   for `getRenderThemeOptions`).
3. No rename needed for `CanvasAdapterModule` — it kept its pre-rewrite name, including the
   "Module" suffix, since it's exported directly rather than backing a component (see §3 above).
4. If you use `LayerPath` with a `.gpx` `filePath`, pre-parse the GPX file to coordinates in JS before
   upgrading, or hold off on that usage.
5. Replace any use of the old `useMapEvents()` hook with the new direct props on `MapContainer`
   (`onMapUpdate` / `onPause` / `onResume`). **Add `.nativeEvent` unwrap** to every handler — these
   are Fabric event props, so your handler receives `NativeSyntheticEvent<T>`, not a plain object.
   The example app's `sharedDeps.ts` and `MapInfo.tsx` show the correct pattern.
6. Replace all `MapContainerModule` calls with `useMap()`. Guard any `requireHandle()` call-sites
   that fire from user interactions (e.g. button `onPress`) until a handle exists — it throws
   synchronously when the map hasn't mounted yet.
7. Convert all `Location { lng, lat, alt? }` objects to GeoJSON `Position [lng, lat, alt?]` tuples
   across your entire app. Rename `LayerPath.positions` to `LayerPath.coordinates`.
8. If you use `LayerMBTilesBitmap`'s `alpha`, convert any `0–255`-scaled value to `0–1`.
9. If you use `LayerMapsforge`'s render-theme/style options, switch to the new
   `useRenderStyleOptions` signature (theme path only, no map handle required).
10. If you use `LayerMarker.triggerEvent`, move it to `MapContainer.triggerEvent`. Bare `<Marker>`
    components no longer need a `<LayerMarker>` wrapper — they work directly inside
    `<MapContainer>`. See §2 above for the new API shape.

11. Re-run lint/format once across your app after upgrading — Prettier's rules changed (tabs vs
    spaces, `printWidth`, plugin set), so expect a one-time reformat diff.
