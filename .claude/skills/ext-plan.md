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

Reference: the core library's extension docs at `docs/advanced/extending.md`
describe each pattern in detail and include Java patterns, threading rules,
and TurboModule spec conventions.

### Phase A — Scaffold with `create-react-native-library`

```sh
npx create-react-native-library --yes \
  --directory ~/Development/android/react-native-mapsforge-vtm-ext-<name> \
  --slug react-native-mapsforge-vtm-ext-<name> \
  --description "<one-line description>" \
  --author-name "jhotadhari" \
  --author-email "tellme@waterproof-webdesign.de" \
  --author-url "https://github.com/jhotadhari" \
  --repo-url "https://github.com/jhotadhari/react-native-mapsforge-vtm-ext-<name>" \
  --type fabric-view \
  --languages kotlin-objc \
  --example vanilla \
  --tools eslint,jest,lefthook \
  --local
```

This generates a complete library skeleton with:
- `package.json` (bob build, codegen config, workspaces, scripts)
- `tsconfig.json`, `tsconfig.build.json`
- `src/index.tsx` (stub), `src/NativeXxxViewNativeComponent.ts` (codegen spec)
- `android/build.gradle`, `android/gradle.properties`
- `android/src/main/AndroidManifest.xml` + `AndroidManifestNew.xml`
- `example/` — full vanilla React Native app (metro, babel, android/, tsconfig)
- `babel.config.js`, `react-native.config.js`, `turbo.json`
- `lefthook.yml`, `.gitignore`, `.editorconfig`, `LICENSE`, `README.md`
- `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`

### Phase B — Android-only cleanup + verbose Java namespace

1. **Remove iOS artifacts** (extension is Android-only):
   ```sh
   rm -rf ios/ *.podspec
   ```

2. **Rename Java namespace** from the flattened form that `create-react-native-library`
   derives to the verbose hierarchical form used by the library:
   - Generated: `com.mapsforgevtmext<name>` (all non-alphanumeric chars stripped)
   - Target: `com.jhotadhari.reactnative.mapsforge.vtm.ext.<name>`

   Files to update:
   - `package.json`: `codegenConfig.android.javaPackageName`
   - `android/build.gradle`: `namespace` and `react.codegenJavaPackageName`
   - Every `.java` file: `package` declaration
   - Directory layout under `android/src/main/java/`: move files to the hierarchical path
   - `android/build.gradle`: `react.libraryName` (use CamelCase: `MapsforgeVtmExt<Name>View`)

### Phase C — Project-standard overrides

The scaffold generates a default config. Override with the project-standard tooling:

| Replace | With | Source |
|---|---|---|
| Inline eslint config in `package.json` + `.eslintrc` | `eslint.config.mjs` (flat config) | Copy from core library |
| Inline prettier config in `package.json` | `.prettierrc` + `.prettierignore` | Copy from core library |
| Generated `.gitignore` | Core library's `.gitignore` | Copy from core library, adapt |
| `devDependencies.release-it` + `release-it` config block | `devDependencies.@jhotadhari/release-kit` + `scripts.release: "release-kit"` | npm install |
| Default `tsconfig.json` paths | Update paths, remove iOS/example references if Android-only | Manual edit |

### Phase D — Example app setup

1. Add `react-native-mapsforge-vtm` to `example/package.json` dependencies
   (the extension's components import from it, and `MapContainer` is needed for any
   meaningful example).

2. During development, `yalc`-link the core library:
   ```sh
   cd example && yalc link react-native-mapsforge-vtm
   ```

3. Replace `example/src/App.tsx` with a minimal example that imports:
   ```tsx
   import { MapContainer, LayerMapsforge } from 'react-native-mapsforge-vtm';
   import { LayerXxx } from 'react-native-mapsforge-vtm-ext-<name>';
   ```

### Phase E — Documentation and commit

1. Write the plan to `/home/jhotadhari/.claude/plans/<slug>.md`
2. Init CLAUDE.md via `/init`
3. Create ROADMAP.md with phased implementation plan
4. `git add -A && git commit -m "Initial scaffold"` on `main`
5. `git checkout -b development` for ongoing work

### Phase F — Add vtm dependencies to `android/build.gradle`

The template's `build.gradle` only depends on `react-android` + `kotlin-stdlib`.
Add the vtm dependencies (same versions as the core library):

```groovy
dependencies {
  // ...existing template deps...

  // react-native-mapsforge-vtm (peer — provides vtm transitively)
  implementation project(':react-native-mapsforge-vtm')

  // vtm — keep versions in sync with core library
  implementation 'com.github.mapsforge.vtm:vtm:0.28.0'
  implementation 'com.github.mapsforge.vtm:vtm-jts:0.28.0'
  implementation 'org.locationtech.jts:jts-core:1.20.0'
}
```
