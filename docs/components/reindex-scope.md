# ReindexScope

Wraps children in a scope that orders as one block. It renders a scope anchor
that permanently marks the block's tree position, and optionally carries an
`order` prop for explicit cross-scope priority.

## When to use it

- **Redux / Zustand layer lists** — The parent subscribes to a slice of state
  that changes the layer array order; the committed-tree walk picks up the new
  order on the next sync.
- **Memoized children** — `React.memo()` or `useMemo()` preventing individual
  re-renders is fine: the anchor marks the block position, and the walk reads
  the committed tree regardless of render memoization.
- **Programmatic reorder** — Layers sorted/rearranged imperatively; the anchor
  change re-triggers the walk and the plan is re-applied.
- **Async children** — Children mount later due to async data (React Query,
  storage restore, network). The scope anchor holds the block position, so
  late children land at the correct tree position.

## When NOT to use it

- **Static JSX** — If your layers are declared once in JSX and never reorder,
  the committed-tree walk already orders them correctly.
- **Conditional toggling** — Mounting/unmounting a layer triggers the
  create/remove lifecycle; the scene re-derives the plan on each change.
- **Adding or removing layers** — New layers land at their tree position via
  the scene plan, applied atomically with the native add.

## How it works

`ReindexScope` calls `useLayerAnchor({ kind: 'scope', scopeOrder: order })`,
which renders an invisible `VtmAnchorView` marking the block's tree position
and registers a scope descriptor with the scene. Descendant layer anchors carry
the scope uid via context so the plan can group them.

There is no sentinel, cursor, or generation machinery — the anchor IS the
placeholder. When children mount later (async data), the committed-tree walk
re-runs and the plan places the block at the anchor's position.

## Ordering: JSX tree vs `order` prop

| Mechanism | When it applies | How position is determined |
|-----------|----------------|---------------------------|
| **JSX tree (committed walk)** | Always (default) | The committed-tree walk yields React's depth-first order — later siblings later in the plan (higher z-index) |
| **`order` prop** | Always takes priority when set | Explicit numeric value — `order={100}` renders before `order={200}`, regardless of JSX position |

## Example

### Basic: layers reordering in a list

```tsx
import { ReindexScope, MapContainer, LayerPath } from 'react-native-mapsforge-vtm';

const LayerList = ({ paths }) => (
  <ReindexScope>
    {paths.map((path) => (
      <LayerPath
        key={path.id}
        coordinates={path.coordinates}
        paint={path.paint}
      />
    ))}
  </ReindexScope>
);

const App = () => {
  const paths = useSelector((state) => state.paths);

  return (
    <MapContainer center={[-77.6, -9.1]} zoomLevel={12}>
      <LayerList paths={paths} />
      <LayerBitmapTile
        url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
        zoomMax={18}
      />
    </MapContainer>
  );
};
```

### With `order` prop: explicit priority across scopes

```tsx
const App = () => {
  const { baseMapReady, lines, routes } = useAsyncData();

  return (
    <MapContainer center={[-77, -9]} zoomLevel={8}>
      <ReindexScope order={100}>
        {baseMapReady && <LayerBitmapTile url="..." />}
      </ReindexScope>

      <ReindexScope order={200}>
        {lines.map((l) => (
          <LayerPath key={l.id} coordinates={l.coords} />
        ))}
      </ReindexScope>

      <ReindexScope order={300}>
        {routes.map((r) => (
          <LayerPath key={r.id} coordinates={r.coords} />
        ))}
      </ReindexScope>

      <LayerScalebar />
    </MapContainer>
  );
};
```

**Always render the `<ReindexScope>` wrapper** — even when data hasn't loaded
yet. Put the conditional _inside_ the wrapper so the anchor always marks the
block position:

```tsx
// BAD: scope doesn't render → no anchor → block position unmarked on late data
if (!points) return null;
return <ReindexScope>...</ReindexScope>;

// GOOD: scope always renders → anchor holds the position
<ReindexScope order={200}>
  {points && <LayerPath ... />}
</ReindexScope>
```

## API

```tsx
type ReindexScopeProps = {
  children?: ReactNode;
  /**
   * Optional priority for ordering across sibling ReindexScope instances.
   * Lower values = earlier in the layer order = lower z-index on the map.
   *
   * When set, position is determined by comparing this value with other
   * scopes' `order` values, NOT by JSX tree position.
   */
  order?: number;
};
```

```tsx
<ReindexScope order={100}>{children}</ReindexScope>
```

- **Props**: `children?: ReactNode`, `order?: number`
- **Context required**: Must be inside `<MapContainer>` children (at any nesting depth)
- **Returns**: A wrapper providing `ReindexContext` (the scope uid) around the anchor + children

## Caveats

- **Always render the wrapper** — For correct positioning of async children,
  render `<ReindexScope>` in the initial pass (with `null` children if needed).
  If the wrapper itself mounts late, use the `order` prop.
- **`order` overrides JSX tree** — When `order` is set, the JSX position of the
  `<ReindexScope>` tag is irrelevant. `order={100}` always renders before
  `order={200}`.
- **Safe to nest** — Nested `ReindexScope`s each mark their own block position;
  the plan orders the outer block and the inner block independently.
- **Works with SharedLayer** — `ReindexScope` manages block position;
  `SharedLayer` manages fragment assignment. They are orthogonal. A layer can
  be in neither, either, or both.

## See also

- **[Layer Ordering](../advanced/layer-ordering.md)** — Full explanation of
  the z-order model, fragment infrastructure, and `SharedLayer`
- **[SharedLayer](../components/shared-layer.md)** — Collapse same-type
  children into shared native layer fragments
- **[MapContainer](../components/map-container.md)** — The root map view
- **[getDebugLayerDump()](../debug/get-debug-layer-dump.md)** — Debug the
  layer order and the scene plan
