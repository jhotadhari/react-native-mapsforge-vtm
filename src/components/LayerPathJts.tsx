/**
 * External dependencies
 */
import { useContext, useEffect, useMemo } from 'react';

/**
 * Internal dependencies
 */
import LayerPathJtsModule, {
	type LayerPathJtsProps,
	type LayerPathJtsResponse,
	type PathJtsTriggerParams,
} from '../NativeModules/NativeLayerPathJts';
import type { ErrorBase } from '../types';
import useLayerPathJtsEventSubscription from '../compose/useLayerPathJtsEventSubscription';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const moduleDefaults = LayerPathJtsModule.getConstants();

/**
 * A path layer backed by vtm-jts's {@code org.oscim.layers.vector.PathLayer}.
 *
 * Unlike {@code LayerPath} (which collapses many components into a single shared
 * {@code VectorLayer} via {@code PathLayerManager}), each {@code LayerPathJts}
 * instance owns its own dedicated native layer. This gives access to JTS-specific
 * features (great-circle arcs, built-in Douglas-Peucker generalization) and
 * guarantees correct render ordering, but is less efficient for high path counts.
 *
 * Prefer {@code LayerPathJts} when you need:
 * - Fewer than ~30 paths with full JTS styling
 * - Guaranteed z-order correctness
 * - Great-circle arcs ({@code addGreatCircle})
 * - Built-in generalization (no external simplify library)
 *
 * Prefer {@code LayerPath} for bulk paths (50–1000+) where the shared-layer
 * architecture's performance advantage matters.
 */
const LayerPathJts = ({
	coordinates,
	responseInclude: responseIncludeParams,
	gestureScreenDistance,
	style,

	onCreate,
	onRemove,
	onChange,
	onError,

	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
	triggerEvent,
}: LayerPathJtsProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const responseInclude = useMemo(
		() => ({
			...moduleDefaults.responseInclude,
			...responseIncludeParams,
		}),
		[responseIncludeParams]
	);

	const supportsGestures = !!onPress || !!onLongPress || !!onDoubleTap;

	// Require at least 2 points — native createLayer rejects fewer.
	// Using >= 2 prevents the stuck-state where a single coordinate
	// resolves to a layer creation failure with no recovery path.
	const hasCoordinates = !!coordinates && coordinates.length >= 2;

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
			return LayerPathJtsModule.createLayer({
				nativeNodeHandle,
				positionIndex,
				coordinates,
				supportsGestures,
				...(style && { style }),
				...(responseInclude && { responseInclude }),
				...(gestureScreenDistance != null && { gestureScreenDistance }),
			}).then((response: LayerPathJtsResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerPathJtsModule.removeLayer({
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

	// Dedicated-layer ordering: registers this component's uuid in the shared
	// order registry so reorderLayers can position it correctly relative to
	// other layers. No layerType means no fragment uuid — each component's own
	// uuid is used directly in orderedUuids.
	const { positionIndex } = useLayerOrder(uuid);

	// Redraw the existing native layer in place when the line or its style
	// changes, instead of tearing down and recreating the layer.
	useEffect(() => {
		if (uuid && nativeNodeHandle && coordinates && coordinates.length > 0) {
			LayerPathJtsModule.updateCoordinates({
				nativeNodeHandle,
				uuid,
				coordinates,
				...(style && { style }),
				...(responseInclude && { responseInclude }),
			})
				.then((response: LayerPathJtsResponse) => {
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

		style,
		responseInclude,
		onChange,
		onError,
	]);

	// Wire up the programmatic triggerEvent ref for hit-testing from JS.
	useEffect(() => {
		const remove = () => {
			if (triggerEvent) {
				triggerEvent.current = null;
			}
		};
		if (uuid) {
			if (triggerEvent) {
				triggerEvent.current = (params: PathJtsTriggerParams) => {
					LayerPathJtsModule.triggerEvent({
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

	useLayerPathJtsEventSubscription({
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	});

	return null;
};

export default LayerPathJts;
