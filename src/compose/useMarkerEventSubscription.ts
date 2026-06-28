import { useRef, useEffect } from 'react';
import type { EventSubscription } from 'react-native';
import LayerMarkerModule, {
	type MarkerEvent,
	type MarkerProps,
} from '../NativeModules/NativeLayerMarker';

const useMarkerEventSubscription = ({
	uuid,
	layerUuid,
	onEvent,
	onPress,
	onLongPress,
	onTrigger,
}: {
	uuid?: null | false | string;
	layerUuid?: null | false | string;
	onEvent: MarkerProps['onEvent'];
	onPress: MarkerProps['onPress'];
	onLongPress: MarkerProps['onLongPress'];
	onTrigger: MarkerProps['onTrigger'];
}) => {
	const markerEventSubscription = useRef<null | EventSubscription>(null);
	useEffect(() => {
		const removeSubscription = () => {
			markerEventSubscription.current?.remove();
			markerEventSubscription.current = null;
		};
		if (onEvent || onPress || onLongPress || onTrigger) {
			markerEventSubscription.current = LayerMarkerModule.onMarkerEvent(
				(response?: MarkerEvent) => {
					if (
						response &&
						(!uuid || response?.uuid === uuid) &&
						(!layerUuid || response?.markerLayerUuid === layerUuid)
					) {
						onEvent && onEvent(response);
						response?.event === 'itemSingleTapUp' &&
							onPress &&
							onPress(response);
						response?.event === 'itemLongPress' &&
							onLongPress &&
							onLongPress(response);
						response?.event === 'itemTrigger' &&
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
		layerUuid,
		onEvent,
		onPress,
		onLongPress,
		onTrigger,
	]);
};

export default useMarkerEventSubscription;
