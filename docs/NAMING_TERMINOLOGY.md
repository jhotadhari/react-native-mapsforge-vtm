# Naming conventions and terminology

## Naming conventions

### Cross-boundary: JS → Java

Every layer follows this chain (`LayerFoo` example):

| JS spec | Registered as | Codegen generates | You write |
|---|---|---|---|
| `NativeModules/NativeLayerFoo.ts` | `'LayerFoo'` | `NativeLayerFooSpec.java` | `modules/LayerFoo.java extends NativeLayerFooSpec` |
| `NativeViews/MapsforgeVtmViewNativeComponent.ts` | `'MapsforgeVtmView'` | `MapsforgeVtmViewManagerInterface<>` | `views/MapsforgeVtmViewManager.java` + `MapsforgeVtmView.java` |

The Java class name **exactly matches** the TurboModule registration string — no prefix, no suffix.

### File naming

| Directory | Convention |
|---|---|
| `src/components/` | `Layer<Name>.tsx` for map layers; plain `.tsx` for children/wrappers |
| `src/NativeModules/` | `Native<Name>.ts` |
| `src/NativeViews/` | `<ViewName>NativeComponent.ts` |
| `src/compose/` | `use<HookName>.ts` |
| `src/context/` | `<Name>Context.ts` |
| `android/…/modules/` | `<Name>.java` (matches TurboModule reg name) |
| `android/…/views/` | `<Name>View.java`, `<Name>ViewManager.java` |
| `android/…/layer/` | `<Name>Layer.java`, `<Name>LayerManager.java` |

### Type/interface suffixes

| Suffix | Meaning |
|---|---|
| `*Props` | React component props |
| `*Response` | Native → JS payload (create/remove/event callbacks) |
| `*GestureResponse` | Gesture event (press/long-press/double-tap) |
| `*Params` | Input to a native method call |
| `*TriggerEvent` | `RefObject` for programmatic gesture simulation |
| `*ResponseInclude` | Bitmask flags for response field inclusion |
| `*Style` | Visual style (fill, stroke, stipple) |
| `Spec extends TurboModule` | Codegen-read interface (one per `Native*.ts`) |
| `ModuleParams` | Return type of `getConstants()` |

**Codegen constraint:** Types must be redeclared inline in spec files — codegen's TS parser cannot follow imports (`Position`, `ResponseBase`, `Bbox` are redeclared with `Double` in every `Native*.ts`). Don't "clean up" these apparent duplicates.

### Java class suffixes

| Suffix | When |
|---|---|
| (none) | TurboModule in `modules/` |
| `*Spec` | Codegen-generated base (in `generated/`) — never edit |
| `*View` / `*ViewManager` | Fabric native View / ViewManager |
| `*Fragment` | Android Fragment hosting the map |
| `*Layer` | Custom vtm `Layer` subclass |
| `*LayerManager` | Concrete shared-layer manager (`PathLayerManager`, `MarkerLayerManager`, `ShapeLayerManager`) |
| `*Manager` | Abstract base (`LayerManager<TEntry>`) |
| `*Queue` | Serialises mutations onto UI thread (`MapMutationQueue`) |
| `*Helper` | Stateless layer utility (`LayerHelper`, `LayerZoomBoundsHelper`) |
| `*Wrapper` | Adapts a third-party class (`PathLayerJtsWrapper`) |

### Component / hook / context naming

| Kind | Pattern | Examples |
|---|---|---|
| Map layers | `Layer<SourceOrType>` (renders `null`, talks to TurboModule) | `LayerMapsforge`, `LayerPath`, `LayerMarker` |
| Children / wrappers | Plain name, no `Layer` prefix | `Marker`, `SharedLayer`, `ReindexScope` |
| Root | `MapContainer` (sole exception) | `MapContainer` |
| Hooks | `use<Thing>()` | `useMap`, `useLayerOrder`, `useNativeLayerLifecycle`, `use<Layer>EventSubscription` |
| Contexts | `<Name>Context`; `null` = "not within provider" | `MapHandleContext`, `SharedLayerContext`, `MarkerLayerContext`, `ReindexContext` |

TurboModule registration names match component names: `LayerFoo` → `NativeModules/NativeLayerFoo.ts` → registered as `'LayerFoo'`.

