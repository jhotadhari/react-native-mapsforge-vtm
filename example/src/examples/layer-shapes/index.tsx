/**
 * External dependencies
 */
import { useCallback, useMemo, useState, type FC } from 'react';
import { View, Button, Text, Switch } from 'react-native';
import { sharedStyles } from '../../sharedDeps';
import {
	LayerBitmapTile,
	LayerPath,
	LayerShape,
	MapContainer,
	Marker,
	type GeometryStyle,
	type LayerShapeGestureResponse,
	type Position,
	type ShapeDefinition,
	type ShapeStyle,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

const defaultCenter: Position = [10, 35]; // [ lng, lat ] — Mediterranean

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

// ── Style presets ─────────────────────────────────────────────────────────

const polygonStyle: ShapeStyle = {
	strokeWidth: 3,
	strokeColor: '#ff4444',
	fillColor: '#ff4444',
	fillAlpha: 0.3,
};

const circleStyle: ShapeStyle = {
	strokeWidth: 2,
	strokeColor: '#4488ff',
	fillColor: '#4488ff',
	fillAlpha: 0.25,
};

const rectangleStyle: ShapeStyle = {
	strokeWidth: 2,
	strokeColor: '#44cc44',
	fillColor: '#44cc44',
	fillAlpha: 0.25,
};

const hexagonStyle: ShapeStyle = {
	strokeWidth: 2,
	strokeColor: '#ffaa00',
	fillColor: '#ffaa00',
	fillAlpha: 0.45,
};

const pointStyle: ShapeStyle = {
	strokeWidth: 5,
	strokeColor: '#cc44cc',
	fillColor: '#cc44cc',
	fillAlpha: 0.9,
};

// ── Shape definitions (static, centered on Mediterranean) ─────────────────

const trianglePolygon: ShapeDefinition = {
	type: 'polygon',
	rings: [
		[8, 38], // west
		[12, 38], // east
		[10, 32], // south
	],
};

const circleShape: ShapeDefinition = {
	type: 'circle',
	center: [5, 38],
	radiusKm: 200,
};

const rectangleShape: ShapeDefinition = {
	type: 'rectangle',
	min: [12, 32], // SW corner
	max: [16, 38], // NE corner
};

const hexagonShape: ShapeDefinition = {
	type: 'hexagon',
	center: [8, 40],
	radiusKm: 150,
};

const pointShape: ShapeDefinition = {
	type: 'point',
	position: [10, 35],
};

// All shapes keyed by display name for the toggle UI.
const allShapes: Record<
	string,
	{ definition: ShapeDefinition; style: ShapeStyle }
> = {
	Polygon: { definition: trianglePolygon, style: polygonStyle },
	Circle: { definition: circleShape, style: circleStyle },
	Rectangle: { definition: rectangleShape, style: rectangleStyle },
	Hexagon: { definition: hexagonShape, style: hexagonStyle },
	Point: { definition: pointShape, style: pointStyle },
};

// ── Interleaved overlay geometry ──────────────────────────────────────────

// A LayerPath + Marker pair that overlaps the shape cluster, demonstrating
// that shapes, paths, and markers interleave correctly in z-order.

const interleavedPathCoords: Position[] = [
	[9.5, 39],
	[10.5, 33],
];

const interleavedPathStyle: GeometryStyle = {
	strokeWidth: 6,
	strokeColor: '#ffffff',
};

const interleavedMarkerPos: Position = [10, 36];
const interleavedMarkerSymbol: SymbolParams = {
	text: '✚',
	textSize: 22,
	fillColor: '#ffffff',
};

// ── Controls ──────────────────────────────────────────────────────────────

const Controls: FC<{
	width: number;
	lastGestureInfo: string;
	visibleShapes: Record<string, boolean>;
	showInterleaved: boolean;
	onToggleShape: (name: string) => void;
	onShowAll: () => void;
	onHideAll: () => void;
	onToggleInterleaved: () => void;
}> = ({
	width,
	lastGestureInfo,
	visibleShapes,
	showInterleaved,
	onToggleShape,
	onShowAll,
	onHideAll,
	onToggleInterleaved,
}) => {
	return (
		<ControlPanel width={width}>
			<ControlSection title="Toggle shapes">
				{Object.keys(allShapes).map((name) => (
					<ControlRow key={name}>
						<Button
							title={`${visibleShapes[name] ? '✓ ' : ''}${name}`}
							onPress={() => onToggleShape(name)}
						/>
					</ControlRow>
				))}
			</ControlSection>
			<ControlSection>
				<ControlRow>
					<Button
						title="Show all"
						onPress={onShowAll}
					/>
					<Button
						title="Hide all"
						onPress={onHideAll}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection title="Interleaved overlay">
				<ControlRow>
					<Text style={sharedStyles.text}>
						Show Path + Marker overlay
					</Text>
					<Switch
						value={showInterleaved}
						onValueChange={onToggleInterleaved}
					/>
				</ControlRow>
				<StatusLine
					label="Declared after shapes"
					value={
						showInterleaved
							? 'Path + Marker should render on top'
							: '-'
					}
				/>
			</ControlSection>

			<ControlSection title="Last gesture">
				<StatusLine
					label="Shape event"
					value={lastGestureInfo}
				/>
			</ControlSection>
			<ControlSection title="Gesture event data">
				<Text style={sharedStyles.text}>
					Each onPress/onLongPress callback receives a
					LayerShapeGestureResponse:{'\n'}
					{'\n'}• type — 'press', 'longPress', 'doubleTap', or
					'trigger'{'\n'}• eventPosition — [lng, lat] of the tap on
					the map{'\n'}• distance — coordinate distance from tap to
					the shape edge{'\n'}• uuid — identifies which shape was hit
					{'\n\n'}Gestures are routed through the shared VectorLayer's
					UuidResolver — verifying that per-shape hit-testing works
					correctly in shared-layer mode.
				</Text>
			</ControlSection>
		</ControlPanel>
	);
};

// ── Example component ─────────────────────────────────────────────────────

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [visibleShapes, setVisibleShapes] = useState<Record<string, boolean>>(
		{
			Polygon: true,
			Circle: true,
			Rectangle: true,
			Hexagon: true,
			Point: true,
		}
	);

	const [showInterleaved, setShowInterleaved] = useState(false);
	const [lastGestureInfo, setLastGestureInfo] = useState('-');

	const handleToggleShape = useCallback((name: string) => {
		setVisibleShapes((prev) => ({ ...prev, [name]: !prev[name] }));
	}, []);

	const handleShowAll = useCallback(() => {
		setVisibleShapes({
			Polygon: true,
			Circle: true,
			Rectangle: true,
			Hexagon: true,
			Point: true,
		});
	}, []);

	const handleHideAll = useCallback(() => {
		setVisibleShapes({
			Polygon: false,
			Circle: false,
			Rectangle: false,
			Hexagon: false,
			Point: false,
		});
	}, []);

	const formatGesture = useCallback(
		(name: string, response: LayerShapeGestureResponse) => {
			const pos = response.eventPosition;
			const lat = pos[1]?.toFixed(4);
			const lng = pos[0]?.toFixed(4);
			setLastGestureInfo(`${name}: ${response.type} @ ${lat},${lng}`);
		},
		[]
	);

	// Per-shape gesture handlers keyed by shape name.
	const gestureHandlers = useMemo(() => {
		const handlers: Record<
			string,
			{
				onPress: (r: LayerShapeGestureResponse) => void;
				onLongPress: (r: LayerShapeGestureResponse) => void;
			}
		> = {};
		Object.keys(allShapes).forEach((name) => {
			handlers[name] = {
				onPress: (r: LayerShapeGestureResponse) =>
					formatGesture(name, r),
				onLongPress: (r: LayerShapeGestureResponse) =>
					formatGesture(name, r),
			};
		});
		return handlers;
	}, [formatGesture]);

	return (
		<View
			style={{
				width,
				height,
				gap: 16,
			}}
		>
			<Controls
				width={width}
				lastGestureInfo={lastGestureInfo}
				visibleShapes={visibleShapes}
				showInterleaved={showInterleaved}
				onToggleShape={handleToggleShape}
				onShowAll={handleShowAll}
				onHideAll={handleHideAll}
				onToggleInterleaved={() => setShowInterleaved((v) => !v)}
			/>

			<View
				style={{
					height,
					width,
				}}
			>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					responseInclude={responseInclude}
					zoomLevel={5}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{Object.entries(allShapes).map(
						([name, { definition, style }]) =>
							visibleShapes[name] ? (
								<LayerShape
									key={name}
									shape={definition}
									style={style}
									gestureScreenDistance={40}
									onPress={gestureHandlers[name]?.onPress}
									onLongPress={
										gestureHandlers[name]?.onLongPress
									}
								/>
							) : null
					)}

					{/* Interleaved overlay: LayerPath + Marker declared
					     AFTER all shapes — they must render on top. */}
					{showInterleaved && (
						<LayerPath
							key="interleaved-path"
							coordinates={interleavedPathCoords}
							style={interleavedPathStyle}
						/>
					)}
					{showInterleaved && (
						<Marker
							key="interleaved-marker"
							position={interleavedMarkerPos}
							symbol={interleavedMarkerSymbol}
						/>
					)}
				</MapContainer>

				<Center
					height={height}
					width={width}
				/>
				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'layerShapes',
	label: 'Shapes (polygon, circle, rect, hex, point)',
	category: 'layers',
} as Example;
