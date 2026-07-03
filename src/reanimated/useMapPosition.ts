import { useCallback, useMemo } from 'react';
import { useSharedValue, type SharedValue } from 'react-native-reanimated';
import type {
	MapEventResponse,
	PositionEventResponse,
} from '../NativeViews/MapsforgeVtmViewNativeComponent';

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
	 * Fast channel (60fps, unthrottled): pass this to
	 * {@code <MapContainer onMapPosition={handleMapPosition} />}.
	 *
	 * Reads a lightweight flat payload (lng, lat, zoom, bearing, tilt,
	 * viewportWidth, viewportHeight) and writes to shared values. Fires
	 * every vtm frame — zero throttling.
	 */
	handleMapPosition: (response: {
		nativeEvent: Readonly<PositionEventResponse>;
	}) => void;
	/**
	 * Legacy channel (throttled): pass this to MapContainer's onMapUpdate prop.
	 * Intercepts bridge events and writes to shared values.
	 *
	 * If you wire both {@code handleMapPosition} and {@code handleMapUpdate},
	 * the throttled onMapUpdate values will briefly overwrite the fresh
	 * onMapPosition values every ~mapUpdateInterval ms. Prefer wiring only
	 * the fast channel for overlay use.
	 */
	handleMapUpdate: (response: {
		nativeEvent: Readonly<MapEventResponse>;
	}) => void;
	/**
	 * Spread this into {@code <MapContainer responseInclude={...} />} to opt
	 * in to viewport-dimension emission for the legacy (throttled) channel.
	 * The fast channel always sends all fields unconditionally.
	 */
	responseInclude: {
		center: number;
		zoomLevel: number;
		bearing: number;
		tilt: number;
		viewportWidth: number;
		viewportHeight: number;
	};
}

/**
 * Creates reanimated shared values that track map position.
 *
 * Returns two handlers:
 * - {@code handleMapPosition} (fast channel, 60fps) — wire to
 *   {@code <MapContainer onMapPosition={...} />}. Fires every vtm frame
 *   with a lightweight flat payload. Use this for overlay positioning.
 * - {@code handleMapUpdate} (legacy channel, throttled) — wire to
 *   {@code <MapContainer onMapUpdate={...} />}. Fires at most once per
 *   {@code mapUpdateInterval} ms with a full payload including elevation.
 *   Use this for debug displays, logging, or non-reanimated consumers.
 *
 * If you wire both, the throttled channel may briefly overwrite the
 * fast channel's values every {@code mapUpdateInterval} ms. Prefer
 * wiring only the fast channel for overlay use.
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
 *   const { centerSv, zoomSv, handleMapPosition } = useMapPosition();
 *
 *   const latText = useDerivedValue(() => {
 *     return centerSv.value?.[1]?.toFixed(6) ?? '';
 *   });
 *
 *   return (
 *     <>
 *       <MapContainer onMapPosition={handleMapPosition}>
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

	const handleMapPosition = useCallback(
		(response: { nativeEvent: Readonly<PositionEventResponse> }) => {
			const e = response.nativeEvent;
			// Fast channel always emits all fields unconditionally —
			// no null checks needed.
			centerSv.value = [e.lng, e.lat];
			zoomSv.value = e.zoomLevel;
			bearingSv.value = e.bearing;
			tiltSv.value = e.tilt;
			viewportWidthSv.value = e.viewportWidth;
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
			handleMapPosition,
			// Request all fields needed for worklet-based coordinate
			// transforms and overlay positioning. Value 2 = include in
			// both lifecycle events and onMapUpdate.
			// The fast channel (onMapPosition) always sends all fields
			// unconditionally — responseInclude only gates the legacy
			// throttled onMapUpdate channel.
			responseInclude: {
				center: 2,
				zoomLevel: 2,
				bearing: 2,
				tilt: 2,
				viewportWidth: 2,
				viewportHeight: 2,
			},
		}),
		[
			centerSv,
			zoomSv,
			bearingSv,
			tiltSv,
			viewportWidthSv,
			viewportHeightSv,
			handleMapUpdate,
			handleMapPosition,
		]
	);
}
