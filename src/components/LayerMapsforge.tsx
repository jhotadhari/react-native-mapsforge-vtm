/**
 * External dependencies
 */
import { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../compose/useRefState';
import promiseQueue from '../promiseQueue';
import usePrevious from '../compose/usePrevious';
import useRenderStyleOptions from '../compose/useRenderStyleOptions';
import { MapLayerMapsforgeModule } from '../nativeMapModules';
import { BUILT_IN_THEMES } from '../constants';
import type {
	Bounds,
	Location,
	ResponseBase
} from '../types';

const Module = MapLayerMapsforgeModule;

export interface LayerMapsforgeResponse extends ResponseBase {
	bounds?: Bounds;
	center?: Location;
	createdBy?: string;
	projectionName?: string;
	comment?: string;
	fileSize?: string;
	fileVersion?: number;
	mapDate?: string;
};

export type LayerMapsforgeProps = {
	nativeNodeHandle?: null | number;
	reactTreeIndex?: number;
	mapFile?: `/${string}` | `content://${string}`;
	renderTheme?: `/${string}` | typeof BUILT_IN_THEMES[number];
	renderStyle?: string;
	renderOverlays?: string[];
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	onRemove?: null | ( ( response: ResponseBase ) => void );
	onCreate?: null | ( ( response: LayerMapsforgeResponse ) => void );
	onChange?: null | ( ( response: LayerMapsforgeResponse ) => void );
	onError?: null | ( ( err: any ) => void );
};

const LayerMapsforge = ( {
	nativeNodeHandle,
	reactTreeIndex,
	mapFile,
	renderTheme = 'DEFAULT',
	renderStyle = '',
	renderOverlays = [],
    enabledZoomMin = 1,
    enabledZoomMax = 30,
	onCreate,
	onRemove,
	onChange,
	onError,
} : LayerMapsforgeProps ) => {

	const renderStylePrev = usePrevious( renderStyle );

	// @ts-ignore
	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	const { renderStyleDefaultId } = useRenderStyleOptions( ( {
		renderTheme,
		nativeNodeHandle: nativeNodeHandle,
		onError,
	} ) );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				nativeNodeHandle,
				mapFile,
				renderTheme,
				renderStyle,
				renderOverlays,
				Math.round( enabledZoomMin ),
				Math.round( enabledZoomMax ),
				reactTreeIndex
			).then( ( response : LayerMapsforgeResponse ) => {
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
		if ( uuid === null && nativeNodeHandle && mapFile ) {
			createLayer();
		}
		return () => {
			if ( uuid && nativeNodeHandle ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						nativeNodeHandle,
						uuid
					).then( ( removedUuid: string ) => {
						onRemove ? onRemove( { uuid: removedUuid } ) : null;
					} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
				} )
			}
		};
	}, [
		nativeNodeHandle,
		!! uuid,
		triggerCreateNew,
	] );

	// enabledZoomMin enabledZoomMax changed.
	useEffect( () => {
		if ( nativeNodeHandle && uuid ) {
			Module.updateEnabledZoomMinMax( nativeNodeHandle, uuid, Math.round( enabledZoomMin ), Math.round( enabledZoomMax ) )
			.catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
	] );

	useEffect( () => {
		if ( nativeNodeHandle ) {
			if ( uuid ) {
				let shouldRecreate = true;
				if (
					renderStyle !== renderStylePrev
					&& ( ! renderStylePrev || ! renderStylePrev?.length )
					&& ( renderStyle && renderStyleDefaultId && renderStyle === renderStyleDefaultId )
				) {
					shouldRecreate = false;
				}
				if ( shouldRecreate ) {
					promiseQueue.enqueue( () => {
						return Module.removeLayer(
							nativeNodeHandle,
							uuid
						).then( () => {
							setUuid( null );
							setTriggerCreateNew( Math.random() );
						} ).catch( ( err: any ) => { console.log( 'ERROR', err ); onError ? onError( err ) : null } );
					} );
				}
			} else if ( uuid === null && mapFile ) {
				setTriggerCreateNew( Math.random() );
			}
		}
	}, [
		mapFile,
		renderTheme,
		renderStyle,
		( renderOverlays && Array.isArray( renderOverlays ) && renderOverlays.length
			? renderOverlays.join( '' )
			: null
		),
	] );

	return null;
};
LayerMapsforge.isMapLayer = true;

LayerMapsforge.BUILT_IN_THEMES = BUILT_IN_THEMES;

export default LayerMapsforge;
