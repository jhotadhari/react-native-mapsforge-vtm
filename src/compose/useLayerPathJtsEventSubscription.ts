/**
 * External dependencies
 */
import { useEffect, useRef } from 'react';
import type { EventSubscription } from 'react-native';

/**
 * Internal dependencies
 */
import LayerPathJtsModule, {
	type LayerPathJtsGestureResponse,
} from '../NativeModules/NativeLayerPathJts';

/**
 * Subscribes to the native `onPathJtsEvent` EventEmitter and routes events to the
 * appropriate gesture handler based on `response.type`.
 *
 * Uses the same pattern as `useLayerPathEventSubscription` for LayerPath.
 */
const useLayerPathJtsEventSubscription = ({
	uuid,
	onPress,
	onLongPress,
	onDoubleTap,
	onTrigger,
}: {
	uuid: null | false | string;
	onPress?: null | ((response: LayerPathJtsGestureResponse) => void);
	onLongPress?: null | ((response: LayerPathJtsGestureResponse) => void);
	onDoubleTap?: null | ((response: LayerPathJtsGestureResponse) => void);
	onTrigger?: null | ((response: LayerPathJtsGestureResponse) => void);
}) => {
	const pathEventSubscription = useRef<null | EventSubscription>(null);

	useEffect(() => {
		if (!onPress && !onLongPress && !onDoubleTap && !onTrigger) {
			return;
		}

		pathEventSubscription.current = LayerPathJtsModule.onPathJtsEvent(
			(response: LayerPathJtsGestureResponse) => {
				// If a uuid filter is set (the component has resolved), only accept
				// events matching this component. Otherwise accept all events.
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
			pathEventSubscription.current?.remove();
			pathEventSubscription.current = null;
		};
	}, [
		uuid,
		onPress,
		onLongPress,
		onDoubleTap,
		onTrigger,
	]);
};

export default useLayerPathJtsEventSubscription;
