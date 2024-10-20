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
import { MapLayerMBTilesBitmapModule } from '../nativeMapModules';
import { isNumber, isFunction } from 'lodash-es';

const Module = MapLayerMBTilesBitmapModule;

const LayerMBTilesBitmap = ( {
	mapViewNativeTag,
	reactTreeIndex,
    mapFile,
    alpha,
    transparentColor,
	onCreate,
	onRemove,
	onChange,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	mapFile = mapFile || '';
    alpha = isNumber( alpha ) ? alpha : 256,
	transparentColor = transparentColor || '';

	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;
	onChange = isFunction( onChange ) ? onChange : null;

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			Module.createLayer(
				mapViewNativeTag,
                mapFile,
                parseInt( alpha, 10 ),
                transparentColor,
				parseInt( reactTreeIndex, 10 ),
			).then( newUuid => {
				if ( newUuid ) {
					setUuid( newUuid );
					setRandom( Math.random() );
					( null === triggerCreateNew
						? isFunction( onCreate ) ? onCreate( { uuid: newUuid } ) : null
						: isFunction( onChange ) ? onChange( { uuid: newUuid } ) : null
					);
				}

			} );
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
					);
				} ).then( removedUuid => {
                    if ( removedUuid ) {
						isFunction( onRemove ) ? onRemove( { uuid: removedUuid } ) : null;
                    }
                } );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
                Module.removeLayer(
                    mapViewNativeTag,
                    uuid
                ).then( removedUuid => {
                    if ( removedUuid ) {
                        setUuid( null )
                        setTriggerCreateNew( Math.random() );
                    }
                } );
            } );
		}
	}, [
		mapFile,
		alpha,
		transparentColor,
	] );

	return null;
};
LayerMBTilesBitmap.isMapLayer = true;

LayerMBTilesBitmap.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
    mapFile: PropTypes.string,
    alpha: PropTypes.number,
    transparentColor: PropTypes.string,
	onCreate: PropTypes.func,
	onRemove: PropTypes.func,
	onChange: PropTypes.func,
};

export default LayerMBTilesBitmap;
