# TODO

## 1. Re-implement `CanvasAdapterModule` (carried over from the New Architecture rewrite)

The old bridge module `CanvasAdapterModule` (`setTextScale`/`setLineScale`/`setSymbolScale` — global
vtm rendering-scale controls) has no New Architecture / JS-facing equivalent yet. `CanvasAdapter`
itself is still used internally (`LayerMarker.java`, `LayerScalebar.java`), so the native hook point
exists — what's missing is a public TurboModule exposing it to JS again, e.g. a new
`NativeCanvasAdapter.ts` + `android/.../modules/CanvasAdapter.java`. This is wanted back, not a
permanent removal — see `MIGRATION.md`. Not started.

## 2. Dependency upgrade plan (post-rewrite version bump)

Drafted 2026-06-23, right after the New Architecture rewrite landed (commit `c9a6ace`). Follow these
steps in order — each one should land as its own commit/PR so a regression is easy to bisect, given
how much native surface (NDK/vtm JNI) this touches.

### Step 1: `react-native` + `react-native-builder-bob` to latest — done 2026-06-24

Bumped `react-native` 0.78.2 → 0.86.0, `react` 19.0.0 → 19.2.7, `@types/react` ^19.0.0 → ^19.2.17,
`react-native-builder-bob` ^0.40.6 → ^0.43.0 (root + `example/package.json`, including example's
`@react-native/babel-preset`/`metro-config`/`typescript-config` kept in lockstep). Kotlin bumped
2.0.21 → 2.1.20 to match the new RN template's recommendation. NDK/minSdk/compileSdk/targetSdk left
unchanged (27.1.12297006 / 24 / 35 / 35) — the upstream template moved compileSdk/targetSdk to 36,
but staying at 35 sidesteps RN 0.81's edge-to-edge-by-default behavior change, which only kicks in at
SDK 36+.

Two breaking changes the bump itself required (not optional cleanup):
- RN 0.86's `jest-preset.js` no longer bundles the actual preset — added `@react-native/jest-preset`
  as a devDependency (root + example) and pointed both jest configs at it instead of the old
  `"react-native"` preset string.
- RN's own `@react-native/gradle-plugin` (included via `example/android/settings.gradle`'s
  `pluginManagement`) resolves AGP `8.12.0` internally via its own bundled version catalog, which
  requires Gradle ≥8.13. Bumped `example/android`'s Gradle wrapper 8.12 → 9.3.1 (matching what
  `@react-native/gradle-plugin` itself ships) and aligned the library's own `android/build.gradle`
  classpath pin from 8.7.2 to 8.12.0 to avoid two different AGP versions resolving within the same
  multi-project build.

Verified: `yarn typecheck && yarn lint && yarn test` clean (same pre-existing warnings, no new
ones). Full clean rebuild (`yarn clean && ./gradlew clean && ./gradlew :app:assembleDebug`) builds
successfully end to end, including codegen and CMake native compilation for all 4 architectures.

**Unblocked the `@react-native-community/cli` bump deferred from Step 2** — now on `20.1.3` (root +
`example/package.json`, `cli-platform-android` kept in lockstep). Verified: `yarn example start`
boots Metro cleanly and bundles the example app's entry point without resolution errors.

**`jest`/`@types/jest` are still blocked, even after this bump — root cause was misattributed in
the Step 2 note below.** It's not `react-native`'s own version gating it: `@react-native/jest-preset`
(the now-separate package added by this bump) *itself* still depends on `jest-environment-node:
^29.7.0`, which nests its own `jest-mock@29.7.0` lacking the `clearMocksOnScope` method Jest 30's
runtime calls — same crash as before, just moved one package over. This is a durable upstream gap in
`@react-native/jest-preset` itself (confirmed against its latest version, `0.86.0`, matching this
bump), not something the RN version bump fixes. Re-attempt once `@react-native/jest-preset` bumps its
own `jest-environment-node` dependency past 29.x.

- Watch for: Gradle/AGP/Kotlin/NDK/compileSdk minimums bumped by the new RN version's template —
  this repo currently pins Gradle 9.3.1, AGP 8.12.0, Kotlin 2.1.20, NDK 27.1.12297006, compileSdk 35
  (both `android/gradle.properties` and `example/android/gradle.properties` — keep them in sync,
  see CLAUDE.md's "Versions/config that must stay in sync" section).

### Step 2: other devDependencies in `package.json`

- Go through every remaining devDependency in root `package.json` (eslint, `@eslint/*`,
  `@react-native/eslint-config`, `typescript`, `prettier` + its plugins, `del-cli`, `turbo`) and
  bump to latest. `@evilmartians/lefthook` and `keep-a-changelog` are this repo's own (not from the
  rewrite) — bump those too while here.
