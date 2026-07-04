# Extending `react-native-mapsforge-vtm`

This library provides hooks and patterns for building custom layer-type extensions
that integrate with the map's render-order registry, lifecycle management, and
native rendering pipeline.

## Quick start

Run the interactive planning command to determine which pattern fits your idea:

```
/ext-plan
```

It walks through 7 questions (rendering target, native changes needed, vtm classes
to shadow, core hooks required, data flow, bridge payload, naming) and outputs a
plan file plus scaffolded repo. See `.claude/skills/ext-plan.md` for the full
decision tree.

## Architecture of an extension

An extension is a separate npm package that depends on `react-native-mapsforge-vtm`
as a peer dependency. It uses the library's public extension hooks to create React
components that render custom content on the map.

### Three patterns (pick one)

| | Pattern A: JS-only | Pattern B: TurboModule | Pattern C: vtm-shadowing |
|---|---|---|---|
| Native code? | No | Yes (TurboModule) | Yes (TurboModule + vtm patches) |
| vtm classes shadowed? | No | No | Yes (LineBucket, RenderBuckets, etc.) |
| Custom GLSL shaders? | No | No | Yes |
| Complexity | Low | Medium | High |
| Example | `useSlopeColoring` hook | Custom tile source (ext-grib) | Color-ramp paths (ext-path-color-ramp) |
| Best for | Calculations, color mapping, data transforms | New layer types using existing vtm rendering | New GPU rendering effects |

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

### Core library extensibility hooks (Java)

For extensions that need to customize layer rendering (Pattern B and C), the core
library exposes these hooks on the Java side:

| Hook | Location | Purpose |
|---|---|---|
| `createPathLayerManager(nativeNodeHandle, mapView)` | `LayerPath.java` | Factory method — override to return a custom `PathLayerManager` subclass |
| `drawSegments(...)` | `PathLayerManager.java` | `protected` — override to customize how `LineDrawable` objects are styled per segment |
| `getStyleBuilder(styleMap)` | `PathLayerManager.java` | `protected` — override to customize `Style.Builder` creation |

Extension TurboModules override `createPathLayerManager()` to inject their custom
manager without duplicating the full `createLayer`/`removeLayer`/`updateCoordinates`
machinery:

```java
// In your extension's TurboModule (extends LayerPath):
@Override
protected PathLayerManager createPathLayerManager(int nativeNodeHandle, MapView mapView) {
    return ColorRampPathLayerManager.get(nativeNodeHandle, mapView);
}
```

## Pattern A: JS-only extension

No native code. Hooks, utilities, calculations on the JS side. Components use
existing `<LayerPath>`, `<LayerMarker>`, etc. from the core library.

### File structure

```
ext-<name>/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.tsx
│   ├── hooks/
│   └── utils/
└── (no android/ dir)
```

### Example: a slope-coloring hook

```tsx
// ext-slope-utils/src/hooks/useSlopeColoring.ts
export function useSlopeColoring(coordinates, demData) {
  return useMemo(() => {
    const slopes = calculateSlope(coordinates);
    const colors = slopes.map(s => slopeToColor(s));
    return colors;
  }, [coordinates, demData]);
}

// Consumer:
const colors = useSlopeColoring(trailCoords, demTiles);
// Pass colors to existing <LayerPath> instances split by color group
```

Core library hooks needed: typically none.

## Pattern B: TurboModule (new layer using existing vtm rendering)

Creates a new TurboModule that extends an existing `LayerManager` from the core
library. No vtm class shadowing — reuses vtm's existing `VectorLayer`,
`ItemizedLayer`, or `TileSource` infrastructure.

### File structure

```
ext-<name>/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.tsx
│   ├── components/LayerXxx.tsx
│   ├── NativeModules/NativeLayerXxx.ts
│   └── hooks/
├── android/
│   ├── build.gradle
│   └── src/main/java/.../
│       ├── XxxPackage.java          ← ReactPackage for autolinking
│       └── modules/LayerXxx.java    ← TurboModule implementation
└── (no vtm shadow classes)
```

### Two rendering strategies

#### Strategy B1: Custom TileSource (simpler, faster to ship)

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

#### Strategy B2: Custom vtm Layer (more control, more complex)

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

Core library hooks needed: factory method in existing TurboModule if extending a
`LayerManager`. Phase 1 hooks cover `PathLayerManager`.

