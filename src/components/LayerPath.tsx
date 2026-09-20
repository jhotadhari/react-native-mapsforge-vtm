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
	type PathTriggerParams,
} from '../NativeModules/NativeLayerPath';
import type { ErrorBase } from '../types';
import useLayerPathEventSubscription from '../compose/useLayerPathEventSubscription';
import useLayerAnchor from '../compose/useLayerAnchor';
import useLayerEntry from '../compose/useLayerEntry';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import {
	enqueueCreatePath,
	enqueueRemovePath,
} from '../compose/PathBatchQueue';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import SharedLayerContext from '../context/SharedLayerContext';
import { fragmentUuidFor, runUuidFor } from '../scene/ids';

const moduleDefaults = LayerPathModule.getConstants();

const LayerPath = ({
	coordinates,
	responseInclude: responseIncludeParams,
	gestureScreenDistance,
	paint,

	onCreate,
	onRemove,
	onChange,
	onError,

	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
	triggerEvent,

	vtmSortIndex,
	order,
}: LayerPathProps & {
	/** Owner-injected sibling position inside a SharedLayer fragment. */
	vtmSortIndex?: number;
	/**
	 * Explicit position inside a fragment. Overrides vtmSortIndex — use it
	 * when the layer is nested inside wrapper components or host Views that
	 * can't forward the injected index. Lower = earlier = lower z-order.
	 */
	order?: number;
}) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);
	const sharedId = useContext(SharedLayerContext);
	const isGrouped = sharedId !== null;

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

	// Standalone: this component contributes an anchor and belongs to an
	// implicit type-run fragment keyed by its anchor uid.
	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
		layerType: 'path',
		shared: true,
		active: !isGrouped,
	});

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
			const fragmentUuid =
				sharedId !== null
					? fragmentUuidFor(sharedId, 'path')
					: runUuidFor(anchorUid);
			return enqueueCreatePath({
				nativeNodeHandle,
				fragmentUuid,
				supportsGestures,
				coordinates,
				...(paint && { paint }),
				...(responseInclude && { responseInclude }),
				...(gestureScreenDistance && { gestureScreenDistance }),
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
			return enqueueRemovePath(nativeNodeHandle, currentUuid)
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

	// Grouped (inside a SharedLayer): declare an entry instead of an anchor.
	useLayerEntry({
		active: isGrouped,
		fragmentId: sharedId,
		layerType: 'path',
		sortIndex: order ?? vtmSortIndex,
		uuid,
	});

	// Standalone: bind the resolved entry uuid to the anchor uid — the
	// type-run fragment exists once one member resolved.
	useSceneUuidBinding(isGrouped ? null : anchorUid, uuid);

	// Redraw the existing native layer in place when the line or its paint
	// changes, instead of tearing down and recreating the layer.
	useEffect(() => {
		if (uuid && nativeNodeHandle && coordinates && coordinates.length > 0) {
			LayerPathModule.updateCoordinates({
				nativeNodeHandle,
				uuid,
				coordinates,
				...(paint && { paint }),
				...(responseInclude && { responseInclude }),
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
		paint,
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
				triggerEvent.current = (params: PathTriggerParams) => {
					LayerPathModule.triggerEvent({
						...(nativeNodeHandle != null && { nativeNodeHandle }),
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

	return anchorElement;
};

LayerPath.defaults = moduleDefaults;

export default LayerPath;
