# Installation

## Prerequisites

- **React Native ≥ 0.80.0** with the **New Architecture** enabled
- **Android** — this library is Android-only (no iOS implementation)
- `newArchEnabled=true` in your app's `gradle.properties`

## Install

```sh
yarn add react-native-mapsforge-vtm
```

Or with npm:

```sh
npm install react-native-mapsforge-vtm
```

## Android configuration

### 1. Enable the New Architecture

Ensure your `android/gradle.properties` includes:

```properties
newArchEnabled=true
```

### 2. Add Maven Central repository

The library pulls vtm and mapsforge dependencies from Maven Central. Your
`android/build.gradle` should already include:

```groovy
repositories {
    mavenCentral()
    google()
}
```

### 3. Minimum SDK version

The library requires `minSdkVersion >= 24`. Set this in your
`android/build.gradle`:

```groovy
buildscript {
    ext {
        minSdkVersion = 24
    }
}
```

### 4. Hermes (recommended)

Hermes is required by the New Architecture. It's on by default in React Native
0.80+, but if you've customized it, ensure:

```properties
hermesEnabled=true
```

## Optional peer dependencies

### react-native-reanimated

Add `react-native-reanimated >= 3.0.0` to access `useMapPosition()` — shared
values that track the map's center, zoom, bearing, and tilt at 60fps on the UI
thread, without triggering React re-renders.

```sh
yarn add react-native-reanimated
```

### react-native-worklets

Add `react-native-worklets >= 0.1.0` for worklet-based map integrations.

```sh
yarn add react-native-worklets
```

Both peer dependencies are marked optional — the library works without them.

## Verify installation

After installing, build your app to trigger the codegen step:

```sh
yarn android
```

If the build succeeds, the library is correctly wired. A quick smoke test —
render a `MapContainer` with a `LayerBitmapTile` using the OpenStreetMap tile
URL from the [Quick Start](./quick-start.md).

## Troubleshooting

### `Unknown CMake command: target_compile_reactnative_options`

Your React Native version is too old. Upgrade to ≥ 0.80.0, or if you're on a
custom build, ensure the `target_compile_reactnative_options` CMake function is
available.

### `Could not resolve com.github.mapsforge.vtm:vtm:0.28.0`

Ensure `mavenCentral()` is in the `repositories` block of
`android/build.gradle` (not just `android/settings.gradle`).

### `java.lang.NoClassDefFoundError: com.facebook.react.bridge.TurboModule`

You're running without the New Architecture. Set `newArchEnabled=true` in
`android/gradle.properties` and rebuild.
