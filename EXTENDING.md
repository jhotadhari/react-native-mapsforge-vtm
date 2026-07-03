# Extending `react-native-mapsforge-vtm`

This library provides hooks and patterns for building custom layer-type extensions
that integrate with the map's render-order registry, lifecycle management, and
native rendering pipeline.

## Architecture of an extension

An extension is a separate npm package that depends on `react-native-mapsforge-vtm`
as a peer dependency. It uses the library's public extension hooks to create React
components that render custom content on the map (native layers, tile sources, or
reanimated overlays).

### Extension hooks (public API)

Three hooks form the extension API. Import them from the main package:

```tsx
import {
  MapHandleContext,
  useLayerOrder,
  useNativeLayerLifecycle,
} from 'react-native-mapsforge-vtm';
```

| Hook | Purpose |
|---|---|
| `MapHandleContext` | React context. Provides `nativeNodeHandle` (the map view's Android view ID) and `registry` (the `LayerOrderRegistry` that tracks every layer's position in the render tree). |
| `useLayerOrder(uuid, layerType?)` | Registers a component into the render-order registry. Returns `{ nativeNodeHandle, positionIndex, fragmentUuid }`. Call this once per layer component. |
| `useNativeLayerLifecycle({ enabled, create, remove, onError })` | State machine: `null → false → uuid`. Callers provide `create` (returns `Promise<uuid>`) and `remove` (returns `Promise<boolean>`) callbacks. The hook handles mount/unmount, re-creation on prop changes, and error reporting. |

### Creating a native layer extension

The `react-native-mapsforge-vtm-ext-grib` library is the reference implementation.
Follow this pattern:

```
extension-package/
  src/
    NativeModules/NativeMyLayer.ts    ← TurboModule spec (codegen)
    components/MyLayer.tsx            ← React component
    index.ts                          ← Public exports
  android/
    build.gradle
    src/main/java/.../
      MyLayerPackage.java             ← ReactPackage for autolinking
      modules/MyLayer.java            ← TurboModule implementation
      tiles/MyTileSource.java         ← Custom vtm TileSource or Layer
```

### Two rendering strategies

#### Strategy A: Custom TileSource (simpler, faster to ship)

Implement `org.oscim.tiling.ITileDataSource` and extend `org.oscim.tiling.TileSource`
directly. Feed into vtm's existing `BitmapTileLayer`. Best for data that can be
rasterized per tile (weather grids, heatmaps, radar data).

```java
public class MyTileSource extends TileSource {
    @Override
    public ITileDataSource getDataSource() {
        return new MyTileDataSource(this);
    }
}

public class MyTileDataSource implements ITileDataSource {
    @Override
    public void query(MapTile tile, ITileDataSink sink) {
        // 1. Convert tile.tileX/tileY/zoomLevel → lat/lon bounds
        // 2. Sample your data grid
        // 3. Render to android.graphics.Bitmap
        // 4. sink.setTileImage(bitmap)
        // 5. sink.completed(QueryResult.SUCCESS)
    }
}
```

Reference: `LayerHillshading.java` in this repo, `WeatherTileSource.java` in ext-grib.

#### Strategy B: Custom vtm Layer (more control, more complex)

Extend `org.oscim.layers.Layer` directly. Get the Mercator projection matrix from
vtm's rendering pipeline. Render to OpenGL in `update()`/`render()`. Best for
animated content (particles, real-time data) or when you need shader-level effects.

```java
public class MyLayer extends Layer {
    public MyLayer(Map map) {
        super(map);
    }

    @Override
    public void update() {
        // Called before each render frame
    }

    @Override
    public void render(GLViewport viewport) {
        // Direct OpenGL rendering
    }
}
```

### TurboModule pattern

Every extension that creates native layers needs a TurboModule. Follow this structure
(in `src/NativeModules/NativeMyLayer.ts`):

```typescript
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';

export interface ModuleParams {
  // Custom params for your layer
}

interface CreateLayerParams extends ModuleParams {
  nativeNodeHandle?: Int32;
  positionIndex: Int32;
}

interface RemoveLayerParams {
  nativeNodeHandle: Int32;
  uuid: string;
}

export interface Spec extends TurboModule {
  getConstants(): ModuleParams;
  createLayer(params: CreateLayerParams): Promise<string>;
  removeLayer(params: RemoveLayerParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('MyLayer');
```

**Important:** Types used in `Spec` must be declared inline — react-native-codegen's
TypeScript parser cannot follow cross-file imports.

The `package.json` must include:

```json
{
  "codegenConfig": {
    "name": "RNMyExtensionSpec",
    "type": "modules",
    "jsSrcsDir": "src",
    "android": {
      "javaPackageName": "com.example.myextension"
    },
    "includesGeneratedCode": true
  }
}
```

