/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';
import { isArray, isString } from 'lodash-es';

/**
 * Internal dependencies
 */
import useRefState from '../../src/compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerPathModule } from '../nativeMapModules';
import { isValidPositions } from '../utils';
import type { ResponseInclude, Location, LocationExtended, GeometryStyle } from '../types';

const Module = MapLayerPathModule;

export type LayerPathResponse = {
	uuid: string;
	coordinates?: LocationExtended[];
};

export type LayerPathProps = {
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	filePath?: string;
	positions?: Location[];
	responseInclude?: ResponseInclude;
	style?: GeometryStyle;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerPathResponse ) => void );
	onChange?: null | ( ( response: LayerPathResponse ) => void );
};

const responseIncludeDefaults : ResponseInclude = {
	coordinates: 0,
	bounds: 0,
};

const isValidFilePath = ( filePath : string ) : boolean => {
	return filePath && filePath.length > 0 && ( filePath.startsWith( '/' ) || filePath.startsWith( 'content://' ) ) ? true : false;
};

const defaultStyle : GeometryStyle = {
	strokeWidth: 4,
	strokeColor: '#ff0000',
}

const LayerPath = ( {
	mapViewNativeTag,
	positions = [],
	filePath = '',
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

	positions = isValidPositions( positions ) ? positions : [];
	filePath = isValidFilePath( filePath ) ? filePath : '';

	responseInclude = { ...responseIncludeDefaults, ...responseInclude };

	style = {...defaultStyle, ...style };

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {

			console.log( 'debug create new' ); // debug
			return Module.createLayer(
				mapViewNativeTag,
				positions,
				filePath,
				style,
				responseInclude,
				reactTreeIndex
			).then( ( response: false | LayerPathResponse ) => {
				if ( response ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
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
		if ( uuid === null && mapViewNativeTag && ( isValidFilePath( filePath ) || positions.length > 0 ) ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						if ( removedUuid ) {
							onRemove ? onRemove( { uuid: removedUuid } ) : null;
						}
					});
				} );
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag && uuid ) {
				promiseQueue.enqueue( () => {
					return Module.updateStyle(
						mapViewNativeTag,
						uuid,
						style,
						responseInclude
					).then( ( response: false | LayerPathResponse ) => {
						if ( response ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
							onChange ? onChange( response ) : null;
						}
					} ).catch( ( err: any ) => console.log( 'ERROR', err ) );;
				} );
			}
	}, [Object.values( style ).join( '' )] );

	useEffect( () => {

		console.log( 'debug layer positions', positions ); // debug
		console.log( 'debug layer uuid', uuid ); // debug
		if ( mapViewNativeTag ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid : string ) => {
						if ( removedUuid ) {
							setUuid( null );
							setTriggerCreateNew( Math.random() );
						}
					} );
				} );
			} else if ( uuid === null && ( isValidFilePath( filePath ) || positions.length > 0 ) ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		( positions.length > 0
			? [...positions].map( pos => pos.lng + pos.lat ).join( '' )
			: null
		),
		filePath,
	] );

	return null;
};
LayerPath.isMapLayer = true;

export default LayerPath;
