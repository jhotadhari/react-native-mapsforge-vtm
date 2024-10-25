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
import { MapLayerBitmapTileModule } from '../nativeMapModules';
import { isNumber, isFunction } from 'lodash-es';

const Module = MapLayerBitmapTileModule;

const LayerBitmapTile = ( {
	mapViewNativeTag,
	reactTreeIndex,
    url,
    zoomMin,
    zoomMax,
    cacheSize,
	onCreate,
	onRemove,
	onChange,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	url = url || 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png';
    zoomMin = isNumber( zoomMin ) ? zoomMin : 1,
    zoomMax = isNumber( zoomMax ) ? zoomMax : 20,
	cacheSize = isNumber( cacheSize ) ? cacheSize : 0 * 1024 * 1024;

	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;
	onChange = isFunction( onChange ) ? onChange : null;

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			Module.createLayer(
				mapViewNativeTag,
                url,
				parseInt( zoomMin, 10 ),
				parseInt( zoomMax, 10 ),
                parseInt( cacheSize, 10 ),
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
		url,
		zoomMin,
		zoomMax,
		cacheSize,
	] );

	return null;
};
LayerBitmapTile.isMapLayer = true;

LayerBitmapTile.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
    url: PropTypes.string,
    zoomMin: PropTypes.number,
    zoomMax: PropTypes.number,
    cacheSize: PropTypes.number,
};

export default LayerBitmapTile;