### Java ReactPackage

Every extension needs a `ReactPackage` for autolinking:

```java
public class MyExtensionPackage extends BaseReactPackage implements ReactPackage {
    @Override
    public NativeModule getModule(String name, ReactApplicationContext ctx) {
        if (MyLayer.NAME.equals(name)) return new MyLayer(ctx);
        return null;
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> Map.of(MyLayer.NAME, new ReactModuleInfo(
            MyLayer.NAME, MyLayer.NAME,
            false, false, false, true  // isTurboModule
        ));
    }
}
```

### Threading rules

- Your TurboModule's `createLayer`/`removeLayer` run on the **native modules thread**.
- Use the parent library's `LayerHelper.addLayerAsync()` to enqueue layer mutations —
  it serializes them through `MapMutationQueue.flush()` on the **UI thread**.
- Your `ITileDataSource.query()` runs on vtm's **tile worker thread** (background).
  Bitmap rendering is thread-safe if it only reads immutable data.
- Never call `mapView.map().layers().add/remove` or `mapView.map().updateMap()`
  directly — always go through `LayerHelper` / `MapMutationQueue`.

### Layer render ordering

Layer z-order follows React component tree order. Later siblings render on top.
`useLayerOrder` returns a `positionIndex` — pass this to your native `createLayer`
call so the layer lands at the correct position immediately:

```typescript
const { positionIndex } = useLayerOrder(uuid);
Module.createLayer({ nativeNodeHandle, positionIndex, ...otherParams });
```

### Reanimated overlays (no native code)

For simple markers that don't need native rendering, use `useMapOverlay` from the
`/reanimated` subpath export. Positions an `<Animated.View>` over a fixed geographic
coordinate at 60fps with zero bridge crossings:

```tsx
import { useMapPosition, useMapOverlay } from 'react-native-mapsforge-vtm/reanimated';
import Animated from 'react-native-reanimated';

const pos = useMapPosition();
const overlay = useMapOverlay({ lat: 51.5, lng: -0.12 }, pos);

<MapContainer onMapUpdate={pos.handleMapUpdate} responseInclude={pos.responseInclude}>
  <LayerMapsforge mapFile="..." />
</MapContainer>
<Animated.View style={[styles.marker, overlay.animatedStyle]} />
```

**Limitation (v1):** Bearing and tilt are not accounted for. Correct only when
north-up and untilted.

### Complete example

See [`react-native-mapsforge-vtm-ext-grib`](https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-grib)
for a working extension that uses Strategy A (custom TileSource) to render weather
GRIB overlays.

---

## Potential architectural improvements

These are ideas for making the library more extensible. Not implemented — open for
discussion.

### 1. Dedicated `createCustomLayer` API

Currently extensions must write their own TurboModule from scratch, following the
codegen pattern. A generic `createCustomLayer` TurboModule could allow extensions
to register custom vtm `Layer` or `TileSource` implementations without writing
boilerplate Java/JS bridge code.

```typescript
// Hypothetical API
const uuid = await MapContainer.createCustomLayer({
  type: 'tileSource',
  implementation: 'com.example.WeatherTileSource',
  params: { dataUrl: '...', colorMap: 'wind' },
});
```

### 2. Pre-built extension base classes

Provide abstract Java classes that extensions extend, reducing the amount of
boilerplate needed:

```java
// Hypothetical
public abstract class BaseTileSourceExtension extends TileSource {
    protected abstract Bitmap renderTile(MapTile tile);
    // Handles ITileDataSource, caching, disposal automatically
}
```

### 3. `useLayerOrder` without native layer

Allow registering a React component's position in the render tree even when it
doesn't create a native layer — e.g., for reanimated overlays that need correct
z-ordering relative to native layers.

### 4. Public `MapMutationQueue` access for extensions

Currently extensions go through `LayerHelper` which is a parent-library class.
Making `MapMutationQueue` public (or providing an extension-facing façade) would
give extensions more control over batching and ordering.

### 5. Type-only extensions for reanimated overlays

A pure-JS extension pattern that doesn't require any native Java code.
Extensions would be npm packages that only export React components using
`useMapOverlay` + `useMapPosition`. No TurboModule, no Android build.

### 6. Extension registry / plugin system

A registry where extensions declare capabilities, and the map container queries
them. Enables features like "show a list of available overlays" or "toggle all
weather layers."

```typescript
// Hypothetical
import { registerExtension } from 'react-native-mapsforge-vtm';
registerExtension({
  name: 'ext-grib',
  version: '0.1.0',
  components: { WeatherOverlay, WeatherParticles },
  capabilities: ['weather-overlay'],
});
```
