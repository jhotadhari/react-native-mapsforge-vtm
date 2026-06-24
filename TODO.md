# TODO

## 1. Re-implement `CanvasAdapterModule` (carried over from the New Architecture rewrite)

The old bridge module `CanvasAdapterModule` (`setTextScale`/`setLineScale`/`setSymbolScale` — global
vtm rendering-scale controls) has no New Architecture / JS-facing equivalent yet. `CanvasAdapter`
itself is still used internally (`LayerMarker.java`, `LayerScalebar.java`), so the native hook point
exists — what's missing is a public TurboModule exposing it to JS again, e.g. a new
`NativeCanvasAdapter.ts` + `android/.../modules/CanvasAdapter.java`. This is wanted back, not a
permanent removal — see `MIGRATION.md`. Not started.

## 2. Dependency upgrade plan (post-rewrite version bump)

Drafted 2026-06-23, right after the New Architecture rewrite landed (commit `c9a6ace`). Not started
yet. Follow these steps in order — each one should land as its own commit/PR so a regression is easy
to bisect, given how much native surface (NDK/vtm JNI) this touches.

### Step 1: `react-native` + `react-native-builder-bob` to latest

- Bump `react-native` and `react-native-builder-bob` (root `package.json` devDependencies, plus
  `react`/`@types/react` as required by the new RN version) to their latest stable releases.
- Bump `example/package.json`'s `react`/`react-native`/`@types/react` to match.
- Make any code changes the new RN version requires (check its own changelog/upgrade-helper diff).
  Re-run `yarn typecheck && yarn lint` and a clean `./gradlew :app:assembleDebug` after.
- Watch for: Gradle/AGP/Kotlin/NDK/compileSdk minimums bumped by the new RN version's template —
  this repo currently pins Gradle 8.12, AGP 8.7.2, Kotlin 2.0.21, NDK 27.1.12297006, compileSdk 35
  (both `android/gradle.properties` and `example/android/gradle.properties` — keep them in sync,
  see CLAUDE.md's "Versions/config that must stay in sync" section).

### Step 2: other devDependencies in `package.json`

- Go through every remaining devDependency in root `package.json` (eslint, `@eslint/*`,
  `@react-native/eslint-config`, `typescript`, `prettier` + its plugins, `del-cli`, `turbo`) and
  bump to latest. `@evilmartians/lefthook` and `keep-a-changelog` are this repo's own (not from the
  rewrite) — bump those too while here.
- **`@react-native-community/cli` is blocked here, not part of this step.** Confirmed 2026-06-24:
  the CLI's own README ships a compatibility table gating major CLI versions to specific RN ranges
  (`^15.0.0` → RN `^0.76.0`–`^0.78.0`, `^18.0.0` → RN `^0.79.0`, `^19.0.0` → RN `^0.80.0`,
  `^20.0.0` → RN `^0.81.0`–`^0.85.0`). This repo's RN is `0.78.2`, squarely in the `^15.0.0` row —
  bumping past that (tried `20.1.3`) reproducibly crashes `react-native start`
  (`Cannot read properties of undefined (reading 'handle')` in
  `@react-native-community/cli`'s `runServer.js`). Re-attempt this bump as part of Step 1 (the RN
  version bump itself), not Step 2.
- Re-run `yarn typecheck && yarn lint` after each batch; fix anything the version bump surfaces
  before moving on (new ESLint rules turning on, stricter TS, etc.).
- **`typescript` was bumped to `^6.0.3`** (2026-06-24) — TS 6 changed `types` to default to an empty
  array instead of auto-including everything under `node_modules/@types`; added
  `"types": ["jest"]` to `tsconfig.json` to keep `it`/`describe`/etc. resolving in
  `src/__tests__/index.test.tsx`.
- **`jest`/`@types/jest` are blocked, NOT bumped here.** Confirmed 2026-06-24: `react-native@0.78.2`
  itself depends on `jest-environment-node: ^29.6.3` (used by its own jest-preset). Jest 30's
  `jest-runtime` calls `jest-mock`'s `clearMocksOnScope`, which doesn't exist on the 29.x
  `jest-mock` that comes bundled with that pinned `jest-environment-node` — so `yarn test` fails
  immediately with `TypeError: this._moduleMocker.clearMocksOnScope is not a function` as soon as
  jest itself is bumped to 30.x, regardless of `@types/jest`. Re-attempt alongside the RN version
  bump (Step 1), once `react-native`'s own `jest-environment-node` pin has moved past 29.x.

