
/**
 * External dependencies
 */
import React, {
	useEffect,
	useState,
	useRef,
} from 'react';
import {
	SafeAreaView,
	StatusBar,
	Text,
	useColorScheme,
	useWindowDimensions,
	PixelRatio,
	View,
	TouchableOpacity,
	NativeModules,
} from 'react-native';

/**
 * react-native-mapsforge-vtm dependencies
 */
import {
	MapContainer,
	// LayerMapsforge,
	// LayerBitmapTile,
	// LayerMBTilesBitmap,
	LayerScalebar,
	// LayerPathSlopeGradient,
	// LayerPath,
	// // LayerDownload,
	// LayerHillshading,
	// // Marker,
	// // Polyline,
	// usePromiseQueueState,
	useMapEvents,
	// useRenderStyleOptions,
	// nativeMapModules,
} from 'react-native-mapsforge-vtm';
// const { MapContainerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import Button from './components/Button.jsx';
import ExampleLayerBitmapTile from './examples/ExampleLayerBitmapTile.jsx';
import ExampleLayerMapsforge from './examples/ExampleLayerMapsforge.jsx';
import ExampleLayerMBTilesBitmap from './examples/ExampleLayerMBTilesBitmap.jsx';
import ExampleLayerPath from './examples/ExampleLayerPath.jsx';
import ExampleDem from './examples/ExampleDem.jsx';
// import usePermissionsOk from './compose/usePermissionsOk.jsx';
// import { randomNumber } from './utils';
import { LINKING_ERROR } from './constants.js';

// const iconMarkerBase = {
// 	width: PixelRatio.getPixelSizeForLayoutSize( 40 ),
// 	height: PixelRatio.getPixelSizeForLayoutSize( 60 ),
// 	anchor: [
// 		0,
// 		-PixelRatio.getPixelSizeForLayoutSize( 60 ) / 2,
// 	],
// };

// const icons = [
// 	{},		// fallback to default icon.
// 	{
// 		...iconMarkerBase,
// 		path: '/storage/emulated/0/Android/media/com.jhotadhari.reactnative.mapsforgeExample/dummy/marker_green.png',
// 	},
// 	{
// 		...iconMarkerBase,
// 		path: '/storage/emulated/0/Android/media/com.jhotadhari.reactnative.mapsforgeExample/dummy/marker_red.png',
// 	},
// ];

// const renderThemeOptions = [
// 	{ label: 'DEFAULT', value: 'DEFAULT' },
// 	{ label: 'BIKER', value: 'BIKER' },
// 	// { label: 'MAPZEN', value: 'MAPZEN' },
// 	{ label: 'MOTORIDER', value: 'MOTORIDER' },
// 	{ label: 'MOTORIDER_DARK', value: 'MOTORIDER_DARK' },
// 	{ label: 'NEWTRON', value: 'NEWTRON' },
// 	// { label: 'OPENMAPTILES', value: 'OPENMAPTILES' },
// 	{ label: 'OSMAGRAY', value: 'OSMAGRAY' },
// 	{ label: 'OSMARENDER', value: 'OSMARENDER' },
// 	{ label: 'TRONRENDER', value: 'TRONRENDER' },
// 	{ label: 'Alti', value: '/storage/emulated/0/Documents/orux/mapstyles/Alti.xml' },
// 	{ label: 'Elements', value: '/storage/emulated/0/Documents/orux/mapstyles/Elements.xml' },
// ];

// const mapFileOptions = [
// 	{ label: 'Ecuador', value: '/storage/emulated/0/Documents/orux/mapfiles/Ecuador_oam.osm.map' },
// 	{ label: 'Colombia', value: '/storage/emulated/0/Documents/orux/mapfiles/Colombia_oam.osm.map' },
// ];


const HelperModule = NativeModules.HelperModule
	? NativeModules.HelperModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

const MapEvents = ( {
	nativeTag,
} ) => {

	useMapEvents( {
		nativeTag,
		onMapEvent: event => {
			console.log( 'debug onMapEvent event', event ); // debug
		},
	} );

	return null;
};

const exampleOptions = [
	{
		label: 'Example LayerBitmapTile',
		value: 'ExampleLayerBitmapTile',
		component: ExampleLayerBitmapTile,
	},
	{
		label: 'Example LayerMapsforge',
		value: 'ExampleLayerMapsforge',
		component: ExampleLayerMapsforge,
	},
	{
		label: 'Example LayerMBTilesBitmap',
		value: 'ExampleLayerMBTilesBitmap',
		component: ExampleLayerMBTilesBitmap,
	},
	{
		label: 'Example LayerPath',
		value: 'ExampleLayerPath',
		component: ExampleLayerPath,
	},
	{
		label: 'Example DEM',
		value: 'ExampleDem',
		component: ExampleDem,
	},
];