- **`@react-native-community/cli` was blocked here — now landed as part of Step 1 instead.** See
  the Step 1 section above: bumping RN to `0.86.0` unblocked it, now on `20.1.3`.
- Re-run `yarn typecheck && yarn lint` after each batch; fix anything the version bump surfaces
  before moving on (new ESLint rules turning on, stricter TS, etc.).
- **`typescript` was bumped to `^6.0.3`** (2026-06-24) — TS 6 changed `types` to default to an empty
  array instead of auto-including everything under `node_modules/@types`; added
  `"types": ["jest"]` to `tsconfig.json` to keep `it`/`describe`/etc. resolving in
  `src/__tests__/index.test.tsx`.
- **`jest`/`@types/jest` are still blocked — see the Step 1 section above for the corrected root
  cause** (it's `@react-native/jest-preset` itself, not tied to the RN version bump).

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

### Step 3: vtm/mapsforge native dependencies in `android/build.gradle` (the big one) — done 2026-06-24

Bumped all `com.github.mapsforge.vtm:*` artifacts `0.25.0` → `0.28.0` (confirmed via JitPack/GitHub
tags — the `0.28.0` working assumption was correct, not stale). Changelog read for 0.25.0→0.28.0:
no breaking API changes relevant to this repo's usage (tile layers, hillshading, render themes, MVT,
JTS, HTTP tiles, `ItemizedLayer`/`PathLayer`) — additive only (`ThemeCallback.getText`/`getColor`,
`map-background-outside`, new themes, label repeat-proximity). Checked all 5 custom files this repo
hand-rolls (`layer/PathLayer.java`, `layer/VectorLayer.java`, `layer/ItemizedLayer.java`,
`RenderThemeMenuLoader.java`, `HgtReader.java`) against that changelog — **no vtm-native replacement
found for any of them**, so none were deleted.

**Found mid-implementation, not anticipated by the plan above:** `org.mapsforge:mapsforge-core`/
`mapsforge-map`/`mapsforge-map-android` are stuck at `0.25.0`/`0.24.1` on Maven Central not because
mapsforge stopped releasing, but because mapsforge **moved their publishing groupId** to
`com.github.mapsforge.mapsforge` (matching vtm's own naming convention) for releases beyond 0.25.0 —
that new groupId publishes straight to Maven Central (no JitPack repo needed) all the way to
`0.28.0`, including `mapsforge-map-android`/`mapsforge-map-reader`. vtm 0.28.0 itself already depends
on `com.github.mapsforge.mapsforge:mapsforge-core`/`mapsforge-map:0.28.0` transitively, so keeping the
old `org.mapsforge:*:0.25.0` pins alongside it caused a **duplicate-class build failure** (both
publish identical `org.mapsforge.map.*` packages). Fixed by switching this repo's explicit
`mapsforge-core`/`mapsforge-map`/`mapsforge-map-android` declarations onto the new
`com.github.mapsforge.mapsforge` groupId at `0.28.0` — this fully resolves the
`mapsforge-map-android` lag noted below, rather than just bumping it to the equally-stale `0.25.0`.

Verified: `yarn typecheck/lint/test` clean, full clean `yarn clean && ./gradlew clean &&
./gradlew :app:assembleDebug` succeeds, and the `android-example-verifier` agent confirmed `basic`,
`mapsforge` (render-theme/`<stylemenu>` switching), and `hillshading` (`HgtReader`/DEM) all render
correctly on the emulator with no logcat regressions.

Landed in commit `fdeef21` on branch `feature/step3-vtm-bump` (worktree
`.claude/worktree-step3`) — not yet merged into `development`, awaiting sign-off.

Original plan notes (kept for context):

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

**Partially done, folded into Step 3's commit (`fdeef21`) 2026-06-24** — bumped the trivial ones:
`androidx.documentfile` `1.0.1` → `1.1.0`, `com.squareup.okio` `3.6.0` → `3.12.0` (transitive-only,
no direct usage in this repo), `com.google.protobuf:protobuf-java` `3.24.4` → `3.25.8` (stayed within
the 3.x line deliberately — the 4.x major bump changes generated-code APIs and `vtm-mvt`/
`vtm-android-mvt`'s own bundled MVT-parsing classes aren't verified against a 4.x runtime, so that's
deferred, not done). `com.goebl:simplify`, `io.vacco.savitzky-golay`, `com.caverock:androidsvg`,
`org.locationtech.jts:jts-core`, `com.squareup.okhttp3:okhttp`, and `io.github.ci-cmg:mapbox-vector-tile`
were all already at latest stable — left unchanged.

**Still open: the protobuf-java 4.x major bump**, if/when vtm's own MVT classes are confirmed
compatible with it (would need a dedicated MBTiles/MVT-layer regression test, not just a version
bump).

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
