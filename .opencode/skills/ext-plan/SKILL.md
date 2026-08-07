---
name: ext-plan
description: This skill should be used when the user wants to create a new extension for react-native-mapsforge-vtm. Trigger phrases include "build an extension", "create a heatmap overlay", "extend the map", "I want to build a...", "create an extension for", "use ext-plan". Walks through architectural decisions (JS-only, TurboModule, or vtm-shadowing patterns) and generates a plan file plus scaffolded repo.
---

# ext-plan

This is an **OpenCode skill** (not a CLI command). Mention `ext-plan` in an OpenCode chat
prompt to invoke it — for example: "use the ext-plan skill" or "I want to build an extension."
The skill walks through architectural decisions and generates a plan file plus scaffolded repo.

## When to use

Invoke when the user wants to create a new extension for `react-native-mapsforge-vtm`.
Examples: "I want to build a heatmap overlay", "create an extension for animated markers",
"use ext-plan".

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
- **Extending PathLayerManager?** → Needs `drawSegments()` + `getStyleBuilder()` `protected` (available)
- **Custom LayerManager?** → Needs `createPathLayerManager()` factory in `LayerPath.java` (available)
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

**Extension `android/build.gradle` must declare shadowed classes:**

```groovy
ext.shadowedClasses = [
    'org.oscim.renderer.bucket.LineBucket',
    'org.oscim.renderer.bucket.RenderBuckets',
]
```

**App's `android/app/build.gradle` must apply the stripping script:**

```groovy
apply from: "${projectDir}/../../node_modules/react-native-mapsforge-vtm/android/strip-vtm-classes.gradle"
```

Without this, the shadowed classes and the core library's copies collide at DEX
merge time with "Type X is defined multiple times" errors.

Core library hooks needed: depends on which LayerManager is extended. The core library currently exposes createPathLayerManager() (in LayerPath) and drawSegments() / getStyleBuilder() (in PathLayerManager), all protected, for PathLayerManager extensions.

## After planning

Reference: the core library's extension docs at `docs/advanced/extending.md`
describe each pattern in detail and include Java patterns, threading rules,
and TurboModule spec conventions.

### Phase A — Scaffold with `create-react-native-library`

**Before scaffolding, detect author info:**

1. Run `git config user.name` (falls back to `whoami`) → `<author-name>`
2. Run `git config user.email` → `<author-email>`
3. Run `git remote get-url origin` (from core library repo) to extract the GitHub org/user → `<github-org>`

```sh
npx create-react-native-library --yes \
  --directory ~/Development/android/react-native-mapsforge-vtm-ext-<name> \
  --slug react-native-mapsforge-vtm-ext-<name> \
  --description "<one-line description>" \
  --author-name "<author-name>" \
  --author-email "<author-email>" \
  --author-url "https://github.com/<github-org>" \
  --repo-url "https://github.com/<github-org>/react-native-mapsforge-vtm-ext-<name>" \
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
   derives to the verbose hierarchical form used by the library.

   **Ask the user for the base Java namespace** (using `AskUserQuestion`):

   > "What Java namespace should the extension use?"
   >
   > **Suggested default** (derived from the username detected in Phase A):
   > `com.<github-org>.reactnative.mapsforge.vtm.ext.<name>`
   >
   > The user can accept the default by hitting Enter, or type a custom namespace.
   > Store as `<java-namespace>`.

   - Generated by scaffold: `com.mapsforgevtmext<name>` (all non-alphanumeric chars stripped)
   - Target: the user-confirmed `<java-namespace>`
   - Also derive the example app's namespace: `<java-namespace>.example`

   Files to update:
   - `package.json`: `codegenConfig.android.javaPackageName` → `<java-namespace>`
   - `android/build.gradle`: `namespace` and `react.codegenJavaPackageName` → `<java-namespace>`
   - Every `.java` file: `package` declaration → `<java-namespace>`
   - Directory layout under `android/src/main/java/`: move files to the hierarchical path
   - `android/build.gradle`: `react.libraryName` (use CamelCase: `MapsforgeVtmExt<Name>View`)
   - `example/android/app/build.gradle`: `namespace` + `applicationId` → `<java-namespace>.example`
   - `example/.../MainActivity.kt` + `MainApplication.kt`: `package` → `<java-namespace>.example`
   - Directory layout under `example/android/app/src/main/java/`: move to hierarchical path

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

#### D1. Library linking strategy

Three libraries must resolve in the example app at development time:

| Library | Preferred method | `example/package.json` entry |
|---|---|---|
| `react-native-mapsforge-vtm` (core) | yalc | `"file:.yalc/react-native-mapsforge-vtm"` |
| This extension | `workspace:*` (symlink) | `"workspace:*"` |

**Why yalc for the core library but workspace for the extension:**
- yalc copies files into `example/node_modules/.yalc/` — no symlink resolution issues.
- The extension IS the workspace root, so `example/` is a workspace member and `workspace:*` resolves natively. But see Phase G for the duplicate-React problem this creates and its fix.
- yalc for the extension is also valid and avoids the duplicate-React problem entirely. If you choose yalc for the extension too, skip the postinstall script in Phase G.

**Yalc setup commands:**
```sh
# In the core library repo:
yalc publish

