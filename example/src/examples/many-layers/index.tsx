import { Fragment, useMemo, useState, type FC } from 'react';
import { View, Text, Button, Switch } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	Marker,
	SharedLayer,
	type Position,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import { randomNumber } from '../../utils';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import LayerDebugOverlay, {
	LAYER_DEBUG_OVERLAY_HEIGHT,
} from '../../components/LayerDebugOverlay';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
} from '../../components/ControlPanel';

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

const countOptions = [
	50,
	100,
	250,
	500,
	1000,
	3000,
	5000,
];

type LayerPair = {
	id: number;
	pathCoordinates: Position[];
	markerPosition: Position;
};

// Each pair is one path + one marker entry sharing native layers per type.
// The <SharedLayer> wrapper enables type-grouped rendering: all paths and
// markers inside share single VectorLayer / ItemizedLayer fragments,
// so memory is O(1) instead of O(count). The many-pairs stress tests the
// batched entry creation pipeline and position-aware ordering within each
// fragment. Without <SharedLayer>, each type alternation creates a new
// fragment for strict React-tree z-order.
const buildLayerPairs = (count: number): LayerPair[] =>
	Array.from({ length: count }, (_, id) => {
		const lng = randomNumber(-78, -76);
		const lat = randomNumber(-10, -8);
		return {
			id,
			pathCoordinates: [
				[lng, lat],
				[
					lng + randomNumber(-0.2, 0.2),
					lat + randomNumber(-0.2, 0.2),
				],
			],
			markerPosition: [lng, lat],
		};
	});

const Controls: FC<{
	width: number;
	containerHeight: number;
	count: number;
	visible: boolean;
	useSharedLayer: boolean;
	setCount: (count: number) => void;
	setVisible: (visible: boolean) => void;
	onToggleSharedLayer: () => void;
	onRandomize: () => void;
}> = ({
	width,
	containerHeight,
	count,
	visible,
	useSharedLayer,
	setCount,
	setVisible,
	onToggleSharedLayer,
	onRandomize,
}) => {
	// Leave room for the debug overlay so the drawer doesn't cover it.
	const drawerMaxHeight = containerHeight - LAYER_DEBUG_OVERLAY_HEIGHT - 12;

	const nativeLayerInfo = useSharedLayer
		? `2 fragments (1 Path + 1 Marker), ${2 * count} drawables`
		: `${2 * count} dedicated layers (no grouping)`;

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

			<ControlSection>
				<Text style={sharedStyles.text}>
					{count} pairs → {nativeLayerInfo}
				</Text>
				<ControlRow>
					{countOptions.map((option) => (
						<Button
							key={option}
							title={`${option}`}
							onPress={() => setCount(option)}
						/>
					))}
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Button
						title={visible ? 'hide all' : 'show all'}
						onPress={() => setVisible(!visible)}
					/>
					<Button
						title={'randomize'}
						onPress={onRandomize}
					/>
				</ControlRow>
			</ControlSection>
		</ControlPanel>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [count, setCount] = useState(50);
	const [visible, setVisible] = useState(true);
	const [useSharedLayer, setUseSharedLayer] = useState(true);
	const [version, setVersion] = useState(0);

	const layerPairs = useMemo(
		() => buildLayerPairs(count),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[count, version]
	);

	const symbol: SymbolParams = useMemo(
		() => ({ text: '•', fillColor: '#00ff00' }),
		[]
	);

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
				containerHeight={height}
				count={count}
				visible={visible}
				useSharedLayer={useSharedLayer}
				setCount={setCount}
				setVisible={setVisible}
				onToggleSharedLayer={() => setUseSharedLayer((v) => !v)}
				onRandomize={() => setVersion((v) => v + 1)}
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
					zoomLevel={8}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{visible && useSharedLayer && (
						<SharedLayer>
							{layerPairs.map((pair) => (
								<Fragment key={pair.id}>
									<LayerPath
										coordinates={pair.pathCoordinates}
									/>
									<Marker
										position={pair.markerPosition}
										symbol={symbol}
									/>
								</Fragment>
							))}
						</SharedLayer>
					)}
					{visible &&
						!useSharedLayer &&
						layerPairs.map((pair) => (
							<Fragment key={pair.id}>
								<LayerPath coordinates={pair.pathCoordinates} />
								<Marker
									position={pair.markerPosition}
									symbol={symbol}
								/>
							</Fragment>
						))}
					{__DEV__ && <LayerDebugOverlay />}
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
	key: 'manyLayers',
	label: 'manyLayers',
	category: 'gestures',
} as Example;
