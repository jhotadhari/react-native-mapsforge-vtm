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
import { MapLayerPathModule } from '../nativeMapModules';
import { isArray, isString } from 'lodash-es';
import { isValidPositions } from '../utils';

const Module = MapLayerPathModule;

const LayerPath = ( {
	mapViewNativeTag,
	positions,
	filePath,
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	positions = isValidPositions( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';

	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;
	onChange = isFunction( onChange ) ? onChange : null;

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			Module.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				reactTreeIndex,
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
		( positions && Array.isArray( positions ) && positions.length
			? [...positions].map( pos => pos.lng + pos.lat ).join( '' )
			: null
		),
		filePath,
	] );

	return null;
};
LayerPath.isMapLayer = true;

LayerPath.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
	positions: PropTypes.array,
	filePath: PropTypes.string,
};

export default LayerPath;
