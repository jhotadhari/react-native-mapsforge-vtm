/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerPathModule } from '../nativeMapModules';
import type { ResponseInclude, Location, LocationExtended, GeometryStyle } from '../types';

const Module = MapLayerPathModule;

export type LayerPathResponse = {
	uuid: string;
	coordinates?: LocationExtended[];
};

export type LayerPathProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex: number;
	filePath?: null | `/${string}` | `content://${string}`;
	positions?: Location[];
	responseInclude?: ResponseInclude;
	style?: GeometryStyle;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerPathResponse ) => void );
	onChange?: null | ( ( response: LayerPathResponse ) => void );
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
	reactTreeIndex,
	style = defaultStyle,
	onCreate,
	onRemove,
	onChange,
} : LayerPathProps ) => {

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
				reactTreeIndex
			).then( ( response: LayerPathResponse ) => {
				setUuid( response.uuid );
				setRandom( Math.random() );
				( null === triggerCreateNew
					? ( onCreate ? onCreate( response ) : null )
					: ( onChange ? onChange( response ) : null )
				);
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
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
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
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
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
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
					).then( ( removedUuid : string ) => {
						setUuid( null );
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );;
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
		filePath,
		Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' ),
	] );

	return null;
};
LayerPath.isMapLayer = true;

export default LayerPath;
