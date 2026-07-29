# Extending `react-native-mapsforge-vtm`

This library provides hooks and patterns for building custom layer-type extensions
that integrate with the map's render-order registry, lifecycle management, and
native rendering pipeline.

## Quick start

An interactive planning skill (`/ext-plan`) walks through 7 architectural questions
and scaffolds a complete extension repo using `create-react-native-library`. It
handles all boilerplate: bob build setup, example app, Android configuration,
prettier/eslint/release-kit tooling, and the verbose hierarchical Java namespace
(`com.jhotadhari.reactnative.mapsforge.vtm.ext.<name>`).

The skill lives at [`.opencode/skills/ext-plan/SKILL.md`](../../.opencode/skills/ext-plan/SKILL.md).
It is **auto-discovered** by OpenCode — no manual registration needed. OpenCode
scans `.opencode/skills/` for subdirectories containing a `SKILL.md` file (with
YAML frontmatter declaring `name` and `description`). Because the file follows this
convention, `/ext-plan` is available as a slash command in any session opened in
this repo.

If you're reading this from an extension repo (not the core library), copy the
`ext-plan/` skill directory into your own `.opencode/skills/` to make `/ext-plan`
available there too.

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

### Extension API (public)

Two hooks, one context, and one factory function form the extension API. Import them from
the main package:

```tsx
import {
  MapHandleContext,
  createLayerOrderRegistry,
  useLayerOrder,
  useNativeLayerLifecycle,
} from 'react-native-mapsforge-vtm';
```

Also exported as types: `LayerOrderRegistry`, `MapHandleContextValue`, `CreateFlags`,
`RemoveFlags`, `ErrorBase`, `ErrorWithErrorMsg`, `ResponseBase`, `Position`.

#### `MapHandleContext` — React context

