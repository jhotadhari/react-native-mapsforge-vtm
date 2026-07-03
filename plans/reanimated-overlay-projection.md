# Plan: Reanimated Overlay Positioning & Worklet Coordinate Transforms

## Context

The library already ships `src/reanimated/useMapPosition.ts` — shared values for center, zoom,
bearing, tilt that worklets can read at 60fps on the UI thread. But there's no way to convert a
lat/lng to a screen position inside a worklet, so any React Native overlay (tooltip, callout,
floating label) must either use the JS-thread `onMapUpdate` callback + `setState` (25fps, causes
re-renders) or call `useMap().getPosition()` imperatively.

Two gaps prevent worklet-based overlay positioning:
1. Viewport dimensions are never emitted in map update events
2. No Mercator projection math exists in JS/TS

This plan fills both gaps.

---

## API surface

### `useMapPosition()` (extended, not replaced)

```ts
const {
  centerSv, zoomSv, bearingSv, tiltSv,            // existing
  viewportWidthSv, viewportHeightSv,               // NEW
  handleMapUpdate,
  responseInclude,                                 // NEW preset
} = useMapPosition();
```

`responseInclude` is `{ viewportWidth: 2, viewportHeight: 2 }` — consumer spreads it into
`<MapContainer responseInclude={responseInclude} />` to opt in to viewport dimension emission.
This is additive; consumers can spread their own overrides on top.

### `useMapOverlay(target, sharedValues)`

```ts
function useMapOverlay(
  target: { lat: number; lng: number },
  sharedValues: MapPositionSharedValues          // from useMapPosition()
): { animatedStyle: ViewStyle }
```

Returns a `useAnimatedStyle` result with `position: 'absolute'`, `left`, `top` for an
`<Animated.View>`. Hides (opacity 0) when center/viewport is not yet available. The shared
values let many overlay instances share one `useMapPosition()` call — zero extra cost.

Consumer usage:
```tsx
const pos = useMapPosition();

const londonOverlay = useMapOverlay({ lat: 51.5074, lng: -0.1278 }, pos);
const parisOverlay  = useMapOverlay({ lat: 48.8566,  lng: 2.3522  }, pos);

return (
  <View style={{ flex: 1 }}>
    <MapContainer onMapUpdate={pos.handleMapUpdate} responseInclude={pos.responseInclude}>
      <LayerMapsforge mapFile={...} />
    </MapContainer>
    <Animated.View style={[styles.pin, londonOverlay.animatedStyle]}>📍 London</Animated.View>
    <Animated.View style={[styles.pin, parisOverlay.animatedStyle]}>📍 Paris</Animated.View>
  </View>
);
```

### `src/reanimated/mercatorUtils.ts` (NEW)

Pure worklet-compatible functions — no hooks, callable from any worklet or JS thread.

```ts
function toScreenPosition(
  centerSv: SharedValue<[number, number] | null>,
  viewportWidthSv: SharedValue<number>,
  viewportHeightSv: SharedValue<number>,
  geoPoint: { lat: number; lng: number }
): { x: number; y: number } | null

function fromScreenPosition(
  centerSv: SharedValue<[number, number] | null>,
  viewportWidthSv: SharedValue<number>,
  viewportHeightSv: SharedValue<number>,
  screenPoint: { x: number; y: number }
): { lat: number; lng: number } | null
```

These take the shared values directly (not pre-bound) so they can be called from any worklet
context without needing a hook wrapper.

---

## Files to create

### `src/reanimated/mercatorUtils.ts`

Zoom-invariant Web Mercator math using normalized coordinates. Key insight: the `256 * 2^zoom`
scale factor cancels out when computing screen delta from center, so no zoom term appears in the
final formulas.

