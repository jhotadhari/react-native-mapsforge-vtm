/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerPathModule } from '../nativeMapModules';
import type { ResponseInclude, Location, LocationExtended, GeometryStyle, Bounds, ResponseBase } from '../types';
import { NativeEventEmitter } from 'react-native';

const Module = MapLayerPathModule;

export interface LayerPathResponse extends ResponseBase {
	coordinates?: LocationExtended[];
	bounds?: Bounds;
};

export interface LayerPathGestureResponse extends ResponseBase {
	type: string;
	distance: number;
	nearestPoint: Location;
	eventPosition: Location;
};

export type LayerPathProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	filePath?: null | `/${string}` | `content://${string}`;
	positions?: Location[];
	responseInclude?: ResponseInclude;
	gestureScreenDistance?: number;
	simplificationTolerance?: number;
	style?: GeometryStyle;
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onCreate?: null | ( ( response: LayerPathResponse ) => void );
	onChange?: null | ( ( response: LayerPathResponse ) => void );
	onError?: null | ( ( err: any ) => void );
	onPress?: null | ( ( response: LayerPathGestureResponse ) => void );
	onLongPress?: null | ( ( response: LayerPathGestureResponse ) => void );
	onDoubleTap?: null | ( ( response: LayerPathGestureResponse ) => void );
	onTrigger?: null | ( ( response: LayerPathGestureResponse ) => void );
};

// 0	never include in response.
// 1	include in response on create.
// 2	include in response on change.
const responseIncludeDefaults : ResponseInclude = {
	coordinates: 0,
	bounds: 0,
};

const defaultStyle : GeometryStyle = {
	strokeWidth: 4,
	strokeColor: '#ff0000',
}

const LayerPath = ( {
	nativeNodeHandle,
	positions = [],
	filePath,
	responseInclude = responseIncludeDefaults,
	gestureScreenDistance = 20,
	reactTreeIndex,
	style = defaultStyle,
	simplificationTolerance = 0,
	onCreate,
	onRemove,
	onChange,
	onError,
	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
} : LayerPathProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	positions = positions || [];
	responseInclude = { ...responseIncludeDefaults, ...responseInclude };
	style = {...defaultStyle, ...style };

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				positions,
				filePath,
				style,
				responseInclude,
				gestureScreenDistance,
				simplificationTolerance,
				reactTreeIndex
			).then( ( response: LayerPathResponse ) => {
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
					return Module.updateStyle(
						nativeNodeHandle,
						uuid,
						style,
						responseInclude
					).then( ( response: LayerPathResponse ) => {
						onChange ? onChange( response ) : null;
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
	}, [Object.values( style ).join( '' )] );

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
			} else if ( uuid === null && ( filePath || positions.length > 0 ) ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		( positions.length > 0
			? [...positions].map( pos => pos.lng + pos.lat ).join( '' )
			: null
		),
		simplificationTolerance,
		filePath,
		Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' ),
	] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'PathGesture', ( response : LayerPathGestureResponse ) => {
			if ( response.uuid === uuid ) {
				switch( response.type ) {
					case 'doubleTap':
						onDoubleTap ? onDoubleTap( response ) : null;
						break;
					case 'LongPress':
						onLongPress ? onLongPress( response ) : null;
						break;
					case 'press':
						onPress ? onPress( response ) : null;
						break;
					case 'trigger':
						onTrigger ? onTrigger( response ) : null;
						break;
				}
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		uuid,
		onDoubleTap,
		onLongPress,
		onPress,
		onTrigger,
	] );

	return null;
};
LayerPath.isMapLayer = true;

export default LayerPath;
