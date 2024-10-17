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
import { isArray, isFunction, isNumber, isObject, isString } from 'lodash-es';

const LayerPathSlopeGradient = ( {
	mapViewNativeTag,
	positions,
	filePath,
	strokeWidth,
	slopeColors,
	slopeSimplificationTolerance,
	responseInclude,
	onCreate,
	onRemove,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	positions = isArray( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';
	strokeWidth = isNumber( strokeWidth ) && !! strokeWidth ? parseInt( strokeWidth, 10 ) : 4;
	slopeSimplificationTolerance = isNumber( slopeSimplificationTolerance ) ? slopeSimplificationTolerance : 5;
	slopeColors = isArray( slopeColors ) ? slopeColors.sort( ( a, b ) => {
		if ( a[0] < b[0] ) {
		  	return -1;
		}
		if ( a[0] > b[0] ) {
		  	return 1;
		}
		return 0;
	} ) : [
        [-25, '#000a70'],
        [-10, '#0000ff'],
        [-5, '#01c2ff'],
        [0, '#35fd2d'],
        [5, '#f9ff00'],
        [10, '#ff0000'],
        [25, '#810500'],
    ];
	responseInclude = isArray( responseInclude )
		? responseInclude
		: ( isObject( responseInclude )
			? Object.keys( responseInclude ).map( key => !! responseInclude[key] ? key : false ).filter( a => !! a )
			: []
		)
	onCreate = isFunction( onCreate ) ? onCreate : null;
	onRemove = isFunction( onRemove ) ? onRemove : null;


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
				responseInclude,
				reactTreeIndex,
			).then( response => {
				if ( response.hash ) {
					setHash( parseInt( response.hash, 10 ) );
					setRandom( Math.random() );
					onCreate( response );
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
						onRemove( { removedHash } )
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
