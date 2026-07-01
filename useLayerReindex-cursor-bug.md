# `useLayerReindex` — cursor reset broken for non-first siblings

## Summary

`useLayerReindex` unconditionally sets `registry.cursor = undefined`, which
anchors the first child of the reindexing component at **position 0** in the
native layer stack. This is only correct when the component is the **first**
map-layer-rendering sibling inside `<MapContainer>`. For any later sibling, the
cursor should anchor right after the **last child of the previous sibling** —
otherwise layers get buried underneath earlier siblings.

## Component hierarchy (Straymap `AppView.tsx`)

```
<MapContainer>
  <BaseMap />              ← 1st sibling: renders LayerMapsforge, LayerBitmapTile, LayerHillshading
  <LayerScalebar />        ← 2nd sibling: renders LayerScalebar
  <LinesMapView />         ← 3rd sibling: renders LayerPath for selected lines
  <RoutingMapView />       ← 4th sibling: renders LayerPath (segments) + Marker (waypoints)
</MapContainer>
```

The expected z-ordering (bottom → top): BaseMap layers → LayerScalebar →
LinesMapView layers → RoutingMapView layers.

## How `useLayerReindex` works

From `src/compose/useLayerReindex.ts`:

```ts
const useLayerReindex = () => {
    const { registry } = useContext(MapHandleContext);
    registry.generation++;
    registry.cursor = undefined;  // ← the problem
};
```

It bumps `registry.generation` (so `useLayerOrder`'s repositioning branch
executes) and resets `registry.cursor` to `undefined` (so the first child's
`expectedIndex` computes to 0).

From `src/compose/useLayerOrder.ts:88-91`:

```ts
const expectedIndex =
    previousId !== undefined
        ? registry.order.indexOf(previousId) + 1
        : 0;  // ← hits this when cursor was reset
```

## The bug: step-by-step

### Initial full render (MapContainer renders — everything mounts)

| Step | What happens |
|------|-------------|
| 1 | `MapContainer` renders: bumps `gen=1`, resets `cursor=undefined` |
| 2 | `BaseMap` renders → `useLayerReindex()` → `gen=2`, `cursor=undefined` |
| 3 | BaseMap's children call `useLayerOrder`: `generationChanged=true`, `previousId=undefined` → `expectedIndex=0`. They get positions **0, 1, 2…**. ✓ Correct — BaseMap is the first sibling. |
| 4 | `LayerScalebar` calls `useLayerOrder`: `previousId` = last BaseMap child → gets next position. ✓ |
| 5 | `LinesMapView` children call `useLayerOrder`: chain from cursor, get next positions. ✓ |
| 6 | **`RoutingMapView` renders → `useLayerReindex()` → `gen=3`, `cursor=undefined`** |
| 7 | **RoutingMapView's children call `useLayerOrder`: `generationChanged=true`, `previousId=undefined` → `expectedIndex=0`. They get positions starting from 0.** |

At step 7, RoutingMapView's children reposition to positions 0, 1, 2… —
colliding with the BaseMap layers already at those positions. The
`registry.order` array ends up with routing layers at the front. The debounced
`scheduleSync` sends a native `reorderLayers` call → **routing layers render
underneath the basemap**. 🐛

### Adding a routing point (only RoutingMapView re-renders via Redux)

| Step | What happens |
|------|-------------|
| 1 | `MapContainer` does **not** re-render |
| 2 | `BaseMap` does **not** re-render |
| 3 | `LinesMapView` does **not** re-render |
| 4 | **`RoutingMapView` re-renders → `useLayerReindex()` → `gen++`, `cursor=undefined`** |
| 5 | **RoutingMapView's children call `useLayerOrder`: `generationChanged=true`, `previousId=undefined` → `expectedIndex=0`. All reposition to 0, 1, 2…** |

BaseMap's and LinesMapView's layers don't re-render, so they keep their
existing positions. But `registry.order` now has routing layers at the
front → native `reorderLayers` pushes them to the bottom. 🐛

### Why toggling a basemap temporarily "fixes" it

When the user toggles a BaseMap layer visibility:

1. `BaseMap` re-renders → `useLayerReindex()` → `gen++`, `cursor=undefined`
2. BaseMap's children reposition to 0, 1, 2… (correct — first sibling)
3. RoutingMapView's children do **not** re-render → they keep their current
   positions (which now happen to be after BaseMap's new positions)
4. Order looks correct — until the next routing point is added and step 4
   above repeats

## Why `useLayerReindex` is still needed

It's not safe to simply remove the call. `RoutingMapView`'s Markers use stable
keys (`point.id`), so when the point order changes (user reorders waypoints),
React reuses the same component instances in a different document order.
Without the generation bump from `useLayerReindex`, `useLayerOrder`'s
repositioning branch never executes (`generationChanged=false`), and the
Markers stay in their old z-order.

`BaseMap` has the same need: `layersReverse.map(...)` with stable keys
(`layer.key`) — reordering the layers array changes document order without
changing component identity. `useLayerReindex` is required here. It works for
BaseMap only because BaseMap is the first sibling (position 0 is correct).

## What the fix should look like

`useLayerReindex` needs to anchor the first child at the position of the
**last sibling that rendered before this component**, not at position 0.

Proposed approach — capture a "block base" cursor during full MapContainer
render passes and reuse it during partial re-renders:

1. `MapContainer` bumps a `registry.fullPassId` counter at the start of every
   one of its own renders (separate from `registry.generation`, which
   `useLayerReindex` also bumps).

2. `useLayerReindex` tracks `fullPassId`:

```ts
const useLayerReindex = () => {
    const { registry } = useContext(MapHandleContext);
    const blockBaseRef = useRef<symbol | undefined>(undefined);
    const lastFullPassIdRef = useRef<number>(registry.fullPassId);

    // Full MapContainer render pass: save the current cursor as this
    // component's block base (the cursor was set by the sibling that
    // rendered just before us in document order).
    if (lastFullPassIdRef.current !== registry.fullPassId) {
        blockBaseRef.current = registry.cursor;
        lastFullPassIdRef.current = registry.fullPassId;
    }

    registry.generation++;
    // Reset cursor to the saved block base, not undefined.
    // undefined → anchors at position 0 (only correct for 1st sibling).
    // blockBase → anchors right after the previous sibling (correct for all).
    registry.cursor = blockBaseRef.current;
};
```

This way:
- For `BaseMap` (1st sibling): `blockBaseRef` = `undefined` → `cursor=undefined` → first child anchors at 0. Same behavior as today. ✓
- For `RoutingMapView` (4th sibling): `blockBaseRef` = last LinesMapView child's id → `cursor=<that id>` → first child computes `expectedIndex = indexOf(thatId) + 1`. Anchors right after LinesMapView. ✓
- During partial re-renders: `fullPassId` hasn't changed → `blockBaseRef` preserved from full pass → same correct anchor reused. ✓
