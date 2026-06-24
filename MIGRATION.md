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
- The following old public exports from the library root have **no replacement** — if your app
  imports any of these directly, you'll need to inline your own copy:
  - `promiseQueue`
  - `usePromiseQueueState`
  - `useRefState`
  - `usePrevious`
  - `useMapLayersCreated`
  - `constants` (`LINKING_ERROR`, `BUILT_IN_THEMES`, `MarkerHotspotPlaces`)
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

1. Bump `react`, `react-native`, Node, and Kotlin to the versions above; enable
   `newArchEnabled=true` / `hermesEnabled=true` in your app (there is no old-arch fallback anymore).
2. Search your app for imports of: `promiseQueue`, `usePromiseQueueState`, `useRefState`,
   `usePrevious`, `useMapLayersCreated`, `constants` / `BUILT_IN_THEMES` / `MarkerHotspotPlaces`,
   `LayerPathSlopeGradient`. None of these have a drop-in replacement — inline your own copy if still
   needed.
3. No rename needed for `CanvasAdapterModule` — it kept its pre-rewrite name, including the
   "Module" suffix, since it's exported directly rather than backing a component (see §3 above).
4. If you use `LayerPath` with a `.gpx` `filePath`, pre-parse the GPX file to coordinates in JS before
   upgrading, or hold off on that usage.
5. Replace any use of the old `useMapEvents()` hook with the new direct props on `MapContainer`
   (`onMapUpdate` / `onPause` / `onResume`).
6. If you use `LayerMBTilesBitmap`'s `alpha`, convert any `0–255`-scaled value to `0–1`.
7. If you use `LayerMapsforge`'s render-theme/style options, switch to the new
   `useRenderStyleOptions` signature (theme path only, no map handle required).
8. Re-run lint/format once across your app after upgrading — Prettier's rules changed (tabs vs
   spaces, `printWidth`, plugin set), so expect a one-time reformat diff.
