/**
 * External dependencies
 */
import { useContext, useMemo } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';
import NativeMapContainer, {
	type GetPositionResponse,
} from '../NativeModules/NativeMapContainer';
// `Bbox` already exists as a public type (LayerPath's response shape) -- same GeoJSON
// [ west, south, east, north ] convention, so reused here rather than duplicated.
import type { Bbox } from '../NativeModules/NativeLayerPath';
import type { Position } from '../types';

/**
 * One of vtm's org.oscim.utils.animation.Easing.Type names, lowercased.
 */
export type EasingType =
	| 'linear'
	| 'sine_inout'
	| 'sine_in'
	| 'sine_out'
	| 'expo_out'
	| 'quad_inout'
	| 'cubic_inout'
	| 'quart_inout'
	| 'quint_inout';

export type MapPositionTarget = {
	center?: Position;
	zoomLevel?: number;
	bearing?: number;
	tilt?: number;
	roll?: number;
};

export type AnimationOptions = {
	// Milliseconds. Defaults differ per method -- see each one below.
	duration?: number;
	easing?: EasingType;
};

export type FitBoundsOptions = AnimationOptions & {
	paddingPx?: number;
};

export type { GetPositionResponse };

const requireHandle = (nativeNodeHandle: null | number): number => {
	if (!nativeNodeHandle) {
		throw new Error(
			'useMap: nativeNodeHandle is not set yet -- the map view has not been created.'
		);
	}
	return nativeNodeHandle;
};

/**
 * Imperative map control -- panning, zooming, rotating/tilting, animated camera moves and
 * bounds-fitting -- all implemented as thin wrappers around two native primitives,
 * NativeMapContainer.animateTo (itself a thin wrapper around vtm's own
 * org.oscim.map.Animator, which already handles Mercator-correct interpolation and easing) and
 * NativeMapContainer.getPosition. Every method here resolves once the underlying native
 * animation actually finishes (or, for a duration of 0, once the jump is applied), so calls can
 * be awaited and chained.
 *
 * MapHandleContext is only provided to MapContainer's own `children`, so calling useMap() from
 * a component that isn't nested inside <MapContainer> (e.g. a toolbar rendered as its sibling)
 * would otherwise always see a null handle. Passing `nativeNodeHandleOverride` lets such a
 * caller use MapContainer's existing nativeNodeHandle/setNativeNodeHandle "lift the state up"
 * props instead of needing its own bridge component just to reach the context.
 */
const useMap = (nativeNodeHandleOverride?: null | number) => {
	const { nativeNodeHandle: contextHandle } = useContext(MapHandleContext);
	const nativeNodeHandle =
		nativeNodeHandleOverride === undefined
			? contextHandle
			: nativeNodeHandleOverride;

	return useMemo(() => {
		const animateTo = (
			target: MapPositionTarget,
			options?: AnimationOptions
		): Promise<void> =>
			NativeMapContainer.animateTo({
				nativeNodeHandle: requireHandle(nativeNodeHandle),
				...target,
				duration: options?.duration ?? 0,
				easing: options?.easing ?? 'linear',
			});

		const getPosition = (): Promise<GetPositionResponse> =>
			NativeMapContainer.getPosition({
				nativeNodeHandle: requireHandle(nativeNodeHandle),
			});

		const jumpTo = (target: MapPositionTarget): Promise<void> =>
			animateTo(target);

		const panTo = (center: Position): Promise<void> =>
			animateTo({ center });

		const panBy = async (
			deltaLngLat: readonly [number, number]
		): Promise<void> => {
			const [
				lng,
				lat,
				alt,
			] = await getPosition().then((p) => p.center);
			return panTo(
				alt !== undefined
					? [
							lng! + deltaLngLat[0],
							lat! + deltaLngLat[1],
							alt,
						]
					: [lng! + deltaLngLat[0], lat! + deltaLngLat[1]]
			);
		};

		const setZoom = (zoomLevel: number): Promise<void> =>
			animateTo({ zoomLevel });

		const zoomOut = async (by: number = 1): Promise<void> => {
			const current = await getPosition();
			return setZoom(current.zoomLevel - by);
		};

		const setBearing = (bearing: number): Promise<void> =>
			animateTo({ bearing });

		const resetNorth = (): Promise<void> => setBearing(0);

		const resetNorthPitch = (): Promise<void> =>
			animateTo({ bearing: 0, tilt: 0 });

		const setRoll = (roll: number): Promise<void> => animateTo({ roll });

		const easeTo = (
			target: MapPositionTarget,
			options?: AnimationOptions
		): Promise<void> =>
			animateTo(target, {
				duration: options?.duration ?? 300,
				easing: options?.easing ?? 'sine_inout',
			});

		const flyTo = (
			target: MapPositionTarget,
			options?: AnimationOptions
		): Promise<void> =>
			animateTo(target, {
				duration: options?.duration ?? 1200,
				easing: options?.easing ?? 'expo_out',
			});

		const fitBounds = (
			bounds: Bbox,
			options?: FitBoundsOptions
		): Promise<void> =>
			NativeMapContainer.animateTo({
				nativeNodeHandle: requireHandle(nativeNodeHandle),
				bounds,
				boundsPaddingPx: options?.paddingPx ?? 0,
				duration: options?.duration ?? 0,
				easing: options?.easing ?? 'linear',
			});

		const flyToBounds = (
			bounds: Bbox,
			options?: FitBoundsOptions
		): Promise<void> =>
			fitBounds(bounds, {
				paddingPx: options?.paddingPx ?? 0,
				duration: options?.duration ?? 1200,
				easing: options?.easing ?? 'expo_out',
			});

		// Approximate: clamps the current center into `bounds` along each axis independently.
		// This is not Leaflet's minimal-pan-to-reveal-the-bounds algorithm (that needs the current
		// on-screen viewport's own geographic extent, which isn't exposed natively yet) -- it's a
		// simpler "keep the camera over this area" behaviour.
		const panInsideBounds = async (bounds: Bbox): Promise<void> => {
			const [lng, lat] = await getPosition().then((p) => p.center);
			const [
				west,
				south,
				east,
				north,
			] = bounds;
			const clampedLng = Math.min(Math.max(lng!, west!), east!);
			const clampedLat = Math.min(Math.max(lat!, south!), north!);
			if (clampedLng === lng && clampedLat === lat) {
				return;
			}
			return panTo([clampedLng, clampedLat]);
		};

		// Approximate, same caveat as panInsideBounds: treats `point` as the new center if it
		// isn't already one, rather than doing a true minimal on-screen reveal.
		const panInside = (point: Position): Promise<void> => panTo(point);

		return {
			getPosition,
			jumpTo,
			panTo,
			panBy,
			setZoom,
			zoomTo: setZoom,
			zoomOut,
			setBearing,
			rotateTo: setBearing,
			resetNorth,
			resetNorthPitch,
			setRoll,
			easeTo,
			flyTo,
			fitBounds,
			setBounds: fitBounds,
			flyToBounds,
			panInsideBounds,
			panInside,
		};
	}, [nativeNodeHandle]);
};

export default useMap;
