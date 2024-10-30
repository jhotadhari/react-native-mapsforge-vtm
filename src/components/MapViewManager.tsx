/**
 * External dependencies
 */
import type { Component, ComponentClass, MutableRefObject } from 'react';
import {
    requireNativeComponent,
    UIManager,
} from 'react-native';

/**
 * Internal dependencies
 */
import type { Location } from '../types';

const ComponentName = 'MapViewManager';

const LINKING_ERROR =
    `The package 'react-native-mapsforge-vtm' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- You are not using Expo Go\n';

export type Props = {
	ref: MutableRefObject<number | Component<any, any, any> | ComponentClass<any, any> | null>;
	width: number;
	height: number;
	widthForLayoutSize: number;
	heightForLayoutSize: number;
	center: Location;
	moveEnabled: 1 | 0;
	tiltEnabled: 1 | 0
	rotationEnabled: 1 | 0
	zoomEnabled: 1 | 0
	zoomLevel: number;
	minZoom: number;
	maxZoom: number;
	tilt: number;
	minTilt: number;
	maxTilt: number;
	bearing: number;
	minBearing: number;
	maxBearing: number;
	roll: number;
	minRoll: number;
	maxRoll: number;
	hgtDirPath?: `/${string}` | `content://${string}`;
};

const MapViewManager = UIManager.getViewManagerConfig( ComponentName ) != null
	? requireNativeComponent<Props>( ComponentName )
	: () => {
		throw new Error( LINKING_ERROR );
	};

export default MapViewManager;