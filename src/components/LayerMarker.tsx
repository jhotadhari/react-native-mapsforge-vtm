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
import type { MarkerSymbol, ResponseBase } from '../types';

const Module = MapLayerMarkerModule;

export type LayerMarkerProps = {
	children?: React.ReactNode;
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
    symbol?: null | MarkerSymbol;
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onCreate?: null | ( ( response: ResponseBase ) => void );
	onChange?: null | ( ( response: ResponseBase ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerMarker = ( {
	children,
	nativeNodeHandle,
	symbol = null,
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

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				symbol,
				reactTreeIndex
			).then( ( response: ResponseBase ) => {
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
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			} else if ( uuid === null ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		symbol ? Object.values( symbol ).join( '' ) : null,
	] );

    if ( ! uuid ) {
        return null;
    }

	const wrapChildren = ( children: React.ReactNode ): null | React.ReactNode => ! children ? null : Children.map( children, child => {
		let newChild = child;
		if ( ! isValidElement<{ children?: React.ReactNode }>( child )) {
			return newChild
		}
		newChild = cloneElement(
			child,
			{
				...( { layerUuid: uuid } ),
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