**Blocked: `eslint` / `@eslint/js` stuck on 9.x, not 10.x.** Tried bumping to `eslint@10.5.0` +
`@eslint/js@10.0.1` (alongside `@react-native/eslint-config@0.86.0`, `@eslint/compat@2.1.0`,
`@eslint/eslintrc@3.3.5`, `eslint-config-prettier@10.1.8`, `eslint-plugin-prettier@5.5.6` — those six
landed fine). `yarn lint` hard-crashes under ESLint 10 with `TypeError: scopeManager.addGlobals is not
a function` in `SourceCode.finalize`. Root cause: ESLint 10 requires `ScopeManager` implementations to
implement `addGlobals()` (see the v10 migration guide), and `@react-native/eslint-config`'s pinned
`@typescript-eslint/parser`/`@typescript-eslint/scope-manager@^8.36.0` doesn't implement it yet. This
isn't specific to `0.86.0` — every published `@react-native/eslint-config` version up to and including
latest (`0.86.0`) pins the same `@typescript-eslint/*@^8.36.0`, and `@react-native/eslint-config@0.86.0`
itself also declares `peerDependencies.eslint: "^8.0.0 || ^9.0.0"` (no `10` yet) — so this is purely an
upstream gap (`@react-native/eslint-config` → `@typescript-eslint`), not something fixable by editing
this repo's `eslint.config.mjs`. Left `eslint` and `@eslint/js` at `^9.22.0` (was already there) for
now; re-attempt once either `@react-native/eslint-config` or `@typescript-eslint` ships ESLint
10 support.

### Step 3: vtm/mapsforge native dependencies in `android/build.gradle` (the big one)

Current versions (as of the rewrite landing):

```
com.github.mapsforge.vtm:vtm:0.25.0
com.github.mapsforge.vtm:vtm-themes:0.25.0
com.github.mapsforge.vtm:vtm-android:0.25.0
com.github.mapsforge.vtm:vtm-jts:0.25.0
com.github.mapsforge.vtm:vtm-http:0.25.0
com.github.mapsforge.vtm:vtm-android-mvt:0.25.0
com.github.mapsforge.vtm:vtm-mvt:0.25.0
com.github.mapsforge.vtm:vtm-hillshading:0.25.0
org.mapsforge:mapsforge-core:0.25.0
org.mapsforge:mapsforge-map:0.25.0
org.mapsforge:mapsforge-map-android:0.24.1   # already inconsistent with the others, check why
```

- Read the vtm changelog before touching version numbers:
  https://github.com/mapsforge/vtm/blob/master/docs/Changelog.md — confirm what the actual latest
  release is (the user's working assumption going into this was `0.28.0`, but verify against the
  changelog/repo tags rather than assuming that's still current by the time this gets picked up).
- Bump all `com.github.mapsforge.vtm:*` and `org.mapsforge:*` artifacts together to the same
  confirmed latest version (don't leave `mapsforge-map-android` lagging like it currently is).
- While reading the changelog, specifically look for: new built-in features that could replace
  custom code in this repo's `android/src/main/java/.../layer/` (`ItemizedLayer`, `PathLayer`,
  `VectorLayer`) or the helper classes (`LayerHelper`, `LayerZoomBoundsHelper`,
  `RenderThemeMenuLoader`, `HgtReader`) — if a later vtm version absorbed something this repo
  hand-rolled, prefer deleting the custom code over keeping both.
- This is native/JNI surface (`libvtm-jni.so`) — after bumping, do a full clean
  (`yarn clean && cd example/android && ./gradlew clean`) rebuild, not an incremental one, and
  re-verify on the emulator (use the `android-example-verifier` agent) across multiple examples,
  not just `basic` — at minimum `mapsforge` (vector rendering + render themes) and `hillshading`
  (custom `TileSource`/`DemFolder`), since those exercise the most vtm-internal surface.

### Step 4: everything else in `android/build.gradle`

Also check for newer versions of:

```
com.goebl:simplify:1.0.0
io.vacco.savitzky-golay:savitzky-golay:1.0.1
androidx.documentfile:documentfile:1.0.1
com.caverock:androidsvg:1.4
org.locationtech.jts:jts-core:1.20.0
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okio:okio:3.6.0
com.google.protobuf:protobuf-java:3.24.4
io.github.ci-cmg:mapbox-vector-tile:4.0.6
```

Fold whatever's found here into the same plan/PR as Step 3 if the bumps are trivial (just version
number changes), or split out separately if any of them needs real code changes (e.g. a protobuf
major version bump can change generated-code APIs).
