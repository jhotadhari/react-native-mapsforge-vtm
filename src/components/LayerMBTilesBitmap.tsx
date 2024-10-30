/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerMBTilesBitmapModule } from '../nativeMapModules';
import type { Bounds, Location } from '../types';

const Module = MapLayerMBTilesBitmapModule;

export type LayerMBTilesBitmapResponse = {
	uuid: string;
	bounds?: Bounds;
	minZoom?: number;
	maxZoom?: number;
	supportedFormats?: string[];
	attribution?: null | string;
	version?: string;
	format?: string;
	description?: string;
	center?: Location;
};

export type LayerMBTilesBitmapProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	mapFile?: `/${string}`;
	alpha?: number;
	transparentColor?: `#${string}`;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerMBTilesBitmapResponse ) => void );
	onChange?: null | ( ( response: LayerMBTilesBitmapResponse ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerMBTilesBitmap = ( {
	nativeNodeHandle,
	reactTreeIndex,
    mapFile,
    alpha = 256,
    transparentColor,
	onCreate,
	onRemove,
	onChange,
	onError,
} : LayerMBTilesBitmapProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

    alpha = Math.round( alpha );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				mapFile,
				alpha,
				transparentColor,
				reactTreeIndex
			).then( ( response: LayerMBTilesBitmapResponse ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				( null === triggerCreateNew
					? ( onCreate ? onCreate( response ) : null )
					: ( onChange ? onChange( response ) : null )
				);
			} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		} );
	};

	useEffect( () => {
		if ( uuid === null && nativeNodeHandle && mapFile ) {
			createLayer();
		}
		return () => {
			if ( uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( ( removedUuid : string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
		};
	}, [
		nativeNodeHandle,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( () => {
						setUuid( null );
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			} else if ( uuid === null && mapFile ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		mapFile,
		alpha,
		transparentColor,
	] );

	return null;
};
LayerMBTilesBitmap.isMapLayer = true;

export default LayerMBTilesBitmap;
