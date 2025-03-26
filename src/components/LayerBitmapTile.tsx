/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerBitmapTileModule } from '../nativeMapModules';
import type { ResponseBase } from '../types';

const Module = MapLayerBitmapTileModule;

export type LayerBitmapTileProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	url?: string;
	zoomMin?: number;
	zoomMax?: number;
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	cacheSize?: number;	// mb
	cacheDirBase?: `/${string}`;
	cacheDirChild?: string;
	onCreate?: null | ( ( result: ResponseBase ) => void );
	onRemove?: null | ( ( result: ResponseBase ) => void );
	onChange?: null | ( ( result: ResponseBase ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerBitmapTile = ( {
	nativeNodeHandle,
	reactTreeIndex,
    url = 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png',
    zoomMin = 1,
    zoomMax = 20,
    enabledZoomMin = 1,
    enabledZoomMax = 20,
    cacheSize = 0,
	cacheDirBase = '/',	// if `/`, will fallback to java getReactApplicationContext().getCacheDir();
	cacheDirChild = '',	// if ``, will fallback to slugify url;
	onCreate,
	onRemove,
	onChange,
	onError,
} : LayerBitmapTileProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				url,
				Math.round( zoomMin ),
				Math.round( zoomMax ),
				Math.round( enabledZoomMin ),
				Math.round( enabledZoomMax ),
				Math.round( cacheSize ),
				cacheDirBase.trim(),
				cacheDirChild.trim(),
				reactTreeIndex
			).then( ( response: ResponseBase ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				( null === triggerCreateNew
					? onCreate ? onCreate( response ) : null
					: onChange ? onChange( response ) : null
				);
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

	// enabledZoomMin enabledZoomMax changed.
	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
			Module.updateEnabledZoomMinMax( nativeNodeHandle, uuid, Math.round( enabledZoomMin ), Math.round( enabledZoomMax ) )
			.catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
	] );

	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
            promiseQueue.enqueue( () => {
                return Module.removeLayer(
					nativeNodeHandle,
					uuid
				).then( () => {
					setUuid( null );
					setTriggerCreateNew( Math.random() );
				} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
            } );
		}
	}, [
		url,
		zoomMin,
		zoomMax,
		cacheSize,
		cacheDirBase,
		cacheDirChild,
	] );

	return null;
};
LayerBitmapTile.isMapLayer = true;

export default LayerBitmapTile;
