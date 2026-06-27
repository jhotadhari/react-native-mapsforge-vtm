import { Fragment, useMemo, useState, type FC } from 'react';
import { View, Text, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	Marker,
	type Position,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import { randomNumber } from '../../utils';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
} from '../../components/ControlPanel';

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

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

const countOptions = [
	100,
	1000,
	3000,
	5000,
];

type LayerPair = {
	id: number;
	pathCoordinates: Position[];
	markerPosition: Position;
};

// Each pair is one path + one marker entry in the shared native layers.
// With the virtual-layers architecture, all entries share a single native
// VectorLayer (paths) and a single ItemizedLayer (markers), so memory is O(1)
// instead of O(count). The many-pairs stress tests the batched entry creation
// pipeline and the position-aware ordering within each shared layer.
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
	count: number;
	visible: boolean;
	setCount: (count: number) => void;
	setVisible: (visible: boolean) => void;
	onRandomize: () => void;
}> = ({ width, count, visible, setCount, setVisible, onRandomize }) => {
	return (
		<ControlPanel width={width}>
			<ControlSection>
				<Text style={sharedStyles.text}>
					{count} pairs = {2 * count} native layers
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

	const [count, setCount] = useState(100);
	const [visible, setVisible] = useState(true);
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
				count={count}
				visible={visible}
				setCount={setCount}
				setVisible={setVisible}
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
					responseInclude={responseInclude}
					zoomLevel={8}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{visible &&
						layerPairs.map((pair) => (
							<Fragment key={pair.id}>
								<LayerPath coordinates={pair.pathCoordinates} />
								<Marker
									position={pair.markerPosition}
									symbol={symbol}
								/>
							</Fragment>
						))}
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
