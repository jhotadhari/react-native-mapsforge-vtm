import { useRef, useEffect } from 'react';
import type { EventSubscription } from 'react-native';
import LayerPathModule, {
	type LayerPathGestureResponse,
	type LayerPathProps,
} from '../NativeModules/NativeLayerPath';

const useLayerPathEventSubscription = ({
	uuid,
	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
}: {
	uuid?: null | false | string;
	onPress: LayerPathProps['onPress'];
	onLongPress: LayerPathProps['onLongPress'];
	onDoubleTap: LayerPathProps['onDoubleTap'];
	onTrigger: LayerPathProps['onTrigger'];
}) => {
	const pathEventSubscription = useRef<null | EventSubscription>(null);
	useEffect(() => {
		const removeSubscription = () => {
			pathEventSubscription.current?.remove();
			pathEventSubscription.current = null;
		};
		if (onPress || onLongPress || onDoubleTap || onTrigger) {
			pathEventSubscription.current = LayerPathModule.onPathEvent(
				(response?: LayerPathGestureResponse) => {
					if (response && (!uuid || response?.uuid === uuid)) {
						response?.type === 'press' &&
							onPress &&
							onPress(response);
						response?.type === 'longPress' &&
							onLongPress &&
							onLongPress(response);
						response?.type === 'doubleTap' &&
							onDoubleTap &&
							onDoubleTap(response);
						response?.type === 'trigger' &&
							onTrigger &&
							onTrigger(response);
					}
				}
			);
		} else {
			removeSubscription();
		}
		return removeSubscription;
	}, [
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	]);
};

export default useLayerPathEventSubscription;
