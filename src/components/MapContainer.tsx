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
import type { Location, mapEvent, ResponseInclude } from '../types';

const createFragment = ( nativeNodeHandle: number ) : void => {
	const create = UIManager.getViewManagerConfig( 'MapViewManager' )?.Commands?.create;
	if ( create ) {
		try {
			UIManager.dispatchViewManagerCommand(
				nativeNodeHandle,
				create.toString(),
				[nativeNodeHandle],
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
	nativeNodeHandle?: null | number;
	setNativeNodeHandle?: null | Dispatch<SetStateAction<number | null>>;
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
	responseInclude?: ResponseInclude;
};

const defaultCenter : Location = {
	lng: -77.605,
	lat: -9.118,
};

// 0	never include in response.
// 1	include in lifeCycle response.
// 2	include in lifeCycle and onMapEvent response.
const responseIncludeDefaults : ResponseInclude = {
	zoomLevel: 0,
	zoom: 0,
	scale: 0,
	zoomScale: 0,
	bearing: 0,
	roll: 0,
	tilt: 0,
	center: 0,
};

const numOrBoolToNum = ( numOrBool: number | boolean | undefined, defaultVal: 1 | 0 ): ( 1 | 0 ) => {
	return isBoolean( numOrBool ) || isNumber( numOrBool ) ? ( !! numOrBool ? 1 : 0 ) : defaultVal;
};

const MapContainer = ( {
	children,
	nativeNodeHandle = null,	// It's not possible to control the nativeNodeHandle. It's a prop just to lift the state up.
	setNativeNodeHandle = null,
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
	responseInclude = responseIncludeDefaults,
} : MapContainerProps ) => {

	const ref = useRef<number | Component<any, any, any> | ComponentClass<any, any> | null>( null );

	const [nativeNodeHandle_, setNativeNodeHandle_] = useState< number | null >( null );
	nativeNodeHandle = nativeNodeHandle ? nativeNodeHandle : nativeNodeHandle_;
	setNativeNodeHandle = setNativeNodeHandle ? setNativeNodeHandle : setNativeNodeHandle_;

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

	responseInclude = { ...responseIncludeDefaults, ...responseInclude };

	useEffect( () => {
		const nodeHandle = findNodeHandle( ref?.current );
		if ( nodeHandle ) {
			setNativeNodeHandle( nodeHandle );
		}
	}, [] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			createFragment( nativeNodeHandle );
		}
	}, [nativeNodeHandle] );


	// center changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setCenter( nativeNodeHandle, center )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [Object.values( center ).join( '' )] );
	// moveEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setPropsInteractionsEnabled( nativeNodeHandle, 'moveEnabled', moveEnabled )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [moveEnabled] );
	// tiltEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setPropsInteractionsEnabled( nativeNodeHandle, 'tiltEnabled', tiltEnabled )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [tiltEnabled] );
	// rotationEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setPropsInteractionsEnabled( nativeNodeHandle, 'rotationEnabled', rotationEnabled )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [rotationEnabled] );
	// zoomEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setPropsInteractionsEnabled( nativeNodeHandle, 'zoomEnabled', zoomEnabled )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [zoomEnabled] );
	// zoomLevel changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setZoomLevel( nativeNodeHandle, zoomLevel )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [zoomLevel] );
	// minZoom changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setMinZoom( nativeNodeHandle, minZoom )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [minZoom] );
	// maxZoom changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setMaxZoom( nativeNodeHandle, maxZoom )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [maxZoom] );
	// tilt changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'tilt', tilt )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [tilt] );
	// minTilt changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'minTilt', minTilt )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [minTilt] );
	// maxTilt changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'maxTilt', maxTilt )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [maxTilt] );

	// bearing changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'bearing', bearing )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [bearing] );
	// minBearing changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'minBearing', minBearing )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [minBearing] );
	// maxBearing changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'maxBearing', maxBearing )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [maxBearing] );

	// roll changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'roll', roll )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [roll] );
	// minRoll changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'minRoll', minRoll )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [minRoll] );
	// maxRoll changed.
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setViewport( nativeNodeHandle, 'maxRoll', maxRoll )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [maxRoll] );

	// hgtDirPath
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setHgtDirPath( nativeNodeHandle, hgtDirPath )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [hgtDirPath] );

	// responseInclude
	useEffect( () => {
		if ( mapLayersCreated && nativeNodeHandle ) {
			MapContainerModule.setResponseInclude( nativeNodeHandle, responseInclude )
			.catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' )] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MapLifecycle', ( response : MapLifeCycleResponse ) => {
			if ( response.nativeNodeHandle === nativeNodeHandle ) {
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
	}, [nativeNodeHandle] );

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
				...( { nativeNodeHandle } ),
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
			responseInclude={ responseInclude }
		/>
		{ mapLayersCreated && wrappedChildren }
	</ScrollView>;
};

export default MapContainer;
