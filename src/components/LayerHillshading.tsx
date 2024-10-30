/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerHillshadingModule } from '../nativeMapModules';
import { get } from 'lodash-es';

const Module = MapLayerHillshadingModule;

export type ShadingAlgorithm = 'SimpleShadingAlgorithm' | 'DiffuseLightShadingAlgorithm';

export type ShadingAlgorithmOptions = {
	linearity?: number;
	scale?: number;
	heightAngle?: number;
};

export type LayerHillshadingResponse = {
	uuid: string
};

export type LayerHillshadingProps = {
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	hgtDirPath?: `/${string}` | `content://${string}`;
	zoomMin?: number;
	zoomMax?: number;
	shadingAlgorithm?: ShadingAlgorithm;
	shadingAlgorithmOptions?: ShadingAlgorithmOptions;
	magnitude?: number;
	cacheSize?: number;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerHillshadingResponse ) => void );
	onChange?: null | ( ( response: LayerHillshadingResponse ) => void );
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
	mapViewNativeTag,
	hgtDirPath,
	zoomMin = 6,
	zoomMax = 20,
	shadingAlgorithm = shadingAlgorithms.SIMPLE,
	shadingAlgorithmOptions = shadingAlgorithmOptionsDefaults,
	magnitude = 90,
	cacheSize = 64,
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
} : LayerHillshadingProps ) => {

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
				mapViewNativeTag,
				hgtDirPath,
				zoomMin,
				zoomMax,
				shadingAlgorithm,
				shadingAlgorithmOptions,
				magnitude,
				cacheSize,
				reactTreeIndex
			).then( ( response : false | LayerHillshadingResponse ) => {
				if ( response ) {		// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
					setUuid( response.uuid );
					setRandom( Math.random() );
					( null === triggerCreateNew
						? ( onCreate ? onCreate( response ) : null )
						: ( onChange ? onChange( response ) : null )
					);
				}
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
		} );
	};

	useEffect( () => {
		if ( uuid === null && mapViewNativeTag ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid: string ) => {
						if ( removedUuid ) { // ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
							onRemove ? onRemove( { uuid: removedUuid } ) : null;
						}
					} );
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid: string ) => {
						if ( removedUuid ) {		// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
							setUuid( null );
							setTriggerCreateNew( Math.random() );
						}
					} );
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
		Object.keys( shadingAlgorithmOptions ).map( key => key + get( shadingAlgorithmOptions, key ) ).join( '' ),
	] );

	return null;
};
LayerHillshading.isMapLayer = true;

LayerHillshading.shadingAlgorithms = shadingAlgorithms;

export default LayerHillshading;
