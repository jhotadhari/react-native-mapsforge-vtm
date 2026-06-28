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
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

/**
 * Buildings and labels each get their own real native layer (and uuid), registered via
 * useLayerOrder alongside the main tile layer rather than bundled into one
 * org.oscim.layers.GroupLayer (see CLAUDE.md). Calling useLayerOrder for all three -- main, then
 * buildings, then labels, in that fixed order every render -- keeps them contiguous in the shared
 * ordering registry the same way any other set of sibling layers would be. This internal component
 * isn't part of the public API; LayerMapsforge below renders it conditionally as a child for
 * whichever of hasBuildings/hasLabels is on, so mounting/unmounting alone drives create/remove.
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
		positionIndex: number;
		parentUuid: string;
		enabledZoomMin?: number;
		enabledZoomMax?: number;
	}) => Promise<string>;
	removeSubLayer: (params: {
		nativeNodeHandle: number;
		uuid: string;
	}) => Promise<string>;
}) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

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
				positionIndex,
				parentUuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax: Math.round(enabledZoomMax),
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

	const { positionIndex } = useLayerOrder(uuid, 'mapsforge');

	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMapsforgeModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax: Math.round(enabledZoomMax),
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

	return null;
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
	const { nativeNodeHandle } = useContext(MapHandleContext);

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
				positionIndex,
				mapFile,
				...(renderTheme && { renderTheme }),
				...(renderStyle && { renderStyle }),
				...(renderOverlays && { renderOverlays }),
				hasBuildings: !!hasBuildings,
				hasLabels: !!hasLabels,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax: Math.round(enabledZoomMax),
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

	const { positionIndex } = useLayerOrder(uuid, 'mapsforge');

	// enabledZoomMin/enabledZoomMax changed -- update in place, same as every other layer type.
	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMapsforgeModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax: Math.round(enabledZoomMax),
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
