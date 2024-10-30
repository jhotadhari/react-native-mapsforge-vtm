/**
 * External dependencies
 */
import React, {
	Component,
	cloneElement,
	useEffect,
	useRef,
	useState,
	type ComponentClass,
	type Dispatch,
	type SetStateAction,
} from 'react';
import {
	PixelRatio,
	UIManager,
	findNodeHandle,
	useWindowDimensions,
	ScrollView,
	NativeEventEmitter,
} from 'react-native';
import { get, isBoolean, isNumber } from 'lodash-es';

/**
 * Internal dependencies
 */
import MapViewManager from './MapViewManager';
import useMapLayersCreated from '../compose/useMapLayersCreated';
import { MapContainerModule } from '../nativeMapModules';
import { isValidPosition } from '../utils';
import type { Location, mapEvent } from '../types';

const createFragment = ( mapViewNativeTag: number ) : void => {
	const create = UIManager.getViewManagerConfig( 'MapViewManager' )?.Commands?.create;
	if ( create ) {
		try {
			UIManager.dispatchViewManagerCommand(
				mapViewNativeTag,
				create.toString(),
				[mapViewNativeTag],
			);
		} catch ( err ) {
			console.log( 'Error', err );
		}
	}
}

const useDefaultWidth = ( propsWidth: number | undefined ) => {
	const { width } = useWindowDimensions();
	return propsWidth || width;
};

export interface MapLifeCycleResponse extends mapEvent {
	type: 'onPause' | 'onResume';
};

export type MapContainerProps = {
	children?: React.ReactNode;
	mapViewNativeTag?: null | number;
	setMapViewNativeTag?: null | Dispatch<SetStateAction<number | null>>;
	onPause?: null | ( ( response: MapLifeCycleResponse ) => void );
	onResume?: null | ( ( response: MapLifeCycleResponse ) => void );
	width?: number;
	height?: number;
	center?: Location;
	moveEnabled?: 1 | 0 | boolean;
	tiltEnabled?: 1 | 0 | boolean
	rotationEnabled?: 1 | 0 | boolean
	zoomEnabled?: 1 | 0 | boolean
	zoomLevel?: number;
	minZoom?: number;
	maxZoom?: number;
	tilt?: number;
	minTilt?: number;
	maxTilt?: number;
	bearing?: number;
	minBearing?: number;
	maxBearing?: number;
	roll?: number;
	minRoll?: number;
	maxRoll?: number;
	hgtDirPath?: `/${string}` | `content://${string}`;
};

const defaultCenter : Location = {
	lng: -77.605,
	lat: -9.118,
};

const numOrBoolToNum = ( numOrBool: number | boolean | undefined, defaultVal: 1 | 0 ): ( 1 | 0 ) => {
	return isBoolean( numOrBool ) || isNumber( numOrBool ) ? ( !! numOrBool ? 1 : 0 ) : defaultVal;
};

