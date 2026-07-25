/**
 * External dependencies
 */
import { useRef, useState } from 'react';
import type { MapEventResponse } from '../NativeViews/MapsforgeVtmViewNativeComponent';

/**
 * Internal dependencies
 */
import { useMapEventInterval } from './useMapEventInterval';
import {
	computeViewportBbox,
	snapBboxToTiles,
	type ViewportBbox,
} from '../mercatorMath';

const bboxKey = (bbox: ViewportBbox | null): string =>
	bbox ? `${bbox[0]},${bbox[1]},${bbox[2]},${bbox[3]}` : 'null';

/**
 * Computes and returns a tile-snapped viewport bounding box, updating
 * only when the snapped bbox actually changes (stable-key dedup).
 *
 * Polls the map-event ref at {@code intervalMs}, projects the four
 * screen corners to geographic coordinates, snaps the resulting bbox
 * to coarse tile boundaries, and only updates React state when the
 * snapped bbox key differs from the previous one.
 *
 * Tile snapping means the value only changes on significant pans
 * (~150 km+ at typical snap zoom) — same-tile pans produce no
 * re-render, so spatial queries and DB fetches stay stable.
 *
 * @param eventRef   - Ref holding the latest map event.
 * @param intervalMs - Poll interval in milliseconds.
 * @param opts.snapZoomOffset - Subtracted from the current map zoom to
 *   determine the tile-zoom for snapping.  Default 4 (so zoom 12 snaps
 *   at tile-zoom 8, ~150 km tiles).
 * @param opts.minSnapZoom    - Floor for tile-zoom.  Default 0.
 * @param opts.maxSnapZoom    - Ceiling for tile-zoom.  Default 8.
 *
 * @returns The tile-snapped {@link ViewportBbox}.  Returns {@code null}
 *          initially (before the first map event arrives) but retains the
 *          last valid bbox during transient invalid states (e.g. zero-size
 *          viewport during layout transitions) to avoid flickering spatial
 *          queries.
 */
export function useViewportBbox(
	eventRef: React.MutableRefObject<MapEventResponse | null | undefined>,
	intervalMs: number,
	opts?: {
		snapZoomOffset?: number;
		minSnapZoom?: number;
		maxSnapZoom?: number;
	}
): ViewportBbox | null {
	const snapZoomOffset = opts?.snapZoomOffset ?? 4;
	const minSnapZoom = opts?.minSnapZoom ?? 0;
	const maxSnapZoom = opts?.maxSnapZoom ?? 8;

	const [bbox, setBbox] = useState<ViewportBbox | null>(null);
	const lastBboxKeyRef = useRef<string | null>(null);

	useMapEventInterval(eventRef, intervalMs, (event) => {
		if (
			!event?.center ||
			event.center.length < 2 ||
			event.zoomLevel == null ||
			!event.viewportWidth ||
			!event.viewportHeight
		) {
			return;
		}

		let raw = computeViewportBbox(
			event.center as [number, number],
			event.zoomLevel,
			event.viewportWidth,
			event.viewportHeight,
			event.bearing ?? 0,
			event.tilt ?? 0
		);

		if (!raw) return;

		const tileZoom = Math.min(
			maxSnapZoom,
			Math.max(minSnapZoom, Math.floor(event.zoomLevel - snapZoomOffset))
		);

		raw = snapBboxToTiles(raw, tileZoom);

		const key = bboxKey(raw);
		if (key !== lastBboxKeyRef.current) {
			lastBboxKeyRef.current = key;
			setBbox(raw);
		}
	});

	return bbox;
}
