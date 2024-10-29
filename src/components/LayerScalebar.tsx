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
import { isFunction } from 'lodash-es';

const Module = MapLayerScalebarModule;

export type LayerScalebarProps = {
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	onCreate?: null | ( ( result: object ) => void );
	onRemove?: null | ( ( result: object ) => void );
	onChange?: null | ( ( result: object ) => void );
};

const LayerScalebar = ( {
	mapViewNativeTag = null,
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
} : LayerScalebarProps ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );

	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;
	onChange = isFunction( onChange ) ? onChange : null;

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				reactTreeIndex
			).then( ( newUuid : string ) => {
				if (newUuid) {
					setUuid(newUuid);
					setRandom(Math.random());

					// ( null === triggerCreateNew
					// 	? isFunction( onCreate ) ? onCreate( { uuid: newUuid } ) : null
					// 	: isFunction( onChange ) ? onChange( { uuid: newUuid } ) : null
					// );
					isFunction( onCreate ) ? onCreate( { uuid: newUuid } ) : null;
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
						if ( removedUuid ) {
							isFunction( onRemove ) ? onRemove( { uuid: removedUuid } ) : null;
						}
					} );
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
