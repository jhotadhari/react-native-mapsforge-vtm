/**
 * External dependencies
 */
import { useContext, useEffect } from 'react';

/**
 * Internal dependencies
 */
import LayerMBTilesBitmapModule, {
	type LayerMBTilesBitmapProps,
	type LayerMBTilesBitmapResponse,
} from '../NativeModules/NativeLayerMBTilesBitmap';
import type { ErrorBase } from '../types';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const LayerMBTilesBitmap = ({
	mapFile,
	transparentColor,
	alpha,
	enabledZoomMin,
	enabledZoomMax,
	cacheSize,
	cacheDirBase,
	cacheDirChild,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerMBTilesBitmapProps) => {
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
			return LayerMBTilesBitmapModule.createLayer({
				nativeNodeHandle,
				positionIndex,
				mapFile,
				...(transparentColor && { transparentColor }),
				...(alpha && { alpha }), // java side will ensure it is between 0 and 1.
				...(enabledZoomMin && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax && {
					enabledZoomMax: Math.round(enabledZoomMax),
				}),
				...(cacheSize !== undefined && {
					cacheSize: Math.round(cacheSize),
				}),
				...(cacheDirBase && { cacheDirBase: cacheDirBase.trim() }),
				...(cacheDirChild && { cacheDirChild: cacheDirChild.trim() }),
			}).then((response: LayerMBTilesBitmapResponse) => {
				triggerOnCreate && onCreate ? onCreate(response) : null;
				triggerOnChange && onChange ? onChange(response) : null;
				return response.uuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerMBTilesBitmapModule.removeLayer({
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

	const { positionIndex } = useLayerOrder(uuid);

	// enabledZoomMin enabledZoomMax changed.
	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMBTilesBitmapModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax && {
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

	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerMBTilesBitmapModule.setAlpha({
				nativeNodeHandle,
				uuid,
				...(alpha && { alpha }), // java side will ensure it is between 0 and 1.
			}).catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
		}
	}, [
		alpha,
		nativeNodeHandle,
		uuid,
		onError,
	]);

	// There's no native "update in place" for these -- changing any of them requires tearing down
	// and recreating the layer (mapFile/transparentColor/cache* are all baked into the tile
	// source at construction time -- vtm requires the cache to be set before the tile source is
	// attached to a layer). triggerRemove resets uuid to null on success, which is what lets the
	// hook's own mount logic re-trigger creation via triggerCreate below.
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
		transparentColor,
		cacheSize,
		cacheDirBase,
		cacheDirChild,
		triggerRemove,
		triggerCreate,
	]);

	return null;
};

LayerMBTilesBitmap.defaults = LayerMBTilesBitmapModule.getConstants();

export default LayerMBTilesBitmap;
