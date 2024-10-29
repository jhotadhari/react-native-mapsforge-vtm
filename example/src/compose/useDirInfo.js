/**
 * External dependencies
 */
import { useState, useEffect } from 'react';
import {
	NativeModules,
} from 'react-native';

/**
 * Internal dependencies
 */
import { LINKING_ERROR } from '../constants';

const FsModule = NativeModules.FsModule
	? NativeModules.FsModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

const useDirInfo = navDir => {
	// navDir = navDir || '/storage/emulated/0/Download';
	const [navParent,setNavParent] = useState( null );
	const [navChildren,setNavChildren] = useState( null );
	useEffect( () => {
		if ( navDir ) {
			FsModule.getInfo( navDir ).then( info => {
				if ( info.navParent ) {
					setNavParent( info.navParent );
				}
				if ( info.navChildren ) {
					setNavChildren( info.navChildren );
				}
			} );
		}
	}, [navDir] );
	return { navParent, navChildren };
}

export default useDirInfo;