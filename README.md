# react-native-mapsforge-vtm

React Native components to build vector maps using [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). Offline rendering of OpenStreetMap data. Android only

## Requirements

- **React Native ≥ 0.80.0** with the **New Architecture** enabled. The library ships pre-generated native code that requires `target_compile_reactnative_options` (introduced in React Native 0.80.0). Older versions will hit `Unknown CMake command` errors at build time.
- **Android only** — there is no iOS implementation.
- `newArchEnabled=true` in your app's `gradle.properties`.

## Quick Example

```tsx
import { MapContainer, LayerBitmapTile, LayerScalebar } from 'react-native-mapsforge-vtm';

const App = () => (
  <MapContainer
    center={[-77.6, -9.1]}
    zoomLevel={12}
    width={400}
    height={600}
  >
    <LayerBitmapTile
      url="https://tile.openstreetmap.org/{Z}/{X}/{Y}.png"
      zoomMax={18}
    />
    <LayerScalebar />
  </MapContainer>
);
```

**[Full documentation →](./docs/README.md)** — API reference for all 13 components, 3 hooks, and advanced topics.

## Installation

```sh
# using npm
npm install react-native-mapsforge-vtm

# OR using Yarn
yarn add react-native-mapsforge-vtm
```

### Performance notes: touch/gesture latency

When overlaying React Native `View`s on top of `MapContainer`, always set `pointerEvents="none"` or `pointerEvents="box-none"`. A full-screen absolute-positioned overlay without this prop forces every touch to hit-test through the JS-side React Native View before reaching the native `MapsforgeVtmView`. On Android this adds 1–2 frames of latency to pan/zoom gesture start, producing a perceptible "sticky" feel when swiping the map.

**Why it matters:** React Native renders sibling Views in order — the last child is on top. The native map view (an Android `LinearLayout`) sits beneath any JS View rendered after it in the tree. Without `pointerEvents="none"`, React Native's touch dispatch system performs a JS-side hit-test through the overlay on every `MotionEvent`, which delays delivery to the native gesture recognizer.

Observed at straymap commit `1db69df` (Center component overlaying `MapContainer` in `AppView.tsx`).

### Where to get maps?

Vector maps in mapsforge V5 format and xml render styles [https://www.openandromaps.org/en/downloads](https://www.openandromaps.org/en/downloads).

Raster overview maps in MBtiles format [https://www.openandromaps.org/en/downloads/general-maps](https://www.openandromaps.org/en/downloads/general-maps).

Digital elevation Models, elevation data in hgt format at 3 arc second resolution [https://viewfinderpanoramas.org/dem3.html](https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm)

## Contribution

Contributions welcome. You can report [issues or suggest features](https://github.com/jhotadhari/react-native-mapsforge-vtm/issues). Help me coding, [fork the repository and make pull requests](./CONTRIBUTING.md).

[![liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/jhotadhari/donate)
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/H2H3162PAG)

## License

MIT

## Apps using `react-native-mapsforge-vtm`

- The example app, included in the [repository](https://github.com/jhotadhari/react-native-mapsforge-vtm/tree/main/example)
- [straymap](https://github.com/jhotadhari/straymap)

## Credits

- It's just a wrapper with limited features around [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). **All credits to mapsforge and vtm!!!**
- Dependencies of [vtm](https://github.com/mapsforge/vtm):
[AndroidSVG](https://bigbadaboom.github.io/androidsvg/);
[Simple Logging Facade for Java](https://www.slf4j.org/);
[OkHttp](https://square.github.io/okhttp/);
[Okio](https://github.com/square/okio);
[Protocol Buffers - Google's data interchange format](https://github.com/protocolbuffers/protobuf);
[MapBox Vector Tile - Java](https://github.com/wdtinc/mapbox-vector-tile-java)
- [JTS Topology Suite](https://github.com/locationtech/jts)
- To retrieve the elevation for certain coordinates, most code is copied from [mapsforge](https://github.com/mapsforge/mapsforge) and [JOSM-Elevation-Plugin by Harald Hetzner](https://github.com/hhtznr/JOSM-Elevation-Plugin) and [Java OpenStreetMap Editor - Plugins - ElevationProfile by Oliver Wieland](https://github.com/JOSM/josm-plugins/tree/master/ElevationProfile)
- [Android GPX Parser](https://github.com/ticofab/android-gpx-parser)
- [Simplification of a 2D-polyline or a 3D-polyline](https://github.com/hgoebl/simplify-java/)
- For smoothing data: [Savitzky–Golay filter in Java](https://github.com/vaccovecrana/savitzky-golay)
- Always helpful: [Lodash](https://lodash.com)
- To help limiting the amount of data that flows through the bottleneck between react and java: [queue-promise](https://www.npmjs.com/package/queue-promise)
- [Keep a Changelog](https://www.npmjs.com/package/keep-a-changelog) helps maintaining a [CHANGELOG.md](https://github.com/jhotadhari/react-native-mapsforge-vtm/blob/main/CHANGELOG.md).
- Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
