/**
 * External dependencies
 */
import { useContext, useEffect, useRef } from 'react';
import { omit } from 'lodash-es';

/**
 * Internal dependencies
 */
// import { MarkerHotspotPlaces } from '../constants';
import LayerMarkerModule, {
	type MarkerProps,
} from '../NativeModules/NativeLayerMarker';
import {
	MarkerHotspotPlaces,
	type MarkerResponse,
} from '../NativeModules/NativeLayerMarker';
import type { ErrorBase } from '../types';
import useMarkerEventSubscription from '../compose/useMarkerEventSubscription';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';

const Marker = ({
	title,
	description,
	position,
	symbol,
	onCreate,
	onRemove,
	onChange,
	onError,
	onEvent,
	onPress,
	onLongPress,
	onTrigger,
}: MarkerProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);
	const { markerLayerUuid } = useContext(MarkerLayerContext);

	const indexRef = useRef<number>(-1);

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && !!markerLayerUuid && !!position,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || !markerLayerUuid || !position) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg:
							'Missing nativeNodeHandle, markerLayerUuid or position',
					},
				} as ErrorBase);
			}
			return LayerMarkerModule.createMarker({
				nativeNodeHandle,
				markerLayerUuid,
				...(title && { title }),
				...(description && { description }),
				...(position && { position }),
				...(symbol && { symbol }),
			}).then((response: MarkerResponse) => {
				indexRef.current = response.index;
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle || !markerLayerUuid) {
				return Promise.resolve(false);
			}
			return LayerMarkerModule.removeMarker({
				nativeNodeHandle,
				markerLayerUuid,
				uuid: currentUuid,
			})
				.then((removedUuid) => {
					triggerOnRemove && onRemove
						? onRemove({ uuid: removedUuid, nativeNodeHandle })
						: null;
					return true;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
					return false;
				});
		},
		onError,
	});

	// Update the existing native marker in place when its position or symbol
	// changes, instead of tearing down and recreating it.
	useEffect(() => {
		if (uuid && markerLayerUuid && nativeNodeHandle) {
			LayerMarkerModule.updateMarker({
				nativeNodeHandle,
				markerLayerUuid,
				uuid,
				...(position && { position }),
				...(symbol && { symbol }),
			})
				.then((updatedUuid: string) => {
					onChange
						? onChange({
								uuid: updatedUuid,
								nativeNodeHandle,
								index: indexRef.current,
							})
						: null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		markerLayerUuid,
		nativeNodeHandle,
		position,
		symbol,
		onChange,
		onError,
	]);

	useMarkerEventSubscription({
		uuid,
		onEvent,
		onPress,
		onLongPress,
		onTrigger,
	});

	return null;
};

Marker.MarkerHotspotPlaces = MarkerHotspotPlaces;

Marker.defaults = omit(LayerMarkerModule.getConstants(), ['strategy']);

export default Marker;
