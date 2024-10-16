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
import { MapLayerPathSlopeGradientModule } from '../nativeMapModules';
import { isArray, isNumber, isString } from 'lodash-es';

const LayerPathSlopeGradient = ( {
	mapViewNativeTag,
	positions,
	filePath,
	strokeWidth,
	slopeColors,
	slopeSimplificationTolerance,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	positions = isArray( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';
	strokeWidth = isNumber( strokeWidth ) && !! strokeWidth ? parseInt( strokeWidth, 10 ) : 4;
	slopeSimplificationTolerance = isNumber( slopeSimplificationTolerance ) ? slopeSimplificationTolerance : 0.0005;
	slopeColors = isArray( slopeColors ) ? slopeColors.sort( ( a, b ) => {
		if ( a[0] < b[0] ) {
		  	return -1;
		}
		if ( a[0] > b[0] ) {
		  	return 1;
		}
		return 0;
	} ) : [];

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerPathSlopeGradientModule.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				strokeWidth,
				slopeColors,
				slopeSimplificationTolerance,
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
					MapLayerPathSlopeGradientModule.removeLayer(
						mapViewNativeTag,
						hash
					);
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! hash,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag && hash ) {
            promiseQueue.enqueue( () => {
                MapLayerPathSlopeGradientModule.removeLayer(
                    mapViewNativeTag,
                    hash
                ).then( removedHash => {
                    if ( removedHash ) {
                        setHash( null )
                        setTriggerCreateNew( Math.random() );
                    }
                } );
            } );
		}
	}, [
		( positions && Array.isArray( positions ) && positions.length
			? positions.join( '' )
			: null
		),
		filePath,
		strokeWidth,
		( slopeColors && Array.isArray( slopeColors ) && slopeColors.length
			? [...slopeColors].map( entry => entry.join( '' ) ).join( '' )
			: null
		),
		slopeSimplificationTolerance,
	] );

	return null;
};
LayerPathSlopeGradient.isMapLayer = true;

LayerPathSlopeGradient.propTypes = {
	mapViewNativeTag: PropTypes.number,
	reactTreeIndex: PropTypes.number,
	positions: PropTypes.array,
	filePath: PropTypes.string,
	strokeWidth: PropTypes.number,
	slopeColors: PropTypes.array,
	slopeSimplificationTolerance: PropTypes.number,
};

export default LayerPathSlopeGradient;
