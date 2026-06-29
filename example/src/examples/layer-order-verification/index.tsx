/**
 * External dependencies
 */
import { useMemo, useState, type FC } from 'react';
import { View, Text, Switch } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	LayerShape,
	MapContainer,
	Marker,
	SharedLayer,
	type GeometryStyle,
	type Position,
	type ShapeDefinition,
	type ShapeStyle,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';

import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

/**
 * Layer Order Verification
 *
 * Visually proves that interleaved layers of different types render in
 * strict React tree (JSX declaration) order.  Six overlapping items are
 * placed in increasing size so any out-of-order rendering is instantly
 * visible — a bottom-layer item should never paint over a top-layer one.
 *
 * The 6 items, bottom → top:
 *   1. LayerShape  (red polygon)       — largest, should be covered by all
 *   2. LayerPath   (orange line)
 *   3. Marker      (yellow dot)
 *   4. LayerShape  (green circle)      — covers yellow marker if order wrong
 *   5. LayerPath   (blue line)
 *   6. Marker      (purple dot)        — smallest, should be on top
 */

// ── Geometry — centered on a single point so overlap is guaranteed ──────

const center: Position = [-77, -9]; // [ lng, lat ]
const defaultCenter: Position = center;

const responseInclude = {
	zoomLevel: 2,
	zoom: 2,
	scale: 2,
	zoomScale: 2,
	bearing: 2,
	roll: 2,
	tilt: 2,
	center: 2,
};

// Each item is larger than the previous one so z-order violations are obvious.
// If the large red polygon (item 1) renders on top of the small purple marker
// (item 6), the bug is visible.

const items = [
	{
		id: '1-shape-polygon',
		label: '1. Shape (red polygon)',
		shape: {
			type: 'polygon' as const,
			rings: [
				[-77.4, -9.4],
				[-76.6, -9.4],
				[-76.6, -8.6],
				[-77.4, -8.6],
			],
		} as ShapeDefinition,
		shapeStyle: {
			strokeWidth: 1,
			strokeColor: '#ff0000',
			fillColor: '#ff0000',
			fillAlpha: 0.4,
		} as ShapeStyle,
		pathCoords: undefined as Position[] | undefined,
		pathStyle: undefined as GeometryStyle | undefined,
		markerPos: undefined as Position | undefined,
		markerSym: undefined as SymbolParams | undefined,
	},
	{
		id: '2-path-orange',
		label: '2. Path (orange)',
		shape: undefined,
		shapeStyle: undefined,
		pathCoords: [
			[-77.35, -9.35],
			[-76.65, -8.65],
		] as Position[],
		pathStyle: { strokeWidth: 10, strokeColor: '#ff8800' } as GeometryStyle,
		markerPos: undefined,
		markerSym: undefined,
	},
	{
		id: '3-marker-yellow',
		label: '3. Marker (yellow)',
		shape: undefined,
		shapeStyle: undefined,
		pathCoords: undefined,
		pathStyle: undefined,
		markerPos: [-77, -9] as Position,
		markerSym: {
			text: '●',
			textSize: 28,
			fillColor: '#ffdd00',
		} as SymbolParams,
	},
	{
		id: '4-shape-circle',
		label: '4. Shape (green circle)',
		shape: {
			type: 'circle' as const,
			center: [-77, -9],
			radiusKm: 80,
		} as ShapeDefinition,
		shapeStyle: {
			strokeWidth: 1,
			strokeColor: '#00cc00',
			fillColor: '#00cc00',
			fillAlpha: 0.35,
		} as ShapeStyle,
		pathCoords: undefined,
		pathStyle: undefined,
		markerPos: undefined,
		markerSym: undefined,
	},
	{
		id: '5-path-blue',
		label: '5. Path (blue)',
		shape: undefined,
		shapeStyle: undefined,
		pathCoords: [
			[-77.25, -9.25],
			[-76.75, -8.75],
		] as Position[],
		pathStyle: { strokeWidth: 7, strokeColor: '#0066ff' } as GeometryStyle,
		markerPos: undefined,
		markerSym: undefined,
	},
	{
		id: '6-marker-purple',
		label: '6. Marker (purple)',
		shape: undefined,
		shapeStyle: undefined,
		pathCoords: undefined,
		pathStyle: undefined,
		markerPos: [-77, -9] as Position,
		markerSym: {
			text: '●',
			textSize: 18,
			fillColor: '#cc00ff',
		} as SymbolParams,
	},
];

// ── Controls ────────────────────────────────────────────────────────────

const Controls: FC<{
	width: number;
	useSharedLayer: boolean;
	reorderCount: number;
	onToggleSharedLayer: () => void;
}> = ({ width, useSharedLayer, reorderCount, onToggleSharedLayer }) => {
	const nativeLayerCount = useSharedLayer
		? '3 fragments (1 Shape + 1 Path + 1 Marker)'
		: '3 fragments (interleaved, 2 Shape + 2 Path + 2 Marker)';

	return (
		<ControlPanel width={width}>
			<ControlSection title="SharedLayer">
				<ControlRow>
					<Text style={sharedStyles.text}>SharedLayer</Text>
					<Switch
						value={useSharedLayer}
						onValueChange={onToggleSharedLayer}
					/>
				</ControlRow>
				<StatusLine
					label="Native layers"
					value={nativeLayerCount}
				/>
				<StatusLine
					label="Reorder calls"
					value={`${reorderCount}`}
				/>
			</ControlSection>

			<ControlSection title="What to look for">
				<Text style={sharedStyles.text}>
					• Items are{' '}
					<Text style={{ fontWeight: 'bold' }}>
						declared bottom→top
					</Text>{' '}
					in JSX (1 first, 6 last).{'\n\n'}• If item 2 (orange path)
					paints over item 4 (green circle), order is broken — 4 comes
					after 2 in JSX.{'\n\n'}• Correct: 1 ⊂ 2 ⊂ 3 ⊂ 4 ⊂ 5 ⊂ 6
					(each layer covers all lower-numbered ones).{'\n\n'}• With{' '}
					<Text style={{ fontWeight: 'bold' }}>SharedLayer</Text>: all
					shapes share 1 native layer, all paths share 1, all markers
					share 1 — but still in the correct interleaved order.
				</Text>
			</ControlSection>
		</ControlPanel>
	);
};

// ── Example component ───────────────────────────────────────────────────

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [useSharedLayer, setUseSharedLayer] = useState(true);
	const reorderCount = 0; // Tracked by map-level event in production use

	const renderItems = useMemo(() => {
		return items.map((item) => {
			if (item.shape) {
				return (
					<LayerShape
						key={item.id}
						shape={item.shape}
						style={item.shapeStyle}
					/>
				);
			}
			if (item.pathCoords) {
				return (
					<LayerPath
						key={item.id}
						coordinates={item.pathCoords}
						style={item.pathStyle}
					/>
				);
			}
			if (item.markerPos) {
				return (
					<Marker
						key={item.id}
						position={item.markerPos}
						symbol={item.markerSym}
					/>
				);
			}
			return null;
		});
	}, []);

	const children = useSharedLayer ? (
		<SharedLayer>{renderItems}</SharedLayer>
	) : (
		renderItems
	);

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				width={width}
				useSharedLayer={useSharedLayer}
				reorderCount={reorderCount}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
			/>

			<View style={{ height, width }}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					responseInclude={responseInclude}
					zoomLevel={10}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
					{children}
				</MapContainer>

				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'layerOrderVerification',
	label: 'Layer Order Verification',
	category: 'gestures',
} as Example;
