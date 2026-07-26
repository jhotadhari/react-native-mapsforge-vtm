# Shared TypeScript Types

Types shared across the library. Most layer-specific types are defined in their
respective `NativeXxx.ts` codegen spec files and re-exported from
`src/index.tsx`.

## Core types

### `Position`

The GeoJSON `Position` tuple — used everywhere for geographic coordinates:

```tsx
type Position = [lng: number, lat: number, alt?: number];
```

This library uses GeoJSON `Position` tuples rather than named-property
objects. All props and return values use `Position`.

### `Bbox`

A GeoJSON bounding box:

```tsx
type Bbox = [west: number, south: number, east: north: number];
```

### `ResponseBase`

Every layer's `onCreate`/`onChange`/`onRemove` callback receives at least:

```tsx
type ResponseBase = {
  uuid: string;    // native layer/drawable identifier
};
```

### `ErrorBase`

Every layer's `onError` callback receives:

```tsx
type ErrorBase = {
  errorMsg: string;
};
```

## Layer-specific types

Each layer's props, response, and gesture types are defined in the layer's
codegen spec file and re-exported as a namespace from `src/index.tsx`:

| Type namespace | Source file |
|---|---|
| `LayerMapsforgeProps` / `LayerMapsforgeResponse` | `NativeLayerMapsforge.ts` |
| `LayerPathProps` / `LayerPathResponse` / `PathPaint` / `PathTriggerEvent` | `NativeLayerPath.ts` |
| `LayerPathJtsProps` / `LayerPathJtsResponse` / `PathJtsPaint` | `NativeLayerPathJts.ts` |
| `LayerShapeProps` / `ShapeDefinition` / `ShapePaint` | `NativeLayerShape.ts` |
| `LayerMarkerProps` / `MarkerProps` / `MarkerPaint` / `MarkerEvent` | `NativeLayerMarker.ts` |
| `LayerBitmapTileProps` | `NativeLayerBitmapTile.ts` |
| `LayerMBTilesBitmapProps` | `NativeLayerMBTilesBitmap.ts` |
| `LayerHillshadingProps` / `ShadingAlgorithm` | `NativeLayerHillshading.ts` |
| `LayerScalebarProps` | `NativeLayerScalebar.ts` |

Import them directly:

```tsx
import type {
  Position,
  Bbox,
  PathPaint,
  MarkerPaint,
  ShapeDefinition,
} from 'react-native-mapsforge-vtm';
```

All types are also accessible via `import type { ... } from 'react-native-mapsforge-vtm'`.

## map-control types (useMap)

```tsx
import type {
  EasingType,
  MapPositionTarget,
  AnimationOptions,
  FitBoundsOptions,
  GetPositionResponse,
} from 'react-native-mapsforge-vtm';
```

## Event types

Event types for `MapContainer`'s `onMapUpdate` / `onTap` / `onLongPress` /
`onPause` / `onResume`:

```tsx
import type {
  MapEventResponse,
  TapEventResponse,
  LongPressEventResponse,
  ResponseInclude,
} from 'react-native-mapsforge-vtm';
```

## Important: codegen types must be inline

TypeScript types in `NativeXxx.ts` codegen spec files **must** be defined
inline — not imported from other files. React Native's codegen parser doesn't
resolve cross-file imports. This is why some type shapes appear duplicated
across spec files.

## See also

- Each component's doc page for its specific types
- [CLAUDE.md: codegen spec types must be inline](../../CLAUDE.md) — project rule about type duplication
