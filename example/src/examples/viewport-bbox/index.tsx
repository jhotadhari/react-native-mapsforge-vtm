/**
 * External dependencies
 */
import { useMemo, useState, useRef, type FC } from 'react';
import { View, Text } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	useViewportBbox,
	type PathPaint,
	type MapEventResponse,
	type ViewportBbox,
} from 'react-native-mapsforge-vtm';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

const INTERVAL_MS = 250;

const rectPaint: PathPaint = {
	strokeColor: '#00ff88',
	strokeWidth: 2,
	fillColor: '#00ff8820',
};

const formatBbox = (bbox: ViewportBbox): string =>
	bbox.map((v) => v.toFixed(4)).join(', ');

const Controls: FC<{
	width: number;
	bbox: ViewportBbox | null;
	changeCount: number;
}> = ({ width, bbox, changeCount }) => (
	<ControlPanel width={width}>
		<ControlSection title="Tile-snapped viewport bbox">
			<StatusLine
				label="Bbox"
				value={bbox ? formatBbox(bbox) : '(waiting for map)'}
			/>
			<StatusLine
				label="Update count"
				value={`${changeCount}`}
			/>
		</ControlSection>
		<ControlSection>
			<Text style={sharedStyles.text}>
				The green rectangle outlines the tile-snapped viewport bbox. It
				only updates when you pan across a coarse tile boundary (~150 km
				at default snapZoomOffset=4, min/maxSnapZoom=0/8). Small pans
				within the same tile produce no re-render — use this for spatial
				queries and DB fetches that shouldn't fire on every frame.
			</Text>
		</ControlSection>
	</ControlPanel>
);

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();
	const eventRef = useRef<MapEventResponse | null>(null);
	const [changeCount, setChangeCount] = useState(0);

	// useViewportBbox internally uses useMapEventInterval at 250ms,
	// then computes + snaps the bbox, and only updates React state
	// when the snapped bbox key differs from the previous one.
	const bbox = useViewportBbox(eventRef, INTERVAL_MS, {
		snapZoomOffset: 4,
		minSnapZoom: 0,
		maxSnapZoom: 8,
	});

	// Track how many times the bbox actually changed.
	const prevBboxKeyRef = useRef<string | null>(null);
	const bboxKey = bbox ? formatBbox(bbox) : null;
	if (bboxKey && bboxKey !== prevBboxKeyRef.current) {
		prevBboxKeyRef.current = bboxKey;
		// Use a microtask to avoid setState-during-render.
		Promise.resolve().then(() => setChangeCount((c) => c + 1));
	}

	// Build a closed ring for the current bbox to render as an outline.
	const bboxRing = useMemo(() => {
		if (!bbox) return null;
		const [
			west,
			south,
			east,
			north,
		] = bbox;
		return [
			[west, south],
			[east, south],
			[east, north],
			[west, north],
			[west, south],
		];
	}, [bbox]);

	const handleMapEventWired = useMemo(
		() => (e: any) => {
			eventRef.current = e.nativeEvent;
			handleMapUpdate(e);
		},
		[handleMapUpdate]
	);

	const stylesDynamic = useMemo(
		() => ({
			container: { width, height, gap: 16 } as const,
			containerMap: { height, width } as const,
		}),
		[width, height]
	);

	return (
		<View style={stylesDynamic.container}>
			<Controls
				width={width}
				bbox={bbox}
				changeCount={changeCount}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={[13.405, 52.52]}
					zoomLevel={8}
					onMapUpdate={handleMapEventWired}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{bboxRing && (
						<LayerPath
							coordinates={bboxRing}
							paint={rectPaint}
						/>
					)}
				</MapContainer>

				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'viewportBbox',
	label: 'useViewportBbox — tile-snapped spatial query',
	category: 'api',
} as Example;
