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
import useSceneFragmentUuid from '../compose/useSceneFragmentUuid';
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
	order,
}: MarkerProps & {
	/** Owner-injected sibling position inside a SharedLayer/LayerMarker fragment. */
	vtmSortIndex?: number;
	/**
	 * Explicit position inside a fragment. Overrides vtmSortIndex — use it
	 * when the marker is nested inside wrapper components or host Views that
	 * can't forward the injected index. Lower = earlier = lower z-order.
	 */
	order?: number;
}) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);
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

	const runFragmentUuid = useSceneFragmentUuid(isGrouped ? null : anchorUid);
	// The fragment uuid the current native entry was created under — ground
	// truth for detecting a run re-key (first member removed).
	const usedFragmentUuidRef = useRef<string | null>(null);

	const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
		// Standalone creation waits for the scene-resolved run key: without
		// it the entry would land under a self-keyed (unmanaged) fragment.
		enabled:
			!!nativeNodeHandle &&
			markerLayerUuid !== false &&
			!!position &&
			(isGrouped || runFragmentUuid !== null),
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
					: (runFragmentUuid ?? runUuidFor(anchorUid));
			usedFragmentUuidRef.current = fragmentUuid;
			// The absolute target order: the plan as if this entry were
			// already resolved — the fragment appears at its tree position.
			// The native side applies it atomically with the add.
			const layerUuids = scene
				.planWithResolved(isGrouped ? entryUid : anchorUid)
				.layers.map((l) => l.uuid);
			return enqueueCreateMarker({
				nativeNodeHandle,
				markerLayerUuid,
				...(title && { title }),
				...(description && { description }),
				...(position && { position }),
				...(paint && { paint }),
				fragmentUuid,
				layerUuids,
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

	const entryUid = useLayerEntry({
		active: isGrouped,
		fragmentId,
		layerType: 'marker',
		sortIndex: order ?? vtmSortIndex,
		uuid,
	});

	useSceneUuidBinding(isGrouped ? null : anchorUid, uuid);

	// Standalone: when the type-run re-keys (its first member was removed),
	// the native entry must move to the new fragment — remove and recreate
	// under the new key. `uuid` is a dep so a re-key that lands while the
	// create is in-flight is caught when the stale uuid resolves.
	useEffect(() => {
		if (isGrouped || runFragmentUuid === null) {
			return;
		}
		if (usedFragmentUuidRef.current === runFragmentUuid) {
			return;
		}
		triggerRemove({ triggerOnRemove: false }).then((success) => {
			if (success) {
				triggerCreate({
					triggerOnCreate: false,
					triggerOnChange: true,
				});
			}
		});
	}, [
		isGrouped,
		runFragmentUuid,
		uuid,
		triggerRemove,
		triggerCreate,
	]);

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
