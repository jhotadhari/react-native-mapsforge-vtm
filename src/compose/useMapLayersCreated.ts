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

const useMapLayersCreated = ( mapViewNativeTag: null | number | undefined ): boolean =>  {

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
			MapContainerModule.getLayersCreated( mapViewNativeTag ).then( ( created: boolean ) => {
				setMapLayersCreated( created );
			} ).catch( ( err: any ) => console.log( 'Error', err ) );
		}
	}, [mapViewNativeTag] );

	return mapLayersCreated;
};

export default useMapLayersCreated;
