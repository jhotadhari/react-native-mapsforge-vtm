import { useAnimatedStyle } from 'react-native-reanimated';
import type { MapPositionSharedValues } from './useMapPosition';
import { toScreenPosition } from './mercatorUtils';

/**
 * Returns an animated style that positions an `<Animated.View>` over a fixed
 * geographic coordinate, tracking the map at 60fps with zero bridge crossings
 * and zero React re-renders.
 *
 * The returned `animatedStyle` object carries `position: 'absolute'`, `left`,
 * and `top` (in dp). Spread it into an `<Animated.View>` that is rendered as
 * a sibling of `<MapContainer>` (or inside the wrapper `<View>` that also
 * contains the map).
 *
 * Multiple overlay instances can share a single `useMapPosition()` call —
 * there is no extra cost for additional overlays; they all read from the same
 * shared values.
 *
 * **Limitation (v1):** Bearing and tilt are not accounted for. The overlay
 * position is correct only when the map is north-up and untilted.
 *
 * @param target - The fixed geographic coordinate to track.
 * @param sharedValues - The object returned by {@link useMapPosition}.
 *   Provides the shared values and the `handleMapUpdate` callback.
 * @returns An object with an `animatedStyle` property ready for
 *   `<Animated.View style={...} />`.
 *
 * @example
 * ```tsx
 * import { useMapPosition, useMapOverlay } from 'react-native-mapsforge-vtm/reanimated';
 * import Animated from 'react-native-reanimated';
 *
 * const pos = useMapPosition();
 * const overlay = useMapOverlay({ lat: 51.5074, lng: -0.1278 }, pos);
 *
 * return (
 *   <View style={{ flex: 1 }}>
 *     <MapContainer
 *       onMapUpdate={pos.handleMapUpdate}
 *       responseInclude={pos.responseInclude}
 *     >
 *       <LayerMapsforge mapFile={...} />
 *     </MapContainer>
 *     <Animated.View style={[styles.pin, overlay.animatedStyle]}>
 *       <Text>📍 London</Text>
 *     </Animated.View>
 *   </View>
 * );
 * ```
 */
export function useMapOverlay(
	target: { lat: number; lng: number },
	sharedValues: MapPositionSharedValues
) {
	const { centerSv, viewportWidthSv, viewportHeightSv, zoomSv } =
		sharedValues;

	const animatedStyle = useAnimatedStyle(() => {
		const screenPos = toScreenPosition(
			centerSv,
			viewportWidthSv,
			viewportHeightSv,
			zoomSv,
			target
		);

		if (!screenPos) {
			return {
				position: 'absolute' as const,
				left: 0,
				top: 0,
				opacity: 0,
			};
		}

		return {
			position: 'absolute' as const,
			left: screenPos.x,
			top: screenPos.y,
		};
	});

	return { animatedStyle };
}

export default useMapOverlay;
