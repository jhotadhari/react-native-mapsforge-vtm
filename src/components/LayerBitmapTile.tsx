/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';

/**
 * Internal dependencies
 */
import useRefState from '../../src/compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerBitmapTileModule } from '../nativeMapModules';
import { isNumber, isFunction } from 'lodash-es';

const Module = MapLayerBitmapTileModule;

export type LayerBitmapTileResponse = {
	uuid: string
	// ??? ...
}; // ???


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
			).then( ( newUuid: string ) => {	// ??? change to object
				if ( newUuid ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
					setUuid( newUuid );
					setRandom( Math.random() );
					( null === triggerCreateNew
						? onCreate ? onCreate( { uuid: newUuid } ) : null
						: onChange ? onChange( { uuid: newUuid } ) : null
					);
				}
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
						if ( removedUuid ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
							onRemove ? onRemove( { uuid: removedUuid } ) : null;
						}
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
					if ( removedUuid ) {
						setUuid( null );
						setTriggerCreateNew( Math.random() );
					}
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
