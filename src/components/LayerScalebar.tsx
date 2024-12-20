/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerScalebarModule } from '../nativeMapModules';
import type { ResponseBase } from '../types';

const Module = MapLayerScalebarModule;

export type LayerScalebarProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	onCreate?: null | ( ( response: ResponseBase ) => void );
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerScalebar = ( {
	nativeNodeHandle = null,
	reactTreeIndex,
	onCreate,
	onRemove,
	onError,
} : LayerScalebarProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				reactTreeIndex
			).then( ( response: ResponseBase ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				onCreate ? onCreate( response ) : null;
			} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		} );
	};

	useEffect( () => {
		if ( uuid === null && nativeNodeHandle ) {
			createLayer();
		}
		return () => {
			if ( uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( ( removedUuid: string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
		};
	}, [
		nativeNodeHandle,
		!! uuid,
	] );

	return null;
};
LayerScalebar.isMapLayer = true;

export default LayerScalebar;
