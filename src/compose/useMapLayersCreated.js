/**
 * External dependencies
 */
import {
	useEffect,
	useState,
} from 'react';
import {
	NativeModules,
	NativeEventEmitter,
} from 'react-native';

/**
 * Internal dependencies
 */
const { MapContainerModule } = NativeModules;

const useMapLayersCreated = mapViewNativeTag => {

	const [mapLayersCreated, setMapLayersCreated] = useState( false );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MapLayersCreated', result => {
			if ( result.nativeTag === mapViewNativeTag ) {
				setMapLayersCreated( true );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [mapViewNativeTag] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
			MapContainerModule.getLayersCreated( mapViewNativeTag ).then( created => {
				setMapLayersCreated( !! created );
			} );
		}
	}, [mapViewNativeTag] );

	return mapLayersCreated;
};

export default useMapLayersCreated;
