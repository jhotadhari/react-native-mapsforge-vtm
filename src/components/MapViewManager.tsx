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
import type { HardwareKeyEventResponse, Location, ResponseInclude } from '../types';
import { LINKING_ERROR } from '../constants';

const ComponentName = 'MapViewManager';

type Props = {
	ref: MutableRefObject<number | Component<any, any, any> | ComponentClass<any, any> | null>;
	width: number;
	height: number;
	widthForLayoutSize: number;
	heightForLayoutSize: number;
	center: Location;
	moveEnabled: 1 | 0;
	tiltEnabled: 1 | 0;
	rotationEnabled: 1 | 0;
	zoomEnabled: 1 | 0;
	zoomLevel: number;
	zoomMin: number;
	zoomMax: number;
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
	responseInclude: ResponseInclude;
	hgtInterpolation:  1 | 0;
	mapEventRate: number;
	hgtReadFileRate: number;
	hgtFileInfoPurgeThreshold: number;
	emitsMapEvents: 1 | 0;
	emitsHardwareKeyUp: HardwareKeyEventResponse['keyCodeString'][];
};

const MapViewManager = UIManager.getViewManagerConfig( ComponentName ) != null
	? requireNativeComponent<Props>( ComponentName )
	: () => {
		throw new Error( LINKING_ERROR );
	};

export default MapViewManager;