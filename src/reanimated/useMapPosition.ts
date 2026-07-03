import { useCallback, useMemo } from 'react';
import { useSharedValue, type SharedValue } from 'react-native-reanimated';
import type { MapEventResponse } from '../NativeViews/MapsforgeVtmViewNativeComponent';

export interface MapPositionSharedValues {
	/** [longitude, latitude] or null if no position received yet */
	centerSv: SharedValue<[number, number] | null>;
	zoomSv: SharedValue<number>;
	bearingSv: SharedValue<number>;
	tiltSv: SharedValue<number>;
	/** Map viewport width in dp (0 until the first map update event arrives). */
	viewportWidthSv: SharedValue<number>;
	/** Map viewport height in dp (0 until the first map update event arrives). */
	viewportHeightSv: SharedValue<number>;
	/**
	 * Pass this to MapContainer's onMapUpdate prop.
	 * Intercepts bridge events and writes to shared values.
	 */
	handleMapUpdate: (response: {
		nativeEvent: Readonly<MapEventResponse>;
	}) => void;
	/**
	 * Spread this into {@code <MapContainer responseInclude={...} />} to opt
	 * in to viewport-dimension emission. Without it the native side does not
	 * send {@code viewportWidth} / {@code viewportHeight}, and the
	 * corresponding shared values stay at {@code 0}.
	 */
	responseInclude: { viewportWidth: number; viewportHeight: number };
}

/**
 * Creates reanimated shared values that track map position.
 *
 * The returned `handleMapUpdate` should be passed to
 * `<MapContainer onMapUpdate={handleMapUpdate} />`.
 * Worklet consumers (`useDerivedValue`, `useAnimatedProps`, etc.) can
 * then read the shared values at 60fps on the UI thread without bridge
 * crossings or React re-renders.
 *
 * Coexists with a separate `onMapUpdate` callback — you can use both.
 *
 * @example
 * ```tsx
 * import { useMapPosition } from 'react-native-mapsforge-vtm/reanimated';
 * import Animated, {
 *   useDerivedValue,
 *   useAnimatedProps,
 * } from 'react-native-reanimated';
 *
 * function App() {
 *   const { centerSv, zoomSv, handleMapUpdate } = useMapPosition();
 *
 *   const latText = useDerivedValue(() => {
 *     return centerSv.value?.[1]?.toFixed(6) ?? '';
 *   });
 *
 *   return (
 *     <>
 *       <MapContainer onMapUpdate={handleMapUpdate}>
 *         {/* layers * /}
 *       </MapContainer>
 *       <AnimatedText text={latText} />
 *     </>
 *   );
 * }
 * ```
 */
export function useMapPosition(): MapPositionSharedValues {
	const centerSv = useSharedValue<[number, number] | null>(null);
	const zoomSv = useSharedValue<number>(0);
	const bearingSv = useSharedValue<number>(0);
	const tiltSv = useSharedValue<number>(0);
	const viewportWidthSv = useSharedValue<number>(0);
	const viewportHeightSv = useSharedValue<number>(0);

	const handleMapUpdate = useCallback(
		(response: { nativeEvent: Readonly<MapEventResponse> }) => {
			const e = response.nativeEvent;
			if (e.center && e.center.length >= 2) {
				centerSv.value = [e.center[0]!, e.center[1]!];
			}
			if (e.zoomLevel != null) zoomSv.value = e.zoomLevel;
			if (e.bearing != null) bearingSv.value = e.bearing;
			if (e.tilt != null) tiltSv.value = e.tilt;
			if (e.viewportWidth != null)
				viewportWidthSv.value = e.viewportWidth;
			if (e.viewportHeight != null)
				viewportHeightSv.value = e.viewportHeight;
		},
		[
			centerSv,
			zoomSv,
			bearingSv,
			tiltSv,
			viewportWidthSv,
			viewportHeightSv,
		]
	);

	return useMemo(
		() => ({
			centerSv,
			zoomSv,
			bearingSv,
			tiltSv,
			viewportWidthSv,
			viewportHeightSv,
			handleMapUpdate,
			responseInclude: { viewportWidth: 2, viewportHeight: 2 },
		}),
		[
			centerSv,
			zoomSv,
			bearingSv,
			tiltSv,
			viewportWidthSv,
			viewportHeightSv,
			handleMapUpdate,
		]
	);
}
