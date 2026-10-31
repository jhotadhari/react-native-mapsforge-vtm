/**
 * External dependencies
 */
import { useContext, useEffect, useMemo } from 'react';

/**
 * Internal dependencies
 */
import LayerScreenLineModule, {
	type LayerScreenLineProps,
	type LineResponse,
} from '../NativeModules/NativeScreenLine';
import type { ErrorBase } from '../types';
import { splitPathCoordinates } from '../utils/screenPosition';
import useLayerAnchor from '../compose/useLayerAnchor';
import useSceneFragmentUuid from '../compose/useSceneFragmentUuid';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';
import { runUuidFor } from '../scene/ids';

/**
 * A polyline whose geometry is rebuilt on the Java side every rendered
 * frame from the frame's own sync viewport — zero latency, zero bridge
 * crossings, rotation/bearing-correct.
 *
 * This is the zero-lag sibling of {@code LayerPath} for lines that must
 * stay glued to the viewport while panning: screen-anchored points
 * (`['50%', '50%']`) track the screen exactly (LayerPath's anchored
 * points recompile asynchronously on a worker thread and trail by 1–2
 * frames). Each component renders one dedicated native layer, so use it
 * for a handful of lines, not thousands.
 *
 * Gestures (onPress/hit-testing) are not supported yet.
 */
const LayerScreenLine = ({
	coordinates,
	paint,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerScreenLineProps) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);

	// Split the coordinates prop into the geographic coordinates and the
	// screen-anchored point descriptors (string sugar normalized here —
	// only numeric values cross the bridge).
	const { geoCoordinates, screenPoints } = useMemo(
		() => splitPathCoordinates(coordinates ?? []),
		[coordinates]
	);

	const hasCoordinates = geoCoordinates.length + screenPoints.length >= 2;

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
		layerType: 'screenLine',
		shared: true,
	});

	// The scene-authoritative run key — standalone creation waits for it so
	// the line lands at its tree position in the layer stack.
	const runFragmentUuid = useSceneFragmentUuid(anchorUid);

	const { uuid } = useNativeLayerLifecycle({
		enabled:
			!!nativeNodeHandle && hasCoordinates && runFragmentUuid !== null,
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
			return LayerScreenLineModule.createLine({
				nativeNodeHandle,
				fragmentUuid,
				layerUuids,
				coordinates: geoCoordinates,
				// Always sent (possibly empty): the key itself carries the
				// "replace the anchor set" semantics on the native side.
				screenPoints,
				...(paint && { paint }),
			}).then((response: LineResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerScreenLineModule.removeLine({
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

	// Update the existing native line in place when its props change.
	useEffect(() => {
		if (uuid && nativeNodeHandle) {
			LayerScreenLineModule.updateLine({
				nativeNodeHandle,
				uuid,
				coordinates: geoCoordinates,
				// Always sent (possibly empty): the key itself carries the
				// "replace the anchor set" semantics on the native side.
				screenPoints,
				...(paint && { paint }),
			})
				.then((response: LineResponse) => {
					onChange ? onChange(response) : null;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		}
	}, [
		uuid,
		nativeNodeHandle,
		geoCoordinates,
		screenPoints,
		paint,
		onChange,
		onError,
	]);

	return anchorElement;
};

export default LayerScreenLine;
