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
import { isNumber } from 'lodash-es';

const LayerMBTilesBitmap = ( {
	mapViewNativeTag,
	reactTreeIndex,
    mapFile,
    alpha,
    transparentColor,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );

	mapFile = mapFile || '';
    alpha = isNumber( alpha ) ? alpha : 256,
	transparentColor = transparentColor || '';

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerMBTilesBitmapModule.createLayer(
				mapViewNativeTag,
                mapFile,
                parseInt( alpha, 10 ),
                transparentColor,
				parseInt( reactTreeIndex, 10 ),
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
					MapLayerMBTilesBitmapModule.removeLayer(
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
LayerMBTilesBitmap.mapLayers = 1;

LayerMBTilesBitmap.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
    mapFile: PropTypes.string,
    alpha: PropTypes.number,
    transparentColor: PropTypes.string,
};

export default LayerMBTilesBitmap;
