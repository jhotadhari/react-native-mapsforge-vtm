/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerScalebarModule } from '../nativeMapModules';
import { isFunction } from 'lodash-es';

const Module = MapLayerScalebarModule;

const LayerScalebar = ( {
	mapViewNativeTag,
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );

	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;
	onChange = isFunction( onChange ) ? onChange : null;

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			Module.createLayer(
				mapViewNativeTag,
				reactTreeIndex,
			).then( newUuid => {
				if ( newUuid ) {
					setUuid( newUuid );
					setRandom( Math.random() );

					// ( null === triggerCreateNew
					// 	? isFunction( onCreate ) ? onCreate( { uuid: newUuid } ) : null
					// 	: isFunction( onChange ) ? onChange( { uuid: newUuid } ) : null
					// );
					isFunction( onCreate ) ? onCreate( { uuid: newUuid } ) : null;
				}
			} ).catch( err => console.log( 'ERROR', err ) );
		} );
	};

	useEffect( () => {
		if ( uuid === null && mapViewNativeTag ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( removedUuid => {
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

LayerScalebar.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
};

export default LayerScalebar;
