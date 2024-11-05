
/**
 * External dependencies
 */
import React, {
	useEffect,
	useState,
} from 'react';
import {
	SafeAreaView,
	useColorScheme,
	useWindowDimensions,
	View,
	NativeModules,
} from 'react-native';

/**
 * Internal dependencies
 */
import Button from './components/Button.jsx';
import ExampleLayerBitmapTile from './examples/ExampleLayerBitmapTile.jsx';
import ExampleLayerMapsforge from './examples/ExampleLayerMapsforge.jsx';
import ExampleLayerMBTilesBitmap from './examples/ExampleLayerMBTilesBitmap.jsx';
import ExampleLayerPath from './examples/ExampleLayerPath.jsx';
import ExampleLayerPathSlopeGradient from './examples/ExampleLayerPathSlopeGradient.jsx';
import ExampleDem from './examples/ExampleDem.jsx';
import ExampleMarker from './examples/ExampleMarker.jsx';
import { LINKING_ERROR } from './constants.js';

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
		label: 'Example LayerPathSlopeGradient',
		value: 'ExampleLayerPathSlopeGradient',
		component: ExampleLayerPathSlopeGradient,
	},
	{
		label: 'Example DEM',
		value: 'ExampleDem',
		component: ExampleDem,
	},
	{
		label: 'Example Marker',
		value: 'ExampleMarker',
		component: ExampleMarker,
	},
];

const App = () => {

	const isDarkMode = useColorScheme() === 'dark';

	const style = {
		backgroundColor: isDarkMode ? 'black' : '#eee',
		color: isDarkMode ? '#eee' : 'black',
	};

	const [appDirs,setAppDirs] = useState( null );

	const [selectedExample,setSelectedExample] = useState( null );

	useEffect( () => {
		HelperModule.getAppDirs().then( dirs => {
			setAppDirs( dirs );
		} ).catch( err => console.log( 'ERROR', err ) );
	}, [] );

	const {
		width,
		height,
	} = useWindowDimensions();

	const ExampleComponent = null === selectedExample ? null : exampleOptions.find( option => option.value === selectedExample ).component;

	return <SafeAreaView style={ {
		...style,
		height,
		width,
		alignItems: 'center',
		justifyContent: 'space-evenly',
	} }>
		<View style={ { padding: 10 } }>

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
	</SafeAreaView>;
};

export default App;