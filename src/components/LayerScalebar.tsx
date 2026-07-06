/**
 * External dependencies
 */
import { useContext, useRef } from 'react';

/**
 * Internal dependencies
 */
import LayerScalebarModule, {
	type LayerScalebarProps,
} from '../NativeModules/NativeLayerScalebar';
import type { ErrorBase } from '../types';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const LayerScalebar = ({ onCreate, onRemove, onError }: LayerScalebarProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const positionIndexRef = useRef<number>(-1);
	const { uuid } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle,
		create: ({ triggerOnCreate }) => {
			if (!nativeNodeHandle) {
				return Promise.reject<string>({
					userInfo: { errorMsg: 'Missing nativeNodeHandle' },
				} as ErrorBase);
			}
			return LayerScalebarModule.createLayer({
				nativeNodeHandle,
				positionIndex: positionIndexRef.current,
			}).then((newUuid) => {
				triggerOnCreate && onCreate
					? onCreate({ nativeNodeHandle, uuid: newUuid })
					: null;
				return newUuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerScalebarModule.removeLayer({
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
	positionIndexRef.current = positionIndex;

	return null;
};

export default LayerScalebar;
