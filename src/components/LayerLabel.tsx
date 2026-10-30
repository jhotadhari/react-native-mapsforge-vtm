/**
 * External dependencies
 */
import { useContext, useEffect, useMemo } from 'react';

/**
 * Internal dependencies
 */
import LayerLabelModule, {
	type LabelPosition,
	type LabelResponse,
	type LayerLabelProps,
} from '../NativeModules/NativeLayerLabel';
import type { ErrorBase } from '../types';
import { parseScreenPoint } from '../utils/screenPosition';
import useLayerAnchor from '../compose/useLayerAnchor';
import useSceneFragmentUuid from '../compose/useSceneFragmentUuid';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import { runUuidFor } from '../scene/ids';

/**
 * A text label rendered into the map itself (a native bitmap layer at a
 * screen position) — pixel-perfect alignment with map-rendered geometry,
 * zero bridge crossings while panning.
 *
 * When `followPosition` is set, the Java side re-positions the label on
 * every map movement so it stays on the line between the screen `position`
 * and that geographic point: inside the line (with a `gapDp` gap from the
 * anchor) when the line is long enough, beyond the anchor
 * (`fallbackOffsetDp`) when it's too short, hidden when the point is
 * closer than ~5dp.
 */
const LayerLabel = ({
	text,
	position,
	followPosition,
	gapDp,
	fallbackOffsetDp,
	paint,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerLabelProps) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);

	// Normalize the screen-anchor sugar ('50%', '100dp', {x, y, unit})
	// before the bridge call — only numeric values cross the bridge.
	const normalizedPosition: LabelPosition | undefined = useMemo(() => {
		if (!position) {
			return undefined;
		}
		const { x, y, unit } = parseScreenPoint(position);
		return { x, y, unit };
	}, [position]);

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
		layerType: 'label',
		shared: true,
	});

	// The scene-authoritative run key — standalone creation waits for it so
	// the label lands at its tree position in the layer stack.
	const runFragmentUuid = useSceneFragmentUuid(anchorUid);

	const { uuid } = useNativeLayerLifecycle({
		enabled:
			!!nativeNodeHandle &&
			!!text &&
			normalizedPosition !== undefined &&
			runFragmentUuid !== null,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg: 'Missing nativeNodeHandle',
					},
				} as ErrorBase);
			}
			const fragmentUuid =
				runFragmentUuid ??
				scene.plan().runKeysByAnchor.get(anchorUid) ??
				runUuidFor(anchorUid);
			// The absolute target order: the fragment appears at its tree
			// position, atomically with the add.
			const layerUuids = scene
				.planWithResolved(anchorUid)
				.layers.map((l) => l.uuid);
			return LayerLabelModule.createLabel({
				nativeNodeHandle,
				fragmentUuid,
				layerUuids,
				...(text != null && { text }),
				...(normalizedPosition !== undefined && {
					position: normalizedPosition,
				}),
				...(followPosition && { followPosition }),
				...(gapDp != null && { gapDp }),
				...(fallbackOffsetDp != null && { fallbackOffsetDp }),
				...(paint && { paint }),
			}).then((response: LabelResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerLabelModule.removeLabel({
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

	useSceneUuidBinding(anchorUid, uuid);

	// Update the existing native label in place when its props change.
	useEffect(() => {
		if (uuid && nativeNodeHandle) {
			LayerLabelModule.updateLabel({
				nativeNodeHandle,
				uuid,
				...(text != null && { text }),
				...(normalizedPosition !== undefined && {
					position: normalizedPosition,
				}),
				...(followPosition && { followPosition }),
				...(gapDp != null && { gapDp }),
				...(fallbackOffsetDp != null && { fallbackOffsetDp }),
				...(paint && { paint }),
			})
				.then((response: LabelResponse) => {
					onChange ? onChange(response) : null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		nativeNodeHandle,
		text,
		normalizedPosition,
		followPosition,
		gapDp,
		fallbackOffsetDp,
		paint,
		onChange,
		onError,
	]);

	return anchorElement;
};

export default LayerLabel;
