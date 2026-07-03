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
	FontFamily,
	FontStyle,
	type MarkerProps,
} from '../NativeModules/NativeLayerMarker';
import {
	MarkerHotspotPlaces,
	MarkerShape,
	type MarkerResponse,
} from '../NativeModules/NativeLayerMarker';
import type { ErrorBase } from '../types';
import useMarkerEventSubscription from '../compose/useMarkerEventSubscription';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import {
	enqueueCreateMarker,
	enqueueRemoveMarker,
} from '../compose/MarkerBatchQueue';
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
	const justCreatedRef = useRef(false);
	const createdPositionRef = useRef(position);
	const createdSymbolRef = useRef(symbol);
	const positionIndexRef = useRef<number>(-1);
	const fragmentUuidRef = useRef<string | undefined>(undefined);

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && markerLayerUuid !== false && !!position,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || markerLayerUuid === false || !position) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg:
							'Missing nativeNodeHandle, markerLayerUuid or position',
					},
				} as ErrorBase);
			}
			// Snapshot the props used for creation so the update guard
			// below can detect a change that landed between enqueue and
			// uuid resolution.
			createdPositionRef.current = position;
			createdSymbolRef.current = symbol;
			return enqueueCreateMarker({
				nativeNodeHandle,
				markerLayerUuid,
				...(title && { title }),
				...(description && { description }),
				...(position && { position }),
				...(symbol && { symbol }),
				positionIndex: positionIndexRef.current,
				...(fragmentUuidRef.current && {
					fragmentUuid: fragmentUuidRef.current,
				}),
			}).then((response: MarkerResponse) => {
				indexRef.current = response.index;
				justCreatedRef.current = true;
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle || markerLayerUuid === false) {
				return Promise.resolve(false);
			}
			return enqueueRemoveMarker(nativeNodeHandle, currentUuid)
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

	const { positionIndex, fragmentUuid } = useLayerOrder(uuid, 'marker');
	positionIndexRef.current = positionIndex;
	fragmentUuidRef.current = fragmentUuid;

	// Update the existing native marker in place when its position or symbol
	// changes, instead of tearing down and recreating it.
	useEffect(() => {
		if (justCreatedRef.current) {
			justCreatedRef.current = false;
			if (
				createdPositionRef.current === position &&
				createdSymbolRef.current === symbol
			) {
				// Props unchanged since creation — nothing to update.
				return;
			}
			// Props changed between the create enqueue and uuid
			// resolution. Fall through to apply the update.
		}
		if (uuid && markerLayerUuid !== false && nativeNodeHandle) {
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

Marker.HotspotPlaces = MarkerHotspotPlaces;
Marker.FontFamily = FontFamily;
Marker.FontStyle = FontStyle;
Marker.Shapes = MarkerShape;

Marker.defaults = omit(LayerMarkerModule.getConstants(), ['strategy']);

export default Marker;
