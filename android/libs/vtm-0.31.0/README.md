# Vendored mapsforge/vtm 0.31.0 jars

**Why vendored:** vtm 0.31.0 has no published artifacts — the JitPack build
is broken (its build image can't provision the Android SDK for the new AGP
9.1.1 toolchain) and upstream disabled Maven publishing after the OSSRH
end-of-life (mapsforge/vtm PR #1234). Upstream now documents
`publishToMavenLocal` as the only consumption path.

**Source:** mapsforge/vtm tag `0.31.0`, commit
`d8c0ba8510094be18253a578b84e2446e228db25` (released 2026-10-28).

**Build command** (run in a clean worktree of the tag):

```bash
git worktree add /tmp/opencode/vtm-0.31.0 0.31.0
cd /tmp/opencode/vtm-0.31.0
# Upstream's `static def androidCompileSdk() { return 37.0 }` interpolates to
# `platforms/android-37.0` (broken path).  Patch to an installed int platform:
sed -i 's/return 37.0/return 36/' build.gradle
ANDROID_HOME=<sdk> ./gradlew \
  :vtm:jar :vtm-themes:jar :vtm-android:jar :vtm-jts:jar :vtm-http:jar \
  :vtm-mvt:jar :vtm-android-mvt:jar :vtm-hillshading:jar \
  :vtm-android:nativesJar-arm64-v8a :vtm-android:nativesJar-armeabi-v7a \
  :vtm-android:nativesJar-x86 :vtm-android:nativesJar-x86_64
```

The `natives-*` jars only re-pack the prebuilt `.so` files committed at
`vtm-android/natives/<abi>/lib/<abi>/libvtm-jni.so` — no NDK needed.

**TODO:** when upstream publishes 0.31.0 artifacts for real (JitPack fix or
Sonatype Central Portal), swap the `files(...)` dependencies in
`android/build.gradle` back to `com.github.mapsforge.vtm:*` coordinates and
delete this directory.