## Pattern C: vtm class shadowing (new GPU rendering)

When the extension needs new vertex attributes, custom GLSL shaders, or changes to
vtm's internal rendering pipeline. The extension places modified copies of vtm JAR
classes in its own source tree — the Android Gradle classpath gives these compiled
classes precedence over the originals in the vtm JAR.

vtm is LGPL-licensed, which explicitly allows this pattern. Only shadow the minimum
set of classes needed (typically 2–3). Add an LGPL attribution comment at the top
of each shadowed file.

### When to use Pattern C

- Per-segment or per-vertex data passed to the GPU (new vertex attributes)
- Custom GLSL shaders for fragment-level effects (color ramps, gradients, animation)
- Changes to vtm's render pipeline (bucket compilation, draw call setup, texture binding)
- The feature cannot be achieved by extending existing vtm Layer classes

### File structure

```
ext-<name>/
├── (all Pattern B files)
├── android/src/main/
│   ├── assets/
│   │   └── shaders/
│   │       └── custom_shader.glsl      ← shadows vtm JAR shader
│   └── java/
│       ├── org/oscim/renderer/bucket/
│       │   ├── LineBucket.java         ← shadows vtm JAR class (LGPL attribution)
│       │   └── RenderBuckets.java      ← shadows vtm JAR class (VERTEX_CNT change)
│       └── com/.../ext/<name>/
│           ├── CustomVectorLayer.java
│           ├── CustomLayerManager.java
│           └── modules/LayerXxx.java
```

### What changes in each shadowed class

| Shadowed class | Typical change |
|---|---|
| `RenderBuckets.java` | `VERTEX_CNT[LINE]` (or other bucket type): increase stride for new vertex attrib |
| `LineBucket.java` | New vertex attribute (`a_value`), new `addVertex` overload, modified `Shader` and `Renderer` inner classes |
| `PolygonBucket.java` | Same pattern for polygon rendering |
| `TextureBucket.java` | Additional texture bindings or sampler uniforms |

### GLSL shaders

Place modified shaders in `android/src/main/assets/shaders/`. Android asset merging
gives these precedence over the same-named files in the vtm JAR. Use new shader
filenames (e.g., `line_aa_value.glsl` instead of overwriting `line_aa.glsl`) to
avoid conflicts with other extensions.

For OpenGL ES 2.0 compatibility:
- Use `sampler2D` (not `sampler1D` — unsupported in GLES 2.0)
- For 1D color ramp lookups: `texture2D(u_ramp, vec2(v_value, 0.5))` with a 256×1 RGBA8 texture

### Example: color-ramp paths

See [`react-native-mapsforge-vtm-ext-path-color-ramp`](https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-path-color-ramp)
for a working extension that shadows `LineBucket` and `RenderBuckets` to add a
per-vertex `a_value` attribute and a `u_colorRamp` sampler2D uniform. The vertex
format changes from 4 shorts (x, y, dx, dy) to 5 shorts (x, y, dx, dy, value).

Core library hooks needed: Phase 1 hooks (`drawSegments()` protected,
`createPathLayerManager()` factory).

## TurboModule spec pattern

Every extension that creates native layers needs a TurboModule spec. Follow this
structure (in `src/NativeModules/NativeLayerXxx.ts`):

```typescript
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';

interface CreateLayerParams {
  nativeNodeHandle?: Int32;
  positionIndex?: Int32;
  // ...extension-specific params
}

interface RemoveLayerParams {
  nativeNodeHandle: Int32;
  uuid: string;
}

export interface Spec extends TurboModule {
  createLayer(params: CreateLayerParams): Promise<Object>;
  removeLayer(params: RemoveLayerParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerXxx');
```

**Important:** Types used in `Spec` must be declared inline — react-native-codegen's
TypeScript parser cannot follow cross-file imports. If you need to mirror types
from the core library (e.g., `GeometryStyle`), copy them inline and add a comment
referencing the canonical source to keep them in sync.

The `package.json` must include:

```json
{
  "codegenConfig": {
    "name": "RNMyExtensionSpec",
    "type": "all",
    "jsSrcsDir": "src",
    "outputDir": {
      "android": "android/generated"
    },
    "android": {
      "javaPackageName": "com.jhotadhari.reactnative.mapsforge.vtm.ext.<name>"
    },
    "includesGeneratedCode": true
  }
}
```

