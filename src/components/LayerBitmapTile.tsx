/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

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
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	url?: string;
	zoomMin?: number;
	zoomMax?: number;
	cacheSize?: number;
	onCreate?: null | ( ( result: { uuid: string } ) => void );
	onRemove?: null | ( ( result: { uuid: string } ) => void );
	onChange?: null | ( ( result: { uuid: string } ) => void );
};

const LayerBitmapTile = ( {
	mapViewNativeTag,
	reactTreeIndex,
    url = 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png',
    zoomMin = 1,
    zoomMax = 20,
    cacheSize =  0 * 1024 * 1024,
	onCreate,
	onRemove,
	onChange,
} : LayerBitmapTileProps ) => {

	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				url,
				Math.round( zoomMin ),
				Math.round( zoomMax ),
				Math.round( cacheSize ),
				Math.round( reactTreeIndex )
			).then( ( response: LayerBitmapTileResponse ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				( null === triggerCreateNew
					? onCreate ? onCreate( response ) : null
					: onChange ? onChange( response ) : null
				);
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
		} );
	};

	useEffect( () => {
		if ( uuid === null && mapViewNativeTag ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid: string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
	] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
                return Module.removeLayer(
					mapViewNativeTag,
					uuid
				).then( ( removedUuid: string ) => {
					setUuid( null );
					setTriggerCreateNew( Math.random() );
				} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
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