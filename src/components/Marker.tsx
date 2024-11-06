/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import { MarkerHotspotPlaces } from '../constants';
import { MapLayerMarkerModule } from '../nativeMapModules';
import type { Location, MarkerSymbol, ResponseBase } from '../types';
import { NativeEventEmitter } from 'react-native';

const Module = MapLayerMarkerModule;

export interface MarkerResponse extends ResponseBase {
	index: number;
};

export type MarkerProps = {
	nativeNodeHandle?: null | number;
	layerUuid?: string;
	position?: Location;
    title?: string;
    description?: string;
    symbol?: null | MarkerSymbol;
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onCreate?: null | ( ( response: MarkerResponse ) => void );
	onChange?: null | ( ( response: MarkerResponse ) => void );
	onError?: null | ( ( err: any ) => void );
	onPress?: null | ( ( response: MarkerResponse ) => void );
	onLongPress?: null | ( ( response: MarkerResponse ) => void );
	onTrigger?: null | ( ( response: MarkerResponse ) => void );
};

const Marker = ( {
	nativeNodeHandle,
	layerUuid,
	position,
    title = '',
    description = '',
	symbol = null,
	onCreate,
	onRemove,
	onChange,
	onError,
	onPress,
	onLongPress,
	onTrigger,
} : MarkerProps ) => {

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	const create = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createMarker(
                nativeNodeHandle,
				layerUuid,
				position,
                title,
                description,
                symbol,
			).then( ( response: MarkerResponse ) => {
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
		if ( uuid === null && nativeNodeHandle && position ) {
			create();
		}
		return () => {
			if ( !! uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeMarker(
				        nativeNodeHandle,
				        layerUuid,
						uuid
					).then( ( removedUuid : string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} );
			}
		};
	}, [
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
        if ( nativeNodeHandle && !! uuid ) {
            promiseQueue.enqueue( () => {
                return Module.removeMarker(
                    nativeNodeHandle,
                    layerUuid,
                    uuid
                ).then( () => {
                    setUuid( null );
                    setTriggerCreateNew( Math.random() );
                } ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
            } );
        } else if ( uuid === null && position ) {
            setTriggerCreateNew( Math.random() );
        }
	}, [
		position ? ( position.lng + position.lat ) : null,
		symbol ? Object.values( symbol ).join( '' ) : null,
	] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MarkerItemSingleTapUp', ( response : MarkerResponse ) => {
			if ( response.uuid === uuid && onPress ) {
                onPress( response );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		uuid,
		onPress,
	] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MarkerItemLongPress', ( response : MarkerResponse ) => {
			if ( response.uuid === uuid && onLongPress ) {
                onLongPress( response );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		uuid,
		onLongPress,
	] );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'MarkerItemTriggerEvent', ( response : MarkerResponse ) => {
			if ( response.uuid === uuid && onTrigger ) {
                onTrigger( response );
			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [
		uuid,
		onTrigger,
	] );

	return null;
};

Marker.MarkerHotspotPlaces = MarkerHotspotPlaces;

export default Marker;
