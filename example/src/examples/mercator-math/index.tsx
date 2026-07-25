/**
 * External dependencies
 */
import { useMemo, useRef, useState, type FC } from 'react';
import { View, Text } from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	LayerPath,
	MapContainer,
	Marker,
	geoToScreenPosition,
	screenToGeoPosition,
	computeViewportBbox,
	lngLatToTile,
	snapBboxToTiles,
	type GeometryStyle,
	type MapEventResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

const center: Position = [13.405, 52.52]; // Berlin
const testPoint = { lat: 52.53, lng: 13.42 }; // ~1.4 km north-east of center
const testPointCoord = [testPoint.lng, testPoint.lat];

const crosshairStyle: GeometryStyle = {
	strokeColor: '#00ff33',
	strokeWidth: 2,
};

const markerSymbol = {
	text: '●',
	fillColor: '#ff3300',
	textColor: '#ffffff',
	textSize: 10,
} as const;

const Controls: FC<{
	width: number;
	screenPos: string;
	geoPos: string;
	bboxStr: string;
	snappedBboxStr: string;
	tileStr: string;
}> = ({ width, screenPos, geoPos, bboxStr, snappedBboxStr, tileStr }) => (
	<ControlPanel width={width}>
		<ControlSection title="Screen ↔ Geo (toScreenPosition)">
			<StatusLine
				label="Input geo"
				value={`${testPoint.lat.toFixed(4)}°, ${testPoint.lng.toFixed(4)}°`}
			/>
			<StatusLine
				label="Screen px"
				value={screenPos}
			/>
		</ControlSection>
		<ControlSection title="Geo ↔ Screen (fromScreenPosition)">
			<StatusLine
				label="Round-trip geo"
				value={geoPos}
			/>
		</ControlSection>
		<ControlSection title="Viewport bbox (computeViewportBbox)">
			<StatusLine
				label="Raw"
				value={bboxStr}
			/>
			<StatusLine
				label="Snapped (z=8)"
				value={snappedBboxStr}
			/>
		</ControlSection>
		<ControlSection title="Tile math (lngLatToTile)">
			<StatusLine
				label="Tile @ z=14"
				value={tileStr}
			/>
		</ControlSection>
		<ControlSection>
			<Text style={sharedStyles.text}>
				A small crosshair marks the screen position of{' '}
				{testPoint.lat.toFixed(4)}°, {testPoint.lng.toFixed(4)}°
				computed via toScreenPosition(). The viewport bbox is projected
				from the four screen corners. Snapping at tile-zoom 8 groups
				small pans into ~150 km tiles.
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

	const [screenPos, setScreenPos] = useState('-');
	const [geoPos, setGeoPos] = useState('-');
	const [bboxStr, setBboxStr] = useState('-');
	const [snappedBboxStr, setSnappedBboxStr] = useState('-');
	const [tileStr, setTileStr] = useState('-');

	const onMapUpdate = useMemo(
		() => (e: any) => {
			const ev = e.nativeEvent;
			eventRef.current = ev;
			handleMapUpdate(e);

			if (
				!ev.center ||
				ev.center.length < 2 ||
				!ev.zoomLevel ||
				!ev.viewportWidth ||
				!ev.viewportHeight
			)
				return;

			const c = ev.center as [number, number];

			// Screen position of the test point
			const sp = geoToScreenPosition(
				c,
				ev.zoomLevel,
				ev.viewportWidth,
				ev.viewportHeight,
				ev.bearing ?? 0,
				ev.tilt ?? 0,
				testPoint
			);
			setScreenPos(sp ? `${sp.x}, ${sp.y}` : '(invalid)');

			// Compute crosshair lines at the test point's screen position
			if (sp) {
				const hs = screenToGeoPosition(
					c,
					ev.zoomLevel,
					ev.viewportWidth,
					ev.viewportHeight,
					ev.bearing ?? 0,
					ev.tilt ?? 0,
					{ x: sp.x - 15, y: sp.y }
				);
				const he = screenToGeoPosition(
					c,
					ev.zoomLevel,
					ev.viewportWidth,
					ev.viewportHeight,
					ev.bearing ?? 0,
					ev.tilt ?? 0,
					{ x: sp.x + 15, y: sp.y }
				);
				const vs = screenToGeoPosition(
					c,
					ev.zoomLevel,
					ev.viewportWidth,
					ev.viewportHeight,
					ev.bearing ?? 0,
					ev.tilt ?? 0,
					{ x: sp.x, y: sp.y - 15 }
				);
				const ve = screenToGeoPosition(
					c,
					ev.zoomLevel,
					ev.viewportWidth,
					ev.viewportHeight,
					ev.bearing ?? 0,
					ev.tilt ?? 0,
					{ x: sp.x, y: sp.y + 15 }
				);
				if (hs && he && vs && ve) {
					setCrosshairCoords([
						[hs.lng, hs.lat],
						[he.lng, he.lat],
						[vs.lng, vs.lat],
						[ve.lng, ve.lat],
					]);
				} else {
					setCrosshairCoords(null);
				}
			} else {
				setCrosshairCoords(null);
			}

			// Round-trip: screen back to geo
			const centerScreen = geoToScreenPosition(
				c,
				ev.zoomLevel,
				ev.viewportWidth,
				ev.viewportHeight,
				ev.bearing ?? 0,
				ev.tilt ?? 0,
				{ lat: c[1], lng: c[0] }
			);
			if (centerScreen) {
				const gp = screenToGeoPosition(
					c,
					ev.zoomLevel,
					ev.viewportWidth,
					ev.viewportHeight,
					ev.bearing ?? 0,
					ev.tilt ?? 0,
					centerScreen
				);
				setGeoPos(
					gp
						? `${gp.lat.toFixed(4)}°, ${gp.lng.toFixed(4)}°`
						: '(invalid)'
				);
			}

			// Viewport bbox
			const bbox = computeViewportBbox(
				c,
				ev.zoomLevel,
				ev.viewportWidth,
				ev.viewportHeight,
				ev.bearing ?? 0,
				ev.tilt ?? 0
			);
			if (bbox) {
				setBboxStr(bbox.map((v) => v.toFixed(4)).join(', '));
				const snapped = snapBboxToTiles(bbox, 8);
				setSnappedBboxStr(snapped.map((v) => v.toFixed(4)).join(', '));
			}

			// Tile at map zoom
			const tile = lngLatToTile(c[0], c[1], Math.round(ev.zoomLevel));
			const tx = Math.floor(tile.x);
			const ty = Math.floor(tile.y);
			setTileStr(`x=${tx}, y=${ty} @ z=${Math.round(ev.zoomLevel)}`);
		},
		[handleMapUpdate]
	);

	const [crosshairCoords, setCrosshairCoords] = useState<Position[] | null>(
		null
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
				screenPos={screenPos}
				geoPos={geoPos}
				bboxStr={bboxStr}
				snappedBboxStr={snappedBboxStr}
				tileStr={tileStr}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={center}
					zoomLevel={14}
					onMapUpdate={onMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					<LayerMarker symbol={markerSymbol}>
						<Marker position={testPointCoord} />
					</LayerMarker>

					{crosshairCoords && (
						<LayerPath
							coordinates={crosshairCoords}
							style={crosshairStyle}
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
	key: 'mercatorMath',
	label: 'Mercator Math — screen↔geo, viewport bbox',
	category: 'api',
} as Example;
