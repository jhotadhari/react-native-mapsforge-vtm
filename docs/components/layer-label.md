# LayerLabel

A text label rendered **into the map itself**: the text + bubble are
rasterized into a bitmap (once per text change) and drawn by a native
`BitmapRenderer` at a screen offset in the same frame as the rest of the
map. Because it's rendered by the map, it stays pixel-perfectly aligned with
map-rendered geometry — no bridge crossings, no overlay lag.

The label is horizontal (axis-aligned), matching typical cartography labels.

## Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `text` | `string` | — | Label text; `\n` renders multiple lines |
| `position` | `ScreenPointSugar` | `['50%','50%']` | Screen anchor the label is positioned relative to (its center): `['50%','50%']`, `['100dp','24dp']`, or `{ x, y, unit }` |
| `followPosition` | `Position` (`[lng, lat]`) | — | Geographic point the label follows — see below |
| `gapDp` | `number` | 52 | Distance (dp) from `position` along the follow direction |
| `fallbackOffsetDp` | `number` | 48 | Offset (dp) beyond `position`, away from the follow point, when the line is too short |
| `paint` | `LabelPaint` | defaults | Text/bubble styling |
| `onCreate` | `(response: LabelResponse) => void` | — | Fires after the native label is created |
| `onRemove` | `(response: LabelResponse) => void` | — | Fires after the native label is removed |
| `onChange` | `(response: LabelResponse) => void` | — | Fires on prop changes |
| `onError` | `(err: ErrorBase) => void` | — | Fires on native errors |

## LabelPaint

```tsx
type LabelPaint = {
  fontSize?: number;          // dp
  textColor?: string;         // '#000000'
  backgroundColor?: string;   // '#ffffff'
  backgroundAlpha?: number;   // 0–1
  borderRadius?: number;      // dp
  paddingHorizontal?: number; // dp
  paddingVertical?: number;   // dp
  fontFamily?: FontFamily;    // 'DEFAULT' | 'SANS_SERIF' | ...
  fontStyle?: FontStyle;      // 'NORMAL' | 'BOLD' | 'ITALIC' | ...
};
```

## Follow position

With `followPosition` set, the Java side re-positions the label on **every
map movement** so it stays on the line between the screen `position` and
that geographic point:

1. follow point closer than ~5dp to the anchor → label hidden
2. line long enough to hold the label + `gapDp` gap → label centered
   **inside** the line at `gapDp` from the anchor
3. otherwise → label placed **beyond** the anchor, away from the follow
   point, at `fallbackOffsetDp`

Without `followPosition`, the label stays at the screen `position`.

## Example

```tsx
import { MapContainer, LayerLabel } from 'react-native-mapsforge-vtm';

<MapContainer center={[13.41, 52.53]} zoomLevel={13}>
  <LayerLabel
    text={'1.2 km\nNE 45°'}
    position={['50%', '50%']}
    followPosition={[13.405, 52.52]}
    gapDp={52}
    paint={{ fontSize: 12, backgroundColor: '#ffffff' }}
  />
</MapContainer>
```

## Notes

- Taps on the label are reported through the map's hit aggregation
  (`onTap`/`onLongPress` `hits`) with `manager: 'labels'` — no dedicated
  gesture event.
- The label bitmap is re-rasterized only when `text` or `paint` change;
  position updates are pure renderer-offset writes per frame.
- Screen-relative positions use the map view's own viewport (top-left
  origin).
