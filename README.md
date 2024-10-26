# react-native-mapsforge-vtm

React Native components to build vector maps using [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). Offline rendering of OpenStreetMap data. Android only

**Just some ideas in early development state. Do not use this for production!**

## Installation

```sh
# npm install react-native-mapsforge-vtm

# It's not hosted on npm yet, let's install it from github:
npm install git+https://github.com/jhotadhari/react-native-mapsforge-vtm.git
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
} from 'react-native';
import {
	MapContainer,
	LayerMapsforge,
	LayerBitmapTile,
	LayerMBTilesBitmap,
	LayerHillshading,
	LayerPathSlopeGradient,
	LayerScalebar,
	useMapEvents,
	useRenderStyleOptions,
	nativeMapModules,
} from 'react-native-mapsforge-vtm';

const MapEvents = ( {
	nativeTag,
} ) => {
	useMapEvents( {
		nativeTag,
		onMapEvent: event => {
			console.log( 'onMapEvent event', event ); // debug
		},
	} );
	return null;
};

const App = () => {

	const { width, height } = useWindowDimensions();

	const [mainMapViewId, setMainMapViewId] = useState( null );     // To lift the mainMapViewId state into the app

	const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );

	const [renderOverlays, setRenderOverlays] = useState( [] );

	const [renderThemeOptions,setRenderThemeOptions] = useState( [
		{ label: 'DEFAULT', value: 'DEFAULT' },
		{ label: 'BIKER', value: 'BIKER' },
		{ label: 'MOTORIDER', value: 'MOTORIDER' },
		{ label: 'MOTORIDER_DARK', value: 'MOTORIDER_DARK' },
		{ label: 'NEWTRON', value: 'NEWTRON' },
		{ label: 'OSMAGRAY', value: 'OSMAGRAY' },
		{ label: 'OSMARENDER', value: 'OSMARENDER' },
		{ label: 'TRONRENDER', value: 'TRONRENDER' },

		{ label: 'Custom', value: '/storage/emulated/0/...' },  // Absolute path to xml render theme. Doesn't work with content uri.
	] );

    const renderTheme = renderThemeOptions[renderThemeOptions.length-1];

	const {
		renderStyleDefaultId,
		renderStyleOptions,
	} = useRenderStyleOptions( ( {
		renderTheme,
		nativeTag: mainMapViewId,
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
            mapViewNativeTag={ mainMapViewId /* Not possible to control this prop, it's just to lift the state up */ }
            setMapViewNativeTag={ setMainMapViewId }
            height={ height }
            width={ width /* defaults to full width */ }
            center={ {
                lng: -70.239,
                lat: -10.65,
            } }
            zoomLevel={ 12 }
            minZoom={ 2 }
            maxZoom={ 20 }
            moveEnabled={ true }
            tiltEnabled={ false }
            rotationEnabled={ false }
            zoomEnabled={ true }
            hgtDirPath={ '/storage/emulated/0/...' /* If you need altitude data of map center in MapEvents. Absolute path or content uri to dem directory. Bad performance with content uri */ }
            onPause={ result => console.log( 'lifecycle event onPause', result ) }
            onResume={ result => console.log( 'lifecycle event onResume', result ) }
         >

            <MapEvents
                nativeTag={ mainMapViewId }
            />

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

            <LayerScalebar/>

        </MapContainer>

    </SafeAreaView>;
};

```

### Where to get maps?

Download maps in mapsforge V5 format here [https://www.openandromaps.org/en/downloads](https://www.openandromaps.org/en/downloads)

## Contribution

Contributions welcome. You can report [issues or suggest features](https://github.com/jhotadhari/react-native-mapsforge/issues). Help me coding, fork the repository and make pull requests. Or [get me a coffee](https://waterproof-webdesign.de/donate).

## License

MIT

## Credits

- It's just a wrapper with limited features around [Mapsforges fork of vtm](https://github.com/mapsforge/vtm). **All credits to mapsforge and vtm!!!**
- Always helpful: [Lodash](https://lodash.com)
- To help limiting the amount of data that flows through the bottleneck between react and java: [queue-promise](https://www.npmjs.com/package/queue-promise)
- [Keep a Changelog](https://www.npmjs.com/package/keep-a-changelog) helps maintaining a [CHANGELOG.md](https://github.com/jhotadhari/react-native-mapsforge-vtm/blob/main/CHANGELOG.md).
- Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
