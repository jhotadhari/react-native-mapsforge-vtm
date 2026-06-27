# BUG: "Unable to find markerLayer" on Marker unmount

## Symptom

```
ERROR Unable to find markerLayer
```

Logged when a `<Marker>` unmounts, e.g. after its parent React tree is removed or re-rendered to `null`.

## Where it fires

`android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/modules/LayerMarker.java` — four methods look up the marker layer by `markerLayerUuid` in `LayerHelper.layersByHandle` and reject the promise when the lookup returns `null`:

| Line | Method |
|------|--------|
| 351 | `createMarker()` |
| 414 | `removeMarker()` |
| 447 | `updateLayer()` |
| 494 | `updateMarker()` |

## Root cause: removeMarker / removeLayer ordering race

When a tree containing `<LayerMarker><Marker/></LayerMarker>` unmounts, both components fire their cleanup effects:

1. **Marker cleanup** queues `removeMarker({ markerLayerUuid: "X", ... })`
2. **LayerMarker cleanup** queues `removeLayer({ uuid: "X", ... })`

React unmounts children before parents, so the Marker effect cleanup fires before the LayerMarker effect cleanup. However, **both native calls are async `@ReactMethod` promises dispatched across the bridge / JNI**. There is no guarantee that `removeMarker` is fully processed on the native side before `removeLayer` executes.

If `removeLayer` reaches `LayerHelper.removeLayer()` first (line 65–98 of `LayerHelper.java`), it removes the `ItemizedLayer` from `layersByHandle`. When `removeMarker` subsequently looks up the same `markerLayerUuid` at line 412–413 of `LayerMarker.java`, the `.get()` returns `null` and the error fires.

The same race also affects `updateMarker` and `updateLayer` calls queued by effect re-runs (symbol/position changes) that land after the component has already unmounted.

## Trigger in straymap

`src/store/features/routing/components/RoutingMapView.tsx` renders `<LayerMarker>` wrappers around per-point `<Marker>` components. When the route is cleared or recalculated (e.g. `points` becomes empty or changes), the entire marker tree unmounts and re-mounts, hitting the race reliably.

```tsx
// Simplified — each unmount/remount cycle can trigger the race
<LayerMarker>
  {points.map((point, index) => (
    <Marker key={point.id} position={...} symbol={...} />
  ))}
</LayerMarker>
```

## Suggested fix direction

**Option A (JS-side): Sequence removeMarker before removeLayer**  
In `Marker.tsx`, when `markerLayerUuid` is available, call `removeMarker` synchronously (or await it) before allowing the LayerMarker to proceed with `removeLayer`. This is fragile across component boundaries.

**Option B (JS-side): Suppress the error in removeMarker when the layer is already gone**  
In `Marker.tsx`'s `remove` callback, catch the "Unable to find markerLayer" rejection and treat it as success (no-op) rather than logging it as an error. The marker is already gone because its parent layer was removed — that's the desired end state.

**Option C (Native-side): Make `removeMarker` idempotent**  
In `LayerMarker.java:removeMarker()`, when the `markerLayerUuid` lookup returns `null`, resolve the promise successfully instead of rejecting — the marker's parent layer is already gone, so removal is a no-op.

**Option D (Native-side): Bulk-cleanup on layer removal**  
When `LayerMarker.removeLayer()` is called, also remove all markers that belonged to that layer, and set a flag so pending marker operations short-circuit instead of looking up the now-deleted layer.

Option C is the simplest, least invasive fix and follows the robustness principle (be tolerant in what you accept).
