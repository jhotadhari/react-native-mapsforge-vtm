import { useCallback, useMemo, useRef } from 'react';
import {
	useSharedValue,
	runOnUI,
	type SharedValue,
} from 'react-native-reanimated';
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
	 * Pass this to {@code <MapContainer onMapUpdate={handleMapUpdate} />}.
	 * Fires every vtm frame at 60fps.
	 *
	 * When the native shared-value bridge is active (activated via
	 * {@code activateNativeBridge}), this callback still fires for backward
	 * compatibility but shared values are already updated directly from
	 * native — the callback writes are redundant.
	 */
	handleMapUpdate: (response: {
		nativeEvent: Readonly<MapEventResponse>;
	}) => void;
	/**
	 * Call once the {@code nativeNodeHandle} of the {@code MapContainer} is
	 * known, e.g. from a ref callback:
	 *
	 * {@code <MapContainer ref={(r) => pos.activateNativeBridge(findNodeHandle(r))} />}
	 *
	 * Activates the native shared-value bridge: native creates
	 * Synchronizable primitives, writes position data directly into them
	 * from the render thread at 60fps, and a worklet poller reads them
	 * each frame with zero bridge crossings.
	 *
	 * When reanimated is not installed, this is a no-op — the hook falls
	 * back to the existing {@code onMapUpdate} Fabric-event path.
	 */
	activateNativeBridge: (nativeNodeHandle: number) => void;
}

/**
 * Creates reanimated shared values that track map position at 60fps.
 *
 * The returned {@code handleMapUpdate} should be passed to
 * {@code <MapContainer onMapUpdate={handleMapUpdate} />}.
 *
 * For zero-jitter overlay tracking at true 60fps, call
 * {@code activateNativeBridge(handle)} once the native view handle is
 * known.  Use {@code setNativeNodeHandle / nativeNodeHandle} (exposed by
 * MapContainer) with a useEffect:
 *
 * @example
 * ```tsx
 * import { useMapPosition } from 'react-native-mapsforge-vtm/reanimated';
 * import Animated, { useDerivedValue } from 'react-native-reanimated';
 * import { useState, useEffect } from 'react';
 *
 * function App() {
 *   const pos = useMapPosition();
 *   const [nativeNodeHandle, setNativeNodeHandle] =
 *     useState<number | null>(null);
 *
 *   useEffect(() => {
 *     if (nativeNodeHandle) pos.activateNativeBridge(nativeNodeHandle);
 *   }, [nativeNodeHandle, pos]);
 *
 *   return (
 *     <>
 *       <MapContainer
 *         nativeNodeHandle={nativeNodeHandle}
 *         setNativeNodeHandle={setNativeNodeHandle}
 *         onMapUpdate={pos.handleMapUpdate}
 *       >
 *         {/* layers * /}
 *       </MapContainer>
 *     </>
 *   );
 * }
 * ```
 *
 * When the native bridge is inactive (no {@code activateNativeBridge} call,
 * or reanimated not installed), the hook falls back to the existing
 * {@code onMapUpdate} Fabric-event path.
 */
export function useMapPosition(): MapPositionSharedValues {
	// --- SharedValues (public API — always created) ---

	const centerSv = useSharedValue<[number, number] | null>(null);
	const zoomSv = useSharedValue<number>(0);
	const bearingSv = useSharedValue<number>(0);
	const tiltSv = useSharedValue<number>(0);
	const viewportWidthSv = useSharedValue<number>(0);
	const viewportHeightSv = useSharedValue<number>(0);

	// Track whether the native shared-value bridge is active so the
	// handleMapUpdate callback can skip redundant writes.
	const nativeBridgeActive = useRef(false);

	// One-shot: prevent double activation.
	const bridgeActivated = useRef(false);

	// --- activateNativeBridge ---

	const activateNativeBridge = useMemo(
		() => (nativeNodeHandle: number) => {
			if (bridgeActivated.current) return;
			bridgeActivated.current = true;

			try {
				// The JSI global function is installed automatically by
				// MapContainer.initialize() during TurboModule init on the
				// JS thread.  No explicit TurboModule call needed.

				// Get synchronizables from C++.  The C++ side creates 7
				// Synchronizable instances (one per scalar field), stores
				// them in the writer for this handle, and returns them as
				// a JS object.  Each value has getBlocking() / setBlocking()
				// methods that worklets can read on the UI thread.
				const getSyncFn = (global as any)
					.__getMapPositionSynchronizables as
					| ((handle: number) => any)
					| undefined;
				if (!getSyncFn) {
					// JSI function not installed — reanimated/worklets
					// might not be available.  Fall back to bridge path.
					return;
				}

				const syncs = getSyncFn(nativeNodeHandle);
				if (!syncs) {
					return;
				}

				// Destructure the returned synchronizables.
				const {
					lng: lngSync,
					lat: latSync,
					zoom: zoomSync,
					bearing: bearingSync,
					tilt: tiltSync,
					viewportWidth: vpWSync,
					viewportHeight: vpHSync,
				} = syncs;

				// Start the worklet poller on the UI runtime.  It reads the
				// synchronizables each frame (direct C++ access via
				// getBlocking(), no bridge) and writes into ordinary
				// SharedValue objects, triggering reanimated's normal
				// dependency tracking.
				nativeBridgeActive.current = true;
				runOnUI(() => {
					'worklet';
					const frame = () => {
						const newLng = lngSync.getBlocking() as number;
						const newLat = latSync.getBlocking() as number;
						if (newLng !== 0 || newLat !== 0) {
							centerSv.value = [newLng, newLat];
						}
						zoomSv.value = zoomSync.getBlocking() as number;
						bearingSv.value = bearingSync.getBlocking() as number;
						tiltSv.value = tiltSync.getBlocking() as number;
						viewportWidthSv.value = vpWSync.getBlocking() as number;
						viewportHeightSv.value =
							vpHSync.getBlocking() as number;
						requestAnimationFrame(frame);
					};
					requestAnimationFrame(frame);
				})();
			} catch (_e: unknown) {
				void _e;
				// reanimated/worklets not installed — fall back to the
				// existing Fabric-event path.
				nativeBridgeActive.current = false;
			}
		},
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[]
	);

	// --- handleMapUpdate callback (backward compat / fallback) ---

	const handleMapUpdate = useCallback(
		(response: { nativeEvent: Readonly<MapEventResponse> }) => {
			// When the native shared-value bridge is active, shared values
			// are already updated directly from native — the callback
			// writes are redundant.  Skip them to avoid wasted JS-thread
			// work on every frame.
			if (nativeBridgeActive.current) {
				return;
			}

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

			// Dev guard: warn if vtm's Tile.SIZE changed.
			if (__DEV__ && e.tileSize != null && e.tileSize !== 576) {
				console.warn(
					'[mapsforge-vtm] vtm Tile.SIZE is ' +
						e.tileSize +
						' (expected 576).  Update TILE_SIZE in ' +
						'mercatorUtils.ts or overlays will be offset.'
				);
			}
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
			activateNativeBridge,
		}),
		[
			centerSv,
			zoomSv,
			bearingSv,
			tiltSv,
			viewportWidthSv,
			viewportHeightSv,
			handleMapUpdate,
			activateNativeBridge,
		]
	);
}

export default useMapPosition;
