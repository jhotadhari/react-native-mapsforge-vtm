/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerHillshadingModule } from '../nativeMapModules';
import { get } from 'lodash-es';
import type { ResponseBase } from '../types';

const Module = MapLayerHillshadingModule;

export type ShadingAlgorithm = 'SimpleShadingAlgorithm' | 'DiffuseLightShadingAlgorithm';

export type ShadingAlgorithmOptions = {
	linearity?: number;
	scale?: number;
	heightAngle?: number;
};

export type LayerHillshadingProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	hgtDirPath?: `/${string}` | `content://${string}`;
	zoomMin?: number;
	zoomMax?: number;
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	shadingAlgorithm?: ShadingAlgorithm;
	shadingAlgorithmOptions?: ShadingAlgorithmOptions;
	magnitude?: number;
	cacheSize?: number;
	cacheDirBase?: `/${string}`;
	cacheDirChild?: string;
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onCreate?: null | ( ( response: ResponseBase ) => void );
	onChange?: null | ( ( response: ResponseBase ) => void );
	onError?: null | ( ( err: any ) => void );
};

const shadingAlgorithms : { [value: string]: ShadingAlgorithm } = {
	SIMPLE: 'SimpleShadingAlgorithm',
	DIFFUSE_LIGHT: 'DiffuseLightShadingAlgorithm',
};

const shadingAlgorithmOptionsDefaults : ShadingAlgorithmOptions = {
	linearity: 0.1,		// SimpleShadingAlgorithm		// 1 or higher for linear grade, 0 or lower for a triple-applied sine of grade that gives high emphasis on changes in slope in near-flat areas, but reduces details within steep slopes (default 0.1).
	scale: 0.666,		// SimpleShadingAlgorithm		// scales the input slopes, with lower values slopes will saturate later, but nuances closer to flat will suffer (default: 0.666)
	heightAngle: 50,	// DiffuseLightShadingAlgorithm	// height angle of light source over ground (in degrees 0..90)
};

const LayerHillshading = ( {
	nativeNodeHandle,
	hgtDirPath,
	zoomMin = 6,
	zoomMax = 20,
    enabledZoomMin = 6,
    enabledZoomMax = 20,
	shadingAlgorithm = shadingAlgorithms.SIMPLE,
	shadingAlgorithmOptions = shadingAlgorithmOptionsDefaults,
	magnitude = 90,
	cacheSize = 64,
	cacheDirBase = '/',	// if `/`, will fallback to java getReactApplicationContext().getCacheDir();
	cacheDirChild = '',	// if ``, will fallback to cache dbname;
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
	onError,
} : LayerHillshadingProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	shadingAlgorithmOptions = { ...shadingAlgorithmOptionsDefaults, ...shadingAlgorithmOptions };
	magnitude = Math.round( magnitude );
	cacheSize = Math.round( cacheSize );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				hgtDirPath,
				Math.round( zoomMin ),
				Math.round( zoomMax ),
				Math.round( enabledZoomMin ),
				Math.round( enabledZoomMax ),
				shadingAlgorithm,
				shadingAlgorithmOptions,
				Math.round( magnitude ),
				Math.round( cacheSize ),
				cacheDirBase.trim(),
				cacheDirChild.trim(),
				reactTreeIndex
			).then( ( response : ResponseBase ) => {
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
		if ( uuid === null && nativeNodeHandle ) {
			createLayer();
		}
		return () => {
			if ( uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( ( removedUuid: string ) => {
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

	// enabledZoomMin enabledZoomMax changed.
	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
			Module.updateEnabledZoomMinMax( nativeNodeHandle, uuid, Math.round( enabledZoomMin ), Math.round( enabledZoomMax ) )
			.catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
	] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( () => {
						setUuid( null );
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
		} else if ( uuid === null && hgtDirPath ) {
			setTriggerCreateNew( Math.random() );
		}
	}, [
		hgtDirPath,
		zoomMin,
		zoomMax,
		shadingAlgorithm,
		magnitude,
		cacheSize,
		cacheDirBase,
		cacheDirChild,
		Object.keys( shadingAlgorithmOptions ).map( key => key + get( shadingAlgorithmOptions, key ) ).join( '' ),
	] );

	return null;
};
LayerHillshading.isMapLayer = true;

LayerHillshading.shadingAlgorithms = shadingAlgorithms;

export default LayerHillshading;
