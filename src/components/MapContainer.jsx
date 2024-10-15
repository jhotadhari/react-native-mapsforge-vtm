/**
 * External dependencies
 */
import React, {
	cloneElement,
	useEffect,
	useRef,
	useState,
} from 'react';
import {
	PixelRatio,
	UIManager,
	findNodeHandle,
	useWindowDimensions,
	ScrollView,
	NativeEventEmitter,
} from 'react-native';
import PropTypes from 'prop-types';

/**
 * Internal dependencies
 */
import MapPropTypes from '../MapPropTypes';
import { MapViewManager } from './MapViewManager.jsx';
import useMapLayersCreated from '../compose/useMapLayersCreated.js';
import { MapContainerModule } from '../nativeMapModules';
import { isBoolean, isNumber } from 'lodash-es';

const createFragment = mapViewNativeTag =>
	UIManager.dispatchViewManagerCommand(
		mapViewNativeTag,
		// we are calling the 'create' command
		UIManager.MapViewManager.Commands.create.toString(),
		[mapViewNativeTag],
	);

const useDefaultWidth = propsWidth => {
	const { width } = useWindowDimensions();
	return propsWidth || width;
};

const MapContainer = ( {
	children,
	mapViewNativeTag,	// It's not possible to control the nativeTag. It's a prop just to lift the state up.
	setMapViewNativeTag,
	onPause,
	onResume,

	width,
	height,

	center,

	moveEnabled,
	tiltEnabled,
	rotationEnabled,
	zoomEnabled,

	zoomLevel,
	minZoom,
	maxZoom,

	tilt,
	minTilt,
	maxTilt,

	bearing,
	minBearing,
	maxBearing,

	roll,
	minRoll,
	maxRoll,
} ) => {

	const ref = useRef( null );

	const [mapViewNativeTag_, setMapViewNativeTag_] = useState( null );
	mapViewNativeTag = mapViewNativeTag ? mapViewNativeTag : mapViewNativeTag_;
	setMapViewNativeTag = setMapViewNativeTag ? setMapViewNativeTag : setMapViewNativeTag_;

	const mapLayersCreated = useMapLayersCreated( ref?.current?._nativeTag );

	width = useDefaultWidth( width );
	height = height || 200;

	center = center && Array.isArray( center ) && center.length === 2 ? center : [52.5, 13.4];

	moveEnabled = isBoolean( moveEnabled ) || isNumber( moveEnabled ) ? ( !! moveEnabled ? 1 : 0 ) : 1;
	rotationEnabled = isBoolean( rotationEnabled ) || isNumber( rotationEnabled ) ? ( !! rotationEnabled ? 1 : 0 ) : 1;
	zoomEnabled = isBoolean( zoomEnabled ) || isNumber( zoomEnabled ) ? ( !! zoomEnabled ? 1 : 0 ) : 1;
	tiltEnabled = isBoolean( tiltEnabled ) || isNumber( tiltEnabled ) ? ( !! tiltEnabled ? 1 : 0 ) : 1;

	zoomLevel = isNumber( zoomLevel ) ? parseInt( zoomLevel, 10 ) : 12;
	minZoom = isNumber( minZoom ) ? parseInt( minZoom, 10 ) : 3;
	maxZoom = isNumber( maxZoom ) ? parseInt( maxZoom, 10 ) : 20;

	tilt = isNumber( tilt ) ? parseFloat( tilt, 10 ) : 0;
	minTilt = isNumber( minTilt ) ? parseFloat( minTilt, 10 ) : 0;
	maxTilt = isNumber( maxTilt ) ? parseFloat( maxTilt, 10 ) : 65;

	bearing = isNumber( bearing ) ? parseFloat( bearing, 10 ) : 0;
	minBearing = isNumber( minBearing ) ? parseFloat( minBearing, 10 ) : -180;
	maxBearing = isNumber( maxBearing ) ? parseFloat( maxBearing, 10 ) : 180;

	roll = isNumber( roll ) ? parseFloat( roll, 10 ) : 0;
	minRoll = isNumber( minRoll ) ? parseFloat( minRoll, 10 ) : -180;
	maxRoll = isNumber( maxRoll ) ? parseFloat( maxRoll, 10 ) : 180;

	useEffect( () => {
		setMapViewNativeTag( findNodeHandle( ref.current ) );
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
	}, [center.join( '' )] );
	// moveEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'moveEnabled', isBoolean( moveEnabled ) || isNumber( moveEnabled ) ? ( !! moveEnabled ? 1 : 0 ) : 1 );
		}
	}, [moveEnabled] );
	// tiltEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'tiltEnabled', isBoolean( tiltEnabled ) || isNumber( tiltEnabled ) ? ( !! tiltEnabled ? 1 : 0 ) : 1 );
		}
	}, [tiltEnabled] );
	// rotationEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'rotationEnabled', isBoolean( rotationEnabled ) || isNumber( rotationEnabled ) ? ( !! rotationEnabled ? 1 : 0 ) : 1 );
		}
	}, [rotationEnabled] );
	// zoomEnabled changed.
	useEffect( () => {
		if ( mapLayersCreated && mapViewNativeTag ) {
			MapContainerModule.setPropsInteractionsEnabled( mapViewNativeTag, 'zoomEnabled', isBoolean( zoomEnabled ) || isNumber( zoomEnabled ) ? ( !! zoomEnabled ? 1 : 0 ) : 1 );
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

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MapLifecycle', result => {
			if ( result.nativeTag === mapViewNativeTag ) {
				switch( result.type ) {
					case 'onPause':
						if ( onPause ) {
							onPause( result );
						}
						break;
					case 'onResume':
						if ( onResume ) {
							onResume( result );
						}
						break;
				}
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [mapViewNativeTag] );

	let lastIndex = 0; // It starts with the MapFragment event layer.
	const wrapChildren = () => ! children || ! ref?.current?._nativeTag ? null : React.Children.map( children, child => {
		lastIndex = child?.type?.isMapLayer ? lastIndex + 1 : lastIndex;
		const newChild = child && child.type ? cloneElement(
			child,
			{
				mapViewNativeTag: ref?.current?._nativeTag,
				...( child.type.isMapLayer && { reactTreeIndex: lastIndex } ),
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

			moveEnabled = { moveEnabled }
			tiltEnabled = { tiltEnabled }
			rotationEnabled = { rotationEnabled }
			zoomEnabled = { zoomEnabled }

			zoomLevel = { zoomLevel }
			minZoom = { minZoom }
			maxZoom = { maxZoom }

			tilt = { tilt }
			minTilt = { minTilt }
			maxTilt = { maxTilt }

			bearing = { bearing }
			minBearing = { minBearing }
			maxBearing = { maxBearing }

			roll = { roll }
			minRoll = { minRoll }
			maxRoll = { maxRoll }
		/>
		{ mapLayersCreated && wrappedChildren }
	</ScrollView>;
};

MapContainer.propTypes = {
	mapViewNativeTag: PropTypes.number,
	setMapViewNativeTag: PropTypes.func,
	onPause: PropTypes.func,
	onResume: PropTypes.func,
	width: PropTypes.number,
	height: PropTypes.number,
	center: MapPropTypes.latLong,
	moveEnabled: PropTypes.bool,
	tiltEnabled: PropTypes.bool,
	rotationEnabled: PropTypes.bool,
	zoomEnabled: PropTypes.bool,
	zoom: PropTypes.number,
	minZoom: PropTypes.number,
	maxZoom: PropTypes.number,
	tilt: PropTypes.number,
	minTilt: PropTypes.number,
	maxTilt: PropTypes.number,
	bearing: PropTypes.number,
	minBearing: PropTypes.number,
	maxBearing: PropTypes.number,
	roll: PropTypes.number,
	minRoll: PropTypes.number,
	maxRoll: PropTypes.number,
};

export default MapContainer;
