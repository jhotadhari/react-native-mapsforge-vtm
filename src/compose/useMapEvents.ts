/**
 * External dependencies
 */
import { isFunction } from 'lodash-es';
import { useEffect } from 'react';
import { NativeEventEmitter } from 'react-native';

/**
 * Internal dependencies
 */
import type { MapEventResponse  } from '../types';

const useMapEvents = ( {
	nativeNodeHandle,
	onMapEvent,
} : {
	nativeNodeHandle: null | number,
	onMapEvent?: null | ( ( response: MapEventResponse ) => void ),
} ) : void => {

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'onMapEvent', ( response : MapEventResponse ) => {
			if ( response.nativeNodeHandle === nativeNodeHandle && isFunction( onMapEvent ) ) {
                onMapEvent( response );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		nativeNodeHandle,
		onMapEvent,
	] );

};

export default useMapEvents;
