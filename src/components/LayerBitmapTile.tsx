/**
 * External dependencies
 */
import { useContext, useEffect } from 'react';

/**
 * Internal dependencies
 */
import LayerBitmapTileModule, {
	type LayerBitmapTileProps,
} from '../NativeModules/NativeLayerBitmapTile';
import type { ErrorBase } from '../types';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const LayerBitmapTile = ({
	url,
	alpha,
	zoomMin,
	zoomMax,
	enabledZoomMin,
	enabledZoomMax,
	cacheSize,
	cacheDirBase,
	cacheDirChild,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerBitmapTileProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: { errorMsg: 'Missing nativeNodeHandle' },
				} as ErrorBase);
			}
			return LayerBitmapTileModule.createLayer({
				nativeNodeHandle,
				positionIndex,
				...(url && { url }),
				...(alpha && { alpha }), // java side will ensure it is between 0 and 1.
				...(zoomMin && { zoomMin: Math.round(zoomMin) }),
				...(zoomMax && { zoomMax: Math.round(zoomMax) }),
				...(enabledZoomMin && {
					enabledZoomMin: Math.round(enabledZoomMin),
				}),
				...(enabledZoomMax && {
					enabledZoomMax: Math.round(enabledZoomMax),
				}),
				...(cacheSize && { cacheSize: Math.round(cacheSize) }),
				...(cacheDirBase && { cacheDirBase: cacheDirBase.trim() }),
				...(cacheDirChild && {
					cacheDirChild: cacheDirChild.trim(),
				}),
			}).then((newUuid) => {
				triggerOnCreate && onCreate
					? onCreate({ nativeNodeHandle, uuid: newUuid })
					: null;
				triggerOnChange && onChange
					? onChange({ nativeNodeHandle, uuid: newUuid })
					: null;
				return newUuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerBitmapTileModule.removeLayer({
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

	const { positionIndex } = useLayerOrder(uuid, 'bitmap-tile');

	// enabledZoomMin enabledZoomMax changed.
	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerBitmapTileModule.updateEnabledZoomMinMax({
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
			LayerBitmapTileModule.setAlpha({
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
	// and recreating the layer. triggerRemove resets uuid to null on success, which is what lets
	// the hook's own mount logic re-trigger creation via triggerCreate below.
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
		url,
		zoomMin,
		zoomMax,
		cacheSize,
		cacheDirBase,
		cacheDirChild,
		triggerRemove,
		triggerCreate,
	]);

	return null;
};

LayerBitmapTile.defaults = LayerBitmapTileModule.getConstants();

export default LayerBitmapTile;
