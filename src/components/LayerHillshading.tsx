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

export type ShadingAlgorithm = 'SimpleShadingAlgorithm'
	| 'DiffuseLightShadingAlgorithm'
	| 'StandardClasyHillShading'
	| 'SimpleClasyHillShading'
	| 'HalfResClasyHillShading'
	| 'HiResClasyHillShading'
	| 'AdaptiveClasyHillShading';

export type ShadingAlgorithmOptions = {
	linearity?: number;
	scale?: number;
	heightAngle?: number;
	maxSlope?: number;
	minSlope?: number;
	asymmetryFactor?: number;
	readingThreadsCount?: number;
	computingThreadsCount?: number;
	isPreprocess?: boolean;
	isHqEnabled?: boolean;
	qualityScale?: number;
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
	CLASY_ADAPTIVE: 'AdaptiveClasyHillShading',		// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/AdaptiveClasyHillShading.java
	CLASY_STANDARD: 'StandardClasyHillShading',		// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/StandardClasyHillShading.java
	CLASY_SIMPLE: 'SimpleClasyHillShading',			// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/SimpleClasyHillShading.java
	CLASY_HALF_RES: 'HalfResClasyHillShading',		// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HalfResClasyHillShading.java
	CLASY_HI_RES: 'HiResClasyHillShading',			// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HiResClasyHillShading.java
	SIMPLE: 'SimpleShadingAlgorithm',				// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/SimpleShadingAlgorithm.java
	DIFFUSE_LIGHT: 'DiffuseLightShadingAlgorithm',	// https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/DiffuseLightShadingAlgorithm.java
};

const clasyParamsKeys = [
	'maxSlope',
	'minSlope',
	'asymmetryFactor',
	'readingThreadsCount',
	'computingThreadsCount',
	'isPreprocess',
];

const shadingAlgorithmsOptionKeys : { [value: string]: string[] } = {
	CLASY_ADAPTIVE: [...clasyParamsKeys,'isHqEnabled','qualityScale'],
	CLASY_STANDARD: clasyParamsKeys,
	CLASY_SIMPLE: clasyParamsKeys,
	CLASY_HALF_RES: clasyParamsKeys,
	CLASY_HI_RES: clasyParamsKeys,
	SIMPLE: ['linearity','scale'],
	DIFFUSE_LIGHT: ['heightAngle'],
};

const shadingAlgorithmOptionsDefaults : ShadingAlgorithmOptions = {
	linearity: 0.1,					// 1 or higher for linear grade, 0 or lower for a triple-applied sine of grade that gives high emphasis on changes in slope in near-flat areas, but reduces details within steep slopes (default 0.1).
	scale: 0.666,					// scales the input slopes, with lower values slopes will saturate later, but nuances closer to flat will suffer (default: 0.666)
	heightAngle: 50,				// height angle of light source over ground (in degrees 0..90)
	maxSlope: 80,
	minSlope: 0,
	asymmetryFactor: 0.5,
	readingThreadsCount: -1,		// -1 and java fallback Math.max(1, AvailableProcessors);
	computingThreadsCount: -1,		// -1 and java fallback AvailableProcessors
	isPreprocess: true,
	isHqEnabled: true,
	qualityScale: 1,
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

LayerHillshading.shadingAlgorithmsOptionKeys = shadingAlgorithmsOptionKeys;

LayerHillshading.shadingAlgorithmOptionsDefaults = shadingAlgorithmOptionsDefaults;

export default LayerHillshading;
