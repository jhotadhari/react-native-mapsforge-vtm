# getDebugLayerDump()

Imperative debug method on `useMap()` that returns a complete JSON dump of every
layer on the map — combining native ground truth (actual vtm `Layer` objects, their
Java class names, z-indices, enabled state) with the JS-side component registry
(React render order, fragment assignments, generation counter).

Unlike [`useLayerDebugInfo`](./use-layer-debug-info.md) (live, render-phase
snapshot) and [`LayerDebugTree`](./layer-debug-tree.md) (visual overlay), this is
a one-shot async call — press a button, get the full picture in the console.

```tsx
import { useMap } from 'react-native-mapsforge-vtm';
```

## Usage

```tsx
const MyDumpButton = () => {
  const { getDebugLayerDump } = useMap();

  const handlePress = () => {
    getDebugLayerDump()
      .then((dump) => console.log(JSON.stringify(dump, null, 2)))
      .catch((err) => console.error('Dump failed:', err));
  };

  return <Button title="Dump layers" onPress={handlePress} />;
};
```

The component **must** be rendered as a child of `<MapContainer>` (not a sibling)
so that `useMap()` has access to the live `MapHandleContext`.

Position in the JSX tree **does not matter** — it can be the first child, the
last, or anywhere in between. Unlike `<LayerDebugTree>` (which renders visual
layer overlays and should be last so it paints on top), `getDebugLayerDump` is
purely a data-fetching call — it doesn't add any layers to the map. The button
itself uses `position: absolute`, so its visual placement is independent of JSX
order.

```tsx
<MapContainer center={[-77, -9]} zoomLevel={8}>
  <LayerBitmapTile />
  {/* ...your layers... */}

  {/* Must be inside MapContainer to reach the real context */}
  <MyDumpButton />
</MapContainer>
```

## Return type

```tsx
type DebugLayerDump = {
  // ── Native ground truth ──────────────────────────────────────────

  nativeNodeHandle: number;     // Android view tag
  totalLayers: number;          // all layers on the map (JS + vtm-internal)
  jsManagedCount: number;       // subset that are JS-managed
  pendingMutations: number;     // mutations waiting to flush (non-zero = in flux)

  layers: Array<{
    zIndex: number;             // position in mapView.map().layers()
    className: string;          // e.g. "org.oscim.layers.tile.vector.VectorLayer"
    simpleName: string;         // e.g. "VectorLayer"
    uuid: string | null;        // JS-managed layer uuid, null for vtm-internal
    isJsManaged: boolean;
    enabled: boolean;
  }>;

  // ── JS-side component registry ───────────────────────────────────

  registry: {
    orderLength: number;        // mounted React components in doc order
    resolvedCount: number;      // how many have resolved their native uuid
    generation: number;         // MapContainer render generation counter
    sharedLayerActive: boolean; // true when <SharedLayer> is present
    fragmentIndices: Record<string, number>;  // per-type fragment counter

    layers: Array<{
      index: number;            // position in React render order
      layerType: string | null; // e.g. "path", "marker", "mapsforge"
      uuid: string | null;      // resolved native uuid (null = still pending)
      fragmentUuid: string | null; // shared-layer fragment uuid
    }>;

    /** Components grouped by their shared fragment UUID.
     *  When <SharedLayer> is active: few entries with memberCount > 1.
     *  Without <SharedLayer>: many entries with memberCount === 1. */
    fragmentSummary: Array<{
      fragmentUuid: string;
      layerType: string;
      memberCount: number;      // > 1 = components sharing a native layer
      memberIndices: number[];  // which registry.layer indices belong
    }>;
  };
};
```

## Interpreting the dump

### Registry vs native — spot discrepancies

| Registry says | Native says | Meaning |
|---|---|---|
| layer has `uuid` | missing from `layers[]` | Creation failed silently, or removal hasn't flushed yet |
| no entry for `uuid` | `isJsManaged: true` in `layers[]` | Leak — a JS-managed layer wasn't cleaned up |
| `pendingMutations > 0` | — | The native snapshot may be stale; mutations from recent mounts/unmounts haven't been applied yet |

### Fragment summary — verify SharedLayer

With `<SharedLayer>` active (e.g. 50 paths + 50 markers):

```json
"fragmentSummary": [
  { "fragmentUuid": "__vtm_shared_path__<scopeId>",  "memberCount": 50 },
  { "fragmentUuid": "__vtm_shared_marker__<scopeId>", "memberCount": 50 }
]
```

Without `<SharedLayer>` (same 50 pairs, interleaved):

```json
"fragmentSummary": [
  { "fragmentUuid": "__vtm_shared_path__1",   "memberCount": 1 },
  { "fragmentUuid": "__vtm_shared_marker__1", "memberCount": 1 },
  { "fragmentUuid": "__vtm_shared_path__2",   "memberCount": 1 },
  // ...one entry per type-run boundary
]
```

The `memberCount` tells you whether fragment sharing is actually working. The
`fragmentUuid` suffix distinguishes shared-scope IDs (`_<scopeId>`) from
incrementing per-type indices (`_1`, `_2`, …).

## See also

- **[useLayerDebugInfo](./use-layer-debug-info.md)** — Live, render-phase snapshot
  of the JS-side registry (synchronous, no bridge crossing)
- **[LayerDebugTree](./layer-debug-tree.md)** — Visual debug overlay using
  `useLayerDebugInfo` internally
- **[useMap()](../api/use-map.md)** — The hook that exposes `getDebugLayerDump`
  alongside all camera-control methods
- **[Layer Ordering](../advanced/layer-ordering.md)** — Understanding the z-order
  model and fragment system
