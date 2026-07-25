/**
 * External dependencies
 */
import { useEffect, useRef } from 'react';
import type { MapEventResponse } from '../NativeViews/MapsforgeVtmViewNativeComponent';

/**
 * Polls a map-event ref at a fixed interval, calling `callback` with the
 * latest {@link MapEventResponse} (or `null` if no event has arrived yet).
 *
 * Uses a callback-ref so the interval is NOT re-registered when the
 * callback identity changes — only an `intervalMs` change restarts the
 * timer.  This avoids churn when callbacks capture state that changes on
 * every render.
 *
 * @param eventRef    - A ref whose `.current` holds the most recent map
 *                      event.  Typically wired via
 *                      `<MapContainer onMapUpdate={…} />` or obtained
 *                      from a shared context.
 * @param intervalMs  - Poll interval in milliseconds.  Pass the same
 *                      value used for {@link MapContainerProps.mapUpdateInterval}.
 * @param callback    - Invoked on each tick with the latest event (or
 *                      `null`).  Stable across re-renders via callback-ref.
 */
export function useMapEventInterval(
	eventRef: React.MutableRefObject<MapEventResponse | null | undefined>,
	intervalMs: number,
	callback: (event: MapEventResponse | null) => void
): void {
	const callbackRef = useRef(callback);
	callbackRef.current = callback;

	useEffect(() => {
		const id = setInterval(() => {
			callbackRef.current(eventRef.current ?? null);
		}, intervalMs);
		return () => clearInterval(id);
	}, [intervalMs, eventRef]);
}
