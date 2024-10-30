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

const useMapLayersCreated = ( nativeNodeHandle: null | number | undefined ): boolean =>  {

	const [mapLayersCreated, setMapLayersCreated] = useState( false );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MapLayersCreated', result => {
			if ( result.nativeNodeHandle === nativeNodeHandle ) {
				setMapLayersCreated( true );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [nativeNodeHandle] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			MapContainerModule.getLayersCreated( nativeNodeHandle ).then( ( created: boolean ) => {
				setMapLayersCreated( created );
			} ).catch( ( err: any ) => console.log( 'Error', err ) );
		}
	}, [nativeNodeHandle] );

	return mapLayersCreated;
};

export default useMapLayersCreated;
