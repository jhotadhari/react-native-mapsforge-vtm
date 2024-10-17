
/**
 * External dependencies
 */
import React, {
	useEffect,
	useState,
	useRef,
} from 'react';
import {
	Button,
	SafeAreaView,
	StatusBar,
	Text,
	useColorScheme,
	useWindowDimensions,
	PixelRatio,
	View,
	NativeEventEmitter,
} from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

console.log( 'debug GestureHandlerRootView', GestureHandlerRootView ); // debug

/**
 * react-native-mapsforge dependencies
 */
import {
	MapContainer,
	LayerMapsforge,
	LayerBitmapTile,
	LayerMBTilesBitmap,
	LayerScalebar,
	LayerPathSlopeGradient,
	LayerPath,
	// LayerDownload,
	// LayerHillshading,
	// Marker,
	// Polyline,
	usePromiseQueueState,
	useMapEvents,
	useRenderStyleOptions,
	nativeMapModules,
} from 'react-native-mapsforge-vtm';
const { MapContainerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import ChartWrapper from './components/ChartWrapper.jsx';
import PickerModalControl from './components/PickerModalControl.jsx';
import usePermissionsOk from './compose/usePermissionsOk.jsx';
import { randomNumber } from './utils';

const iconMarkerBase = {
	width: PixelRatio.getPixelSizeForLayoutSize( 40 ),
	height: PixelRatio.getPixelSizeForLayoutSize( 60 ),
	anchor: [
		0,
		-PixelRatio.getPixelSizeForLayoutSize( 60 ) / 2,
	],
};

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

const renderThemeOptions = [
	{ label: 'DEFAULT', value: 'DEFAULT' },
	{ label: 'BIKER', value: 'BIKER' },
	// { label: 'MAPZEN', value: 'MAPZEN' },
	{ label: 'MOTORIDER', value: 'MOTORIDER' },
	{ label: 'MOTORIDER_DARK', value: 'MOTORIDER_DARK' },
	{ label: 'NEWTRON', value: 'NEWTRON' },
	// { label: 'OPENMAPTILES', value: 'OPENMAPTILES' },
	{ label: 'OSMAGRAY', value: 'OSMAGRAY' },
	{ label: 'OSMARENDER', value: 'OSMARENDER' },
	{ label: 'TRONRENDER', value: 'TRONRENDER' },
	{ label: 'Alti', value: '/storage/emulated/0/Documents/orux/mapstyles/Alti.xml' },
	{ label: 'Elements', value: '/storage/emulated/0/Documents/orux/mapstyles/Elements.xml' },
];

// const mapFileOptions = [
// 	{ label: 'Ecuador', value: '/storage/emulated/0/Documents/orux/mapfiles/Ecuador_oam.osm.map' },
// 	{ label: 'Colombia', value: '/storage/emulated/0/Documents/orux/mapfiles/Colombia_oam.osm.map' },
// ];


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

const App = () => {

	const isDarkMode = useColorScheme() === 'dark';

	const style = {
		backgroundColor: isDarkMode ? 'black' : '#eee',
		color: isDarkMode ? '#eee' : 'black',
	};

	const { permissionsOk, requestPermission } = usePermissionsOk();


	const [barTopHeight,setBarTopHeight] = useState( 0 );
	const [barBottomHeight,setBottomTopHeight] = useState( 0 );




	// const [randomCenter,setRandomCenter] = useState( [-12.65, -75.239] );
	// const doNewRandomCenter = () => setRandomCenter( [
	// 	randomNumber( -12, -13 ),	// lat
	// 	randomNumber( -74, -75 ),	// long
	// ] );

	// const [randomZoom,setRandomZoom] = useState( 8 );
	// const doNewRandomZoom = () => setRandomZoom( Math.round( randomNumber( 8, 16 ) ) );

	// const [randomMinZoom,setRandomMinZoom] = useState( 2 );
	// const doNewRandomMinZoom = () => setRandomMinZoom( Math.round( randomNumber( 5, 20 ) ) );

	// const [randomMaxZoom,setRandomMaxZoom] = useState( 20 );
	// const doNewRandomMaxZoom = () => setRandomMaxZoom( Math.round( randomNumber( 5, 20 ) ) );


	// const [randomViewportVal,setRandomViewportVal] = useState( 0 );
	// const doNewViewportVal = () => setRandomViewportVal( Math.round( randomNumber( 200, 400 ) ) );




	// const [moveEnabled,setMoveEnabled] = useState( true );
	// const [tiltEnabled,setTiltEnabled] = useState( true );
	// const [rotationEnabled,setRotationEnabled] = useState( true );
	// const [zoomEnabled,setZoomEnabled] = useState( true );


	const promiseQueueState = usePromiseQueueState();

	// const [mapFile, setMapFile] = useState( mapFileOptions[0].value );
	// const [showLayerMapsforge, setShowLayerMapsforge] = useState( true );
	const [showLayerBitmapTile, setShowLayerBitmapTile] = useState( true );
	// const [showMarkers, setShowMarkers] = useState( true );

	const [mainMapViewId, setMainMapViewId] = useState( null );

	// const [iconIndex, setIconIndex] = useState( 0 );

	const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );

	const [renderOverlays, setRenderOverlays] = useState( [
		'alti-background-h',
		'alti-buildings-h',
		'alti-car-h',
		'alti-shops-h',
		'alti-accommodation-h',
		'alti-emergency-h',
		'alti-sports-h',
		'alti-borders-h',
		'alti-landscapefeat-h',
		'alti-amenities-h',
		'alti-settlements-h',
		'alti-acc_allowed-h',
		'alti-tourism-h',
		'alti-h_s_routes',
		'alti-road_surfaces-h',
		'alti-waymarks',
		'alti-barriers-h',
		'alti-pubtrans-h',
		'alti-restaurants-h',
		'alti-h_routes',
	] );
	const [renderTheme, setRenderTheme] = useState( renderThemeOptions.find( o => o.label === 'Alti' ).value );


	const [coordinates, setCoordinates] = useState( [] );
	const [coordinatesSimplified, setCoordinatesSimplified] = useState( [] );



	const [slopeSimplificationTolerance, setSlopeSimplificationTolerance] = useState( 7 );
	const [flattenWindowSize, setFlattenWindowSize] = useState( 9 );



	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'onHardwareKeyUp', result => {
			if ( promiseQueueState > 0 || ! mainMapViewId ) {
				return;
			}
			switch( result.keyCodeString ) {
				case 'KEYCODE_VOLUME_UP':
					MapContainerModule.zoomIn( mainMapViewId );
					break;
				case 'KEYCODE_VOLUME_DOWN':
					MapContainerModule.zoomOut( mainMapViewId );
					break;
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [mainMapViewId] );

	// useEffect( () => {
	// 	const eventEmitter = new NativeEventEmitter();
	// 	let eventListener = eventEmitter.addListener( 'RenderThemeParsed', result => {


	// 		Object.keys( result?.collection ).map( key => {

	// 			console.log( 'debug RenderThemeParsed', key, result.collection[key] ); // debug


	// 		} );

	// 	} );
	// 	return () => {
	// 		eventListener.remove();
	// 	};
	// }, [] );


	const {
		renderStyleDefaultId,
		renderStyleOptions,
	} = useRenderStyleOptions( ( {
		renderTheme,
		nativeTag: mainMapViewId,
	} ) );

	const [renderStyle, setRenderStyle] = useState( renderStyleDefaultId );


	// const test = {
	// 	renderOverlayOptions,
	// 	renderOverlays,
	// 	renderOverlayOptionsCount: renderOverlayOptions.length,
	// 	renderOverlaysCount: renderOverlays.length,
	// 	// renderTheme,
	// 	// renderStyle,
	// 	// renderStyleDefaultId,
	// 	// renderStyleOptions,
	// };
	// console.log( 'debug test', test ); // debug



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

	// const [locations, setLocations] = useState( Array.apply( null, Array( 10 ) ).map( () => [
	// 	randomNumber( -0.25, 0 ),		// lat
	// 	randomNumber( -78.6, -78.37 ),	// long
	// ] ) );

	const {
		width,
		height,
	} = useWindowDimensions();

	const mapHeight = height - barTopHeight - barBottomHeight;


		console.log( 'debug mapHeight', mapHeight ); // debug


	// ??? TODO hillshading layer.
	// 		see vtm MBTilesBitmapTileDataSource, MBTilesTileDataSource, ITileDataSource
	// 		see mapsforge MemoryCachingHgtReaderTileSource.getHillshadingBitmap

	return (
		<GestureHandlerRootView style={{ flex: 1 }}>
		<SafeAreaView style={ {
			...style,
			height,
			width,
		} }>
			<StatusBar
				barStyle={ isDarkMode ? 'light-content' : 'dark-content' }
				backgroundColor={ style.backgroundColor }
			/>

			{ ! permissionsOk && <View
				style={ {
					width,
					height,
					justifyContent: 'space-around',
					alignItems: 'center',
				} }
			>
				<View>

					<Text
						style={ { marginBottom: 10 } }
					>Need permission to access and manage all files</Text>

					<Button
						onPress={ () => {
							requestPermission().then( result => {
								console.log( 'debug result', result ); // debug
							} );
						} }
						title="open settings"
					/>
				</View>
			</View> }

			{ permissionsOk && <View>

				<View
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
							justifyContent: 'space-around',
							alignItems: 'center',
							marginBottom: 10,
						} }
					>


						<Text style={ {width: 100} }>Simplification</Text>

						<Text style={ {width: 30} }>{ slopeSimplificationTolerance }</Text>

						<Button
							onPress={ () => setSlopeSimplificationTolerance( slopeSimplificationTolerance + 1 ) }
							title="  +  "
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => setSlopeSimplificationTolerance( Math.max( 0, slopeSimplificationTolerance -1 ) ) }
							title="  -  "
							disabled={ promiseQueueState > 0 || slopeSimplificationTolerance == 0 }
						/>


					</View>


					<View
						style={ {
							flexDirection: 'row',
							width,
							justifyContent: 'space-around',
							alignItems: 'center',
							marginBottom: 10,
						} }
					>

						<Text style={ {width: 100} }>Flattening</Text>
						<Text style={ {width: 30} }>{ flattenWindowSize }</Text>



						<Button
							onPress={ () => setFlattenWindowSize( flattenWindowSize + 2 ) }
							title="  +  "
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => setFlattenWindowSize( Math.max( 5, flattenWindowSize - 2 ) ) }
							title="  -  "
							disabled={ promiseQueueState > 0 || flattenWindowSize == 5 }
						/>

					</View>


					{/*

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
					</View> */}

				</View>


				{ mapHeight && <MapContainer
					// width={ width }
					height={ mapHeight }
					center={ [-12.65, -75.239] }	// ??? maybe other way around, should do everywhere same order!!!
					zoomLevel={ 12 }
					mapViewNativeTag={ mainMapViewId }
					setMapViewNativeTag={ setMainMapViewId }
					minZoom={ 2 }
					maxZoom={ 20 }


					tiltEnabled = { false }
					rotationEnabled = { false }



					onPause={ result => {
						console.log( 'debug lifecycle event onPause', result );
					} }
					onResume={ result => {
						console.log( 'debug lifecycle event onResume', result );
					} }
				>

					{/* <MapEvents
						nativeTag={ mainMapViewId }
					/> */}


					{/* { showLayerBitmapTile && <LayerBitmapTile
						url={ 'https://mt1.google.com/vt/lyrs=r&x={X}&y={Y}&z={Z}' }
						cacheSize={ 10 * 1024 * 1024 }
					/> } */}


					{ showLayerBitmapTile && <LayerMBTilesBitmap
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/OAM-World-1-10-J70.mbtiles' }
					/> }

					<LayerMapsforge
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/Peru-Ecuador_oam.osm.map' }
						renderTheme={ renderTheme }
						renderStyle={ renderStyle }
						renderOverlays={ renderOverlays }
					/>

					<LayerPathSlopeGradient
						slopeSimplificationTolerance={ slopeSimplificationTolerance }
						flattenWindowSize={ flattenWindowSize }
						onCreate={ response => {

							console.log( 'debug response', response ); // debug

							if ( response.coordinates ) {
								setCoordinates( response.coordinates );
							}
							if ( response.coordinatesSimplified ) {
								setCoordinatesSimplified( response.coordinatesSimplified );
							}
						} }
						onRemove={ () => {
							setCoordinates( [] );
							setCoordinatesSimplified( [] );
						} }
						responseInclude={ {
							coordinates: true,
							coordinatesSimplified: true,
						} }
						filePath={ '/storage/emulated/0/Android/media/jhotadhari.reactnative.mapsforge.vtm.example/dummy/randomTrack.gpx' }
					/>
					{/* <LayerPathSlopeGradient
						filePath={ '/storage/emulated/0/Documents/orux/tracklogs/2024-10-08 0900__20241008_0900.gpx' }
					/>

					<LayerPathSlopeGradient
						filePath={ '/storage/emulated/0/Documents/orux/tracklogs/2024-10-07 1000__20241007_1000.gpx' }
					/>

					<LayerPathSlopeGradient
						filePath={ '/storage/emulated/0/Documents/orux/tracklogs/2024-10-06 1026__20241006_1026.gpx' }
					/> */}



					{/* }
					{ showMarkers && [...locations].map( ( latLong, index ) => <Marker
						latLong={ latLong }
						key={ index }
						tabDistanceThreshold={ 80 }
						icon={ icons[iconIndex] }
						onTab={ res => {
							console.log( 'debug Marker res', res ); // debug
						} }
					/> ) } */}

					<LayerScalebar/>

				</MapContainer> }


				<View
					onLayout={ e => {
						const { height } = e.nativeEvent.layout;
						setBottomTopHeight( height );
					} }
					style={ {
						...style,
						position: 'relative',
						width,
						// paddingTop: 10,
					} }
				>

					<ChartWrapper
						coordinates={ coordinates }
						coordinatesSimplified={ coordinatesSimplified }
					/>

				</View>

			</View> }



				{/* <View
					style={ {
						...style,
						flexDirection: 'row',
						justifyContent: 'space-evenly',
						alignItems: 'center',
						width,
						marginBottom: 10,
					} }
				>

					<Text>{ promiseQueueState > 0 ? 'busy' : 'idle'  }</Text>

					<Button
						onPress={ () => {
							promiseQueue.enqueue( () => {
								MapContainerModule.zoomIn( mainMapViewId );
							} );
						} }
						title="+"
						disabled={ promiseQueueState > 0 }
					/>
					<Button
						onPress={ () => {
							promiseQueue.enqueue( () => {
								MapContainerModule.zoomOut( mainMapViewId );
							} );
						} }
						title="-"
						disabled={ promiseQueueState > 0 }
					/>
				</View>

				<View
					style={ {
						...style,
						flexDirection: 'row',
						justifyContent: 'space-evenly',
						alignItems: 'center',
						width,
						marginBottom: 10,
					} }
				>

					<PickerModalControl
						headerLabel={ 'Map file' }
						options={ mapFileOptions }
						values={ [mapFile] }
						onChange={ clickedVal => setMapFile( clickedVal ) }
						closeOnChange={ false }
						disabled={ promiseQueueState > 0 }
					/>

					<PickerModalControl
						headerLabel={ 'Render theme' }
						options={ renderThemeOptions }
						values={ [renderTheme] }
						onChange={ clickedVal => setRenderTheme( clickedVal ) }
						closeOnChange={ false }
						disabled={ promiseQueueState > 0 }
					/>

				</View> */}

			{/* </View> */}
		</SafeAreaView>

		</GestureHandlerRootView>
	);
};

export default App;
