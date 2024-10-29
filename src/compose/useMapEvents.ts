/**
 * External dependencies
 */
import { isFunction } from 'lodash-es';
import { useEffect } from 'react';
import { NativeEventEmitter } from 'react-native';

const useMapEvents = ( {
	nativeTag,
	onMapEvent,
} : {
	nativeTag: null | number,
	onMapEvent?: null | ( ( result: object ) => void ),
} ) : void => {

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'onMapEvent', result => {
			if ( result.nativeTag === nativeTag && isFunction( onMapEvent ) ) {
                onMapEvent( result );
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