Provides `nativeNodeHandle` (the map view's Android view tag, `null | number`) and
`registry` (the `LayerOrderRegistry` that tracks every layer's position in the render
tree).

```tsx
const { nativeNodeHandle, registry } = useContext(MapHandleContext);
```

#### `createLayerOrderRegistry()` — factory function

Creates a `LayerOrderRegistry` instance with debounced native `reorderLayers` syncing
(16ms debounce, 250ms max-wait cap). Extensions typically don't call this directly — it's
used by `MapContainer` internally. Exported for custom container scenarios.

#### `useLayerOrder(uuid, layerType?)` — hook

Registers a component into the render-order registry during render. Call this once per
layer component.

```tsx
const { nativeNodeHandle, positionIndex, fragmentUuid } =
  useLayerOrder(uuid, layerType);
```

| Parameter | Type | Meaning |
|---|---|---|
| `uuid` | `null \| false \| string` | The layer's native UUID. `null` = not yet created/disabled; `false` = create-in-progress; `string` = resolved native UUID. |
| `layerType` | `string` (optional) | A type string like `'path'`, `'marker'`, `'mapsforge'`. When passed, a shared-layer fragment UUID is computed (e.g. `__vtm_shared_path__2`). When omitted, the component is treated as a dedicated-layer type. |

| Return field | Type | Meaning |
|---|---|---|
| `nativeNodeHandle` | `null \| number` | The map's native view tag from context. |
| `positionIndex` | `number` | This layer's zero-based index in `registry.order` — its document-order position among all JS-managed layers. `-1` if not yet registered. |
| `fragmentUuid` | `string \| undefined` | Shared-layer fragment UUID. `undefined` when no `layerType` was passed. |

#### `useNativeLayerLifecycle({ enabled, create, remove, onError? })` — hook

State machine: `null → false → uuid`. The hook handles mount/create, prop-change
re-creation, unmount cleanup, and error reporting.

```tsx
const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
  enabled: true,
  create: async (flags) => {
    const { uuid } = await MyModule.createLayer({ nativeNodeHandle, positionIndex });
    return uuid;
  },
  remove: async (uuid, flags) => {
    return MyModule.removeLayer({ nativeNodeHandle, uuid });
  },
  onError: (err) => console.error(err),
});
```

| Parameter | Type | Meaning |
|---|---|---|
| `enabled` | `boolean` | Gates whether `create` is attempted. While `false`, `uuid` stays `null`. Flipping back to `true` re-attempts creation. |
| `create` | `(flags: CreateFlags) => Promise<TUuid>` | Calls the native create method and returns the new UUID string. |
| `remove` | `(uuid: TUuid, flags: RemoveFlags) => Promise<boolean>` | Calls the native remove method. Return `true` on success. |
| `onError` | `null \| ((err: ErrorBase) => void)` (optional) | Error handler. When omitted, errors are reported via `reportNativeError`. |

`CreateFlags`:

| Flag | Default | Meaning |
|---|---|---|
| `triggerOnCreate` | `true` | Fire the `onCreate` callback on the native side. |
| `triggerOnChange` | `false` | Fire the `onChange` callback on the native side. |

`RemoveFlags`:

| Flag | Default | Meaning |
|---|---|---|
| `triggerOnRemove` | `true` | Fire the `onRemove` callback on the native side. |

**Return value:**

| Field | Type | Meaning |
|---|---|---|
| `uuid` | `null \| false \| TUuid` | `null` = not yet created/disabled; `false` = create-in-progress; `TUuid` (string) = native resource exists. |
| `triggerCreate` | `(flags?: CreateFlags) => void` | Manually trigger creation (defaults: `triggerOnCreate: true, triggerOnChange: false`). No-op when `enabled` is `false`. Stable identity. |
| `triggerRemove` | `(flags?: RemoveFlags) => Promise<boolean>` | Manually trigger removal (defaults: `triggerOnRemove: true`). Returns `Promise<boolean>` — `false` if no UUID exists. Stable identity. |

**Auto-lifecycle:** The hook auto-creates on mount (when `enabled` is `true`) and
auto-removes on unmount. If the component unmounts while `create` is still in-flight,
the newly-created resource is immediately removed to balance the lifecycle.

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
`LayerManager`. The core library currently exposes `createPathLayerManager()` in
`LayerPath` and `drawSegments()` / `getStyleBuilder()` in `PathLayerManager` (all
`protected`) for this purpose.

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
│       └── com/jhotadhari/reactnative/mapsforge/vtm/ext/<name>/
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

Core library hooks needed: `drawSegments()` (`protected`) and `createPathLayerManager()`
factory method — see the Java extensibility hooks table above.

### Resolving DEX class conflicts

Shadowed vtm classes cause a DEX merge failure at the app level because both the
extension's modified `.class` files and the original vtm JAR's versions have the
same fully-qualified name. The library provides a reusable Gradle script that
patches the vtm JAR (removing the original `.class` entries) and cleans stale
build artifacts from the core library before DEX merging.

**In the extension's `android/build.gradle`**, declare which classes you shadow:

```groovy
ext.shadowedClasses = [
    'org.oscim.renderer.bucket.LineBucket',
    'org.oscim.renderer.bucket.RenderBuckets',
]
```

**In the app's `android/app/build.gradle`**, apply the stripping script once:

```groovy
apply from: "${projectDir}/../../node_modules/react-native-mapsforge-vtm/android/strip-vtm-classes.gradle"
```

The script collects `ext.shadowedClasses` from every extension in the dependency
graph (via Gradle's `allprojects` and a `node_modules/` fallback scan for
setups where the extension project doesn't appear in `allprojects`).
It uses a three-pronged approach, all scoped to the current build:

1. **JAR patching** — the `stripShadowedVtmClasses` task patches the vtm JAR
   in the Gradle cache with `zip -d` to remove matching `.class` entries
   (including inner classes and multi-release JAR copies under
   `META-INF/versions/`). A crash-recovery step (`doFirst`) restores any
   stale `.orig` backups before patching, and a `buildFinished` hook
   unconditionally restores the original JAR after every build — so the
   shared Gradle cache is never left permanently modified.

2. **Configuration-time source exclusion** — the script excludes shadowed
   `.java` source files from the core library's compilation using Gradle's
   `SourceDirectorySet.exclude()`. This is non-destructive: no files are
   deleted from disk, the exclusion is scoped to the current build, and it
   survives yalc symlinks. The core library ships `LineBucket.java` and
   `RenderBuckets.java` (from vtm 0.29.0, restored as an AGP/D8 workaround),
   so these two classes are always excluded when an extension shadows them.

3. **Execution-time artifact cleaning** — a `stripShadowedClassesFromProjects`
   task cleans stale `.class` files, `.dex` files, and the
   `runtime_library_classes_jar` directory from the core library's build tree
   before DEX merging, so incremental-build artifacts can't resurrect
   shadowed classes. The task hooks into `mergeLibDex*`/`mergeDex*` for
   reliable timing.

**Multiple extensions:** Two extensions can shadow different classes without
conflict. If two shadow the same class, pass a `prefer` map to resolve:

```groovy
apply from: "...", to: [
    prefer: ['org.oscim.renderer.bucket.LineBucket': 'ext-b-6shorts'],
]
```

The winning extension's class survives; the other extension's class is stripped
from its own output. Without a `prefer` entry, the build fails with a clear
message listing the conflict.

**App with no extensions:** The `apply from:` line is optional — the script is a
no-op when no `ext.shadowedClasses` are found.

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
from the core library (e.g., `PathPaint`), copy them inline and add a comment
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

## Java patterns

### ReactPackage for autolinking

Every extension needs a `ReactPackage` for autolinking:

```java
public class MyExtensionPackage extends BaseReactPackage {
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
import { MapHandleContext } from 'react-native-mapsforge-vtm';
import Animated from 'react-native-reanimated';
import { useContext, useEffect } from 'react';

// Inside a component under <MapContainer>:
const { nativeNodeHandle } = useContext(MapHandleContext);
const pos = useMapPosition();
const overlay = useMapOverlay({ lat: 51.5, lng: -0.12 }, pos);

// Activate the native shared-value bridge for true zero-bridge 60fps.
// Without this, position data still flows but goes through the JS bridge
// (~25fps) instead of directly from the render thread.
useEffect(() => {
  if (nativeNodeHandle) pos.activateNativeBridge(nativeNodeHandle);
}, [nativeNodeHandle]);

<Animated.View style={[styles.marker, overlay.animatedStyle]} />
```

**`toScreenPosition` and `fromScreenPosition`:** Two Mercator-projection utilities
are also exported from `/reanimated` for converting between geographic coordinates
and screen-space pixel positions. These run as worklets (UI-thread) and can be used
inside `useDerivedValue` / `useAnimatedStyle`:

```tsx
import { toScreenPosition, fromScreenPosition } from 'react-native-mapsforge-vtm/reanimated';
import { useMapPosition } from 'react-native-mapsforge-vtm/reanimated';

const pos = useMapPosition();
// Convert a geo coordinate to screen pixel position (worklet):
const screenPoint = toScreenPosition({ lat: 51.5, lng: -0.12 }, pos);
// screenPoint: { x: number, y: number } — pixel position on the map view
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

**Implemented:** `android/strip-vtm-classes.gradle` handles DEX deduplication
automatically. Declare shadowed classes via `ext.shadowedClasses` in the
extension's `build.gradle`; apply the script once in the app's `build.gradle`.
See "Resolving DEX class conflicts" in Pattern C above.

Still open as future work: a script that diffs a shadowed class against the
original vtm source and produces a patch file. This would make it easier to:
- Review what changed vs upstream vtm
- Rebase patches when upgrading vtm versions
- Audit LGPL compliance (exactly what was modified)

### 8. Extension template repo

The `/ext-plan` skill (at `.opencode/skills/ext-plan/SKILL.md`) scaffolds extensions
using `create-react-native-library`, eliminating manual boilerplate. The scaffolded
output already has bob builder, prettier, eslint, lefthook, release-kit, example
app, and stub TurboModule files — no manual copying needed. The skill is
auto-discovered by OpenCode from the directory-based skill format (see
Quick start above).