```ts
// Normalized Mercator (0..1 range), zoom-independent
latLngToMercator(lat, lng) → { mx, my }
mercatorToLatLng(mx, my) → { lat, lng }

// With antimeridian wrapping: long diff normalized to [-0.5, +0.5]
toScreenPosition(centerSv, vpWidthSv, vpHeightSv, geo):
  center = centerSv.value; if null → return null
  if vpWidth ≤ 0 or vpHeight ≤ 0 → return null
  centerMerc = latLngToMercator(center[1], center[0])
  pointMerc  = latLngToMercator(geo.lat, geo.lng)
  dMx = wrap(pointMerc.mx - centerMerc.mx)  // [-0.5, +0.5]
  dMy = pointMerc.my - centerMerc.my
  x = round(vpWidth  * (0.5 + dMx))
  y = round(vpHeight * (0.5 - dMy))
  return { x, y }

fromScreenPosition(centerSv, vpWidthSv, vpHeightSv, screen):
  // inverse of above — screen → normalized Mercator → lat/lng
```

### `src/reanimated/useMapOverlay.ts`

```ts
function useMapOverlay(
  target: { lat: number; lng: number },
  sharedValues: MapPositionSharedValues
): { animatedStyle: { position: 'absolute'; left: number; top: number } }
```

- Calls `useAnimatedStyle` with a worklet that invokes `toScreenPosition`
- Returns `{ display: 'none' }` when toScreenPosition returns null
- Round `left`/`top` to integers (pixel-aligned)

---

## Files to modify (JS/TS)

### `src/NativeViews/MapsforgeVtmViewNativeComponent.ts`

Add to `MapEventResponse`:
```ts
viewportWidth?: Double;
viewportHeight?: Double;
```
Add to `ResponseInclude`:
```ts
viewportWidth?: Int32;
viewportHeight?: Int32;
```
Must be inline (codegen constraint — no imports). Backward-compatible: existing consumers ignore
unknown fields.

### `src/reanimated/useMapPosition.ts`

- Add `viewportWidthSv`, `viewportHeightSv` shared values (initialized to `0`)
- In `handleMapUpdate`: write `e.viewportWidth`/`e.viewportHeight` when present
- Add to `MapPositionSharedValues` interface
- Expose `responseInclude: { viewportWidth: 2, viewportHeight: 2 }` via `useMemo`
- Update `useCallback`/`useMemo` dependency arrays

### `src/reanimated/index.ts`

```ts
export { useMapPosition } from './useMapPosition';
export type { MapPositionSharedValues } from './useMapPosition';
export { useMapOverlay } from './useMapOverlay';
export { toScreenPosition, fromScreenPosition } from './mercatorUtils';
```

---

## Files to modify (Native Java)

### `MapFragment.java` — `getResponseBase()`

Add after the existing tilt/center blocks (following the same pattern):
```java
if (responseInclude.getInt("viewportWidth") >= includeLevel) {
    MapsforgeVtmView parent = getMapsforgeVtmView();
    if (parent != null) {
        payload.putDouble("viewportWidth", parent.getWidthInDp());
    }
}
if (responseInclude.getInt("viewportHeight") >= includeLevel) {
    MapsforgeVtmView parent = getMapsforgeVtmView();
    if (parent != null) {
        payload.putDouble("viewportHeight", parent.getHeightInDp());
    }
}
```

### `MapsforgeVtmView.java`

The `getDimension("width", "px")` method converts to pixels. We need dp values directly.
Simplest approach: add a `getWidthInDp()` / `getHeightInDp()` that reads the underlying
`width` / `height` fields (which are already stored in dp, set via view manager props).

```java
public double getWidthInDp() { return width; }
public double getHeightInDp() { return height; }
```

The stored `width`/`height` fields are in dp because `setWidth`/`setHeight` receive the
dp values from the JS side and store them raw.

### `MapContainer.java` — `getTypedExportedConstants()`

