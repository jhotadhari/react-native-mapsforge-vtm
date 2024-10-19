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
import { MapLayerHillshadingModule } from '../nativeMapModules';
import { isNumber, isObject, isString } from 'lodash-es';

const shadingAlgorithms = {
	SIMPLE: 'SimpleShadingAlgorithm',
	DIFFUSE_LIGHT: 'DiffuseLightShadingAlgorithm',
};

const shadingAlgorithmOptionsDefaults = {
	linearity: 0.1,		// SimpleShadingAlgorithm		// 1 or higher for linear grade, 0 or lower for a triple-applied sine of grade that gives high emphasis on changes in slope in near-flat areas, but reduces details within steep slopes (default 0.1).
	scale: 0.666,		// SimpleShadingAlgorithm		// scales the input slopes, with lower values slopes will saturate later, but nuances closer to flat will suffer (default: 0.666)
	heightAngle: 50,	// DiffuseLightShadingAlgorithm	// height angle of light source over ground (in degrees 0..90)
};

const LayerHillshading = ( {
	mapViewNativeTag,
	hgtDirPath,
	zoomMin,
	zoomMax,
	shadingAlgorithm,
	shadingAlgorithmOptions,
	magnitude,
	cacheSize,
	reactTreeIndex,
} ) => {

	const [random, setRandom] = useState( 0 );
	const [hash, setHash] = useRefState( null );

	hgtDirPath = hgtDirPath || "";
	zoomMin = isNumber( zoomMin ) ? Math.round( zoomMin ) : 6;
	zoomMax = isNumber( zoomMax ) ? Math.round( zoomMax ) : 20;
	shadingAlgorithm = isString( shadingAlgorithm ) && Object.values( shadingAlgorithms ).includes( shadingAlgorithm ) ? shadingAlgorithm : shadingAlgorithms.SIMPLE;
	shadingAlgorithmOptions = isObject( shadingAlgorithmOptions )
		? { ...shadingAlgorithmOptionsDefaults, ...shadingAlgorithmOptions }
		: shadingAlgorithmOptionsDefaults;
	magnitude = isNumber( magnitude ) ? Math.round( magnitude ) : 90;
	cacheSize = isNumber( cacheSize ) ? Math.round( cacheSize ) : 64;

	const createLayer = () => {
		setHash( false );
		promiseQueue.enqueue( () => {
			MapLayerHillshadingModule.createLayer(
				mapViewNativeTag,
				hgtDirPath,
				zoomMin,
				zoomMax,
				shadingAlgorithm,
				shadingAlgorithmOptions,
				magnitude,
				cacheSize,
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
					MapLayerHillshadingModule.removeLayer(
						mapViewNativeTag,
						hash
					);
				} );
			}
		};
	}, [
		mapViewNativeTag,
		hgtDirPath,
		zoomMin,
		zoomMax,
		shadingAlgorithm,
		shadingAlgorithmOptions,
		!! hash,
	] );

	return null;
};
LayerHillshading.isMapLayer = true;

LayerHillshading.shadingAlgorithms = shadingAlgorithms;

LayerHillshading.propTypes = {
	mapViewNativeTag: PropTypes.number,
	hgtDirPath: PropTypes.string,
	zoomMin: PropTypes.number,
	zoomMax: PropTypes.number,
	shadingAlgorithm: PropTypes.string,
	shadingAlgorithmOptions: PropTypes.object,
	cacheSize: PropTypes.number,
	reactTreeIndex: PropTypes.number,
};

export default LayerHillshading;
