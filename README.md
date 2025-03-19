# react-native-mapsforge-vtm

React Native components to build vector maps using [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). Offline rendering of OpenStreetMap data. Android only

**Just some ideas in early development state. Do not use this for production!** ... Well, you can use it, but things are changing!

## Roadmap

Some things will change sometime soonish:

- Marker improvements: clustering and support for drag and drop.
- Changing the `LayerPathSlopeGradient` props, to be able to control the order of simplification and smoothing, and to apply those steps multiple times.
- Changing the `LayerPathSlopeGradient` component to support gradients as well for speed, elevation or any kind of data.
- Maybe merging the path components: `LayerPathSlopeGradient` and `LayerPath`.
- Hillshading improvements.
- Gesture events for `MapContainer`.
- New components for geometry like polygons and circles.
- New component `LayerGroup`.
- ...

## Installation

```sh
# using npm
npm install react-native-mapsforge-vtm

# OR using Yarn
yarn add react-native-mapsforge-vtm

```

## Usage

```js
import React, {
  useEffect,
  useState,
} from 'react';
import {
  useWindowDimensions,
  SafeAreaView,
  ToastAndroid,
} from 'react-native';
import {
  MapContainer,
  LayerMapsforge,
  LayerBitmapTile,
  LayerMBTilesBitmap,
  LayerHillshading,
  LayerPathSlopeGradient,
  LayerScalebar,
  LayerMarker,
  Marker,
  useRenderStyleOptions,
} from 'react-native-mapsforge-vtm';

const App = () => {

  const { width, height } = useWindowDimensions();

  const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );     // To lift the mapViewNativeNodeHandle state into the app

  const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );

  const [renderOverlays, setRenderOverlays] = useState( [] );

  const [renderThemeOptions,setRenderThemeOptions] = useState( [
    ...[LayerMapsforge.BUILT_IN_THEMES].map( value => ( {
      label: value,
      value: value,
    } ) ),

    { label: 'Custom', value: '/storage/emulated/0/...' },  // Absolute path to xml render theme. Doesn't work with content uri.
  ] );

  const renderTheme = renderThemeOptions[renderThemeOptions.length-1];

  const {
    renderStyleDefaultId,
    renderStyleOptions,
  } = useRenderStyleOptions( ( {
    renderTheme,
    nativeNodeHandle: mapViewNativeNodeHandle,
  } ) );

  const [renderStyle, setRenderStyle] = useState( renderStyleDefaultId );

  useEffect( () => {
    if ( ! renderStyle && renderStyleDefaultId ) {
      setRenderStyle( renderStyleDefaultId );
    }

    if ( ! renderOverlayOptions.length ) {
      const renderStyleOptions_ = renderStyleOptions.find( opt => opt.value === renderStyle );
      if ( undefined !== renderStyleOptions_ ) {
        const newItems = Object.keys( renderStyleOptions_.options ).map( value => {
          return {
            value,
            label: renderStyleOptions_.options[value],
          };
        } );
        setRenderOverlayOptions( newItems );
      }
    }
  }, [renderStyle, renderStyleDefaultId] );

  return <SafeAreaView>

    <MapContainer
      nativeNodeHandle={ mapViewNativeNodeHandle /* Not possible to control this prop, it's just to lift the state up */ }
      setNativeNodeHandle={ setMapViewNativeNodeHandle }
      height={ height }
      width={ width /* defaults to full width */ }
      center={ {
        lng: -70.239,
        lat: -10.65,
      } }
      zoomLevel={ 12 }
      zoomMin={ 2 }
      zoomMax={ 20 }
      moveEnabled={ true }
      tiltEnabled={ false }
      rotationEnabled={ false }
      zoomEnabled={ true }
      hgtDirPath={ '/storage/emulated/0/...' /* If you need altitude data of map center in MapEvents. Absolute path or content uri to dem directory. Bad performance with content uri */ }
      responseInclude={ { center: 2, zoomLevel: 2 } }
      onPause={ response => console.log( 'lifecycle event', response ) }
      onResume={ response => console.log( 'lifecycle event', response ) }
      onMapEvent={ response => console.log( 'map move event', response ) }
    >

      <LayerBitmapTile
        url={ 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png' }
        cacheSize={ 10 * 1024 * 1024 }
      />

      <LayerMapsforge
        mapFile={ mapFile /* Absolute path or content uri to map file */ }
        renderTheme={ renderTheme }
        renderStyle={ renderStyle }
        renderOverlays={ renderOverlays }
      />

      <LayerMBTilesBitmap
        mapFile={ '/storage/emulated/0/...' /* Absolute path to bitmap mbtiles file. Doesn't work with content uri. */ }
      />

      <LayerHillshading
        hgtDirPath={ '/storage/emulated/0/...' /* Absolute path or content uri to dem directory. Bad performance with content uri */ }
        cacheSize={ 512 }
        zoomMin={ 2 }
        zoomMax={ 20 }
        shadingAlgorithm={ LayerHillshading.shadingAlgorithms.SIMPLE }
        magnitude={ 90 }
        shadingAlgorithmOptions={ {
            linearity: -1,
            scale: 1,
        } }
      />

      <LayerPathSlopeGradient
        responseInclude={ {
          coordinates: 1,             // include in response only on create.
          coordinatesSimplified: 2,   // include in response on create and on change.
        } }
        onCreate={ response => console.log( 'response', response ) }
        onRemove={ response => console.log( 'response', response ) }
        onChange={ response => console.log( 'response', response ) }
        slopeSimplificationTolerance={ 7 }
        flattenWindowSize={ 9 }
        strokeWidth={ 5 }
        slopeColors={ [
          [-25, '#000a70'],
          [-10, '#0000ff'],
          [-5, '#01c2ff'],
          [0, '#35fd2d'],
          [5, '#f9ff00'],
          [10, '#ff0000'],
          [25, '#810500'],
        ] }
        // positions={ [
        //     { lng: -76.813, lat: -11.813, alt: 4309 }
        //     // ...
        // ] }
        filePath={ '/storage/emulated/0/...' /* Absolute path or content uri to gpx file */ }
      />

      <LayerMarker>
        <Marker
          position={ lng: -76.813, lat: -11.813 }
          onPress={ response => {
            ToastAndroid.show( 'Marker pressed. index: ' + response.index + ' uuid: ' + response.uuid, ToastAndroid.SHORT );
          } }
          symbol={ {
            height: 100,
            textMargin: 20,
            textPositionY: 0,
            textStrokeWidth: 3,
            filePath={ '/storage/emulated/0/...' /* Absolute path or content uri to raster image or svg file */ }
            hotspotPlace: 'BOTTOM_CENTER',
            text: 'hello',
          } }
        />
        <Marker
          position={ lng: -75.814, lat: -12.274 }
          onLongPress={ response => {
            ToastAndroid.show( 'Marker long pressed. index: ' + response.index + ' uuid: ' + response.uuid, ToastAndroid.SHORT );
          } }
          symbol={ {
            width: 80,
            height: 80,
            textMargin: 20,
            textStrokeWidth: 3,
            textPositionY: 7,
            strokeColor: '#ff0000',
            fillColor: '#eeeeee',
            strokeWidth: 5,
            hotspotPlace: 'CENTER',
            text: 'hello',
          } }
        />
      </LayerMarker>

      <LayerScalebar/>

    </MapContainer>

  </SafeAreaView>;
};

```

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

