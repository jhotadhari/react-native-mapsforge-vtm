/**
 * External dependencies
 */
import { Children, cloneElement, isValidElement, useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MapLayerMarkerModule } from '../nativeMapModules';
import type { ResponseInclude, MarkerSymbol, Bounds } from '../types';

const Module = MapLayerMarkerModule;

export type LayerMarkerResponse = {
	uuid: string;
	bounds?: Bounds;
};

export type LayerMarkerProps = {
	children?: React.ReactNode;
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
    symbol?: null | MarkerSymbol;
	responseInclude?: ResponseInclude;
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerMarkerResponse ) => void );
	onChange?: null | ( ( response: LayerMarkerResponse ) => void );
	onError?: null | ( ( err: any ) => void );
};

// 0	never include in response.
// 1	include in response on create.
// 2	include in response on change.
const responseIncludeDefaults : ResponseInclude = {
	bounds: 0,		// ??? should include bounds
};

const LayerMarker = ( {
	children,
	nativeNodeHandle,
	symbol = null,
	responseInclude = responseIncludeDefaults,
	reactTreeIndex,
	onCreate,
	onRemove,
	onChange,
	onError,
} : LayerMarkerProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	// positions = positions || [];
	responseInclude = { ...responseIncludeDefaults, ...responseInclude };
	// style = {...defaultStyle, ...style };

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				symbol,
				responseInclude,
				reactTreeIndex
			).then( ( response: LayerMarkerResponse ) => {
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
		if ( uuid === null && nativeNodeHandle ) {
		// if ( uuid === null && nativeNodeHandle && ( filePath || positions.length > 0 ) ) {
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
		if ( nativeNodeHandle ) {
			if ( uuid ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( () => {
						setUuid( null );
						setTriggerCreateNew( Math.random() );
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );;
				} );
			// } else if ( uuid === null && ( filePath || positions.length > 0 ) ) {
			} else if ( uuid === null ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		symbol ? Object.values( symbol ).join( '' ) : null,
		Object.keys( responseInclude ).map( key => key + responseInclude[key] ).join( '' ),
	] );

    if ( ! uuid ) {
        return null;
    }


	// let lastIndex = 0; // It starts with the MapFragment event layer. Otherwise it would be -1 here.
	const wrapChildren = ( children: React.ReactNode ): null | React.ReactNode => ! children ? null : Children.map( children, child => {
		let newChild = child;

		if ( ! isValidElement<{ children?: React.ReactNode }>( child )) {
			return newChild
		}

		// const type = get( child, 'type' );
		// if ( ! type || ! type.valueOf ) {
		// 	return newChild
		// }
		// const isMapLayer = get( type.valueOf(), 'isMapLayer' );

		// lastIndex = isMapLayer ? lastIndex + 1 : lastIndex;
		newChild = cloneElement(
			child,
			{
				// ...( { nativeNodeHandle } ),
				...( { layerUuid: uuid } ),
				// ...( isMapLayer ? { reactTreeIndex: lastIndex } : {} ),
				...( child?.props?.children && { children: wrapChildren( child.props.children ) } ),
			},
		);

		return newChild;
	} );

	const wrappedChildren = wrapChildren( children );


    return wrappedChildren;


};
LayerMarker.isMapLayer = true;

export default LayerMarker;
