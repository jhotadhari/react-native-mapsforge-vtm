/**
 * External dependencies
 */
import { useContext, useEffect } from 'react';

/**
 * Internal dependencies
 */
import LayerShapeModule, {
	type LayerShapeProps,
	type LayerShapeResponse,
	type ShapeTriggerParams,
} from '../NativeModules/NativeLayerShape';
import type { ErrorBase } from '../types';
import useLayerShapeEventSubscription from '../compose/useLayerShapeEventSubscription';
import useLayerAnchor from '../compose/useLayerAnchor';
import useLayerEntry from '../compose/useLayerEntry';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import {
	enqueueCreateShape,
	enqueueRemoveShape,
} from '../compose/ShapeBatchQueue';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import SharedLayerContext from '../context/SharedLayerContext';
import { fragmentUuidFor, runUuidFor } from '../scene/ids';

type ShapeParams = {
	type: string;
	rings?: ReadonlyArray<ReadonlyArray<number>>;
	holes?: ReadonlyArray<ReadonlyArray<ReadonlyArray<number>>>;
	center?: ReadonlyArray<number>;
	radiusKm?: number;
	numSegments?: number;
	min?: ReadonlyArray<number>;
	max?: ReadonlyArray<number>;
	position?: ReadonlyArray<number>;
};

// Converts the typed ShapeDefinition to a codegen-compatible plain object
// (codegen only allows Readonly<{...}> inline shapes).
const shapeToParams = (
	shape: NonNullable<LayerShapeProps['shape']>
): ShapeParams => {
	const shapeParams: Record<string, unknown> = {
		type: shape.type,
	};
	switch (shape.type) {
		case 'polygon':
			shapeParams.rings = shape.rings;
			if (shape.holes) shapeParams.holes = shape.holes;
			break;
		case 'circle':
			shapeParams.center = shape.center;
			shapeParams.radiusKm = shape.radiusKm;
			if (shape.numSegments) shapeParams.numSegments = shape.numSegments;
			break;
		case 'rectangle':
			shapeParams.min = shape.min;
			shapeParams.max = shape.max;
			break;
		case 'hexagon':
			shapeParams.center = shape.center;
			shapeParams.radiusKm = shape.radiusKm;
			break;
		case 'point':
			shapeParams.position = shape.position;
			break;
	}
	return shapeParams as ShapeParams;
};

/**
 * Draws geometric shapes on the map using vtm-jts drawables.
 *
 * Multiple {@code LayerShape} components that are consecutive siblings of the
 * same type share a single native {@code VectorLayer} fragment (via
 * {@code ShapeLayerManager}) — or one fragment per type when grouped by a
 * {@code SharedLayer} wrapper.
 */
const LayerShape = ({
	shape,
	paint,
	gestureScreenDistance,

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
}: LayerShapeProps & {
	/** Owner-injected sibling position inside a SharedLayer fragment. */
	vtmSortIndex?: number;
	/**
	 * Explicit position inside a fragment. Overrides vtmSortIndex — use it
	 * when the shape is nested inside wrapper components or host Views that
	 * can't forward the injected index. Lower = earlier = lower z-order.
	 */
	order?: number;
}) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);
	const sharedId = useContext(SharedLayerContext);
	const isGrouped = sharedId !== null;

	const supportsGestures = !!onPress || !!onLongPress || !!onDoubleTap;

	const hasShape = !!shape;

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
		layerType: 'shape',
		shared: true,
		active: !isGrouped,
	});

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && hasShape,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || !shape) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg: 'Missing nativeNodeHandle or shape',
					},
				} as ErrorBase);
			}
			const fragmentUuid =
				sharedId !== null
					? fragmentUuidFor(sharedId, 'shape')
					: runUuidFor(anchorUid);
			return enqueueCreateShape({
				nativeNodeHandle,
				fragmentUuid,
				shape: shapeToParams(shape),
				supportsGestures,
				...(paint && { paint }),
				...(gestureScreenDistance != null && { gestureScreenDistance }),
			}).then((response: LayerShapeResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return enqueueRemoveShape(nativeNodeHandle, currentUuid)
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

	useLayerEntry({
		active: isGrouped,
		fragmentId: sharedId,
		layerType: 'shape',
		sortIndex: order ?? vtmSortIndex,
		uuid,
	});

	useSceneUuidBinding(isGrouped ? null : anchorUid, uuid);

	// Update shape in place when props change.
	useEffect(() => {
		if (uuid && nativeNodeHandle && shape) {
			LayerShapeModule.updateShape({
				nativeNodeHandle,
				uuid,
				shape: shapeToParams(shape),
				...(paint && { paint }),
			})
				.then((response: LayerShapeResponse) => {
					onChange ? onChange(response) : null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		nativeNodeHandle,
		shape,
		paint,
		onChange,
		onError,
	]);

	// Wire up the programmatic triggerEvent ref.
	useEffect(() => {
		const remove = () => {
			if (triggerEvent) {
				triggerEvent.current = null;
			}
		};
		if (uuid) {
			if (triggerEvent) {
				triggerEvent.current = (params: ShapeTriggerParams) => {
					LayerShapeModule.triggerEvent({
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

	useLayerShapeEventSubscription({
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	});

	return anchorElement;
};

LayerShape.defaults = LayerShapeModule.getConstants();

export default LayerShape;
