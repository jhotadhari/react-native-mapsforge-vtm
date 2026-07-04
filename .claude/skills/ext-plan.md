# /ext-plan

Interactive planning command for creating new `react-native-mapsforge-vtm` extensions.
Walks through architectural decisions and generates a plan file plus scaffolded repo.

## When to use

Invoke when the user wants to create a new extension for `react-native-mapsforge-vtm`.
Examples: "I want to build a heatmap overlay", "create an extension for animated markers",
"/ext-plan".

## What it does

1. Asks the key architectural questions (below)
2. Determines the pattern: JS-only, TurboModule, or vtm-shadowing
3. Identifies any core library extensibility hooks needed
4. Writes a plan file and scaffolds the repo skeleton

## Decision flow

Ask these questions in order. Use the answers to select the pattern.

### Q1: What does the extension render or compute?

"Describe what your extension does in one sentence."
(Examples: "slope-colored paths", "heatmap overlay from point data", "animated bouncing markers")

### Q2: Does it need native rendering changes?

- **NO — JS-only extension**
  - Only hooks, utilities, calculations on the JS side
  - No custom native module or TurboModule
  - Pattern: `src/hooks/`, `src/utils/`, no `android/` dir needed beyond build.gradle
  - Example: a `useSlopeColoring` hook that returns colors for use with existing `LayerPath`
  - Files needed: package.json, tsconfig, src/index.tsx, src/hooks/, src/utils/

- **YES, but reuses existing vtm rendering**
  - Creates a new TurboModule that extends an existing LayerManager
  - No vtm class shadowing needed
  - Pattern: TurboModule spec + native module + React component
  - Example: a custom marker type that extends `ItemizedLayer`
  - Files needed: all JS-only files + NativeXxx.ts spec + Xxx.java module + component

- **YES, needs new GPU rendering**
  - Requires vtm class shadowing (modified copies of vtm JAR classes)
  - Custom GLSL shaders in `android/src/main/assets/shaders/`
  - Pattern: TurboModule + vtm shadow classes + GLSL shaders
  - Example: path-color-ramp (shadows LineBucket, RenderBuckets, adds shaders)
  - Files needed: all TurboModule files + shadowed .java + .glsl shaders

### Q3: What vtm classes need shadowing? (only if "new GPU rendering")

Based on the feature description, identify which vtm pipeline classes need modification:
- **Line rendering** → `LineBucket.java`, `RenderBuckets.java`, shader `.glsl` files
- **Polygon/fill rendering** → `PolygonBucket.java`, `RenderBuckets.java`
- **Texture rendering** → `TextureBucket.java`, `TextureItem.java`
- **Marker/item rendering** → `ItemizedLayer.java`, `MarkerRenderer.java`
- **Map view itself** → `MapView.java` (rare — prefer CustomRenderLayer)

Also identify what changes in each:
- New vertex attributes? (needs `VERTEX_CNT` change in `RenderBuckets.java`)
- New shader uniforms? (needs `Shader` inner class modification)
- New draw method? (needs `Renderer` inner class modification)

### Q4: Core library extensibility hooks needed?

Check if the core library needs API openings for this extension:
- **Extending PathLayerManager?** → Needs `drawSegments()` + `getStyleBuilder()` `protected` (DONE in Phase 1)
- **Custom LayerManager?** → Needs `createPathLayerManager()` factory in `LayerPath.java` (DONE in Phase 1)
- **Hooking into map lifecycle?** → Needs event emission from `MapFragment.java`
- **Custom map rendering?** → `CustomRenderLayer` already available in vtm — no core hooks needed
- **New gesture handling?** → `GestureLayer` already available — no core hooks needed

If core hooks are needed and not yet implemented, add them to the plan as a prerequisite phase.

### Q5: Data flow pattern?

- **One-way (JS → native):** JS sends data, native renders. Simple `createLayer`/`updateXxx` pattern.
- **Bidirectional (JS ↔ native):** Native sends events back (gestures, lifecycle). Needs `EventEmitter` in spec.
- **High-frequency (60fps):** Reanimated shared values, worklet integration. May need `reanimated/` export.

### Q6: Bridge payload estimate

Estimate bridge payload for typical usage:
```
coordinates: N points × (2-3 numbers) × 8 bytes ≈ 16N-24N bytes
segmentValues: N × 8 bytes
colorRampStops: K stops × 7 bytes (hex) ≈ 7K bytes
```
Flag if payload exceeds ~10 KB per frame at a reasonable N (warn about bridge saturation).

### Q7: Naming

Suggest a name following the pattern: `react-native-mapsforge-vtm-ext-<feature>`
- Keep it short and descriptive
- Check npm availability (optional)

## Pattern reference

### Pattern A: JS-only (e.g., a calculation hook)

```
ext-<name>/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.tsx
│   ├── hooks/
│   └── utils/
└── (no android/ dir — no native code)
```

Core library hooks needed: typically none.

### Pattern B: TurboModule (e.g., a new layer type building on existing vtm rendering)

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
│   └── src/main/java/.../modules/LayerXxx.java
└── (no vtm shadow classes)
```

Core library hooks needed: factory method in existing TurboModule if extending a LayerManager.

### Pattern C: vtm-shadowing (e.g., color-ramp paths with custom shaders)

```
ext-<name>/
├── (all Pattern B files)
├── android/src/main/
│   ├── assets/shaders/
│   │   └── custom_shader.glsl
│   └── java/
│       ├── org/oscim/renderer/bucket/
│       │   └── ShadowedBucket.java    ← shadows vtm JAR class
│       └── com/.../ext/<name>/
│           ├── CustomVectorLayer.java
│           ├── CustomLayerManager.java
│           └── modules/LayerXxx.java
```

Core library hooks needed: depends on which LayerManager is extended. Phase 1 hooks cover PathLayerManager.

## After planning

1. Write the plan to `/home/jhotadhari/.claude/plans/<slug>.md`
2. Scaffold the repo at `~/Development/android/react-native-mapsforge-vtm-ext-<name>/`
3. Copy configs from the core library or ext-template
4. Install release-kit from npm (`@jhotadhari/release-kit@^0.0.6`)
5. Init CLAUDE.md via `/init`
6. Create ROADMAP.md with phased implementation plan
7. Commit on `development` branch

## Template repo

If the user wants a faster start, suggest copying from `react-native-mapsforge-vtm-ext-path-color-ramp`
(or a future `ext-template` repo) which already has:
- bob builder, prettier, eslint, lefthook, release-kit configured
- Android build.gradle with vtm dependencies
- Stub TurboModule spec + component
- JS utility patterns (hooks, color utils, metric calculators)