# In the extension's example/ directory:
cd example && yalc link react-native-mapsforge-vtm
```

If the extension uses yalc for itself (instead of `workspace:*`):
```sh
# In the extension repo root:
yalc publish
cd example && yalc link react-native-mapsforge-vtm-ext-<name>
```

**Yarn 3 `nmHoistingLimits` workaround:** If `example/package.json` has `"workspace:*"` but Yarn 3 with `nmHoistingLimits: workspaces` is not materializing the symlink, manually create it:
```sh
ln -sf ../.. example/node_modules/react-native-mapsforge-vtm-ext-<name>
```

#### D2. Metro config — watchFolders

Create or update `example/metro.config.js` so file changes in the extension root and the
parent library's dev directory trigger Metro rebundles:

```js
const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const parentRoot = path.resolve(__dirname, '../../react-native-mapsforge-vtm');

const config = getDefaultConfig(__dirname);

module.exports = {
  ...config,
  watchFolders: [...(config.watchFolders ?? []), root, parentRoot],
};
```

If the parent library is not at `../../react-native-mapsforge-vtm` (e.g., it was cloned to
a different path), adjust `parentRoot` accordingly. Remove the `parentRoot` entry if you are
not actively developing the core library alongside the extension.

#### D3. example/tsconfig.json

The scaffold may or may not generate a `tsconfig.json` in `example/`. Create or update it:

```json
{
  "extends": "./node_modules/@react-native/typescript-config/tsconfig.json",
  "compilerOptions": {
    "jsx": "react-jsx",
    "paths": {
      "react-native-mapsforge-vtm-ext-<name>": ["../src/index.ts"]
    }
  },
  "include": ["src", "../src"]
}
```

Key points:
- `"jsx": "react-jsx"` — React Native 0.86 uses the automatic JSX runtime. The base config defaults to `"react-native"` which expects explicit `import React`.
- `paths` — Maps the extension's package name to its source entry point so TypeScript resolves imports during development without needing `yarn prepare` after every change.
- `include` — Both `src` (example app) and `../src` (extension source) so IDE support and type-checking cover both.
- Use the relative path `./node_modules/@react-native/typescript-config/tsconfig.json` for `extends` — the bare package name may not resolve in the IDE depending on workspace setup and `moduleResolution`.

#### D4. Replace example/src/App.tsx

Replace with a minimal example that imports:
```tsx
import { MapContainer, LayerMapsforge } from 'react-native-mapsforge-vtm';
import { LayerXxx } from 'react-native-mapsforge-vtm-ext-<name>';
```

The example must render enough to verify the extension loads and creates its native
resources. At minimum: a `<MapContainer>` with a mapsforge tile source and the
extension's component as a child.

### Phase E — CMakeLists.txt + react-native.config.js + autolinking

The Android autolinking system requires a CMakeLists.txt at `android/CMakeLists.txt`
that pulls in the codegen-generated TurboModule JNI. Without it, native method
dispatch fails at runtime with "couldn't find DSO to load" errors.

#### E1. Create android/CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.16)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/generated/jni/"
                 ext<name>_codegen_build)
```

