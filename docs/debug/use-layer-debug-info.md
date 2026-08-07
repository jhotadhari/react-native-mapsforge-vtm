# useLayerDebugInfo()

Live layer-tree introspection hook. Returns a snapshot of all registered layers
and their positions in the native render order. Useful for debugging z-order
issues and verifying layer registration.

```tsx
import { useLayerDebugInfo } from 'react-native-mapsforge-vtm';
```

## Usage

```tsx
const MyDebugOverlay = () => {
  const debugInfo = useLayerDebugInfo();

  return (
    <View style={styles.overlay}>
      <Text>Layers: {debugInfo?.layers.length ?? 0}</Text>
      {debugInfo?.layers.map((layer) => (
        <Text key={layer.id}>
          [{layer.position}] {layer.type} — {layer.uuid}
        </Text>
      ))}
    </View>
  );
};
```

## Return type

```tsx
type LayerDebugInfo = {
  layers: LayerDebugEntry[];
  orderLength: number;
};

type LayerDebugEntry = {
  id: string;         // stable Symbol identifier
  type: string;       // layer type, e.g. 'LayerPath', 'LayerMapsforge'
  uuid: string;       // native UUID or fragment UUID
  position: number;   // current position in the render order
  fragmentIndex?: number;  // SharedLayer fragment index (if collapsed)
};
```

:::note
The hook subscribes to registry changes and re-renders on each tick.
Don't use it in production render paths — it's a debug tool.
:::

## See also

- **[LayerDebugTree](./layer-debug-tree.md)** — Visual debug overlay
- **[getDebugLayerDump()](./get-debug-layer-dump.md)** — One-shot JSON dump of native + JS layer state
- **[Layer Ordering](../advanced/layer-ordering.md)** — Understanding the z-order model
