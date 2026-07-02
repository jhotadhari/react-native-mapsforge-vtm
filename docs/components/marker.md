# Marker

An individual map marker rendered as a native drawable. Must be a child of
`LayerMarker`.

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `position` | `Position` (`[lng, lat, alt?]`) | — | Marker position |
| `symbol` | `SymbolParams` | — | Marker appearance |
| `onPress` | `(e: MarkerEvent) => void` | — | Tap on this marker |
| `onLongPress` | `(e: MarkerEvent) => void` | — | Long press on this marker |
| `onDoubleTap` | `(e: MarkerEvent) => void` | — | Double-tap on this marker |
| `onCreate` | `(response: MarkerResponse) => void` | — | Fires after the marker is created |
| `onRemove` | `(response: ResponseBase) => void` | — | Fires after the marker is removed |
| `onChange` | `(response: MarkerResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## SymbolParams

```tsx
type SymbolParams = {
  type?: 'circle' | 'square' | 'diamond' | 'triangle' | 'star';
  size?: number;            // radius in dp
  fillColor?: string;       // hex color, e.g. '#FF0000'
  fillAlpha?: number;       // 0–1
  strokeColor?: string;
  strokeWidth?: number;
  text?: string;            // label text
  textColor?: string;
  textSize?: number;
  hotspot?: 'center' | 'bottom' | 'top' | 'left' | 'right';
};
```

## MarkerResponse

```tsx
type MarkerResponse = {
  uuid: string;
  position: Position;
};
```

## MarkerEvent

Gesture events include the marker's position:

```tsx
type MarkerEvent = NativeSyntheticEvent<{
  uuid: string;
  position: Position;
  x: number;    // screen x
  y: number;    // screen y
}>;
```

## Batch creation

Multiple `Marker` mounts within the same render pass are coalesced into a
single native bridge call via `MarkerBatchQueue`. This avoids N bridge calls
= N round trips when rendering many markers.

## Example

```tsx
<LayerMarker>
  <Marker
    position={[13.405, 52.52]}
    symbol={{
      type: 'circle',
      size: 10,
      fillColor: '#FF0000',
      fillAlpha: 0.8,
      hotspot: 'center',
    }}
    onPress={(e) => {
      const { lng, lat } = e.nativeEvent.position;
      console.log(`Tapped marker at ${lng}, ${lat}`);
    }}
  />
</LayerMarker>
```

## See also

- **[LayerMarker](./layer-marker.md)** — Marker container with group defaults
- **[CanvasAdapterModule](../api/canvas-adapter-module.md)** — Global symbol scaling
