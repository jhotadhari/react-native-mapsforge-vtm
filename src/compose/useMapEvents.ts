/**
 * External dependencies
 */
import { isFunction } from 'lodash-es';
import { useEffect } from 'react';
import { NativeEventEmitter } from 'react-native';

/**
 * Internal dependencies
 */
import type { mapEvent  } from '../types';

const useMapEvents = ( {
	nativeTag,
	onMapEvent,
} : {
	nativeTag: null | number,
	onMapEvent?: null | ( ( response: mapEvent ) => void ),
} ) : void => {

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'onMapEvent', response => {
			if ( response.nativeTag === nativeTag && isFunction( onMapEvent ) ) {
                onMapEvent( response );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		nativeTag,
		onMapEvent,
	] );

};

export default useMapEvents;
