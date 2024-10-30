/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerPathSlopeGradientModule } from '../nativeMapModules';
import { isArray, isFunction, isNumber, isObject, isString } from 'lodash-es';
import { isValidPositions } from '../utils';
import type { ResponseInclude, Location, LocationExtended, GeometryStyle } from '../types';

const Module = MapLayerPathSlopeGradientModule;

export type GradientColors = [number, `#${string}`][];

export type LayerPathSlopeGradientResponse = {
	uuid: string;
	coordinates?: LocationExtended[];
	coordinatesSimplified?: LocationExtended[];
};

export type LayerPathSlopeGradientProps = {
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	filePath?: null | `/${string}` | `content://${string}`;
	positions?: Location[];
	responseInclude?: ResponseInclude;
	slopeColors?: GradientColors;
	strokeWidth?: number;
	slopeSimplificationTolerance?: number;
	flattenWindowSize?: number;	// should be odd and greater 5.
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerPathSlopeGradientResponse ) => void );
	onChange?: null | ( ( response: LayerPathSlopeGradientResponse ) => void );
};

// 0	never include in response.
// 1	include in response on create.
// 2	include in response on change.
const responseIncludeDefaults : ResponseInclude = {
	coordinates: 0,
	coordinatesSimplified: 0,
	bounds: 0,
};

const slopeColorsDefault : GradientColors = [
	[-25, '#000a70'],
	[-10, '#0000ff'],
	[-5, '#01c2ff'],
	[0, '#35fd2d'],
	[5, '#f9ff00'],
	[10, '#ff0000'],
	[25, '#810500'],
];

const sortSlopeColors = ( slopeColors : GradientColors ) : GradientColors => [...slopeColors].sort( ( a, b ) => {
	if ( a[0] < b[0] ) {
		  return -1;
	}
	if ( a[0] > b[0] ) {
		  return 1;
	}
	return 0;
} );

const LayerPathSlopeGradient = ( {
	mapViewNativeTag,
	positions = [],
	filePath,
	strokeWidth = 4,
	slopeColors = slopeColorsDefault,
	slopeSimplificationTolerance = 7,
	flattenWindowSize = 9,
	responseInclude = responseIncludeDefaults,
	onCreate,
	onRemove,
	onChange,
	reactTreeIndex,
} : LayerPathSlopeGradientProps ) => {

	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	positions = isValidPositions( positions ) ? positions : [];
	strokeWidth = strokeWidth > 0 ? Math.round( strokeWidth ) : 4;
	slopeSimplificationTolerance = slopeSimplificationTolerance >= 0 ? Math.round( slopeSimplificationTolerance ) : 7;
	flattenWindowSize = flattenWindowSize % 2 != 0 && flattenWindowSize > 5 ? flattenWindowSize : 9;
	slopeColors = sortSlopeColors( slopeColors.length > 0 ? slopeColors : slopeColorsDefault );

	responseInclude = { ...responseIncludeDefaults, ...responseInclude };

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				strokeWidth,
				slopeColors,
				slopeSimplificationTolerance,
				flattenWindowSize,
				responseInclude,
				reactTreeIndex
			).then( ( response: LayerPathSlopeGradientResponse ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				( null === triggerCreateNew
					? ( onCreate ? onCreate( response ) : null )
					: ( onChange ? onChange( response ) : null )
				);
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
		} );
	};

	useEffect( () => {
		if ( uuid === null && mapViewNativeTag && ( filePath || positions.length > 0 ) ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
                	} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
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
                return Module.updateStrokeWidth(
                    mapViewNativeTag,
                    uuid,
					strokeWidth,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => console.log( 'ERROR', err ) );
            } );
		}
	}, [strokeWidth] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
               return Module.updateSlopeColors(
					mapViewNativeTag,
					uuid,
					strokeWidth,
					slopeColors,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => console.log( 'ERROR', err ) );
            } );
		}
	}, [[...slopeColors].map( entry => entry.join( '' ) ).join( '' )] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
            promiseQueue.enqueue( () => {
                return Module.updateCoordinatesSimplified(
					mapViewNativeTag,
					uuid,
					strokeWidth,
					slopeSimplificationTolerance,
					flattenWindowSize,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => console.log( 'ERROR', err ) );
            } );
		}
	}, [
		slopeSimplificationTolerance,
		flattenWindowSize,
	] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						setUuid( null )
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
				} );
			} else if ( uuid === null && ( filePath || positions.length > 0 ) ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		( positions.length > 0
			? [...positions].map( pos => pos.lng + pos.lat ).join( '' )
			: null
		),
		filePath,
		Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' ),
	] );

	return null;
};
LayerPathSlopeGradient.isMapLayer = true;

LayerPathSlopeGradient.slopeColorsDefault = slopeColorsDefault;

export default LayerPathSlopeGradient;
