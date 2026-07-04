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
import LayerDebugOverlay, {
	LAYER_DEBUG_OVERLAY_HEIGHT,
} from '../../components/LayerDebugOverlay';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
} from '../../components/ControlPanel';

/**
 * Mixed Grouping
 *
 * Demonstrates a single MapContainer where SOME children sit inside a
 * {@link SharedLayer} wrapper and OTHERS sit outside it — all in one tree.
 *
 * The map shows overlapping items centered on one point so their draw order
 * is visible, but the real demonstration is in the **debug overlay**:
 *
 * - **SharedLayer ON**: the 4 inner items (Marker → Path → Marker → Path)
 *   collapse into 2 native fragments. The debug tree shows 2 collapsible
 *   fragment-group headers interleaved with individual rows for the 3
 *   dedicated layers.
 *
 * - **SharedLayer OFF**: those same 4 items each get their own native
 *   fragment (because they alternate types). The debug tree shows 7
 *   individual rows with no fragment groups.
 *
 * The toggle changes the **structure** of the debug tree — not just a
 * native-layer count.
 *
 * The 7 items, in JSX order:
 *   1. LayerPath   (dedicated, blue)          — outside SharedLayer
 *   2. Marker      (dedicated, red dot)       — outside SharedLayer
 *   3. Marker      (shared, green dot)        ┐
 *   4. LayerPath   (shared, orange line)      │ inside SharedLayer
 *   5. Marker      (shared, yellow dot)       │ → 2 fragment groups
 *   6. LayerPath   (shared, pink line)        ┘
 *   7. LayerShape  (dedicated, white circle)  — outside SharedLayer
 */

// ── Geometry — all centered on one point for guaranteed overlap ─────────

const center: Position = [-77, -9];
const defaultCenter: Position = center;

// ── Controls ────────────────────────────────────────────────────────────

