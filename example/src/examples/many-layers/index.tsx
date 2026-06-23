import { Fragment, useMemo, useState, type FC } from 'react';
import { View, Text, Button, StyleSheet } from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	LayerPath,
	MapContainer,
	Marker,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import { randomNumber } from '../../utils';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

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
	1000,
	3000,
	6000,
	10000,
];

type LayerPair = {
	id: number;
	pathCoordinates: Position[];
	markerPosition: Position;
};

// Each pair is two real native layers (one LayerPath, one LayerMarker), each with its own
// uuid resolving independently and asynchronously -- mounting many pairs at once is what
// stresses the layer-order registry's reorderLayers batching, since every one of those
// uuid resolutions used to fire its own native call.
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
	count: number;
	visible: boolean;
	setCount: (count: number) => void;
	setVisible: (visible: boolean) => void;
	onRandomize: () => void;
}> = ({ count, visible, setCount, setVisible, onRandomize }) => {
	return (
		<View style={styles.controls}>
			<View style={styles.section}>
				<Text style={sharedStyles.text}>
					{count} pairs = {2 * count} native layers
				</Text>
				<View style={styles.flexRow}>
					{countOptions.map((option) => (
						<Button
							key={option}
							title={`${option}`}
							onPress={() => setCount(option)}
						/>
					))}
				</View>
			</View>

			<View style={styles.section}>
				<View style={styles.flexRow}>
					<Button
						title={visible ? 'hide all' : 'show all'}
						onPress={() => setVisible(!visible)}
					/>
					<Button
						title={'randomize'}
						onPress={onRandomize}
					/>
				</View>
			</View>
		</View>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [count, setCount] = useState(3000);
	const [visible, setVisible] = useState(true);
	const [version, setVersion] = useState(0);

	const layerPairs = useMemo(
		() => buildLayerPairs(count),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[count, version]
	);

	const symbol = useMemo(() => ({ text: '•' }), []);

	return (
		<View
			style={{
				width,
				height,
				gap: 16,
			}}
		>
			<Controls
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
								<LayerMarker>
									<Marker
										position={pair.markerPosition}
										symbol={symbol}
									/>
								</LayerMarker>
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

const styles = StyleSheet.create({
	controls: {
		position: 'absolute',
		backgroundColor: '#000000',
		zIndex: 9,
		padding: 16,
		gap: 16,
	},
	section: {
		alignItems: 'center',
		justifyContent: 'space-evenly',
	},
	flexRow: {
		flexDirection: 'row',
		justifyContent: 'space-evenly',
		gap: 16,
	},
});

export default {
	ExampleComponent,
	key: 'manyLayers',
	label: 'manyLayers',
} as Example;