## Credits

- It's just a wrapper with limited features around [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). **All credits to mapsforge and vtm!!!**
- Dependencies of [vtm](https://github.com/mapsforge/vtm): [Game Controller Extension for libGDX](https://github.com/libgdx/gdx-controllers), [AndroidSVG](https://bigbadaboom.github.io/androidsvg/), [Simple Logging Facade for Java](https://www.slf4j.org/), [OkHttp](https://square.github.io/okhttp/), [Okio](https://github.com/square/okio), [Protocol Buffers - Google's data interchange format](https://github.com/protocolbuffers/protobuf), [MapBox Vector Tile - Java](https://github.com/wdtinc/mapbox-vector-tile-java)
- [JTS Topology Suite](https://github.com/locationtech/jts)
- Most of the hillshading code is copied from [mapsforge](https://github.com/mapsforge/mapsforge)
- To retrieve the elevation for certain coordinates, most code is copied from [mapsforge](https://github.com/mapsforge/mapsforge) and [Java OpenStreetMap Editor - Plugins - ElevationProfile](https://github.com/JOSM/josm-plugins/tree/master/ElevationProfile)
- [Android GPX Parser](https://github.com/ticofab/android-gpx-parser)
- [Simplification of a 2D-polyline or a 3D-polyline](https://github.com/hgoebl/simplify-java/)
- For smoothing data: [Savitzky–Golay filter in Java](https://github.com/vaccovecrana/savitzky-golay)
- Always helpful: [Lodash](https://lodash.com)
- To help limiting the amount of data that flows through the bottleneck between react and java: [queue-promise](https://www.npmjs.com/package/queue-promise)
- [Keep a Changelog](https://www.npmjs.com/package/keep-a-changelog) helps maintaining a [CHANGELOG.md](https://github.com/jhotadhari/react-native-mapsforge-vtm/blob/main/CHANGELOG.md).
- Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
