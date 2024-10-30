/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerPathSlopeGradientModule } from '../nativeMapModules';
import type { ResponseInclude, Location, LocationExtended } from '../types';

const Module = MapLayerPathSlopeGradientModule;

export type GradientColors = [number, `#${string}`][];

export type LayerPathSlopeGradientResponse = {
	uuid: string;
	coordinates?: LocationExtended[];
	coordinatesSimplified?: LocationExtended[];
};

export type LayerPathSlopeGradientProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
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
	onError?: null | ( ( err: any ) => void );
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
	nativeNodeHandle,
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
	onError,
} : LayerPathSlopeGradientProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	positions = positions || [];
	strokeWidth = strokeWidth > 0 ? Math.round( strokeWidth ) : 4;
	slopeSimplificationTolerance = slopeSimplificationTolerance >= 0 ? Math.round( slopeSimplificationTolerance ) : 7;
	flattenWindowSize = flattenWindowSize % 2 != 0 && flattenWindowSize > 5 ? flattenWindowSize : 9;
	slopeColors = sortSlopeColors( slopeColors.length > 0 ? slopeColors : slopeColorsDefault );
	responseInclude = { ...responseIncludeDefaults, ...responseInclude };

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
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
			} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		} );
	};

	useEffect( () => {
		if ( uuid === null && nativeNodeHandle && ( filePath || positions.length > 0 ) ) {
			createLayer();
		}
		return () => {
			if ( uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( ( removedUuid : string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
                	} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
		};
	}, [
		nativeNodeHandle,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
            promiseQueue.enqueue( () => {
                return Module.updateStrokeWidth(
                    nativeNodeHandle,
                    uuid,
					strokeWidth,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
            } );
		}
	}, [strokeWidth] );

	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
            promiseQueue.enqueue( () => {
               return Module.updateSlopeColors(
					nativeNodeHandle,
					uuid,
					strokeWidth,
					slopeColors,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
            } );
		}
	}, [[...slopeColors].map( entry => entry.join( '' ) ).join( '' )] );

	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
            promiseQueue.enqueue( () => {
                return Module.updateCoordinatesSimplified(
					nativeNodeHandle,
					uuid,
					strokeWidth,
					slopeSimplificationTolerance,
					flattenWindowSize,
					responseInclude,
				).then( ( response: LayerPathSlopeGradientResponse ) => {
					onChange ? onChange( response ) : null;
                } ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
            } );
		}
	}, [
		slopeSimplificationTolerance,
		flattenWindowSize,
	] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( () => {
						setUuid( null )
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
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
