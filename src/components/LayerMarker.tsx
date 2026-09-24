/**
 * External dependencies
 */
import { useContext, useEffect, useMemo, useRef } from 'react';
import type { EventSubscription } from 'react-native';
import { omit, pick } from 'lodash-es';

/**
 * Internal dependencies
 */
import LayerMarkerModule, {
	type LayerMarkerProps,
	type MarkerTriggerParams,
} from '../NativeModules/NativeLayerMarker';

import type { ErrorBase, ErrorWithErrorMsg } from '../types';
import useMarkerEventSubscription from '../compose/useMarkerEventSubscription';
import useLayerAnchor from '../compose/useLayerAnchor';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';
import { injectVtmSortIndex } from '../compose/injectVtmSortIndex';
import { fragmentUuidFor } from '../scene/ids';

const defaultsTrigger = pick(LayerMarkerModule.getConstants(), ['strategy']);

const LayerMarker = ({
	children,
	paint,
	onCreate,
	onRemove,
	onChange,
	onError,
	onMarkerEvent,
	onMarkerPress,
	onMarkerLongPress,
	onMarkerTrigger,
	triggerEvent,
}: LayerMarkerProps) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);

	const errorSubscription = useRef<null | EventSubscription>(null);

	useEffect(() => {
		errorSubscription.current = LayerMarkerModule.onError(
			(error?: ErrorWithErrorMsg) => {
				reportNativeError(
					{ userInfo: { errorMsg: error?.errorMsg ?? '' } },
					onError
				);
			}
		);
		return () => {
			errorSubscription.current?.remove();
			errorSubscription.current = null;
		};
	}, [onError]);

	// The LayerMarker is a fragment owner: one anchor marking the marker
	// layer's tree position; Marker children declare entries against its uid.
	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'fragment',
		layerType: 'marker',
	});

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: { errorMsg: 'Missing nativeNodeHandle' },
				} as ErrorBase);
			}
			// The absolute target order: the plan as if this group's layer
			// were already resolved — applied atomically with the add.
			const layerUuids = scene
				.planWithResolved(anchorUid)
				.layers.map((l) => l.uuid);
			return LayerMarkerModule.createLayer({
				nativeNodeHandle,
				fragmentUuid: fragmentUuidFor(anchorUid, 'marker'),
				layerUuids,
				...(paint && { paint }),
			}).then((newUuid) => {
				triggerOnCreate && onCreate
					? onCreate({ nativeNodeHandle, uuid: newUuid })
					: null;
				triggerOnChange && onChange
					? onChange({ nativeNodeHandle, uuid: newUuid })
					: null;
				return newUuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerMarkerModule.removeLayer({
				nativeNodeHandle,
				uuid: currentUuid,
			})
				.then((removedUuid) => {
					triggerOnRemove && onRemove
						? onRemove({ nativeNodeHandle, uuid: removedUuid })
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

	// Existence marker for the scene: the fragment enters the plan once the
	// group uuid resolved.
	useSceneUuidBinding(anchorUid, uuid);

	useEffect(() => {
		const remove = () => {
			if (triggerEvent) {
				triggerEvent.current = null;
			}
		};
		if (uuid) {
			if (triggerEvent) {
				triggerEvent.current = (params: MarkerTriggerParams) => {
					LayerMarkerModule.triggerEvent({
						...(nativeNodeHandle && { nativeNodeHandle }),
						markerLayerUuid: uuid,
						...defaultsTrigger,
						...params,
					});
				};
			}
		} else {
			remove();
		}
		return remove;
	}, [
		uuid,
		triggerEvent,
		nativeNodeHandle,
	]);

	useMarkerEventSubscription({
		layerUuid: uuid,
		onEvent: onMarkerEvent,
		onPress: onMarkerPress,
		onLongPress: onMarkerLongPress,
		onTrigger: onMarkerTrigger,
	});

	// Update the layer's default marker paint in place when it changes,
	// instead of tearing down and recreating the layer (which would also
	// orphan any markers already created under it).
	useEffect(() => {
		if (uuid && nativeNodeHandle) {
			LayerMarkerModule.updateLayer({
				nativeNodeHandle,
				uuid,
				...(paint && { paint }),
			})
				.then((updatedUuid: string) => {
					onChange
						? onChange({ nativeNodeHandle, uuid: updatedUuid })
						: null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		nativeNodeHandle,
		paint,
		onChange,
		onError,
	]);

	// Owner injection: Marker children get their sibling position as
	// vtmSortIndex — the entry-order source for the scene. Gated on uuid so
	// the walk/clone cost isn't paid while the layer uuid is unresolved and
	// the children are discarded.
	const injectedChildren = useMemo(
		() => (uuid ? injectVtmSortIndex(children) : null),
		[children, uuid]
	);

	if (!uuid) {
		return anchorElement;
	}

	return (
		<>
			{anchorElement}
			<MarkerLayerContext.Provider
				value={{ markerLayerUuid: uuid, fragmentId: anchorUid }}
			>
				{injectedChildren}
			</MarkerLayerContext.Provider>
		</>
	);
};

LayerMarker.defaults = omit(LayerMarkerModule.getConstants(), [
	'title',
	'description',
	'position',
	'strategy',
]);

LayerMarker.defaultsTrigger = defaultsTrigger;

export default LayerMarker;
