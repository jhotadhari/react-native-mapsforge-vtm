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
	type PathPaint,
	type Position,
	type ShapeDefinition,
	type ShapePaint,
	type MarkerPaint,
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
 * native fragments — with SharedLayer ON, strict interleaving is lost:
 * all markers paint on top of all paths, which paint on top of all shapes.
 * Relative order within each fragment is preserved. Toggle ReindexScope
 * to test the scope-based reindex component.
 * The 6 items, bottom → top (expected order with SharedLayer OFF):
 *   2. LayerPath   (orange line)
 *   3. Marker      (yellow dot)
 *   4. LayerShape  (green circle)      — covers yellow marker if order wrong
 *   5. LayerPath   (blue line)
 *   6. Marker      (purple dot)        — smallest, should be on top
 */

// ── Geometry — centered on a single point so overlap is guaranteed ──────

const center: Position = [-77, -9]; // [ lng, lat ]
const defaultCenter: Position = center;

// ── Item definitions ─────────────────────────────────────────────────────

interface LayerItem {
	id: string;
	label: string;
	shape: ShapeDefinition | undefined;
	shapePaint: ShapePaint | undefined;
	pathCoords: Position[] | undefined;
	pathPaint: PathPaint | undefined;
	markerPos: Position | undefined;
	markerPaint: MarkerPaint | undefined;
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
		shapePaint: {
			fillColor: '#ff000066',
			strokeColor: '#ff0000',
			strokeWidth: 2,
		} as ShapePaint,
		pathCoords: undefined,
		pathPaint: undefined,
		markerPos: undefined,
		markerPaint: undefined,
	},
	{
		id: '2-path-orange',
		label: '2. Path (orange)',
		shape: undefined,
		shapePaint: undefined,
		pathCoords: [
			[-77.04, -9.04],
			[-76.96, -9.04],
			[-76.96, -8.96],
			[-77.04, -8.96],
			[-77.04, -9.04],
		] as Position[],
		pathPaint: {
			strokeColor: '#ff8800',
			strokeWidth: 8,
		} as PathPaint,
		markerPos: undefined,
		markerPaint: undefined,
	},
	{
		id: '3-marker-yellow',
		label: '3. Marker (yellow)',
		shape: undefined,
		shapePaint: undefined,
		pathCoords: undefined,
		pathPaint: undefined,
		markerPos: [-77, -9] as Position,
		markerPaint: {
			text: '⬤',
			textSize: 28,
			fillColor: '#ffcc00',
		} as MarkerPaint,
	},
	{
		id: '4-shape-green',
		label: '4. Shape (green circle)',
		shape: {
			type: 'circle',
			center: [-77, -9],
			radiusKm: 1.2,
		} as ShapeDefinition,
		shapePaint: {
			fillColor: '#00ff0066',
			strokeColor: '#00ff00',
			strokeWidth: 2,
		} as ShapePaint,
		pathCoords: undefined,
		pathPaint: undefined,
		markerPos: undefined,
		markerPaint: undefined,
	},
	{
		id: '5-path-blue',
		label: '5. Path (blue)',
		shape: undefined,
		shapePaint: undefined,
		pathCoords: [
			[-77.02, -9.02],
			[-76.98, -9.02],
			[-76.98, -8.98],
			[-77.02, -8.98],
			[-77.02, -9.02],
		] as Position[],
		pathPaint: {
			strokeColor: '#0088ff',
			strokeWidth: 6,
		} as PathPaint,
		markerPos: undefined,
		markerPaint: undefined,
	},
	{
		id: '6-marker-purple',
		label: '6. Marker (purple)',
		shape: undefined,
		shapePaint: undefined,
		pathCoords: undefined,
		pathPaint: undefined,
		markerPos: [-77, -9] as Position,
		markerPaint: {
			text: '●',
			textSize: 18,
			fillColor: '#cc00ff',
		} as MarkerPaint,
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
					<Text style={sharedStyles.boldText}>
						declared bottom→top
					</Text>{' '}
					in JSX (1 first, 6 last).{'\n\n'}• If item 2 (orange path)
					paints over item 4 (green circle), order is broken — 4 comes
					after 2 in JSX.{'\n\n'}• Correct: 1 ⊂ 2 ⊂ 3 ⊂ 4 ⊂ 5 ⊂ 6
					(each layer covers all lower-numbered ones).{'\n\n'}• With{' '}
					<Text style={sharedStyles.boldText}>SharedLayer</Text>: all
					shapes share 1 native layer, all paths share 1, all markers
					share 1. Same-type layers collapse into one fragment —
					markers paint on top of paths, which paint on top of shapes.
					Strict interleaving (1 ⊂ 2 ⊂ 3 ⊂ 4 ⊂ 5 ⊂ 6) is lost; within
					each fragment, relative JSX order is preserved.
					{'\n\n'}• With{' '}
					<Text style={sharedStyles.boldText}>ReindexScope</Text>: the
					debug overlay shows scope-tagged layer blocks.
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
						paint={item.shapePaint}
					/>
				);
			}
			if (item.pathCoords) {
				return (
					<LayerPath
						key={item.id}
						coordinates={item.pathCoords}
						paint={item.pathPaint}
					/>
				);
			}
			if (item.markerPos) {
				return (
					<Marker
						key={item.id}
						position={item.markerPos}
						paint={item.markerPaint}
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

	const stylesDynamic = useMemo(
		() =>
			({
				container: { width, height, gap: 16 } as const,
				containerMap: { height, width } as const,
			}) as const,
		[width, height]
	);

	return (
		<View style={stylesDynamic.container}>
			<Controls
				width={width}
				containerHeight={height}
				useSharedLayer={useSharedLayer}
				useReindexScope={useReindexScope}
				reorderCount={reorderCount}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
				onToggleReindexScope={() => setUseReindexScope((v) => !v)}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
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