The second argument (`ext<name>_codegen_build`) must be unique per library to avoid
CMake target-name collisions when multiple vtm extensions are linked into the same
app. Use the extension's short name in lowercase (e.g., `extgrib_codegen_build`).

#### E2. Update react-native.config.js

Ensure `react-native.config.js` declares `cmakeListsPath` and the package registration
for autolinking. The scaffold generates a skeleton — verify it has these fields:

```js
module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        cmakeListsPath: 'CMakeLists.txt',
        packageImportPath: 'import <java-namespace>.<CamelCaseName>Package;',
        packageInstance: 'new <CamelCaseName>Package()',
      },
    },
  },
};
```

Without `cmakeListsPath`, the CLI generates a fallback path
(`android/build/generated/source/codegen/jni/CMakeLists.txt`) that does not match
where bob build outputs codegen (`android/generated/jni/`).

#### E3. Delete autolinking cache

After creating `CMakeLists.txt` and updating `react-native.config.js`, delete the
cached autolinking metadata so the next Gradle sync regenerates it:

```sh
rm -rf example/android/build/generated/autolinking
```

Gradle's `generateAutolinkingNewArchitectureFiles` task re-runs on the next build
and generates the correct `add_subdirectory()` calls. Without this step, old
autolinking config may point to a non-existent or incorrect CMakeLists path.

### Phase F — Add vtm dependencies to `android/build.gradle`

The template's `dependencies` block only has `react-android` + `kotlin-stdlib`.
Add the vtm dependencies, reading versions from the parent library's
`android/build.gradle` to keep them in sync:

```groovy
dependencies {
  // ...existing template deps (react-android, kotlin-stdlib)...

  // Parent library — compile against but do NOT bundle.
  // The consuming app provides these classes at runtime.
  // Using "implementation" here causes duplicate-class errors.
  compileOnly project(':react-native-mapsforge-vtm')

  // vtm — same versions as the parent library (android/build.gradle).
  // Declared here because compileOnly does not pull transitive deps.
  implementation 'com.github.mapsforge.vtm:vtm:0.29.0'
  implementation 'com.github.mapsforge.vtm:vtm-android:0.29.0'
  runtimeOnly 'com.github.mapsforge.vtm:vtm-android:0.29.0:natives-armeabi-v7a'
  runtimeOnly 'com.github.mapsforge.vtm:vtm-android:0.29.0:natives-arm64-v8a'
  runtimeOnly 'com.github.mapsforge.vtm:vtm-android:0.29.0:natives-x86'
  runtimeOnly 'com.github.mapsforge.vtm:vtm-android:0.29.0:natives-x86_64'

  // Conditional: only if the extension uses JTS geometry classes.
  // The parent library includes these; add them if the extension
  // directly references JTS types.
  implementation 'com.github.mapsforge.vtm:vtm-jts:0.29.0'
  implementation 'org.locationtech.jts:jts-core:1.20.0'
}
```

**Why `compileOnly` and not `implementation`:** The extension's Java code imports
classes from `react-native-mapsforge-vtm` (`Utils`, `LayerHelper`, `LayerZoomBoundsHelper`,
etc.). These imports must resolve at compile time. However, the actual class files
are provided by the consuming app's dependency on the core library. If the extension
bundles them (via `implementation`), the app ends up with two copies of the same
classes on its classpath, causing `Duplicate class` build errors.