The default `responseInclude` constant already exists. Add the two new keys with value `0`
(not emitted by default; consumers must opt in via `useMapPosition`'s `responseInclude` preset):
```java
responseInclude.putInt("viewportWidth", 0);
responseInclude.putInt("viewportHeight", 0);
```

These are nested keys inside an existing `WritableMap`, not top-level constants — no generated
code (NativeMapContainerSpec.java) changes needed.

---

## Web Mercator projection math (zoom-invariant)

Mapsforge vtm uses standard Web Mercator (EPSG:3857). The `256 * 2^zoom` tile-scale factor
cancels when computing screen delta from map center, so the worklet formulas are zoom-independent:

```
mercY(lat) = ln(tan(π/4 + lat_rad/2))

// Normalized Mercator (0–1):
mx(lng) = (lng + 180) / 360
my(lat) = 0.5 - mercY(lat) / (2π)

// Screen position of geo point:
dMx = wrap(mx(geoLng) - mx(centerLng))   // antimeridian wrap to [-0.5, +0.5]
dMy = my(geoLat) - my(centerLat)
screenX = viewportWidth  * (0.5 + dMx)
screenY = viewportHeight * (0.5 - dMy)

// Inverse (screen → geo):
dMx = screenX / viewportWidth  - 0.5
dMy = 0.5 - screenY / viewportHeight
lng  = (mx(centerLng) + dMx) * 360 - 180
mercY_geo = mercY(centerLat) + dMy * 2π
lat  = 2 * atan(exp(mercY_geo)) - π/2
```

## Limitation: no bearing/tilt in v1

Mapsforge's `viewport().toScreenPoint()` accounts for bearing and tilt via a rotation matrix and
perspective transform. Implementing this in pure worklet math is significantly more complex.

For v1:
- `toScreenPosition` works correctly only when bearing ≈ 0 and tilt ≈ 0
- Document this in TSDoc on every exported function
- The shared values already track bearing/tilt — a future v2 can add the correction

The 90% use case (tooltip tracking a coordinate on an untilted, north-up map) works perfectly.

## Edge cases

| Edge case | Behavior |
|---|---|
| `centerSv.value` is null (no position yet) | Return null → overlay hides |
| Viewport dimensions are 0 | Return null → overlay hides |
| `lat` out of Web Mercator range (>85.051°) | Clamp to ±85.051° |
| Coordinate is far off-screen | Return the computed position (may be outside viewport; consumer can clip) |
| Antimeridian crossing | Longitude diff wrapped to [-0.5, +0.5] |
| Orientation change (width/height swap) | Next `onMapUpdate` carries new dimensions → auto-update |
| `useMapOverlay` without `onMapUpdate` prop | Shared values stay at 0 → overlay stays hidden |

## Implementation phases

### Phase 1: Native plumbing
1. `MapsforgeVtmViewNativeComponent.ts` — add fields to both interfaces
2. `MapsforgeVtmView.java` — add `getWidthInDp()` / `getHeightInDp()`
3. `MapFragment.java` — emit viewport dims in `getResponseBase()`
4. `MapContainer.java` — add defaults to `responseInclude` constant

### Phase 2: Mercator utilities
5. Create `src/reanimated/mercatorUtils.ts` — pure worklet functions

### Phase 3: Hook extensions
6. Modify `src/reanimated/useMapPosition.ts` — add shared values + responseInclude preset
7. Create `src/reanimated/useMapOverlay.ts`

### Phase 4: Exports
8. Update `src/reanimated/index.ts`

## Verification

1. `yarn typecheck` must pass
2. `yarn lint` must pass
3. `yarn prepare` must produce correct output in `lib/`
4. Example code: create a minimal standalone snippet that imports `useMapPosition` and
   `useMapOverlay`, passes `responseInclude` to MapContainer, and renders an `<Animated.View>`
   at a fixed lat/lng. Verify by inspection that the overlay's `left`/`top` values change
   correctly when the shared values are written to.
5. Device testing: user will handle manually (build, install, pan/zoom to confirm smooth tracking)