const App = () => {

	const isDarkMode = useColorScheme() === 'dark';

	const style = {
		backgroundColor: isDarkMode ? 'black' : '#eee',
		color: isDarkMode ? '#eee' : 'black',
	};

	// const { permissionsOk, requestPermission } = usePermissionsOk();


	const [barTopHeight,setBarTopHeight] = useState( null );
	const [barBottomHeight,setBottomTopHeight] = useState( null );



	const [appDirs,setAppDirs] = useState( null );
	const [selectedExample,setSelectedExample] = useState( null );


	useEffect( () => {
		HelperModule.getAppDirs().then( dirs => {
			setAppDirs( dirs );
		} ).catch( err => console.log( 'ERROR', err ) );
	}, [] );


	// const [randomCenter,setRandomCenter] = useState( [-12.65, -75.239] );
	// const doNewRandomCenter = () => setRandomCenter( [
	// 	randomNumber( -12, -13 ),	// lat
	// 	randomNumber( -74, -75 ),	// lng
	// ] );

	// const promiseQueueState = usePromiseQueueState();

	const [mainMapViewId, setMainMapViewId] = useState( null );

	// console.log( 'debug mainMapViewId', mainMapViewId ); // debug

	// const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );

	// const [renderOverlays, setRenderOverlays] = useState( [
	// 	'alti-background-h',
	// 	'alti-buildings-h',
	// 	'alti-car-h',
	// 	'alti-shops-h',
	// 	'alti-accommodation-h',
	// 	'alti-emergency-h',
	// 	'alti-sports-h',
	// 	'alti-borders-h',
	// 	'alti-landscapefeat-h',
	// 	'alti-amenities-h',
	// 	'alti-settlements-h',
	// 	'alti-acc_allowed-h',
	// 	'alti-tourism-h',
	// 	'alti-h_s_routes',
	// 	'alti-road_surfaces-h',
	// 	'alti-waymarks',
	// 	'alti-barriers-h',
	// 	'alti-pubtrans-h',
	// 	'alti-restaurants-h',
	// 	'alti-h_routes',
	// ] );
	// const [renderTheme, setRenderTheme] = useState( renderThemeOptions.find( o => o.label === 'Alti' ).value );

	// useEffect( () => {
	// 	const eventEmitter = new NativeEventEmitter();
	// 	let eventListener = eventEmitter.addListener( 'onHardwareKeyUp', result => {
	// 		if ( promiseQueueState > 0 || ! mainMapViewId ) {
	// 			return;
	// 		}
	// 		switch( result.keyCodeString ) {
	// 			case 'KEYCODE_VOLUME_UP':
	// 				MapContainerModule.zoomIn( mainMapViewId );
	// 				break;
	// 			case 'KEYCODE_VOLUME_DOWN':
	// 				MapContainerModule.zoomOut( mainMapViewId );
	// 				break;
	// 		}
	// 	} );
	// 	return () => {
	// 		eventListener.remove();
	// 	};
	// }, [mainMapViewId] );

	// const {
	// 	renderStyleDefaultId,
	// 	renderStyleOptions,
	// } = useRenderStyleOptions( ( {
	// 	renderTheme,
	// 	nativeTag: mainMapViewId,
	// } ) );

	// const [renderStyle, setRenderStyle] = useState( renderStyleDefaultId );


	// useEffect( () => {
	// 	if ( ! renderStyle && renderStyleDefaultId ) {
	// 		setRenderStyle( renderStyleDefaultId );
	// 	}

	// 	if ( ! renderOverlayOptions.length ) {
	// 		const renderStyleOptions_ = renderStyleOptions.find( opt => opt.value === renderStyle );
	// 		if ( undefined !== renderStyleOptions_ ) {
	// 			const newItems = Object.keys( renderStyleOptions_.options ).map( value => {
	// 				return {
	// 					value,
	// 					label: renderStyleOptions_.options[value],
	// 				};
	// 			} );

	// 			// console.log( 'debug test newItems', newItems ); // debug
	// 			setRenderOverlayOptions( newItems );
	// 		}
	// 	}
	// }, [renderStyle, renderStyleDefaultId] );

	// const [locations, setLocations] = useState( Array.apply( null, Array( 10 ) ).map( () => [
	// 	randomNumber( -0.25, 0 ),		// lat
	// 	randomNumber( -78.6, -78.37 ),	// lng
	// ] ) );

	const {
		width,
		height,
	} = useWindowDimensions();

	const mapHeight = barTopHeight && barBottomHeight
		? height - barTopHeight - barBottomHeight
		: height;

	const ExampleComponent = null === selectedExample ? null : exampleOptions.find( option => option.value === selectedExample ).component;

	return <SafeAreaView style={ {
			...style,
			height,
			width,
			alignItems: 'center',
			justifyContent: 'space-evenly',
		} }>

			<View style={ {
				padding: 10,
			} }>

				{ null === selectedExample && [...exampleOptions].map( example => <Button
					key={ example.value }
					onPress={ () => setSelectedExample( example.value ) }
					title={ example.label }
					style={ { marginBottom: 10 } }
				/> ) }

				{ null !== selectedExample && ExampleComponent && <ExampleComponent
					setSelectedExample={ setSelectedExample }
					style={ style }
					appDirs={ appDirs }
				/> }

			</View>



				{/* <View
					onLayout={ e => {
						const { height } = e.nativeEvent.layout;
						setBarTopHeight( height );
					} }
					style={ {
						...style,
						position: 'relative',
						width,
						paddingTop: 10,
					} }
				>

					<View
						style={ {
							flexDirection: 'row',
							width,
							justifyContent: 'space-evenly',
							alignItems: 'center',
							marginBottom: 10,
						} }
					>
						<Button
							// onPress={ () => doNewRandomCenter() }
							title="nada"
							disabled={ promiseQueueState > 0 }
						/>
					</View>
				</View> */}


				{ /* mapHeight && <MapContainer
					height={ mapHeight }
					center={ {
						lng: -75.239,
						lat: -12.65,
					} }
					zoomLevel={ 12 }
					mapViewNativeTag={ mainMapViewId }
					setMapViewNativeTag={ setMainMapViewId }
					minZoom={ 2 }
					maxZoom={ 20 }
					moveEnabled={ true }
					tiltEnabled={ false }
					rotationEnabled={ false }
					zoomEnabled={ true }
					// hgtDirPath="/storage/emulated/0/Documents/orux/dem"
					onPause={ result => {
						console.log( 'debug lifecycle event onPause', result );
					} }
					onResume={ result => {
						console.log( 'debug lifecycle event onResume', result );
					} }
				>
					<MapEvents
						nativeTag={ mainMapViewId }
					/>

					{/*

					{/* { showLayerBitmapTile && <LayerBitmapTile
						url={ 'https://mt1.google.com/vt/lyrs=r&x={X}&y={Y}&z={Z}' }
						cacheSize={ 10 * 1024 * 1024 }
					/> }

					<LayerMBTilesBitmap
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/OAM-World-1-10-J70.mbtiles' }
					/>

					<LayerMapsforge
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/Peru-Ecuador_oam.osm.map' }
						renderTheme={ renderTheme }
						renderStyle={ renderStyle }
						renderOverlays={ renderOverlays }
					/>

					<LayerHillshading
						hgtDirPath="/storage/emulated/0/Documents/orux/dem"
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
						// responseInclude={ {
						// 	// coordinates: 1,
						// 	coordinatesSimplified: 1,
						// } }
						// onCreate={ response => {
						// 	console.log( 'debug response', response ); // debug
						// } }
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
						filePath={ '/storage/emulated/0/Documents/orux/tracklogs/2024-10-09 0817__20241009_0817.gpx' }
					/>
					*/}


					{/*



					<LayerScalebar/>

				</MapContainer> */}


				{/* <View
					onLayout={ e => {
						const { height } = e.nativeEvent.layout;
						setBottomTopHeight( height );
					} }
					style={ {
						...style,
						position: 'relative',
						width,
						paddingTop: 10,
					} }
				>

					<View
						style={ {
							flexDirection: 'row',
							width,
							justifyContent: 'space-evenly',
							alignItems: 'center',
							marginBottom: 10,
						} }
					>
						<PickerModalControl
							headerLabel={ 'Render theme' }
							options={ renderThemeOptions }
							values={ [renderTheme] }
							onChange={ clickedVal => setRenderTheme( clickedVal ) }
							closeOnChange={ false }
							disabled={ promiseQueueState > 0 }
						/>
						<PickerModalControl
							headerLabel={ 'Render style' }
							disabled={ promiseQueueState > 0 || ! renderStyleOptions.length }
							buttonLabelFallback={ 'Flavour' }
							options={ renderStyleOptions }
							values={ [renderStyle] }
							onChange={ clickedVal => setRenderStyle( clickedVal ) }
							closeOnChange={ false }
						/>

						<PickerModalControl
							buttonLabel={ 'options' }
							headerLabel={ 'Render style options' }
							disabled={ promiseQueueState > 0 || ! renderStyleOptions.length }
							buttonLabelFallback={ 'test' }
							options={ renderOverlayOptions }
							values={ renderOverlays }
							selectAllNoneLabel={ renderOverlays.length !== renderOverlayOptions.length ? 'All' : 'None' }
							onSelectAllNone={ () => {
								if ( renderOverlays.length !== renderOverlayOptions.length ) {
									setRenderOverlays( [...renderOverlayOptions].map( o => o.value ) );
								} else {
									setRenderOverlays( [] );
								}
							} }
							onChange={ clickedVal => {
								const existingIndex = renderOverlays.findIndex( val => val === clickedVal );
								if ( existingIndex === -1 ) {
									setRenderOverlays( [
										...renderOverlays,
										clickedVal,
									] );
								} else {
									const newSelectedItems = [...renderOverlays];
									newSelectedItems.splice( existingIndex, 1 );
									setRenderOverlays( newSelectedItems );
								}
							} }
							closeOnChange={ false }
						/>
					</View>
				</View> */}



	</SafeAreaView>;
};

export default App;