const MapContainer = ( {
	children,
	mapViewNativeTag = null,	// It's not possible to control the nativeTag. It's a prop just to lift the state up.
	setMapViewNativeTag = null,
	onPause = null,
	onResume = null,
	width,
	height = 200,
	center = defaultCenter,
	moveEnabled,
	tiltEnabled,
	rotationEnabled,
	zoomEnabled,
	zoomLevel,
	minZoom,
	maxZoom,
	tilt = 0,
	minTilt = 0,
	maxTilt = 65,
	bearing = 0,
	minBearing = -180,
	maxBearing = 180,
	roll = 0,
	minRoll = -180,
	maxRoll = 180,
	hgtDirPath,
} : MapContainerProps ) => {

	const ref = useRef<number | Component<any, any, any> | ComponentClass<any, any> | null>( null );

	const [mapViewNativeTag_, setMapViewNativeTag_] = useState< number | null >( null );
	mapViewNativeTag = mapViewNativeTag ? mapViewNativeTag : mapViewNativeTag_;
	setMapViewNativeTag = setMapViewNativeTag ? setMapViewNativeTag : setMapViewNativeTag_;

	const mapLayersCreated = useMapLayersCreated( findNodeHandle( ref?.current ) );

	width = useDefaultWidth( width );

	center = isValidPosition( center ) ? center : defaultCenter;

	moveEnabled = numOrBoolToNum( moveEnabled, 1 );
	rotationEnabled = numOrBoolToNum( rotationEnabled, 1 );
	zoomEnabled = numOrBoolToNum( zoomEnabled, 1 );
	tiltEnabled = numOrBoolToNum( tiltEnabled, 1 );

	zoomLevel = isNumber( zoomLevel ) ? Math.round( zoomLevel ) : 12;
	minZoom = isNumber( minZoom ) ? Math.round( minZoom ) : 3;
	maxZoom = isNumber( maxZoom ) ? Math.round( maxZoom ) : 20;

	useEffect( () => {
		const nodeHandle = findNodeHandle( ref?.current );
		if ( nodeHandle ) {
			setMapViewNativeTag( nodeHandle );
		}
	}, [] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
			createFragment( mapViewNativeTag );
		}
	}, [mapViewNativeTag] );


	// center changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setCenter( mapViewNativeTag, center );
		}
	}, [Object.values( center ).join( '' )] );
	// moveEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'moveEnabled', moveEnabled );
		}
	}, [moveEnabled] );
	// tiltEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'tiltEnabled', tiltEnabled );
		}
	}, [tiltEnabled] );
	// rotationEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'rotationEnabled', rotationEnabled );
		}
	}, [rotationEnabled] );
	// zoomEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'zoomEnabled', zoomEnabled );
		}
	}, [zoomEnabled] );
	// zoomLevel changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setZoomLevel( mapViewNativeTag, zoomLevel );
		}
	}, [zoomLevel] );
	// minZoom changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setMinZoom( mapViewNativeTag, minZoom );
		}
	}, [minZoom] );
	// maxZoom changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setMaxZoom( mapViewNativeTag, maxZoom );
		}
	}, [maxZoom] );
	// tilt changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'tilt', tilt );
		}
	}, [tilt] );
	// minTilt changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'minTilt', minTilt );
		}
	}, [minTilt] );
	// maxTilt changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'maxTilt', maxTilt );
		}
	}, [maxTilt] );

	// bearing changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'bearing', bearing );
		}
	}, [bearing] );
	// minBearing changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'minBearing', minBearing );
		}
	}, [minBearing] );
	// maxBearing changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'maxBearing', maxBearing );
		}
	}, [maxBearing] );

	// roll changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'roll', roll );
		}
	}, [roll] );
	// minRoll changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'minRoll', minRoll );
		}
	}, [minRoll] );
	// maxRoll changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setViewport( mapViewNativeTag, 'maxRoll', maxRoll );
		}
	}, [maxRoll] );

	// hgtDirPath
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setHgtDirPath( mapViewNativeTag, hgtDirPath );
		}
	}, [hgtDirPath] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MapLifecycle', ( response : MapLifeCycleResponse ) => {
			if ( response.nativeTag === mapViewNativeTag ) {
				switch( response.type ) {
					case 'onPause':
						if ( onPause ) {
							onPause( response );
						}
						break;
					case 'onResume':
						if ( onResume ) {
							onResume( response );
						}
						break;
				}
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [mapViewNativeTag] );

	let lastIndex = 0; // It starts with the MapFragment event layer. Otherwise it would be -1 here.
	const wrapChildren = ( children: React.ReactNode ): null | React.ReactNode => ! children || ! findNodeHandle( ref?.current ) ? null : React.Children.map( children, child => {
		let newChild = child;

		if ( ! React.isValidElement<{ children?: React.ReactNode }>( child )) {
			return newChild
		}

		const type = get( child, 'type' );
		if ( ! type || ! type.valueOf ) {
			return newChild
		}
		const isMapLayer = get( type.valueOf(), 'isMapLayer' );

		lastIndex = isMapLayer ? lastIndex + 1 : lastIndex;
		newChild = child && type ? cloneElement(
			child,
			{
				...( { mapViewNativeTag } ),
				...( isMapLayer ? { reactTreeIndex: lastIndex } : {} ),
				...( child?.props?.children && { children: wrapChildren( child.props.children ) } ),
			},
		) : child;

		return newChild;
	} );

	const wrappedChildren = wrapChildren( children );

	// Wrap into non scrollable ScrollView to fix top positioning.
	return <ScrollView scrollEnabled={ false }>
		<MapViewManager
			ref={ ref }
			width={ width }
			height={ height }
			widthForLayoutSize={ PixelRatio.getPixelSizeForLayoutSize( width ) }
			heightForLayoutSize={ PixelRatio.getPixelSizeForLayoutSize( height ) }
			center={ center }
			moveEnabled={ moveEnabled }
			tiltEnabled={ tiltEnabled }
			rotationEnabled={ rotationEnabled }
			zoomEnabled={ zoomEnabled }
			zoomLevel={ zoomLevel }
			minZoom={ minZoom }
			maxZoom={ maxZoom }
			tilt={ tilt }
			minTilt={ minTilt }
			maxTilt={ maxTilt }
			bearing={ bearing }
			minBearing={ minBearing }
			maxBearing={ maxBearing }
			roll={ roll }
			minRoll={ minRoll }
			maxRoll={ maxRoll }
			hgtDirPath={ hgtDirPath }
		/>
		{ mapLayersCreated && wrappedChildren }
	</ScrollView>;
};

export default MapContainer;
