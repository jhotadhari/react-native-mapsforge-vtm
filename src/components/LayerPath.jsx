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
	const [hash, setHash] = useRefState( null );

	positions = isArray( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerPathModule.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				reactTreeIndex,
			).then( newHash => {
				if ( newHash ) {
					setHash( parseInt( newHash, 10 ) );
					setRandom( Math.random() );
				}

			} );
		} );
	};

	useEffect( () => {
		if ( hash === null && mapViewNativeTag ) {
			createLayer();
		}
		return () => {
			if ( hash && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					MapLayerPathModule.removeLayer(
						mapViewNativeTag,
						hash
					);
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! hash,
	] );

	return null;
};
LayerPath.isMapLayer = true;

LayerPath.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
};

export default LayerPath;
