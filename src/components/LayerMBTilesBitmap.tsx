/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../../src/compose/useRefState';
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
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	mapFile?: string;
	alpha?: number;
	transparentColor?: string;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerMBTilesBitmapResponse ) => void );
	onChange?: null | ( ( response: LayerMBTilesBitmapResponse ) => void );
};

const LayerMBTilesBitmap = ( {
	mapViewNativeTag,
	reactTreeIndex,
    mapFile = '',
    alpha = 256,
    transparentColor = '',
	onCreate,
	onRemove,
	onChange,
} : LayerMBTilesBitmapProps ) => {

	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	mapFile = mapFile || '';
    alpha = Math.round( alpha );
	transparentColor = transparentColor || '';

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				mapFile,
				alpha,
				transparentColor,
				reactTreeIndex
			).then( ( response: false | LayerMBTilesBitmapResponse ) => {
				if ( response ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
					setUuid( response.uuid );
					setRandom( Math.random() );
					( null === triggerCreateNew
						? ( onCreate ? onCreate( response ) : null )
						: ( onChange ? onChange( response ) : null )
					);
				}
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
		} );
	};

	useEffect( () => {
		if ( uuid === null && mapViewNativeTag && mapFile ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						if ( removedUuid ) {
							onRemove ? onRemove( { uuid: removedUuid } ) : null;
						}
					} );
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						if ( removedUuid ) {
							setUuid( null );
							setTriggerCreateNew( Math.random() );
						}
					} );
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
