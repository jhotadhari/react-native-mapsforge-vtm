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

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			MapLayerMBTilesBitmapModule.createLayer(
				mapViewNativeTag,
                mapFile,
                parseInt( alpha, 10 ),
                transparentColor,
				parseInt( reactTreeIndex, 10 ),
			).then( newUuid => {
				if ( newUuid ) {
					setUuid( newUuid );
					setRandom( Math.random() );
					isFunction( onCreate ) && null === triggerCreateNew ? onCreate( response ) : null;
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
					MapLayerMBTilesBitmapModule.removeLayer(
						mapViewNativeTag,
						uuid
					);
				} ).then( removedUuid => {
                    if ( removedUuid ) {
						isFunction( onRemove ) ? onRemove( { removedUuid } ) : null;
                    }
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
                MapLayerMBTilesBitmapModule.removeLayer(
                    mapViewNativeTag,
                    uuid
                ).then( removedUuid => {
                    if ( removedUuid ) {
                        setUuid( null )
                        setTriggerCreateNew( Math.random() );
						isFunction( onChange ) ? onChange( { uuid: removedUuid } ) : null;
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
