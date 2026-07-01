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
	ReindexScope,
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
 * Toggle SharedLayer to see how same-type layers collapse into shared
 * native fragments. Toggle ReindexScope to test the scope-based reindex
 * component — each toggle forces a re-render, and the debug overlay shows
 * the resulting fragment layout.
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
	tilt: 2,
	roll: 2,
	center: 2,
};

// ── Item definitions ─────────────────────────────────────────────────────

interface LayerItem {
	id: string;
	label: string;
	shape: ShapeDefinition | undefined;
	shapeStyle: ShapeStyle | undefined;
	pathCoords: Position[] | undefined;
	pathStyle: GeometryStyle | undefined;
	markerPos: Position | undefined;
	markerSym: SymbolParams | undefined;
}

const items: LayerItem[] = [
	{
		id: '1-shape-red',
		label: '1. Shape (red polygon)',
		shape: {
			type: 'polygon',
			rings: [
				[-77.06, -9.06],
				[-76.94, -9.06],
				[-76.94, -8.94],
				[-77.06, -8.94],
			],
		} as ShapeDefinition,
		shapeStyle: {
			fillColor: '#ff000066',
			strokeColor: '#ff0000',
			strokeWidth: 2,
		} as ShapeStyle,
		pathCoords: undefined,
		pathStyle: undefined,
		markerPos: undefined,
		markerSym: undefined,
	},
	{
		id: '2-path-orange',
		label: '2. Path (orange)',
		shape: undefined,
		shapeStyle: undefined,
		pathCoords: [
			[-77.04, -9.04],
			[-76.96, -9.04],
			[-76.96, -8.96],
			[-77.04, -8.96],
			[-77.04, -9.04],
		] as Position[],
		pathStyle: {
			strokeColor: '#ff8800',
			strokeWidth: 8,
		} as GeometryStyle,
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
			text: '⬤',
			textSize: 28,
			fillColor: '#ffcc00',
		} as SymbolParams,
	},
	{
		id: '4-shape-green',
		label: '4. Shape (green circle)',
		shape: {
			type: 'circle',
			center: [-77, -9],
			radiusKm: 1.2,
		} as ShapeDefinition,
		shapeStyle: {
			fillColor: '#00ff0066',
			strokeColor: '#00ff00',
			strokeWidth: 2,
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
			[-77.02, -9.02],
			[-76.98, -9.02],
			[-76.98, -8.98],
			[-77.02, -8.98],
			[-77.02, -9.02],
		] as Position[],
		pathStyle: {
			strokeColor: '#0088ff',
			strokeWidth: 6,
		} as GeometryStyle,
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
	containerHeight: number;
	useSharedLayer: boolean;
	useReindexScope: boolean;
	reorderCount: number;
	onToggleSharedLayer: () => void;
	onToggleReindexScope: () => void;
}> = ({
	width,
	containerHeight,
	useSharedLayer,
	useReindexScope,
	reorderCount,
	onToggleSharedLayer,
	onToggleReindexScope,
}) => {
	const nativeLayerCount = useSharedLayer
		? '3 fragments (1 Shape + 1 Path + 1 Marker)'
		: '3 fragments (interleaved, 2 Shape + 2 Path + 2 Marker)';

	// Leave room for the debug overlay so the drawer doesn't cover it.
	const drawerMaxHeight = containerHeight - LAYER_DEBUG_OVERLAY_HEIGHT - 12;

	return (
		<ControlPanel
			width={width}
			maxHeight={drawerMaxHeight}
		>
			<ControlSection title="SharedLayer">
				<ControlRow>
					<Text style={sharedStyles.text}>SharedLayer</Text>
					<Switch
						value={useSharedLayer}
						onValueChange={onToggleSharedLayer}
					/>
				</ControlRow>
			</ControlSection>
			<ControlSection title="ReindexScope">
				<ControlRow>
					<Text style={sharedStyles.text}>ReindexScope</Text>
					<Switch
						value={useReindexScope}
						onValueChange={onToggleReindexScope}
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
					{'\n\n'}• With{' '}
					<Text style={{ fontWeight: 'bold' }}>ReindexScope</Text>:
					the debug overlay shows scope-tagged layer blocks.
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
	const [useReindexScope, setUseReindexScope] = useState(true);
	// TODO: track reorderCount via a map-level event or the debug hook.
	// Currently hardcoded — the native reorderLayers call is fire-and-forget
	// from JS, so there is no built-in acknowledgment to count.
	const reorderCount = 0;

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

	const children = useReindexScope ? (
		<ReindexScope>
			{useSharedLayer ? (
				<SharedLayer>{renderItems}</SharedLayer>
			) : (
				renderItems
			)}
		</ReindexScope>
	) : useSharedLayer ? (
		<SharedLayer>{renderItems}</SharedLayer>
	) : (
		renderItems
	);

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				width={width}
				containerHeight={height}
				useSharedLayer={useSharedLayer}
				useReindexScope={useReindexScope}
				reorderCount={reorderCount}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
				onToggleReindexScope={() => setUseReindexScope((v) => !v)}
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
					{__DEV__ && <LayerDebugOverlay />}
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
