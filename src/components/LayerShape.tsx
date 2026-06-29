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
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

/**
 * Draws geometric shapes on the map using vtm-jts drawables.
 *
 * Supports five shape types:
 * - **Polygon** — filled polygon with optional holes (inner rings)
 * - **Circle** — filled circle defined by center + radius in km
 * - **Rectangle** — filled rectangle defined by two corners (min/max)
 * - **Hexagon** — filled hexagon defined by center + radius in km
 * - **Point** — circular point marker
 *
 * Each component instance gets its own dedicated native layer (a vtm-jts
 * {@code VectorLayer} with the appropriate {@code Drawable} added).
 *
 * Shapes support the full {@code GeometryStyleJts} styling (fill color,
 * stroke, transparency, stipple, etc.) plus gesture callbacks for
 * tap/long-press/double-tap on the shape.
 */
const LayerShape = ({
	shape,
	style,
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
}: LayerShapeProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const supportsGestures = !!onPress || !!onLongPress || !!onDoubleTap;

	const hasShape = !!shape;

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
			// Convert the typed ShapeDefinition to a codegen-compatible
			// plain object (codegen only allows Readonly<{...}> inline shapes).
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
					if (shape.numSegments)
						shapeParams.numSegments = shape.numSegments;
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

			return LayerShapeModule.createLayer({
				nativeNodeHandle,
				shape: shapeParams as {
					type: string;
					rings?: ReadonlyArray<ReadonlyArray<number>>;
					holes?: ReadonlyArray<ReadonlyArray<ReadonlyArray<number>>>;
					center?: ReadonlyArray<number>;
					radiusKm?: number;
					numSegments?: number;
					min?: ReadonlyArray<number>;
					max?: ReadonlyArray<number>;
					position?: ReadonlyArray<number>;
				},
				supportsGestures,
				...(style && { style }),
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
			return LayerShapeModule.removeLayer({
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

	// Dedicated-layer ordering.
	useLayerOrder(uuid);

	// Update shape in place when props change.
	useEffect(() => {
		if (uuid && nativeNodeHandle && shape) {
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
					if (shape.numSegments)
						shapeParams.numSegments = shape.numSegments;
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

			LayerShapeModule.updateShape({
				nativeNodeHandle,
				uuid,
				shape: shapeParams as {
					type: string;
					rings?: ReadonlyArray<ReadonlyArray<number>>;
					holes?: ReadonlyArray<ReadonlyArray<ReadonlyArray<number>>>;
					center?: ReadonlyArray<number>;
					radiusKm?: number;
					numSegments?: number;
					min?: ReadonlyArray<number>;
					max?: ReadonlyArray<number>;
					position?: ReadonlyArray<number>;
				},
				...(style && { style }),
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
		style,
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

	return null;
};

export default LayerShape;
