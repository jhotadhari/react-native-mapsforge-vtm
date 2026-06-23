/**
 * External dependencies
 */
import { useContext, useEffect, useMemo } from 'react';

/**
 * Internal dependencies
 */
import LayerPathModule, {
	type LayerPathProps,
	type LayerPathResponse,
	type TriggerParams,
} from '../NativeModules/NativeLayerPath';
import type { ErrorBase } from '../types';
import useLayerPathEventSubscription from '../compose/useLayerPathEventSubscription';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const moduleDefaults = LayerPathModule.getConstants();

const LayerPath = ({
	coordinates,
	responseInclude: responseIncludeParams,
	gestureScreenDistance,
	style,
	simplificationTolerance,

	onCreate,
	onRemove,
	onChange,
	onError,

	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
	triggerEvent,
}: LayerPathProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const responseInclude = useMemo(
		() => ({
			...moduleDefaults.responseInclude,
			...responseIncludeParams,
		}),
		[responseIncludeParams]
	);

	// onTrigger is different, it doesn't require native gesture detection.
	const supportsGestures = !!onPress || !!onLongPress || !!onDoubleTap;

	const hasCoordinates = !!coordinates && coordinates.length > 0;

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && hasCoordinates,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || !coordinates) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg: 'Missing nativeNodeHandle or coordinates',
					},
				} as ErrorBase);
			}
			return LayerPathModule.createLayer({
				nativeNodeHandle,
				supportsGestures,
				coordinates,
				...(style && { style }),
				...(responseInclude && { responseInclude }),
				...(gestureScreenDistance && { gestureScreenDistance }),
				...(simplificationTolerance && { simplificationTolerance }),
			}).then((response: LayerPathResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerPathModule.removeLayer({
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

	// Redraw the existing native layer in place when the line or its style
	// changes, instead of tearing down and recreating the layer.
	useEffect(() => {
		if (uuid && nativeNodeHandle && coordinates && coordinates.length > 0) {
			LayerPathModule.updateCoordinates({
				nativeNodeHandle,
				uuid,
				coordinates,
				...(style && { style }),
				...(responseInclude && { responseInclude }),
				...(simplificationTolerance && { simplificationTolerance }),
			})
				.then((response: LayerPathResponse) => {
					onChange ? onChange(response) : null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		nativeNodeHandle,
		coordinates,
		simplificationTolerance,
		style,
		responseInclude,
		onChange,
		onError,
	]);

	// Update gesture detection on the existing native layer when the
	// handlers change, instead of tearing down and recreating the layer.
	useEffect(() => {
		if (uuid && nativeNodeHandle) {
			LayerPathModule.updateSupportsGestures({
				nativeNodeHandle,
				uuid,
				supportsGestures,
			}).catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
		}
	}, [
		uuid,
		nativeNodeHandle,
		supportsGestures,
		onError,
	]);

	useEffect(() => {
		const remove = () => {
			if (triggerEvent) {
				triggerEvent.current = null;
			}
		};
		if (uuid) {
			if (triggerEvent) {
				triggerEvent.current = (params: TriggerParams) => {
					LayerPathModule.triggerEvent({
						...(nativeNodeHandle && { nativeNodeHandle }),
						uuid,
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
		nativeNodeHandle,
		triggerEvent,
	]);

	useLayerPathEventSubscription({
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	});

	return null;
};

/// ??? add defaults

export default LayerPath;
