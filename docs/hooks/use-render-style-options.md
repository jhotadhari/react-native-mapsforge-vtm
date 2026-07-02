# useRenderStyleOptions()

Reads the available render styles and overlay options from a mapsforge render
theme's `<stylemenu>` XML block. Returns a plain `Promise` — not tied to a
live map instance, so callers don't need a `nativeNodeHandle`.

```tsx
import { useRenderStyleOptions } from 'react-native-mapsforge-vtm';
```

## Usage

```tsx
const { useRenderStyleOptions } = require('react-native-mapsforge-vtm');

// Call from any component — no MapHandleContext needed
const styles = await useRenderStyleOptions('OSMARENDER');
// styles: RenderStyleOption[]
```

The result is cached natively by file path + last-modified timestamp.
Subsequent calls for the same theme return the cached result instantly.

## Why a plain function, not a hook

Unlike `useMap()`, this doesn't need `MapHandleContext` — it's a pure
function of the theme file. It parses only the `<stylemenu>` block (not the
full theme), making it far cheaper than compiling the entire theme via vtm's
`XmlThemeBuilder`.

## Return type

```tsx
type RenderStyleOption = {
  value: string;       // style ID to pass as LayerMapsforge's renderStyle prop
  label: string;       // human-readable name
  isDefault?: boolean; // the theme's default style
  overlays: {
    value: string;
    label: string;
  }[];
};
```

## Example

```tsx
import { useEffect, useState } from 'react';
import {
  MapContainer,
  LayerMapsforge,
  useRenderStyleOptions,
  type RenderStyleOption,
} from 'react-native-mapsforge-vtm';

const MapWithStylePicker = () => {
  const [styles, setStyles] = useState<RenderStyleOption[]>([]);
  const [currentStyle, setCurrentStyle] = useState<string>('');

  useEffect(() => {
    useRenderStyleOptions('OSMARENDER').then((s) => {
      setStyles(s);
      setCurrentStyle(s.find((x) => x.isDefault)?.value ?? s[0]?.value ?? '');
    });
  }, []);

  return (
    <MapContainer center={[13.405, 52.52]} zoomLevel={12}>
      <LayerMapsforge
        mapFile="/storage/emulated/0/maps/berlin.map"
        renderTheme="OSMARENDER"
        renderStyle={currentStyle}
      />
    </MapContainer>
  );
};
```

## See also

- **[LayerMapsforge](../components/layer-mapsforge.md)** — Uses `renderStyle` and `renderOverlays`
