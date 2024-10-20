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

// 0	never include in response.
// 1	include in response on create.
// 2	include in response on change.
const responseIncludeDefaults = {
	coordinates: 0,
	coordinatesSimplified: 0,
};

const Module = MapLayerPathSlopeGradientModule;

const LayerPathSlopeGradient = ( {
	mapViewNativeTag,
	positions,
	filePath,
	strokeWidth,
	slopeColors,
	slopeSimplificationTolerance,
	flattenWindowSize,
	responseInclude,
	onCreate,
	onRemove,
	onChange,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState( null );

	positions = isArray( positions ) ? positions : [];
	filePath = isString( filePath ) && filePath.length > 0 ? filePath : '';
	strokeWidth = isNumber( strokeWidth ) && !! strokeWidth ? parseInt( strokeWidth, 10 ) : 4;
	slopeSimplificationTolerance = isNumber( slopeSimplificationTolerance ) ? slopeSimplificationTolerance : 7;
	flattenWindowSize = isNumber( flattenWindowSize ) && flattenWindowSize % 2 != 0 && flattenWindowSize > 5 ? flattenWindowSize : 9;
	slopeColors = isArray( slopeColors ) && slopeColors.length > 0 ? slopeColors : [
        [-25, '#000a70'],
        [-10, '#0000ff'],
        [-5, '#01c2ff'],
        [0, '#35fd2d'],
        [5, '#f9ff00'],
        [10, '#ff0000'],
        [25, '#810500'],
    ];
	slopeColors = [...slopeColors].sort( ( a, b ) => {
		if ( a[0] < b[0] ) {
		  	return -1;
		}
		if ( a[0] > b[0] ) {
		  	return 1;
		}
		return 0;
	} );
	responseInclude = isObject( responseInclude )
		? { ...responseIncludeDefaults, ...responseInclude }
		: responseIncludeDefaults;

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
				strokeWidth,
				slopeColors,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude,
				reactTreeIndex,
			).then( response => {
				if ( response.uuid ) {
					setUuid( response.uuid );
					setRandom( Math.random() );
					( null === triggerCreateNew
						? isFunction( onCreate ) ? onCreate( response ) : null
						: isFunction( onChange ) ? onChange( response ) : null
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
					);
				} ).then( removedUuid => {
                    if ( removedUuid ) {
						isFunction( onRemove ) ? onRemove( { uuid: removedUuid } ) : null;
                    }
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
                Module.updateStrokeWidth(
                    mapViewNativeTag,
                    uuid,
					strokeWidth,
					responseInclude,
                ).then( response => {
					isFunction( onChange ) ? onChange( response ) : null;
                } );
            } );
		}
	}, [strokeWidth] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
                Module.updateSlopeColors(
					mapViewNativeTag,
					uuid,
					strokeWidth,
					slopeColors,
					responseInclude,
                ).then( response => {
					isFunction( onChange ) ? onChange( response ) : null;
                } );
            } );
		}
	}, [ slopeColors && Array.isArray( slopeColors ) && slopeColors.length
		? [...slopeColors].map( entry => entry.join( '' ) ).join( '' )
		: null
	] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
                Module.updateCoordinatesSimplified(
					mapViewNativeTag,
					uuid,
					strokeWidth,
					slopeSimplificationTolerance,
					flattenWindowSize,
					responseInclude,
                ).then( response => {
					isFunction( onChange ) ? onChange( response ) : null;
                } );
            } );
		}
	}, [
		slopeSimplificationTolerance,
		flattenWindowSize,
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
			? positions.join( '' )
			: null
		),
		filePath,
		Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' ),
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
	flattenWindowSize: PropTypes.number,
	responseInclude: PropTypes.object,
	onCreate: PropTypes.func,
	onRemove: PropTypes.func,
	onChange: PropTypes.func,
};

export default LayerPathSlopeGradient;
