# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`react-native-mapsforge-vtm` is a React Native wrapper around [mapsforge/vtm](https://github.com/mapsforge/vtm)
for offline vector map rendering from OpenStreetMap data. **Android only** — `ios/generated` codegen
stubs exist (required by the New Architecture build), but there is no real iOS implementation.

The library was rewritten against the **React Native New Architecture** (Fabric + TurboModules) in
commit `c9a6ace`, replacing an older bridge/`NativeModules` design. The rewrite dropped several
features intentionally (notably `LayerPathSlopeGradient` and GPX-file loading) — don't assume
old-architecture patterns from outside this repo apply here. `TODO.md` tracks open work: a post-rewrite dependency upgrade plan (item 1), and a
historical record of layer-ordering bugs that were fixed (item 0, now theoretical).

## Edit Tool - Whitespace Workaround

For `.ts`/`.tsx`/`.js`/`.jsx` files: match `old_string` in Edit calls **without** leading
whitespace (to avoid the tab-vs-space ambiguity described in
[claude-code/#26996](https://github.com/anthropics/claude-code/issues/26996)). Accumulate all
touched files, then run one `npx prettier --write <file1> <file2> ...` at the end to fix
indentation. Only include leading whitespace when needed to disambiguate non-unique matches.

For `.java` files, `yarn format` doesn't cover them — fall back to `sed` with explicit `\t`
escapes after a single failed Edit attempt.

## Common commands

This is a Yarn workspaces monorepo (`packageManager: yarn@3.6.1` — don't use `npm`). The library lives in
the repo root (`src/`, `android/`); `example/` is a workspace app for manual testing.

```sh
yarn                  # install deps for root + example workspaces
yarn typecheck        # tsc (no emit, just checks)
yarn lint             # eslint over **/*.{js,ts,tsx} (flat config, eslint.config.mjs)
yarn format           # prettier . --write
yarn test             # jest — the only test file is src/__tests__/index.test.tsx and it's a stub (it.todo); don't trust it to catch regressions
yarn clean            # del-cli android/build example/android/build example/android/app/build lib
yarn prepare          # bob build — builds lib/ (codegen + module + typescript) from src/, runs on install via "prepare"

yarn example start    # Metro for the example app
yarn example android  # build & run the example app on a connected device/emulator

yarn release <version>  # release-kit — bumps version, validates CHANGELOG.md, tags, publishes to npm; requires `gh` CLI
```

`lefthook.yml` runs `eslint` and `tsc` on staged `*.{js,ts,jsx,tsx}` files as a pre-commit hook.
CI (`.github/workflows/ci.yml`) drives the Android build through `yarn turbo run build:android`
(`turbo.json`'s `build:android` task), caching on `yarn.lock`'s hash. **CI only triggers on `v**` tags** —
PRs and branch pushes do not get CI; test locally before pushing.

Prettier config (`.prettierrc`): **tabs** (not spaces), 80 char print width, plugins
`prettier-plugin-embed` and `prettier-plugin-multiline-arrays`. The `multilineArraysWrapThreshold: 2`
means arrays with 2+ elements get one-element-per-line formatting.

Dependencies already dropped (don't re-add): `simplify` (commit `39c8833`) and `savitzky-golay`
(commit `f5ade2e`).

To work on native Android code, open `example/android` in Android Studio — library Java sources show up
under the `react-native-mapsforge-vtm` module (this repo is symlinked in via `example/`'s yarn workspace).

## Architecture

### One Fabric view, one TurboModule per layer

`MapContainer` (`src/components/MapContainer.tsx`) renders the single native view —
`MapsforgeVtmView`, the codegen'd Fabric component from
`src/NativeViews/MapsforgeVtmViewNativeComponent.ts`, backed by `MapsforgeVtmViewManager.java` /
`MapFragment.java` (the actual vtm `MapView` host) under
`android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/views/`. Everything nested inside it —
`LayerMapsforge`, `LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap`, `LayerPath`, `LayerPathJts`,
`LayerShape`, `LayerMarker`, `LayerScalebar` — is a plain React component (renders `null`) that talks to
its own TurboModule spec in `src/NativeModules/NativeXxx.ts` via `createLayer`/`removeLayer` calls keyed
by `nativeNodeHandle` (the map view's handle, obtained via `findNodeHandle` since Fabric views still don't
expose a handle any other way) and a `uuid` returned from `createLayer` (used later for
`removeLayer`/update calls). Each spec generates `android/generated/java/.../NativeXxxSpec.java`; the
hand-written implementation lives one level up as `android/.../modules/Xxx.java extends NativeXxxSpec`
(e.g. `LayerMarker.java extends NativeLayerMarkerSpec`).

### Wiring layers together: `MapHandleContext`, not prop injection

`MapContainer` creates a `LayerOrderRegistry` (`src/context/MapHandleContext.ts`) and provides it, along
with the map's `nativeNodeHandle`, through `MapHandleContext`. Every layer component calls
`useLayerOrder` (`src/compose/useLayerOrder.ts`), which registers the component's position in render
order (tracked by a stable `Symbol`, independent of nesting depth) and its resolved native `uuid` into
that shared registry, then debounces a single native `reorderLayers` call whenever the resolved order
actually changes. This replaced the old `cloneElement`/static-`isMapLayer` walk entirely — there is no
prop-injection wiring left in this repo. `LayerMarker` does its own one-level-down equivalent for
`Marker` children.

The registry has grown beyond a simple ordered-symbol list into a rich data structure with ~20 fields.
Key additions beyond the core `order` + `uuids`:

| Field | Purpose |
|---|---|
| `generation` | Monotonically increasing counter bumped by `MapContainer` on each render. `useLayerOrder` compares its last-seen value to detect full render passes (must reposition already-registered layers) vs. solo re-renders (must not disturb sibling order). |
| `cursor` / `cursorLayerType` | Id of the most-recently-rendered sibling + its layer type. Reset by `MapContainer` each render pass. Used for O(1) insertion anchoring and type-run fragment-boundary detection. |
| `fragmentIndices` / `fragmentUuids` | Per-type fragment counters and per-component fragment UUIDs (e.g. `__vtm_shared_path__1`). Computed eagerly during render so `flush()` can synthesize the ordered-UUID list without waiting for native `createLayer` resolutions. |
| `layerTypes` | Per-symbol layer type string (`'path'`, `'marker'`, etc.), used for type-run boundary detection. |
| `layerReindexScopes` | Maps each layer symbol to its enclosing `ReindexScope`'s stable scope symbol. |
| `lastSymbolPerScope` | Per-scope most-recently-inserted symbol — O(1) sibling anchoring during partial re-renders (e.g. Redux-triggered), replacing the old O(n) backwards scan of `order`. |
| `sentinels` / `sentinelScopes` | Placeholder symbols pushed by `ReindexScope` wrappers whose children haven't mounted yet. `flush()` skips them; they ensure sibling scopes see correct relative order before children exist. |
| `scopePriorities` | Optional `order` prop values keyed by scope symbol, for explicit cross-scope z-ordering. |
| `scopeGenerations` | Per-scope monotonic counter bumped by `ReindexScope` on each of its own renders. Enables partial re-render repositioning scoped to the affected `ReindexScope` only, without disturbing unrelated scopes (e.g. Redux updating one panel's data). |
| `scheduleSync` / `destroy` | Debounced (16ms trailing, 250ms max-wait) native `reorderLayers` call. `destroy()` cancels pending timers on map teardown. |
| `listeners` / `subscribe` / `notify` | Debug subscription infrastructure for `useLayerDebugInfo` (devtools). |

**Invariant: native layer rendering must strictly follow React component tree order.** A layer
declared later in JSX (e.g. a `LayerMarker` mounted after a `LayerPath`) must always render on top of
it, same as later siblings paint on top in the DOM. Within a single render pass this holds
automatically (React's depth-first render order drives the cursor chain). For async children, three
mechanisms preserve it: (1) `<ReindexScope>` sentinel placeholders (pushed during initial render,
consumed when children mount), (2) the optional `order` prop for explicit priority, and (3) per-scope
`scopeGenerations` counters for partial re-render detection. Without any of these, new layers append
at the stale cursor.

The ordering bugs documented in TODO.md item 0 were fixed (commit `902fc47` and subsequent); the
remaining theoretical concern (reorder timing vs. `lastReorderWasEffective`) has not been observed in
practice — position-aware insertion + debounced reorder are sufficient.

### Central lifecycle hook: `useNativeLayerLifecycle`

Every layer component (all 9 layer types + `Marker`) uses `useNativeLayerLifecycle`
(`src/compose/useNativeLayerLifecycle.ts`). It owns the `null → false → uuid` state machine, creates
on mount (or whenever re-enabled via the `enabled` gate), removes on unmount, and centralizes native
error reporting through `reportNativeError`. Two refs guard against teardown races:

- **`mountedRef`**: if the component unmounts while `createLayer()` is in-flight (`uuid === false`),
  the hook detects the post-unmount resolution and cleans up the just-created native resource.
- **`uuidRef`**: mirrors the current uuid and is updated *immediately* on promise resolution (before
  React re-render), so the unmount cleanup effect — which fires between resolution and re-render —
  can see the real uuid and call `removeLayer`. Without this, there is a race window where:
  1. Promise resolves → sees `mountedRef` true → proceeds
  2. Component unmounts before React re-renders
  3. Unmount cleanup sees stale `uuid` (still `false`) → skips removal
  4. Native resource is orphaned (zombie)

Callers supply `create`/`remove` callbacks and an `enabled` boolean — the hook handles the rest.

### Shared-layer architecture: `SharedLayer` + fragments

`<SharedLayer>` (`src/components/SharedLayer.tsx`) collapses many same-type JS layer components into
one native `Layer` (a **fragment**). Without it, 1000 `<LayerPath>` components would create 1000
native `Layer` objects + 1000 GPU draw calls; with `<SharedLayer>`, they share ~1–10 native layers.

Fragment boundaries occur at:
- **Type-run boundaries** — consecutive same-type layers share a fragment; a different type starts a new one
- **Scope boundaries** — layers in different `<ReindexScope>` wrappers never share a fragment

Fragment UUIDs follow the pattern `__vtm_shared_<type>__<index>` (e.g. `__vtm_shared_path__1`).

`<ReindexScope>` (`src/components/ReindexScope.tsx`) serves three purposes:
1. **Async children**: pushes a **sentinel** placeholder into the ordering registry during render so
   children that mount later (async data) land at their correct tree position, not at the stale cursor
2. **Cross-scope ordering**: the `order` prop (e.g. `order={100}`) provides explicit priority across
   sibling scopes regardless of mount timing
3. **Partial re-render detection**: bumps a per-scope `scopeGenerations` counter on each of its own
   renders. When a `ReindexScope` re-renders without `MapContainer` re-rendering (e.g. Redux-triggered
   data update), child `useLayerOrder` calls detect the generation change and reposition already-
   registered layers to match the new document order — but only layers inside the changed scope are
   affected; unrelated scopes are undisturbed.

It uses a two-phase protocol: Phase 1 (render) records the scope block's position and manages
sentinel lifecycle; Phase 2 (`useLayoutEffect`) verifies the block hasn't been shifted by a sibling
scope in the same commit. See the source comments for the full algorithm.

For full details, see `docs/advanced/layer-ordering.md`.

### Marker batch creation

`MarkerBatchQueue` (`src/compose/MarkerBatchQueue.ts`) collapses N individual `createMarker`/
`removeMarker` bridge calls into 1 `createMarkers` + 1 `removeMarkers` batch call. The queue is
per-`nativeNodeHandle`, flushed on the microtask boundary (`Promise.resolve().then()`) with a
16ms safety max-wait `setTimeout`. JS is single-threaded so no locking is needed. Call
`drainQueue(nativeNodeHandle)` on map destruction to reject all pending operations.

### Extension points for external layer-type libraries

The public API (`src/index.tsx`) exports stable hooks and contexts for building custom layer types
outside this repo (e.g. `react-native-mapsforge-vtm-ext-grib`):

| Export | Purpose |
|---|---|
| `MapHandleContext` | React context holding `nativeNodeHandle` + `LayerOrderRegistry` |
| `createLayerOrderRegistry()` | Factory for the ordering registry (if you need a separate one) |
| `useLayerOrder` | Register a component in the layer ordering registry |
| `useNativeLayerLifecycle` | `null → false → uuid` state machine for native resource lifecycle |

See `docs/advanced/extending.md` and the `/ext-plan` skill for guidance on the three extension
patterns: JS-only, TurboModule, and vtm-shadowing.

### Path layers: `LayerPath` vs `LayerPathJts`

The library provides two path components backed by different vtm-jts implementations:

| Aspect | `LayerPath` | `LayerPathJts` |
|---|---|---|
| Native backend | `PathLayerManager` + shared `VectorLayer` | Dedicated `org.oscim.layers.vector.PathLayer` per component |
| Architecture | **Shared-layer**: many JS components collapse into one native layer | **Dedicated-layer**: one native layer per JS component |
| Render ordering | Correct (fixed `902fc47` and subsequent; shared-layer fragment UUIDs now flow through `knownLayers` correctly) | Correct — per-component uuid IS the layer uuid |
| Performance at scale | Excellent (1 GPU draw call for all paths) | Worse (1 native layer per path) |
| Great-circle arcs | Not supported | `addGreatCircle` method |
| Douglas-Peucker generalization | Not supported | Built-in via `Style.generalization` |
| JTS `LineString` input | Not supported | `setLineString(double[])` |
| Gesture hit-testing | Shared `VectorLayer` with per-drawable uuid resolution | Per-layer `contains()` + `onGesture()` |
| Best for | 50–1000+ paths, route networks, trajectory data | 1–30 paths, great circles, guaranteed z-order |

Both share the same `GeometryStyle` interface (stroke, fill, stipple, etc.) and the same gesture
callback pattern (`onPress`/`onLongPress`/`onDoubleTap`). Choose `LayerPathJts` when you need
correct render order or JTS-specific features; choose `LayerPath` when you have many paths and
the shared-layer performance matters.

### `LayerShape` — geometric shape overlays

Draws JTS geometric shapes (polygons, circles, rectangles, hexagons, points) on the map using
vtm-jts drawables. Uses **shared-layer** architecture via `ShapeLayerManager` (sibling to
`PathLayerManager` and `MarkerLayerManager`): same-type shapes within a fragment share one native
`VectorLayer`; each shape is a `Drawable` within it. Supports full `GeometryStyleJts` styling
(fill color, stroke, transparency, stipple, etc.) and gesture callbacks.

Shape types: `polygon` (with optional holes), `circle` (center + radius in km), `rectangle`
(two corners), `hexagon` (center + radius), `point` (single position).

### Update flow for layer props

Layer components hold a `uuid` (via the shared `useNativeLayerLifecycle` hook,
`src/compose/useNativeLayerLifecycle.ts`) tracking a null → false → uuid state machine: create on
mount/whenever re-enabled, remove on unmount, with native error reporting centralized through
`reportNativeError`. Most layers recreate (remove then create) on any prop that's baked into native
construction (e.g. `mapFile`/`renderTheme` for `LayerMapsforge`) and update in place for props that
aren't (e.g. `enabledZoomMin`/`enabledZoomMax`). Native async events (map move, lifecycle, marker
press, per-layer create/remove/change, map-level tap/longPress) arrive as direct Fabric event props
(`onMapUpdate`, `onPause`, `onResume`, `onTap`, `onLongPress`, etc. on `MapContainer` itself) rather
than a global `NativeEventEmitter` — this is a
deliberate Fabric-native pattern, not a missing migration.

### Where types come from

Each layer's request/response/props types live in its own `src/NativeModules/NativeXxx.ts` codegen spec
(or alongside the component, e.g. `LayerMapsforgeProps`/`LayerMapsforgeResponse` in
`NativeLayerMapsforge.ts`) and are re-exported as a namespace from `src/index.tsx` (e.g.
`LayerMapsforgeTypes`) — when changing a layer's prop or response shape, edit the spec file, not
`src/types.ts` (which only holds the few truly shared base types: `ResponseBase`, `ErrorBase`,
`Position`).

### Native side layout

Under `android/src/main/java/com/jhotadhari/reactnative/mapsforge/vtm/`:
- `modules/` — one hand-written class per layer/map-container (`LayerMarker.java`, `MapContainer.java`,
  etc.), each extending its codegen-generated `NativeXxxSpec` base class.
- `views/` — `MapFragment` (the vtm `MapView` host), `MapsforgeVtmView`, `MapsforgeVtmViewManager`
  (implements the codegen-generated Fabric manager interface).
- `layer/` — custom vtm layer subclasses and shared-layer infrastructure: `PathLayer`, `VectorLayer`,
  `ItemizedLayer`, `GestureLayer` (vtm layer subclasses); `LayerManager` (base class for shared-layer
  managers — owns `ensureSharedLayer()`, entry lifecycle, gesture delegation); `PathLayerJtsWrapper`
  (`LayerPathJts`-specific vtm `PathLayer` wrapper).
- Top-level shared-layer managers (one per layer type using shared architecture):
  `PathLayerManager extends LayerManager<PathEntry>`, `MarkerLayerManager extends LayerManager<MarkerEntry>`,
  `ShapeLayerManager extends LayerManager<ShapeEntry>`. Each manages entries (drawables/markers) within
  shared `VectorLayer`/`ItemizedLayer` instances, handles hit-testing, ZOMBIE diagnostics, and
  per-entry `scheduleUpdate()` coalescing.
- Top-level helpers: `ElevationReader`/`Utils` (elevation lookups from `.hgt` DEM files), `LayerHelper`/
  `LayerZoomBoundsHelper` (zoom-based layer visibility), `RenderThemeMenuLoader` (parses
  `<stylemenu>` from render-theme XML for `useRenderStyleOptions`), `FixedWindowRateLimiter` (throttles
  native map-event emission, used by `MapFragment`).
- `android/strip-vtm-classes.gradle` — reusable Gradle script that strips shadowed vtm classes
  at multiple stages to prevent "Type X is defined multiple times" errors: (1) patches the vtm
  JAR in the Gradle cache (`zip -d` with crash recovery + automatic post-build restore so the
  shared cache is never left broken), (2) at configuration time, excludes shadowed `.java`
  source files from the core library's compilation via Gradle's `SourceDirectorySet.exclude()`
  (non-destructive — no files are deleted from disk), and (3) at execution time, cleans stale
  `.class`/`.dex` build artifacts and the `runtime_library_classes_jar` directory from the core
  library's build tree before DEX merging. Applied via `apply from:` in the consuming app's
  `android/app/build.gradle`; extensions declare `ext.shadowedClasses` in their own
  `build.gradle`.

### ElevationReader

`ElevationReader` provides elevation lookups from `.hgt` DEM files. It maintains a two-level cache:
an in-memory `dataCache` (backed by `LruCache`) and a filename index (`fileIndex`, built once at
construction). Three executors/threads interact with it:

| Thread | Role |
|---|---|
| **Render thread** (vtm GL, 60fps) | `MapFragment.getResponseBase()` calls `getElevation(lng, lat, 100)` to include elevation in the `center` position array. Uses the **100ms debounced** overload — never blocks on I/O. |
| **`PRELOAD_EXECUTOR`** (4-thread fixed pool) | Handles immediate `preload()` calls (2.9MB `.hgt` reads). Used by the explicit JS `getAltitudeAtPosition` API (delay=0). |
| **`DELAYED_PRELOAD_EXECUTOR`** (single-thread scheduled) | Handles debounced preloads from the render hot-path. |

**Debounced preload (single-slot):** `schedulePreload()` holds at most one pending delayed preload.
When a **different** tile is requested the previous one is cancelled — only the tile the user lingers
on is ever loaded. Calls for the **same** tile leave the existing timer running so that continuous
panning within a tile doesn't perpetually reset the countdown.

**In-flight dedup:** `inFlightPreloads` prevents submitting duplicate `preload()` tasks for the same
filename to `PRELOAD_EXECUTOR`. Both the render hot-path and the JS API could previously queue
redundant reads for the same tile.

**`hasDataFor(lng, lat)`:** Returns true if the tile-index contains an entry for the given coordinate.
Fast, thread-safe, no disk I/O. Callers use this to skip retry loops when no amount of waiting would
produce an elevation (ocean, missing tile). Exposed to JS as `hasDataAtPosition` via the
`MapContainer` TurboModule and `useMap()` hook.

### JS-side elevation enrichment

`enrichCoordinatesWithElevation()` (`src/enrichCoordinates.ts`) batch-enriches coordinate arrays
with SRTM elevation data through a windowed three-phase flow:

1. **Trigger** — fire `getAltitudeAtPosition` preloads for all unique tiles in the current window
2. **Fence** — poll `isTileCached` until every tile is loaded (adaptive timeout); re-trigger
   preloads for stragglers
3. **Collect** — with all tiles cached, iterate every coordinate individually via
   `getAltitudeAtPosition` for full per‑coordinate bilinear interpolation; each call is a
   sub‑millisecond cache hit

Mutates the input array **in place** and returns it for chaining. Coordinates are grouped by
1°×1° SRTM tile for efficient I/O; after the fence proves every tile is cached, each coordinate
gets its own bilinearly‑interpolated elevation rather than a single tile‑representative value.

**`ElevationAPI` bridge interface** — the 4 methods consumers wire from `useMap()`:

| Method | Throws? | Purpose |
|---|---|---|
| `getAltitudeAtPosition(lng, lat)` | Catches → `null` | Get elevation; triggers background preload on cache miss |
| `hasDataAtPosition(lng, lat)` | Catches → `false` | Fast check for HGT file existence (no I/O) |
| `isTileCached?(lng, lat)` | Catches → `false` | Direct LRU‑cache check, never triggers preload, unambiguous vs. void pixels |
| `setCacheCapacity?(capacity)` | **Throws** | LRU cache resize — config command, must not fail silently |

**`EnrichCoordinatesOptions`:**
- `maxCacheCapacity` (default 50) — LRU cache tiles during enrichment (~2.9 MB/tile, ~145 MB at default)
- `keepCacheCapacity` — skip cache restoration in `finally` (opt‑in, for repeated enrichments)
- `onProgress` — fraction callback (0–1) after each window
- `signal` — `AbortSignal`; enrichment stops at the next window boundary

**Native additions** that support the feature:
- `isTileCached(lng, lat)` — direct `LruCache.get()` check, never triggers preload, unambiguous
  vs. void pixels
- `setCacheCapacity(int maxTiles)` — uses `LruCache.resize()`

**Threading update:** `PRELOAD_EXECUTOR` is now a **4‑thread fixed pool** (was single‑thread).
The ElevationReader code was already thread‑safe (synchronized blocks on `dataCache` and
`inFlightPreloads`).

### Threading model

All mutations to `mapView.map().layers()` (add, remove, reorder) **must** flow through
`MapMutationQueue.flush()` on the **UI thread** (Main Looper). Nothing else may call
`mapView.map().layers().add/remove` or `mapView.map().updateMap()` directly.

```
 Native Modules Thread (TurboModule)          UI Thread (Main Looper)
 ================================             =======================

 createLayer (async)  ──enqueue──>  ┌─────────────────────────────┐
 removeLayer (async)  ──enqueue──>  │  MapMutationQueue.flush()    │
 reorderLayers        ──enqueue──>  │  ─────────────────────────  │
                                    │  1. Remove stale layers      │
 animateTo()          ──dispatch──> │  2. Add new layers           │
 getPosition()        ──dispatch──> │  3. Reorder (LIS algorithm)  │
                                    │  4. updateMap() once         │
 scheduleUpdate()     ──post─────>  │  updateMap() coalesced       │
 (LayerManager +       (AtomicBool  │  (per-entry geometry changes)  │
  PathLayerManager +    + Handler)  └───────────────────────────────┘
  MarkerLayerManager +
  ShapeLayerManager)

 LayerManager.ensureSharedLayer() blocks with future.get()
 until the shared layer is placed on the UI thread — this is
 the ONLY cross-thread blocking wait. Everything else is
 fire-and-forget from the JS side.
```

**Key rules:**
- `MapMutationQueue.flush()` is the **only** place that calls `layers().add/remove` and the batch-level `updateMap()`. It runs on the UI thread, serialized with vtm's own rendering.
- `MapFragment.bindUpdateListener()` runs on the **render thread** (vtm's GL thread, 60fps). It writes position data to C++ `Synchronizable` primitives via `MapPositionWriter.nativeSetPosition()` (thread-safe mutex). See `android/src/main/cpp/MapPositionWriter.cpp`.
- `scheduleUpdate()` (in `LayerManager` and its per-type subclasses: `PathLayerManager`,
  `MarkerLayerManager`, `ShapeLayerManager`) coalesces per-entry `updateMap()` calls onto the UI
  thread via `AtomicBoolean` CAS + `Handler.post`. Use it instead of calling
  `mapView.map().updateMap()` directly.
- `MapContainer.animateTo()` / `getPosition()` dispatch to the UI thread via `UiThreadUtil.runOnUiThread` (vtm's `Animator` asserts the UI thread).
- `MapFragment.onDestroy()` must tear down all `LayerManager` instances (including per-type
  `PathLayerManager`, `MarkerLayerManager`, `ShapeLayerManager`) **before** `mapView.onDestroy()`,
  or shared layers silently leak.
- `LayerHelper.addLayerAsync` / `removeLayerAsync` are the preferred API — they enqueue into `MapMutationQueue`. The deprecated `addLayer`/`removeLayer` sync methods now delegate to the async path internally.

**What still runs on the native-modules thread (read-only / non-layers-mutating):**
- `LayerHelper.getLayer()` / `getLayers()` — reads from `MapMutationQueue.getKnownLayers()` (ConcurrentHashMap, safe from any thread)
- `LayerZoomBoundsHelper.removeUpdateListener()` — calls `mapView.map().events.unbind()` (event listener management, not layer mutation)
- Marker/Path/Shape entry creation — operates on already-registered shared `Layer` objects (adds drawables/markers to `VectorLayer`/`ItemizedLayer`), not on `map.layers()`

### ZOMBIE diagnostic system

A deliberate, systematic defensive pattern across both TypeScript and Java sides detects and prevents
"zombie" native resources — GPU objects (drawables, layers) that outlive their JS-side references,
typically from teardown races where the map is destroyed while async operations are in-flight.

**JS side:** `useNativeLayerLifecycle`'s `uuidRef` (see above) prevents zombies from being created by
ensuring the unmount cleanup always sees the real uuid. The `destroy()` method on `LayerOrderRegistry`
cancels pending debounced `reorderLayers` timers so they don't fire against a destroyed map.

**Java side:** All three per-type managers (`PathLayerManager`, `MarkerLayerManager`,
`ShapeLayerManager`) and their owning TurboModules (`LayerPath.java`, `LayerMarker.java`,
`LayerShape.java`) log `"ZOMBIE: ..."` warnings when they detect operations targeting an
already-destroyed shared layer or `MapFragment` instance. These are not errors (they're expected
during teardown) but serve as diagnostic markers for resource-leak investigations. When a zombie is
detected, the manager clears its drawable references so the GC can collect them, even though the
drawables remain in the `VectorLayer`'s QuadTree and will render as zombies until the shared layer
itself is destroyed.

### Reanimated sub-package (`react-native-mapsforge-vtm/reanimated`)

The library has a secondary entry point (defined in `package.json`'s `exports`) that provides
reanimated-based map utilities. Import from `react-native-mapsforge-vtm/reanimated`, not from the
main package:

| Export | Purpose |
|---|---|
| `useMapPosition()` | Creates reanimated shared values for map center, zoom, bearing, tilt. Returns `handleMapUpdate` callback to wire into `onMapUpdate`. With `activateNativeBridge(handle)` called, reads go direct from the render thread to C++ Synchronizable primitives — zero bridge crossings. |
| `useMapOverlay()` | Worklet-based overlay positioning — converts lat/lng to screen coordinates on the UI thread |
| `toScreenPosition()` / `fromScreenPosition()` | Mercator ↔ screen coordinate conversion functions callable from worklets |

`useMapPosition()` is optional (gated by `peerDependenciesMeta` on `react-native-reanimated`). The
native bridge activation flow: call `useMapPosition()`, pass the returned callback as
`MapContainer.onMapUpdate`, then call `activateNativeBridge(handle)` with the map's
`nativeNodeHandle`. The render thread then writes position data directly into C++ `Synchronizable`
primitives via `MapPositionWriter.nativeSetPosition()` — no JS bridge involved. See
`docs/advanced/performance.md` for the full setup.

### Map position consumption patterns

There are four tiers for reading the current map position (center, zoom, bearing, tilt) in JS,
ordered from simplest to most performant:

| Tier | API | Bridge crossings | React re-renders | When to use |
|---|---|---|---|---|
| **Callback** | `MapContainer.onMapUpdate` prop | ~25/sec (one-way native→JS) | ~25/sec | Debug overlays, one-shot reactions, anything that already calls `setState`. |
| **Shared values** | `useMapPosition()` from `react-native-mapsforge-vtm/reanimated` | ~25/sec (writes only) | 0 (worklet reads are UI-thread) | Smooth coordinate displays, overlay positioning. Requires `react-native-reanimated >= 3.0.0`. |
| **Shared values (native)** | `useMapPosition()` + `activateNativeBridge(handle)` | **0** (writes bypass the bridge entirely) | 0 | Zero-jitter overlay tracking at true 60fps. Native writes position data directly from the render thread into reanimated Synchronizable primitives. See `docs/advanced/performance.md`. |
| **Imperative** | `useMap().getPosition()` | 2 per call (round-trip JS→native→JS) | 0–1 per call | Button-triggered snapshots, non-continuous queries. |

**Callback vs shared values — they coexist.** `useMapPosition()` internally creates reanimated shared
values and returns a `handleMapUpdate` callback that you pass as the `onMapUpdate` prop. The bridge
event still fires at the same rate; the shared values receive the same writes. The win is that
worklet consumers (`useDerivedValue`, `useAnimatedStyle`, `useAnimatedProps`) read from shared values
on the UI thread — zero bridge crossings, zero React re-renders for reads.

**Native shared-value bridge.** When `activateNativeBridge(handle)` is called (via
`setNativeNodeHandle` + `useEffect` — see `docs/advanced/performance.md`), the render thread
(vtm's `Map.UpdateListener`) writes position data directly into C++-created `Synchronizable`
primitives via `MapPositionWriter.nativeSetPosition()`. A worklet poller on the UI thread
reads those primitives each frame (`getBlocking()` — direct C++ access, thread-safe mutex)
and updates standard `SharedValue` objects, triggering reanimated's normal dependency tracking.
The entire path: render thread → Synchronizable → worklet poller → SharedValue → consumer —
zero bridge crossings at every stage.

**The trailing-edge guarantee.** `MapFragment` fires events on every vtm frame with no
throttling. The `onMapUpdate` callback path is rate-limited by Fabric's event dispatch.
The native shared-value bridge bypasses this entirely — writes go directly from the
render thread to C++ primitives.
