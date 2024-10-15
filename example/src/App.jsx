
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

/**
 * react-native-mapsforge dependencies
 */
import {
	MapContainer,
	LayerMapsforge,
	LayerBitmapTile,
	LayerMBTilesBitmap,
	LayerScalebar,
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


	const [barTopHeight,setBarTopHeight] = useState( null );
	const [barBottomHeight,setBottomTopHeight] = useState( null );




	const [randomCenter,setRandomCenter] = useState( [-12.787, -74.973] );
	const doNewRandomCenter = () => setRandomCenter( [
		randomNumber( -12, -13 ),	// lat
		randomNumber( -74, -75 ),	// long
	] );

	const [randomZoom,setRandomZoom] = useState( 8 );
	const doNewRandomZoom = () => setRandomZoom( Math.round( randomNumber( 8, 16 ) ) );

	const [randomMinZoom,setRandomMinZoom] = useState( 2 );
	const doNewRandomMinZoom = () => setRandomMinZoom( Math.round( randomNumber( 5, 20 ) ) );

	const [randomMaxZoom,setRandomMaxZoom] = useState( 20 );
	const doNewRandomMaxZoom = () => setRandomMaxZoom( Math.round( randomNumber( 5, 20 ) ) );


	const [randomViewportVal,setRandomViewportVal] = useState( 0 );
	const doNewViewportVal = () => setRandomViewportVal( Math.round( randomNumber( 200, 400 ) ) );




	const [moveEnabled,setMoveEnabled] = useState( true );
	const [tiltEnabled,setTiltEnabled] = useState( true );
	const [rotationEnabled,setRotationEnabled] = useState( true );
	const [zoomEnabled,setZoomEnabled] = useState( true );


	const promiseQueueState = usePromiseQueueState();

	// const [mapFile, setMapFile] = useState( mapFileOptions[0].value );
	const [showLayerMapsforge, setShowLayerMapsforge] = useState( true );
	const [showLayerBitmapTile, setShowLayerBitmapTile] = useState( true );
	// const [showMarkers, setShowMarkers] = useState( true );

	const [mainMapViewId, setMainMapViewId] = useState( null );

	// const [iconIndex, setIconIndex] = useState( 0 );

	const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );

	const [renderOverlays, setRenderOverlays] = useState( [] );
	const [renderTheme, setRenderTheme] = useState( renderThemeOptions.find( o => o.label === 'Alti' ).value );




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

				// console.log( 'debug test newItems', newItems ); // debug
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

	const mapHeight = barTopHeight && barBottomHeight
		? height - barTopHeight - barBottomHeight
		: null;

	return (
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
							justifyContent: 'space-evenly',
							alignItems: 'center',
							marginBottom: 10,
						} }
					>
						<Button
							onPress={ () => doNewViewportVal() }
							title="rand"
							disabled={ promiseQueueState > 0 }
						/>
						{/*
						<Button
							onPress={ () => MapContainerModule.zoomIn( mainMapViewId ) }
							title="z +"
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => MapContainerModule.zoomOut( mainMapViewId ) }
							title="z -"
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => doNewRandomZoom() }
							title="z rand"
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => doNewRandomMinZoom() }
							title="z min rand"
							disabled={ promiseQueueState > 0 }
						/>
						<Button
							onPress={ () => doNewRandomMaxZoom() }
							title="z max rand"
							disabled={ promiseQueueState > 0 }
						/>

						<Button
							onPress={ () => {
								setShowLayerMapsforge( ! showLayerMapsforge );
							} }
							title="Toggle Vector"
							disabled={ promiseQueueState > 0 }
						/>

						<Button
							onPress={ () => {
								setShowLayerBitmapTile( ! showLayerBitmapTile );
							} }
							title="Toggle Bitmap"
							disabled={ promiseQueueState > 0 }
						/> */}


						{/* <Button
							onPress={ () => {
								setMoveEnabled( ! moveEnabled );
							} }
							title="Toggle move"
							disabled={ promiseQueueState > 0 }
						/>


						<Button
							onPress={ () => {
								setTiltEnabled( ! tiltEnabled );
							} }
							title="Toggle tilt"
							disabled={ promiseQueueState > 0 }
						/>


						<Button
							onPress={ () => {
								setRotationEnabled( ! rotationEnabled );
							} }
							title="Toggle rotation"
							disabled={ promiseQueueState > 0 }
						/>


						<Button
							onPress={ () => {
								setZoomEnabled( ! zoomEnabled );
							} }
							title="Toggle zoom"
							disabled={ promiseQueueState > 0 }
						/> */}




					</View>
				</View>


				{ mapHeight && <MapContainer
					width={ randomViewportVal }
					height={ randomViewportVal }
					center={ randomCenter }
					zoomLevel={ randomZoom }
					mapViewNativeTag={ mainMapViewId }
					setMapViewNativeTag={ setMainMapViewId }
					minZoom={ randomMinZoom }
					maxZoom={ randomMaxZoom }


					// bearing={ randomViewportVal }



					moveEnabled = { moveEnabled }
					tiltEnabled = { tiltEnabled }
					rotationEnabled = { rotationEnabled }
					zoomEnabled = { zoomEnabled }




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

					{/*
					{ showLayerMapsforge && <LayerMapsforge
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/Peru-Ecuador_oam.osm.map' }
						renderTheme={ renderTheme }
						renderStyle={ renderStyle }
						renderOverlays={ renderOverlays }
					/> }

					{ showLayerMapsforge && <LayerMapsforge
						mapFile={ '/storage/emulated/0/Documents/orux/mapfiles/Panama_oam.osm.map' }
						renderTheme={ renderTheme }
						renderStyle={ renderStyle }
						renderOverlays={ renderOverlays }
					/> }
					*/}



					<LayerPath
						// positions={ [
						// 	[-12, -74],
						// 	[-13, -75],
						// 	[-13, -74],
						// 	[-13, -73],
						// 	[-12, -73],
						// ] }
						filePath={ '/storage/emulated/0/Android/media/jhotadhari.reactnative.mapsforge.vtm.example/dummy/randomTrack.gpx' }
					/>


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
	);
};

export default App;
