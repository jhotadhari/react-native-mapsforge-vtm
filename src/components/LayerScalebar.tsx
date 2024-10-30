/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerScalebarModule } from '../nativeMapModules';

const Module = MapLayerScalebarModule;

export type LayerScalebarResponse = {
	uuid: string;
};

export type LayerScalebarProps = {
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	onCreate?: null | ( ( response: LayerScalebarResponse ) => void );
	onRemove?: null | ( ( response: LayerScalebarResponse ) => void );
};

const LayerScalebar = ( {
	mapViewNativeTag = null,
	reactTreeIndex,
	onCreate,
	onRemove,
} : LayerScalebarProps ) => {

	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				reactTreeIndex
			).then( ( response: LayerScalebarResponse ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				onCreate ? onCreate( response ) : null;
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

	return null;
};
LayerScalebar.isMapLayer = true;

export default LayerScalebar;
