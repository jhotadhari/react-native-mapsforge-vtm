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
import { MapLayerHillshadingModule } from '../nativeMapModules';

const LayerHillshading = ( {
	mapViewNativeTag,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerHillshadingModule.createLayer(
				mapViewNativeTag,
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
					MapLayerHillshadingModule.removeLayer(
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
LayerHillshading.isMapLayer = true;

LayerHillshading.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
};

export default LayerHillshading;
