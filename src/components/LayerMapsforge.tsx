/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';

/**
 * Internal dependencies
 */
import useRefState from '../../src/compose/useRefState';
import promiseQueue from '../promiseQueue';
import usePrevious from '../compose/usePrevious';
import useRenderStyleOptions from '../compose/useRenderStyleOptions';
import { MapLayerMapsforgeModule } from '../nativeMapModules';
import type {
	Bounds,
	Location
} from '../types';

const Module = MapLayerMapsforgeModule;

const BUILT_IN_THEMES = [
	'DEFAULT',
	'BIKER',
	'MOTORIDER',
	'MOTORIDER_DARK',
	'NEWTRON',
	'OSMAGRAY',
	'OSMARENDER',
	'TRONRENDER',
];

export type LayerMapsforgeResponse = {
	uuid: string;
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
	mapViewNativeTag?: null | number;
	reactTreeIndex: number;
	mapFile?: string;
	renderTheme?: string;
	renderStyle?: string;
	renderOverlays?: string[];
	onRemove?: null | ( ( response: { uuid: string } ) => void );
	onCreate?: null | ( ( response: LayerMapsforgeResponse ) => void );
	onChange?: null | ( ( response: LayerMapsforgeResponse ) => void );
};

const LayerMapsforge = ( {
	mapViewNativeTag,
	reactTreeIndex,
	mapFile = '',
	renderTheme = 'DEFAULT',
	renderStyle = '',
	renderOverlays = [],
	onCreate,
	onRemove,
	onChange,
} : LayerMapsforgeProps ) => {

	const renderStylePrev = usePrevious( renderStyle );

	const [random, setRandom] = useState<number>( 0 );
	const [uuid, setUuid] = useRefState( null );
	const [triggerCreateNew, setTriggerCreateNew] = useState<null | number>( null );

	const { renderStyleDefaultId } = useRenderStyleOptions( ( {
		renderTheme,
		nativeTag: mapViewNativeTag,
	} ) );

	const createLayer = () => {
		setUuid( false );
		promiseQueue.enqueue( () => {
			return Module.createLayer(
				mapViewNativeTag,
				mapFile,
				renderTheme,
				renderStyle,
				renderOverlays,
				reactTreeIndex
			).then( ( response : false | LayerMapsforgeResponse ) => {
				if ( response ) {		// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
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
		if ( uuid === null && mapViewNativeTag && mapFile ) {
			createLayer();
		}
		return () => {
			if ( uuid && mapViewNativeTag ) {
				promiseQueue.enqueue( () => {
					return Module.removeLayer(
						mapViewNativeTag,
						uuid
					).then( ( removedUuid: string ) => {
						if ( removedUuid ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
							onRemove ? onRemove( { uuid: removedUuid } ) : null;
						}
					} );
				} )
			}
		};
	}, [
		mapViewNativeTag,
		!! uuid,
		triggerCreateNew,
	] );

	useEffect( () => {
		if ( mapViewNativeTag ) {
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
							mapViewNativeTag,
							uuid
						).then( ( removedUuid: string ) => {
							if ( removedUuid ) {	// ??? dont need the test here. make sure java responds the uuid. and throws shit instead of responding false.
								setUuid( null );
								setTriggerCreateNew( Math.random() );
							}
						} );
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
