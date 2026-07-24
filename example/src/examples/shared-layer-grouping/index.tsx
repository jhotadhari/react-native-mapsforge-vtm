/**
 * External dependencies
 */
import { Fragment, useMemo, useState, type FC } from 'react';
import { View, Text, Switch } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	Marker,
	SharedLayer,
	type GeometryStyle,
	type Position,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

// ---------------------------------------------------------------------------
// Path geometry — three overlapping lines centered on the same area so their
// z-order is visually obvious. Each path is a 2-point line crossing the center.
// ---------------------------------------------------------------------------

interface ColoredPair {
	id: string;
	color: `#${string}`;
	strokeColor: `#${string}`;
	pathCoordinates: Position[];
	markerPosition: Position;
}

const pairs: ColoredPair[] = [
	{
		id: 'red',
		color: '#ff0000',
		strokeColor: '#ff0000',
		pathCoordinates: [
			[-77.3, -9],
			[-76.7, -9],
		],
		markerPosition: [-77, -9],
	},
	{
		id: 'green',
		color: '#00cc00',
		strokeColor: '#00cc00',
		pathCoordinates: [
			[-77, -9.3],
			[-77, -8.7],
		],
		markerPosition: [-77, -8.85],
	},
	{
		id: 'blue',
		color: '#0066ff',
		strokeColor: '#0066ff',
		pathCoordinates: [
			[-77.2, -9.2],
			[-76.8, -8.8],
		],
		markerPosition: [-77.05, -8.95],
	},
];

const pathStyle: GeometryStyle = {
	strokeWidth: 14,
};

const markerSymbol: SymbolParams = {
	text: '●',
	textSize: 24,
};

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

const Controls: FC<{
	width: number;
	useSharedLayer: boolean;
	swapOrder: boolean;
	onToggleSharedLayer: () => void;
	onToggleSwapOrder: () => void;
}> = ({
	width,
	useSharedLayer,
	swapOrder,
	onToggleSharedLayer,
	onToggleSwapOrder,
}) => {
	const nativeLayerCount = useSharedLayer
		? '2 (1 Path + 1 Marker fragment)'
		: `6 (${pairs.length} × Path + ${pairs.length} × Marker, interleaved)`;

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
			</ControlSection>

			<ControlSection title="Render order">
				<ControlRow>
					<Text style={sharedStyles.text}>
						Swap Path ↔ Marker within each pair
					</Text>
					<Switch
						value={swapOrder}
						onValueChange={onToggleSwapOrder}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection title="What to look for">
				<Text style={sharedStyles.text}>
					• <Text style={sharedStyles.boldText}>SharedLayer ON</Text>:
					all 3 paths share one native layer, all 3 markers share
					another → every marker paints on top of every path. Path
					colors never cover marker dots.
					{'\n\n'}•{' '}
					<Text style={sharedStyles.boldText}>SharedLayer OFF</Text>:
					each (Path, Marker) pair gets its own fragment pair,
					interleaved in React-tree order → green path covers red
					marker, blue path covers green marker.
					{'\n\n'}• Toggle{' '}
					<Text style={sharedStyles.boldText}>Swap order</Text> to
					reverse the Path/Marker stacking within each pair.
				</Text>
			</ControlSection>
		</ControlPanel>
	);
};

// ---------------------------------------------------------------------------
// Example component
// ---------------------------------------------------------------------------

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [useSharedLayer, setUseSharedLayer] = useState(true);
	const [swapOrder, setSwapOrder] = useState(false);

	const renderPairs = useMemo(() => {
		return pairs.map((pair) => {
			const pathColor = { ...pathStyle, strokeColor: pair.strokeColor };
			const symbolColor = {
				...markerSymbol,
				fillColor: pair.color,
			};

			const pathElement = (
				<LayerPath
					key={`${pair.id}-path`}
					coordinates={pair.pathCoordinates}
					style={pathColor}
				/>
			);
			const markerElement = (
				<Marker
					key={`${pair.id}-marker`}
					position={pair.markerPosition}
					symbol={symbolColor}
				/>
			);

			return (
				<Fragment key={pair.id}>
					{swapOrder ? markerElement : pathElement}
					{swapOrder ? pathElement : markerElement}
				</Fragment>
			);
		});
	}, [swapOrder]);

	const children = useSharedLayer ? (
		<SharedLayer>{renderPairs}</SharedLayer>
	) : (
		renderPairs
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
				useSharedLayer={useSharedLayer}
				swapOrder={swapOrder}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
				onToggleSwapOrder={() => setSwapOrder((v) => !v)}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={8}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
					{children}
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
	key: 'sharedLayerGrouping',
	label: 'Shared Layer Grouping',
	category: 'gestures',
} as Example;
