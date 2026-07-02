# react-native-mapsforge-vtm

React Native components for offline vector maps, built on [mapsforge/vtm](https://github.com/mapsforge/vtm).
Renders OpenStreetMap data from `.map` files, online raster tiles, MBTiles, hillshading from DEM data,
and geometric overlays — all without a network connection (except tile/bitmap layers).

**Android only** · **React Native New Architecture (Fabric + TurboModules)** · **RN ≥ 0.80.0**

## Requirements

- **React Native ≥ 0.80.0** with the **New Architecture** enabled. The library ships
  pre-generated native code that requires `target_compile_reactnative_options` (introduced in
  React Native 0.80.0).
- **Android only** — `ios/generated` codegen stubs exist so the New Architecture build
  doesn't fail, but there is no real iOS implementation.
- `newArchEnabled=true` in your app's `gradle.properties`.

## Quick Example

```tsx
import {
  MapContainer,
  LayerBitmapTile,
  LayerScalebar,
} from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer
    center={[-77.6, -9.1]}
    zoomLevel={12}
    width={400}
    height={600}
  >
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
    <LayerScalebar />
  </MapContainer>
);
```

**[Full documentation →](./docs/README.md)** — API reference for 13 components, 2 hooks,
2 debug tools, and advanced topics.

## Installation

```sh
yarn add react-native-mapsforge-vtm
```

See the **[Installation Guide](./docs/getting-started/installation.md)** for prerequisites,
Android setup, and build configuration.

## Examples

The example app includes **21 runnable examples** across 3 categories:

| Category | Examples |
|---|---|
| **layers** | basic, mapsforge, mbtiles-bitmap, hillshading, canvas-adapter, layer-shapes, coastlines, path-jts, markers |
| **mapControls** | pan-zoom, fit-bounds, fly-ease, viewport-orientation, trigger, multi-map |
| **gestures** | tap-events, layer-order-verification, many-layers, many-shapes, mixed-grouping, shared-layer-grouping |

```sh
yarn example start      # start Metro
yarn example android    # build & run on device/emulator
```

## Where to get maps

- Vector maps (mapsforge V5 `.map`): [openandromaps.org](https://www.openandromaps.org/en/downloads)
- Raster overview maps (MBTiles): [openandromaps.org — general maps](https://www.openandromaps.org/en/downloads/general-maps)
- Digital elevation models (`.hgt` at 3 arc-second): [viewfinderpanoramas.org](https://viewfinderpanoramas.org/dem3.html)

## Components

| Component | Description |
|---|---|
| [`MapContainer`](./docs/components/map-container.md) | Root map view (Fabric component) |
| [`LayerMapsforge`](./docs/components/layer-mapsforge.md) | Offline vector maps from `.map` files |
| [`LayerBitmapTile`](./docs/components/layer-bitmap-tile.md) | Online raster tiles (OSM, custom) |
| [`LayerMBTilesBitmap`](./docs/components/layer-mbtiles-bitmap.md) | Offline raster from MBTiles |
| [`LayerHillshading`](./docs/components/layer-hillshading.md) | Shaded relief from DEM (`.hgt`) data |
| [`LayerPath`](./docs/components/layer-path.md) | Shared-layer paths for bulk rendering (50–1000+) |
| [`LayerPathJts`](./docs/components/layer-path-jts.md) | Dedicated paths with JTS features and guaranteed z-order |
| [`LayerShape`](./docs/components/layer-shape.md) | Geometric shapes (polygons, circles, rectangles, etc.) |
| [`LayerScalebar`](./docs/components/layer-scalebar.md) | Map scale bar |
| [`LayerMarker` / `Marker`](./docs/components/layer-marker.md) | Marker container + individual markers |
| [`SharedLayer`](./docs/components/shared-layer.md) | Collapse same-type children into shared native layer fragments |
| [`ReindexScope`](./docs/components/reindex-scope.md) | Signal layer reorder to the native stack |

## Hooks

| Hook | Description |
|---|---|
| [`useMap()`](./docs/hooks/use-map.md) | Imperative map control (pan, zoom, animate, fly, fitBounds, altitude queries) |
| [`useRenderStyleOptions()`](./docs/hooks/use-render-style-options.md) | Read render-theme style menu options |

## Debug tools

| Tool | Description |
|---|---|
| [`useLayerDebugInfo()`](./docs/debug/use-layer-debug-info.md) | Live layer-tree introspection hook |
| [`LayerDebugTree`](./docs/debug/layer-debug-tree.md) | Visual debug overlay component |

## Resources

- **[Documentation](./docs/README.md)** — Full API reference, guides, and advanced topics
- **[Terminology](./docs/TERMINOLOGY.md)** — Inclusive language conventions used in this project
- **[Migration Guide (v0.7 → New Architecture)](./MIGRATION.md)** — Breaking changes from the old bridge-based API
- **[Known Issues](./docs/advanced/known-issues.md)** — Current bugs, limitations, and workarounds
- **[CHANGELOG.md](./CHANGELOG.md)** — Version history
- **[TODO.md](./TODO.md)** — Open work and planned improvements

## Contributing

Contributions welcome. Report [issues or suggest features](https://github.com/jhotadhari/react-native-mapsforge-vtm/issues),
or [fork the repository and make pull requests](./CONTRIBUTING.md).

[![liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/jhotadhari/donate)
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/H2H3162PAG)

## License

MIT

## Apps using react-native-mapsforge-vtm

- The [example app](https://github.com/jhotadhari/react-native-mapsforge-vtm/tree/main/example), included in this repository
- [straymap](https://github.com/jhotadhari/straymap)

## Credits

This library is a React Native wrapper around [mapsforge/vtm](https://github.com/mapsforge/vtm).
**All credit for the map rendering engine goes to the mapsforge and vtm projects.**

### Runtime dependencies

**JavaScript** (bundled with the library):
- [lodash-es](https://lodash.com) — tree-shakeable utility functions

**Native (Android)** — bundled via Gradle:
- [vtm](https://github.com/mapsforge/vtm) (0.28.0) — the rendering engine, plus
  `vtm-android`, `vtm-themes`, `vtm-jts`, `vtm-http`, `vtm-mvt`, `vtm-android-mvt`,
  and `vtm-hillshading`
- [mapsforge](https://github.com/mapsforge/mapsforge) (0.28.0) — map file reader (`mapsforge-core`,
  `mapsforge-map`, `mapsforge-map-android`)
- [JTS Topology Suite](https://github.com/locationtech/jts) (`jts-core:1.20.0`) —
  geometry engine for path and shape layers
- [AndroidSVG](https://bigbadaboom.github.io/androidsvg/) — SVG rendering for vtm
- [OkHttp](https://square.github.io/okhttp/) / [Okio](https://github.com/square/okio) —
  HTTP client for online tile layers
- [Protocol Buffers](https://github.com/protocolbuffers/protobuf) (protobuf-java 3.x) —
  MVT/vector tile decoding
- [Mapbox Vector Tile — Java](https://github.com/wdtinc/mapbox-vector-tile-java) —
  MVT tile parsing
- [simplify-java](https://github.com/hgoebl/simplify-java) — Douglas-Peucker polyline
  simplification for path layers

### Dev dependencies

- [react-native-builder-bob](https://github.com/callstack/react-native-builder-bob) —
  build tooling (codegen, module bundling, type generation)
- [keep-a-changelog](https://www.npmjs.com/package/keep-a-changelog) — CHANGELOG.md maintenance

### Features intentionally dropped

The New Architecture rewrite (see `MIGRATION.md`) intentionally removed:
- `LayerPathSlopeGradient` — GPX-file loading and Savitzky–Golay smoothing (the dependencies
  `android-gpx-parser` and `savitzky-golay` were removed)
- `queue-promise` — replaced by native-side batching (`MarkerBatchQueue`)
- `Lodash` — replaced by `lodash-es` (tree-shakeable ESM variant)
