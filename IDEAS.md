# Ideas

## Extension points

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
	name: 'ext-path-color-ramp',
	version: '0.1.0',
	components: { WeatherOverlay, WeatherParticles },
	capabilities: ['weather-overlay'],
});
```

### 7. vtm class shadowing utilities

**Implemented:** `android/strip-vtm-classes.gradle` handles DEX deduplication
automatically. Declare shadowed classes via `ext.shadowedClasses` in the
extension's `build.gradle`; apply the script once in the app's `build.gradle`.
See "Resolving DEX class conflicts" in Pattern C of the extending docs.

Still open as future work: a script that diffs a shadowed class against the
original vtm source and produces a patch file. This would make it easier to:
- Review what changed vs upstream vtm
- Rebase patches when upgrading vtm versions
- Audit LGPL compliance (exactly what was modified)
