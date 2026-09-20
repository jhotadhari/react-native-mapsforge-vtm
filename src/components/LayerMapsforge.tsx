/**
 * External dependencies
 */
import { useContext, useEffect } from 'react';

/**
 * Internal dependencies
 */
import LayerMapsforgeModule, {
	BUILT_IN_THEMES,
	type LayerMapsforgeProps,
	type LayerMapsforgeResponse,
} from '../NativeModules/NativeLayerMapsforge';
import type { ErrorBase, ResponseBase } from '../types';
import useLayerAnchor from '../compose/useLayerAnchor';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

/**
 * Buildings and labels each get their own real native layer (and uuid),
 * rendered as children of this component, rather than bundled into one
 * org.oscim.layers.GroupLayer (see AGENTS.md). Each renders its own anchor
 * (main, then buildings, then labels, in tree order) so the committed-tree
 * walk places them correctly regardless of when the sub-layers mount — they
 * are gated on the parent uuid and appear asynchronously.
 */
const LayerMapsforgeSubLayer = ({
	parentUuid,
	enabledZoomMin,
	enabledZoomMax,
	onError,
	createSubLayer,
	removeSubLayer,
}: {
	parentUuid: string;
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	onError?: null | ((err: ErrorBase) => void);
	createSubLayer: (params: {
		nativeNodeHandle: number;
		parentUuid: string;
		enabledZoomMin?: number;
		enabledZoomMax?: number;
		layerUuids?: ReadonlyArray<string>;
	}) => Promise<string>;
	removeSubLayer: (params: {
		nativeNodeHandle: number;
		uuid: string;
	}) => Promise<string>;
}) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
	});

	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle,
		create: () => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: { errorMsg: 'Missing nativeNodeHandle' },
				} as ErrorBase);
			}
			return createSubLayer({
				nativeNodeHandle,
				parentUuid,
				layerUuids: scene
					.planWithResolved(anchorUid)
					.layers.map((l) => l.uuid),
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
			});
		},
		remove: (currentUuid) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return removeSubLayer({ nativeNodeHandle, uuid: currentUuid })
				.then(() => true)
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
					return false;
				});
		},
		onError,
	});

	useSceneUuidBinding(anchorUid, uuid);

	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMapsforgeModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
			}).catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
		nativeNodeHandle,
		uuid,
		onError,
	]);

	return anchorElement;
};

const LayerMapsforge = ({
	mapFile,
	renderTheme,
	renderStyle,
	renderOverlays,
	hasBuildings = true,
	hasLabels = true,
	enabledZoomMin,
	enabledZoomMax,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerMapsforgeProps) => {
	const { nativeNodeHandle, scene } = useContext(MapHandleContext);

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
	});

	const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && !!mapFile,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || !mapFile) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg: 'Missing nativeNodeHandle or mapFile',
					},
				} as ErrorBase);
			}
			return LayerMapsforgeModule.createLayer({
				nativeNodeHandle,
				layerUuids: scene
					.planWithResolved(anchorUid)
					.layers.map((l) => l.uuid),
				mapFile,
				...(renderTheme && { renderTheme }),
				...(renderStyle && { renderStyle }),
				...(renderOverlays && { renderOverlays }),
				hasBuildings: !!hasBuildings,
				hasLabels: !!hasLabels,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
			}).then((response: LayerMapsforgeResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerMapsforgeModule.removeLayer({
				nativeNodeHandle,
				uuid: currentUuid,
			})
				.then((removedUuid) => {
					triggerOnRemove && onRemove
						? onRemove({
								nativeNodeHandle,
								uuid: removedUuid,
							} as ResponseBase)
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

	// enabledZoomMin/enabledZoomMax changed -- update in place, same as every other layer type.
	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMapsforgeModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
			}).catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
		nativeNodeHandle,
		uuid,
		onError,
	]);

	// mapFile/renderTheme/renderStyle/renderOverlays are baked into the tile source/theme at
	// construction time, so changing any of them requires a full teardown + recreate (same
	// remove-then-recreate pattern as LayerHillshading/LayerMBTilesBitmap). The buildings/labels
	// sub-layers below are rendered conditionally on this layer's own uuid, so they tear down and
	// recreate themselves automatically when this does -- no separate coordination needed.
	const renderOverlaysKey =
		renderOverlays && renderOverlays.length ? renderOverlays.join(',') : '';
	useEffect(() => {
		triggerRemove({ triggerOnRemove: false }).then((success) => {
			if (success) {
				triggerCreate({
					triggerOnCreate: false,
					triggerOnChange: true,
				});
			}
		});
	}, [
		mapFile,
		renderTheme,
		renderStyle,
		renderOverlaysKey,
		triggerRemove,
		triggerCreate,
	]);

	return (
		<>
			{anchorElement}
			{hasBuildings && uuid && (
				<LayerMapsforgeSubLayer
					parentUuid={uuid}
					enabledZoomMin={enabledZoomMin}
					enabledZoomMax={enabledZoomMax}
					onError={onError}
					createSubLayer={LayerMapsforgeModule.createBuildingLayer}
					removeSubLayer={LayerMapsforgeModule.removeBuildingLayer}
				/>
			)}
			{hasLabels && uuid && (
				<LayerMapsforgeSubLayer
					parentUuid={uuid}
					enabledZoomMin={enabledZoomMin}
					enabledZoomMax={enabledZoomMax}
					onError={onError}
					createSubLayer={LayerMapsforgeModule.createLabelLayer}
					removeSubLayer={LayerMapsforgeModule.removeLabelLayer}
				/>
			)}
		</>
	);
};

LayerMapsforge.defaults = LayerMapsforgeModule.getConstants();
LayerMapsforge.BUILT_IN_THEMES = BUILT_IN_THEMES;

export default LayerMapsforge;