## Domain glossary

| Term | Meaning |
|---|---|
| **nativeNodeHandle** | Fabric handle of the map view (`findNodeHandle`). Every layer passes it to identify which map instance. |
| **uuid** | Unique string from native `createLayer`/`createMarker`. Used for remove/update calls and event filtering. |
| **fragment / fragmentUuid** | A block of same-type shared-layer components collapsed into one native `Layer`. Fragment UUIDs are prefixed `__vtm_shared_`. |
| **shared layer** | Many JS components → one native `Layer` (`LayerPath`, `LayerMarker`, `LayerShape`). Managed by `LayerManager<TEntry>`. |
| **dedicated layer** | One JS component → one native `Layer` (`LayerPathJts`, `LayerMapsforge`, `LayerBitmapTile`, etc.). |
| **LayerOrderRegistry** | Central data structure in `MapHandleContext` tracking every layer's document-order position (`Symbol` keyed), uuid, fragment, and type. |
| **positionIndex** | Zero-based document-order index. Passed at creation time so layers land at correct z-order without a follow-up `reorderLayers`. |
| **MapMutationQueue** | The **only** place that calls `mapView.map().layers().add/remove` and `updateMap()` — serialises onto UI thread. |
| **knownLayers** | `MapMutationQueue.getKnownLayers()` — `ConcurrentHashMap` of tracked UUIDs, thread-safe to read. |
| **triggerEvent** | Programmatic gesture simulation (e.g. fire a marker's `onPress` from JS). Exposed as a `RefObject`. |
| **ReindexScope** | Wrapper that resets `positionIndex` within its subtree. |
| **SharedLayer** | Wrapper that activates shared-layer grouping for its subtree — same-type layers collapse into one fragment per type. |
| **MarkerBatchQueue** | Batches N `createMarker`/`removeMarker` calls → 1 `createMarkers`/`removeMarkers` bridge call. |
| **scheduleUpdate** | Coalesced `updateMap()` — multiple calls per frame → 1 native `updateMap()` (via `AtomicBoolean` CAS + `Handler.post`). |

## vtm / mapsforge terms

| Term | Meaning |
|---|---|
| **mapFile** | `.map` binary vector tile or `.mbtiles` file |
| **renderTheme** | XML (or built-in like `'OSMARENDER'`) defining map element styles |
| **stylemenu** | `<stylemenu>` in render-theme XML listing toggleable layer groups |
| **Layer** (`org.oscim.layers.Layer`) | Any drawable map overlay in vtm's pipeline |
| **GeoPoint** | Lat/lng coordinate |
| **drawable** (vtm-jts) | Single shape/path/marker primitive inside a `VectorLayer` |
| **ItemizedLayer** | vtm's marker overlay (holds `MarkerItem`s + `MarkerSymbol`s) |
| **CanvasAdapter** | vtm's rendering backend abstraction |

## React Native New Architecture terms

| Term | In this codebase |
|---|---|
| **Fabric** | Rendering system. Events arrive as `DirectEventHandler` props, not via `NativeEventEmitter`. |
| **TurboModule** | Native module system. Every `modules/*.java` is a TurboModule, loaded on demand via JSI. |
| **codegen** | Build tool: reads TS `Spec` → generates `Native*Spec.java`. Your class extends the generated base. |
| **findNodeHandle** | Gets the native view handle. Used by `MapContainer` → `nativeNodeHandle`. |
| **DirectEventHandler** | Fabric event callback — direct native→component (no global emitter). |

## Inclusive terminology

This project avoids terminology rooted in colonialism, slavery, and oppression:

| Avoid | Use | Example context |
|---|---|---|
| master/slave | source/mirror, sync source | Multi-map position sync |
| master branch | main branch | Git default branch |
| whitelist/blacklist | allowlist/denylist | Feature flags, permissions |

**Why:** These terms are descriptive of the mechanism rather than metaphorical — "sync source" tells you what the variable does, without relying on a human-ownership analogy.

### References

- [IETF RFC 9456](https://www.rfc-editor.org/rfc/rfc9456)
- [Google style guide: Inclusive language](https://developers.google.com/style/inclusive-documentation)