## Scaffolding a new extension

### Prerequisites

1. Node.js, Yarn 3.6.1 (or match the core library's `packageManager` version)
2. `react-native-builder-bob` (build tool — already in devDependencies)
3. `@jhotadhari/release-kit@^0.0.6` (release pipeline — npm, not yalc)

### Steps

1. **Plan first.** Run `/ext-plan` to determine pattern, identify needed hooks,
   and get a scaffolded plan file.

2. **Create the repo:**
   ```sh
   mkdir react-native-mapsforge-vtm-ext-<name>
   cd react-native-mapsforge-vtm-ext-<name>
   git init && git checkout -b main
   ```

3. **Copy configuration** from the core library (`react-native-mapsforge-vtm`):
   - `.prettierrc`, `.prettierignore`
   - `eslint.config.mjs`
   - `lefthook.yml`
   - `.gitignore` (adapt — remove `example/`, `ios/` entries if Android-only)
   - `.yarnrc.yml`, `.yarn/releases/`, `.yarn/plugins/`
   - Adapt `tsconfig.json` (update paths, remove example references)

4. **Create `package.json`:**
   - `name`: `react-native-mapsforge-vtm-ext-<name>`
   - `peerDependencies`: `react-native-mapsforge-vtm`
   - `devDependencies`: copy versions from core library
   - `react-native-builder-bob` config (same as core, Android-only targets)
   - `codegenConfig` with unique `name` and correct `javaPackageName`

5. **Create `android/build.gradle`:**
   - Depend on `project(':react-native-mapsforge-vtm')` (provides vtm transitively)
   - Add vtm dependencies at same versions as core library
   - `react { ... }` block for codegen

6. **Create `ROADMAP.md`** with phased implementation plan (reference the plan
   file from `/ext-plan`).

7. **Init CLAUDE.md:**
   ```
   /init
   ```

8. **Install and verify:**
   ```sh
   yarn install
   yarn typecheck
   yarn lint
   ```

9. **Start on `development` branch:**
   ```sh
   git checkout -b development
   ```

### Boilerplate checklist

| File | Source |
|---|---|
| `package.json` | Hand-written (follow core's structure) |
| `tsconfig.json` | Copy from core, update paths |
| `tsconfig.build.json` | Copy from core |
| `eslint.config.mjs` | Copy from core |
| `.prettierrc` | Copy from core |
| `.prettierignore` | Copy from core, adapt |
| `lefthook.yml` | Copy from core |
| `.gitignore` | Copy from core, adapt |
| `bob.config.js` | Create (Android only) or use `react-native-builder-bob` key in package.json |
| `android/build.gradle` | Hand-written (follow pattern) |
| `android/src/main/AndroidManifest.xml` | Minimal (`<manifest>`) |
| `ROADMAP.md` | Hand-written (phased plan) |
| `CLAUDE.md` | Generated by `/init` |
| `src/index.tsx` | Hand-written (public exports) |
| `src/NativeModules/NativeXxx.ts` | Hand-written (TurboModule spec) |
| `src/components/Xxx.tsx` | Hand-written (React component) |

## Java patterns

### ReactPackage for autolinking

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

## Reanimated overlays (no native code)

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

## Complete examples

| Extension | Pattern | What it demonstrates |
|---|---|---|
| [`ext-grib`](https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-grib) | B1 (TileSource) | Custom `TileSource` + `ITileDataSource` for weather GRIB overlays |
| [`ext-path-color-ramp`](https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-path-color-ramp) | C (vtm-shadowing) | `LineBucket` shadowing + custom GLSL shaders + per-vertex attributes |

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

### 7. vtm class shadowing utilities

A script that diffs a shadowed class against the original vtm source and produces
a patch file. This would make it easier to:
- Review what changed vs upstream vtm
- Rebase patches when upgrading vtm versions
- Audit LGPL compliance (exactly what was modified)

### 8. Extension template repo

A minimal working extension that can be copied and renamed. Already has bob builder,
prettier, eslint, lefthook, release-kit, and stub TurboModule files — eliminates
the boilerplate checklist above. Currently `ext-path-color-ramp` serves this role
informally; a dedicated `react-native-mapsforge-vtm-ext-template` would be cleaner.
