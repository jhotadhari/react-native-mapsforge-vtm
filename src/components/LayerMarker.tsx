/**
 * External dependencies
 */
import { useContext, useEffect, useRef } from 'react';
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
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';

const defaultsTrigger = pick(LayerMarkerModule.getConstants(), ['strategy']);

const LayerMarker = ({
	children,
	symbol,
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
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const errorSubscription = useRef<null | EventSubscription>(null);

	useEffect(() => {
		errorSubscription.current = LayerMarkerModule.onError(
			(error?: ErrorWithErrorMsg) => {
				console.log('debug error', error); // debug ???
			}
		);
		return () => {
			errorSubscription.current?.remove();
			errorSubscription.current = null;
		};
	}, []);

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: { errorMsg: 'Missing nativeNodeHandle' },
				} as ErrorBase);
			}
			return LayerMarkerModule.createLayer({
				nativeNodeHandle,
				...(symbol && { symbol }),
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

	useLayerOrder(uuid);

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
		onEvent: onMarkerEvent,
		onPress: onMarkerPress,
		onLongPress: onMarkerLongPress,
		onTrigger: onMarkerTrigger,
	});

	// Update the layer's default marker symbol in place when it changes,
	// instead of tearing down and recreating the layer (which would also
	// orphan any markers already created under it).
	useEffect(() => {
		if (uuid && nativeNodeHandle) {
			LayerMarkerModule.updateLayer({
				nativeNodeHandle,
				uuid,
				...(symbol && { symbol }),
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
		symbol,
		onChange,
		onError,
	]);

	if (!uuid) {
		return null;
	}

	return (
		<MarkerLayerContext.Provider value={{ markerLayerUuid: uuid }}>
			{children}
		</MarkerLayerContext.Provider>
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