const Controls: FC<{
	width: number;
	containerHeight: number;
	useSharedLayer: boolean;
	onToggleSharedLayer: () => void;
}> = ({ width, containerHeight, useSharedLayer, onToggleSharedLayer }) => {
	const drawerMaxHeight = containerHeight - LAYER_DEBUG_OVERLAY_HEIGHT - 12;

	return (
		<ControlPanel
			width={width}
			maxHeight={drawerMaxHeight}
		>
			<ControlSection title="SharedLayer wrapper">
				<ControlRow>
					<Text style={sharedStyles.text}>
						Wrap inner items in SharedLayer
					</Text>
					<Switch
						value={useSharedLayer}
						onValueChange={onToggleSharedLayer}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection title="What to look for in the debug tree">
				<Text style={sharedStyles.text}>
					• <Text style={{ fontWeight: 'bold' }}>SharedLayer ON</Text>
					: the 4 inner items collapse into{' '}
					<Text style={{ fontWeight: 'bold', color: '#4ec94e' }}>
						2 fragment groups
					</Text>{' '}
					(marker + path). The debug tree shows collapsible group
					headers interleaved with individual rows.{'\n\n'}•{' '}
					<Text style={{ fontWeight: 'bold' }}>SharedLayer OFF</Text>:
					the debug tree shows{' '}
					<Text style={{ fontWeight: 'bold', color: '#e09040' }}>
						7 individual rows
					</Text>{' '}
					— no fragment groups at all.{'\n\n'}• The{' '}
					<Text style={{ fontWeight: 'bold' }}>Grouped</Text> header
					tells you whether a SharedLayer wrapper is present (not
					whether any fragment happens to have {'>'}1 member).{'\n\n'}
					•{' '}
					<Text style={{ fontWeight: 'bold' }}>Why SharedLayer?</Text>{' '}
					Each native layer = 1 GPU draw call. Without SharedLayer, N
					same-type items produce N draw calls. With it, they collapse
					into{' '}
					<Text style={{ fontWeight: 'bold' }}>
						1 draw call per type
					</Text>
					{' — '}at 100s of items the performance difference is
					dramatic. The{' '}
					<Text style={{ fontWeight: 'bold' }}>many-layers</Text>{' '}
					example demonstrates this at scale.
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

	// ── Dedicated layers (always outside SharedLayer) ────────────────────

	const dedicatedPathCoords: Position[] = useMemo(
		() => [
			[-77.5, -9.5],
			[-76.5, -8.5],
		],
		[]
	);

	const dedicatedMarkerPos: Position = useMemo(() => [-77, -9], []);
	const dedicatedMarkerSymbol: SymbolParams = useMemo(
		() => ({ text: '●', textSize: 32, fillColor: '#ff3333' }),
		[]
	);

	const dedicatedShape: ShapeDefinition = useMemo(
		() => ({
			type: 'circle' as const,
			center: [-77, -9],
			radiusKm: 90,
		}),
		[]
	);
	const dedicatedShapeStyle: ShapeStyle = useMemo(
		() => ({
			strokeWidth: 1.5,
			strokeColor: '#ffffff',
			fillColor: '#ffffff',
			fillAlpha: 0.08,
		}),
		[]
	);

	// ── SharedLayer children (Marker → Path → Marker → Path) ─────────────

	const sharedMarker1Pos: Position = useMemo(() => [-77, -9], []);
	const sharedMarker1Symbol: SymbolParams = useMemo(
		() => ({ text: '●', textSize: 22, fillColor: '#00ff44' }),
		[]
	);

	const sharedPath1Coords: Position[] = useMemo(
		() => [
			[-77.4, -9.4],
			[-76.6, -8.6],
		],
		[]
	);
	const sharedPath1Style: GeometryStyle = useMemo(
		() => ({ strokeWidth: 12, strokeColor: '#ff8800' }),
		[]
	);

	const sharedMarker2Pos: Position = useMemo(() => [-77, -9], []);
	const sharedMarker2Symbol: SymbolParams = useMemo(
		() => ({ text: '●', textSize: 14, fillColor: '#ffdd00' }),
		[]
	);

	const sharedPath2Coords: Position[] = useMemo(
		() => [
			[-77.3, -9.3],
			[-76.7, -8.7],
		],
		[]
	);
	const sharedPath2Style: GeometryStyle = useMemo(
		() => ({ strokeWidth: 6, strokeColor: '#ff44ff' }),
		[]
	);

	// ── Inner items (the 4 that toggle between grouped / flat) ───────────

	const innerItems = (
		<>
			{/* 3. Shared marker 1 (green) */}
			<Marker
				position={sharedMarker1Pos}
				symbol={sharedMarker1Symbol}
			/>
			{/* 4. Shared path 1 (orange) */}
			<LayerPath
				coordinates={sharedPath1Coords}
				style={sharedPath1Style}
			/>
			{/* 5. Shared marker 2 (yellow) */}
			<Marker
				position={sharedMarker2Pos}
				symbol={sharedMarker2Symbol}
			/>
			{/* 6. Shared path 2 (pink) */}
			<LayerPath
				coordinates={sharedPath2Coords}
				style={sharedPath2Style}
			/>
		</>
	);

	// ── Render ───────────────────────────────────────────────────────────

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				width={width}
				containerHeight={height}
				useSharedLayer={useSharedLayer}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
			/>

			<View style={{ height, width }}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={9}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{/* 1. Dedicated path (blue) — outside SharedLayer */}
					<LayerPath
						coordinates={dedicatedPathCoords}
						style={{ strokeWidth: 20, strokeColor: '#4488ff' }}
					/>

					{/* 2. Dedicated marker (red) — outside SharedLayer */}
					<Marker
						position={dedicatedMarkerPos}
						symbol={dedicatedMarkerSymbol}
					/>

					{/* 3-6. Inner items — inside SharedLayer (or flat) */}
					{useSharedLayer ? (
						<SharedLayer>{innerItems}</SharedLayer>
					) : (
						innerItems
					)}

					{/* 7. Dedicated shape (white circle) — outside SharedLayer */}
					<LayerShape
						shape={dedicatedShape}
						style={dedicatedShapeStyle}
					/>

					{__DEV__ && <LayerDebugOverlay />}
				</MapContainer>

				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'mixedGrouping',
	label: 'Mixed Grouping',
	category: 'gestures',
} as Example;
