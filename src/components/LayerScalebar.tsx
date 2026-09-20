/**
 * External dependencies
 */
import { useContext } from 'react';

/**
 * Internal dependencies
 */
import LayerScalebarModule, {
	type LayerScalebarProps,
} from '../NativeModules/NativeLayerScalebar';
import type { ErrorBase } from '../types';
import useLayerAnchor from '../compose/useLayerAnchor';
import useSceneUuidBinding from '../compose/useSceneUuidBinding';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

const LayerScalebar = ({ onCreate, onRemove, onError }: LayerScalebarProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const { uid: anchorUid, element: anchorElement } = useLayerAnchor({
		kind: 'layer',
	});

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

	useSceneUuidBinding(anchorUid, uuid);

	return anchorElement;
};

export default LayerScalebar;
