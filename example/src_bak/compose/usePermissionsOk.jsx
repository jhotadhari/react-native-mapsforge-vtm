
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
import { LINKING_ERROR } from '../constants';

const PermissionModule = NativeModules.PermissionModule
	? NativeModules.PermissionModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

const usePermissionsOk = () => {

	const [permissionsOk, setPermissionsOk] = useState( false );

	useEffect( () => {
		if ( ! permissionsOk ) {
			PermissionModule.getStatus().then( status => {
				if ( status.isExternalStorageManager || ! status.needExternalStorageManager ) {
					setPermissionsOk( true );
				}
			} );
		}
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'PermissionsMaybeChanged', result => {
			if ( result.granted ) {
				setPermissionsOk( true );
			} else {
				setPermissionsOk( false );
			}
		} );
		return () => {
			eventListener.remove();
		};

	}, [] );

	return {
		permissionsOk,
		requestPermission: PermissionModule.requestPermission,
	};
};

export default usePermissionsOk;
