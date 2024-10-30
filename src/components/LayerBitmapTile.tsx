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

const Module = MapLayerBitmapTileModule;

export type LayerBitmapTileResponse = {
	uuid: string;
};

export type LayerBitmapTileProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	url?: string;
	zoomMin?: number;
	zoomMax?: number;
	cacheSize?: number;
	onCreate?: null | ( ( result: { uuid: string } ) => void );
	onRemove?: null | ( ( result: { uuid: string } ) => void );
	onChange?: null | ( ( result: { uuid: string } ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerBitmapTile = ( {
	nativeNodeHandle,
	reactTreeIndex,
    url = 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png',
    zoomMin = 1,
    zoomMax = 20,
    cacheSize =  0 * 1024 * 1024,
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
				Math.round( cacheSize ),
				reactTreeIndex
			).then( ( response: LayerBitmapTileResponse ) => {
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
	] );

	return null;
};
LayerBitmapTile.isMapLayer = true;

export default LayerBitmapTile;