**Why vtm-android + natives:** The extension compiles against vtm's Android-specific
classes (`MapView`, `Layer`, `GLViewport`, etc.). These aren't pulled transitively
via `compileOnly project(':react-native-mapsforge-vtm')`, so the extension must
declare them directly. The `runtimeOnly` native libraries ensure the vtm native
`.so` files are available at runtime on all architectures.

### Phase G — Fix duplicate React

This is the hardest problem in the extension scaffold. It manifests as
`"Cannot read property 'useContext' of null"` or similar hooks errors at runtime,
even though all `react`/`react-native` versions are identical.

#### The cause

When a workspace library is symlinked (e.g., the extension via `workspace:*`),
Metro's module resolver follows the symlink's real path. Inside the extension's
source, `import { useContext } from 'react'` resolves to
`<extension-root>/node_modules/react` instead of `example/node_modules/react`.
Same version, different module instance object. React hooks rely on module-level
singleton state — a different instance returns `null` from `useContext`.

This only happens with symlinks. yalc copies (which produce `file:.yalc/...`
entries) resolve within `example/node_modules/` and do not trigger the problem.

#### Solution A: yalc for both libraries (preferred, simplest)

Use yalc for the extension too instead of `workspace:*`. In `example/package.json`:
```json
"react-native-mapsforge-vtm-ext-<name>": "file:.yalc/react-native-mapsforge-vtm-ext-<name>"
```
Then run `yalc publish && cd example && yalc link react-native-mapsforge-vtm-ext-<name>`
after every source change. For rapid iteration, pair this with `yalc push` (watches
and auto-pushes). No symlinks means no duplicate React.

#### Solution B: postinstall script (when you must use workspace:*)

If you need `workspace:*` (e.g., for monorepo tooling that expects it), add a
`postinstall` script to the extension's root `package.json` that replaces
root-level react/react-native/react-native-mapsforge-vtm with symlinks to the
example app's copies:

```json
"scripts": {
  "postinstall": "node -e \"['react','react-native','react-native-mapsforge-vtm'].forEach(m=>{const t='node_modules/'+m,s='example/'+t;require('fs').existsSync(s)&&!require('fs').lstatSync(t).isSymbolicLink()&&(require('fs').rmSync(t,{recursive:true,force:true}),require('fs').symlinkSync(require('path').relative(require('path').dirname(t),require('path').resolve(s)),t,'dir'))})\""
}
```

This runs after every `yarn install`. It creates symlinks from root-level
`node_modules/react` → `example/node_modules/react` (and same for react-native
and react-native-mapsforge-vtm). Metro then resolves these to the example app's
single instance regardless of which real path it follows.

**What does NOT work:**
- `extraNodeModules` alone (without `blockList`) — Metro's walk-up resolution finds the root's copy first.
- `resolveRequest` returning `{ type: 'sourceFile', filePath }` — causes SHA-1 errors ("file is not watched").
- Deleting react/react-native from root `node_modules` — `yarn install` restores them.

### Phase H — Verify `types` field after `yarn prepare`

The scaffold generates a `types` field in `package.json` pointing to the TypeScript
declaration output. However, bob build may output declarations under a different
subdirectory than the default template expects.

After running `yarn prepare` once, verify:

```sh
ls lib/typescript/module/src/index.d.ts || echo "module types MISSING"
ls lib/typescript/commonjs/src/index.d.ts || echo "commonjs types MISSING"
```

Update the top-level `types` field to point to an existing file:
```json
"types": "./lib/typescript/commonjs/src/index.d.ts",
```

The `exports` map already has per-format `types` conditions, but the top-level
field is a fallback for tooling that does not understand `exports` (older TypeScript,
some IDE configurations). Without it, consumers see "Could not find a declaration
file" errors.

### Phase I — Documentation and commit

1. Write the plan to `./plans/<slug>.md`
2. Init AGENTS.md via `/init`
3. Create ROADMAP.md with phased implementation plan
4. `git add -A && git commit -m "Initial scaffold"` on `main`
5. `git checkout -b development` for ongoing work

