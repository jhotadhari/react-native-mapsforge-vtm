/**
 * External dependencies
 */
import { useContext, useEffect, useRef } from 'react';
import { omit } from 'lodash-es';

/**
 * Internal dependencies
 */
import LayerMarkerModule, {
	FontFamily,
	FontStyle,
	type MarkerProps,
} from '../NativeModules/NativeLayerMarker';
import {
	MarkerHotspotPlaces,
	type MarkerResponse,
} from '../NativeModules/NativeLayerMarker';
import type { ErrorBase } from '../types';
import useMarkerEventSubscription from '../compose/useMarkerEventSubscription';
import useLayerAnchor from '../compose/useLayerAnchor';
import useLayerEntry from '../compose/useLayerEntry';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import {
	enqueueCreateMarker,
	enqueueRemoveMarker,
} from '../compose/MarkerBatchQueue';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';
import SharedLayerContext from '../context/SharedLayerContext';
import { fragmentUuidFor, runUuidFor } from '../scene/ids';

const Marker = ({
	title,
	description,
	position,
	paint,
	onCreate,
	onRemove,
	onChange,
	onError,
	onEvent,
	onPress,
	onLongPress,
	onTrigger,
	vtmSortIndex,
}: MarkerProps & { vtmSortIndex?: number }) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);
	const { markerLayerUuid, fragmentId: markerFragmentId } =
		useContext(MarkerLayerContext);
	const sharedId = useContext(SharedLayerContext);

	// Grouped when inside a LayerMarker or a SharedLayer wrapper.
	const fragmentId = markerFragmentId ?? sharedId;
	const isGrouped = fragmentId !== null;

	const indexRef = useRef<number>(-1);
	const justCreatedRef = useRef(false);
	const createdPositionRef = useRef(position);
	const createdPaintRef = useRef(paint);

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
		layerType: 'marker',
		shared: true,
		active: !isGrouped,
	});

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
			createdPaintRef.current = paint;
			const fragmentUuid =
				fragmentId !== null
					? fragmentUuidFor(fragmentId, 'marker')
					: runUuidFor(anchorUid);
			return enqueueCreateMarker({
				nativeNodeHandle,
				markerLayerUuid,
				...(title && { title }),
				...(description && { description }),
				...(position && { position }),
				...(paint && { paint }),
				fragmentUuid,
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

	useLayerEntry({
		active: isGrouped,
		fragmentId,
		layerType: 'marker',
		sortIndex: vtmSortIndex,
		uuid,
	});

	useSceneUuidBinding(isGrouped ? null : anchorUid, uuid);

	// Update the existing native marker in place when its position or paint
	// changes, instead of tearing down and recreating it.
	useEffect(() => {
		if (justCreatedRef.current) {
			justCreatedRef.current = false;
			if (
				createdPositionRef.current === position &&
				createdPaintRef.current === paint
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
				...(paint && { paint }),
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
		paint,
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

	return anchorElement;
};

Marker.HotspotPlaces = MarkerHotspotPlaces;
Marker.FontFamily = FontFamily;
Marker.FontStyle = FontStyle;

Marker.defaults = omit(LayerMarkerModule.getConstants(), ['strategy']);

export default Marker;
