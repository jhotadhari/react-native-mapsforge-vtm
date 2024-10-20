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

const LayerPath = ( {
	mapViewNativeTag,
	positions,
	filePath,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );

	positions = isArray( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			MapLayerPathModule.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				reactTreeIndex,
			).then( newUuid => {
				if ( newUuid ) {
					setUuid( newUuid );
					setRandom( Math.random() );
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
					MapLayerPathModule.removeLayer(
						mapViewNativeTag,
						uuid
					);
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
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
