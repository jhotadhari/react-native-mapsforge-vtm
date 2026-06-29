/**
 * External dependencies
 */
import { useEffect, useRef } from 'react';
import type { EventSubscription } from 'react-native';

/**
 * Internal dependencies
 */
import LayerShapeModule, {
	type LayerShapeGestureResponse,
} from '../NativeModules/NativeLayerShape';

/**
 * Subscribes to the native `onShapeEvent` EventEmitter and routes events
 * to the appropriate gesture handler based on `response.type`.
 */
const useLayerShapeEventSubscription = ({
	uuid,
	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
}: {
	uuid: null | false | string;
	onPress?: null | ((response: LayerShapeGestureResponse) => void);
	onLongPress?: null | ((response: LayerShapeGestureResponse) => void);
	onDoubleTap?: null | ((response: LayerShapeGestureResponse) => void);
	onTrigger?: null | ((response: LayerShapeGestureResponse) => void);
}) => {
	const shapeEventSubscription = useRef<null | EventSubscription>(null);

	useEffect(() => {
		if (!onPress && !onLongPress && !onDoubleTap && !onTrigger) {
			return;
		}

		shapeEventSubscription.current = LayerShapeModule.onShapeEvent(
			(response: LayerShapeGestureResponse) => {
				if (!uuid || response?.uuid === uuid) {
					if (response.type === 'press' && onPress) {
						onPress(response);
					} else if (response.type === 'longPress' && onLongPress) {
						onLongPress(response);
					} else if (response.type === 'doubleTap' && onDoubleTap) {
						onDoubleTap(response);
					} else if (response.type === 'trigger' && onTrigger) {
						onTrigger(response);
					}
				}
			}
		);

		return () => {
			shapeEventSubscription.current?.remove();
			shapeEventSubscription.current = null;
		};
	}, [
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	]);
};

export default useLayerShapeEventSubscription;
