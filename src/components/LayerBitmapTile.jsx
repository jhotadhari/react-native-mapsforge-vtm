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
import { isNumber } from 'lodash-es';

const LayerBitmapTile = ( {
	mapViewNativeTag,
	reactTreeIndex,
    url,
    zoomMin,
    zoomMax,
    cacheSize,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );

	url = url || 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png';
    zoomMin = isNumber( zoomMin ) ? zoomMin : 1,
    zoomMax = isNumber( zoomMax ) ? zoomMax : 20,
	cacheSize = isNumber( cacheSize ) ? cacheSize : 0 * 1024 * 1024;

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerBitmapTileModule.createLayer(
				mapViewNativeTag,
                url,
				parseInt( zoomMin, 10 ),
				parseInt( zoomMax, 10 ),
                parseInt( cacheSize, 10 ),
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
					MapLayerBitmapTileModule.removeLayer(
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
LayerBitmapTile.mapLayers = 1;

LayerBitmapTile.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
    url: PropTypes.string,
    zoomMin: PropTypes.number,
    zoomMax: PropTypes.number,
    cacheSize: PropTypes.number,
};

export default LayerBitmapTile;