**Data URL note:** If the example app fetches data from a dev server during
development, use `10.0.2.2` for the Android emulator (maps to host localhost)
but a LAN IP (e.g., `192.168.x.x`) for physical devices. Document both in a
comment in the example App.tsx.

## Post-scaffold verification checklist

Run through this checklist after completing all phases. Every item should pass
before the scaffold is considered done.

- [ ] `package.json` `types` field points to an existing file (Phase H)
- [ ] `android/build.gradle` uses `compileOnly project(':react-native-mapsforge-vtm')` (Phase F)
- [ ] `android/build.gradle` includes `vtm-android` + `runtimeOnly` natives (Phase F)
- [ ] `android/CMakeLists.txt` exists and delegates to `generated/jni/` (Phase E1)
- [ ] `react-native.config.js` declares `cmakeListsPath: 'CMakeLists.txt'` (Phase E2)
- [ ] `example/metro.config.js` has `watchFolders` with extension root + parent dev dir (Phase D2)
- [ ] `example/tsconfig.json` has `react-jsx`, `paths`, and `include` both src dirs (Phase D3)
- [ ] Libraries linked in `example/package.json`: core via yalc, extension via yalc or workspace (Phase D1)
- [ ] No duplicate React: yalc everywhere OR `postinstall` script present (Phase G)
- [ ] Autolinking cache deleted: `rm -rf example/android/build/generated/autolinking` (Phase E3)
- [ ] `yarn prepare` (bob build) completes without errors
- [ ] Data URLs use correct host (`10.0.2.2` for emulator, LAN IP for physical device) (Phase I note)

## Troubleshooting

### "Cannot read property 'useContext' of null" at runtime

Duplicate React. See Phase G. The extension's `import` from `'react'` resolved to a different module instance than the example app's. Solution: yalc for both libraries, or add the postinstall script.

### "couldn't find DSO to load" / libreact_codegen_*.so not found

Autolinking didn't find the codegen JNI. Verify:
1. `android/CMakeLists.txt` exists at the extension root (Phase E1)
2. `react-native.config.js` has `cmakeListsPath: 'CMakeLists.txt'` (Phase E2)
3. `rm -rf example/android/build/generated/autolinking` then rebuild (Phase E3)

### "Type X is defined multiple times" (DEX merge failure)

The extension shadows vtm classes but the app doesn't apply the stripping script. Two fixes needed:

1. Extension's `android/build.gradle` must declare `ext.shadowedClasses` (see Pattern C above).
2. App's `android/app/build.gradle` must apply the stripping script:
   ```groovy
   apply from: "${projectDir}/../../node_modules/react-native-mapsforge-vtm/android/strip-vtm-classes.gradle"
   ```

### Duplicate class errors at build time (non-shadowed)

`android/build.gradle` uses `implementation project(':react-native-mapsforge-vtm')` instead of `compileOnly`. Fix per Phase F.

### "Could not find a declaration file" for the extension package

The top-level `types` field in `package.json` points to a non-existent file. Run `yarn prepare` and verify the output path per Phase H.

### Metro does not detect file changes

`watchFolders` is missing or wrong. Verify `example/metro.config.js` per Phase D2. Note that adding `watchFolders` requires a Metro restart.

### TypeScript errors in example app about missing imports

`example/tsconfig.json` is missing `paths` mapping or `include` for `../src`. Fix per Phase D3.

### Module not found: react-native-mapsforge-vtm in example app

The core library is not linked. Run `cd example && yalc link react-native-mapsforge-vtm` (publish from the core library repo first). Verify `example/package.json` has `"file:.yalc/react-native-mapsforge-vtm"`.

### Yarn 3 workspace symlink not materialized

`nmHoistingLimits: workspaces` may prevent the symlink. Manually create it per the workaround in Phase D1.
