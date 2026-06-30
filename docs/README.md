# react-native-mapsforge-vtm Documentation

Android offline vector map rendering for React Native, built on [mapsforge/vtm](https://github.com/mapsforge/vtm).

## Getting Started

- **[Installation](./getting-started/installation.md)** — Prerequisites, Android setup, New Architecture
- **[Quick Start](./getting-started/quick-start.md)** — Your first map in 5 minutes

## Components

- **[MapContainer](./components/map-container.md)** — The root map view (Fabric component)
- **[LayerMapsforge](./components/layer-mapsforge.md)** — Offline vector maps from `.map` files
- **[LayerBitmapTile](./components/layer-bitmap-tile.md)** — Online raster tiles (OSM, custom)
- **[LayerMBTilesBitmap](./components/layer-mbtiles-bitmap.md)** — Offline raster from MBTiles
- **[LayerHillshading](./components/layer-hillshading.md)** — Shaded relief from DEM (`.hgt`) data
- **[LayerPath](./components/layer-path.md)** — Shared-layer paths (bulk rendering, 50–1000+ paths)
- **[LayerPathJts](./components/layer-path-jts.md)** — Dedicated paths (JTS features, guaranteed z-order, 1–30 paths)
- **[LayerShape](./components/layer-shape.md)** — Geometric shapes (polygons, circles, rectangles, hexagons, points)
- **[LayerScalebar](./components/layer-scalebar.md)** — Map scale bar
- **[LayerMarker](./components/layer-marker.md)** — Marker container with group-level defaults
- **[Marker](./components/marker.md)** — Individual map marker
- **[SharedLayer](./components/shared-layer.md)** — Collapse children into shared native layer fragments

## Hooks

- **[useMap](./hooks/use-map.md)** — Imperative map control (pan, zoom, animate, fly, fitBounds)
- **[useRenderStyleOptions](./hooks/use-render-style-options.md)** — Read render-theme style menu
- **[useLayerReindex](./hooks/use-layer-reindex.md)** — Signal layer reorder to the native stack

## Advanced

- **[Layer Ordering](./advanced/layer-ordering.md)** — How z-order works, SharedLayer, `useLayerReindex`
- **[Performance](./advanced/performance.md)** — Batch insertion, shared layers, avoiding recreations
- **[Known Issues](./advanced/known-issues.md)** — Current bugs and workarounds

## Debug Tools

- **[useLayerDebugInfo](./debug/use-layer-debug-info.md)** — Live layer-tree introspection hook
- **[LayerDebugTree](./debug/layer-debug-tree.md)** — Visual debug overlay component

## API Reference

- **[CanvasAdapterModule](./api/canvas-adapter-module.md)** — Global text/line/symbol scale
- **[Types](./api/types.md)** — Shared TypeScript types

## Resources

- **[Example App](https://github.com/jhotadhari/react-native-mapsforge-vtm/tree/main/example)** — 19 runnable examples (`yarn example android`)
- **[Migration Guide (v0.7 → New Architecture)](../MIGRATION.md)** — Breaking changes from the old bridge-based API
- **[Changelog](../CHANGELOG.md)** — Version history
