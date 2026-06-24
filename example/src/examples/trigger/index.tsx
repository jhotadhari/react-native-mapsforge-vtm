import {
	useMemo,
	useRef,
	useState,
	type Dispatch,
	type FC,
	type RefObject,
	type SetStateAction,
} from 'react';
import { View, Text, Button, PixelRatio, StyleSheet } from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	LayerPath,
	MapContainer,
	Marker,
	type LayerMarkerTriggerEvent,
	type LayerPathGestureResponse,
	type MarkerEvent,
	type PathTriggerEvent,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import { randomNumber } from '../../utils';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

const coordsCount = 100;

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

const getRandomCoordinates = (length: number): Position[] =>
	Array.apply(null, Array(length)).map(
		(): Position => [
			randomNumber(-78, -76), // lng
			randomNumber(-10, -8), // lat
		]
	);

// const MarkerWrapper: FC<{
// 	position: Position;
// 	idx: number;
// }> = ({ position, idx }) => {
// 	const handleMarkerEvent = useMemo(() => {
// 		return {
// 			onEvent: (response?: MarkerTypes.MarkerEvent) => {
// 				console.log('debug onEvent', response); // debug
// 			},
// 		};
// 	}, []);

// 	const symbol = useMemo(() => {
// 		return {
// 			text: idx + '',
// 		};
// 	}, [idx]);

// 	return (
// 		<Marker
// 			key={idx}
// 			position={position}
// 			onEvent={handleMarkerEvent.onEvent}
// 			symbol={symbol}
// 		/>
// 	);
// };

// const triggerMarkerEvent = useRef<LayerMarkerTriggerEvent>(null);
// const triggerPathEvent = useRef<LayerPathTypes.TriggerEvent>(null);

const Controls: FC<{
	mapWidth: number;
	mapHeight: number;
	lastMarkerEvent: string;
	lastPathEvent: string;
	hasMarker: boolean;
	hasPath: boolean;
	setHasMarker: Dispatch<SetStateAction<boolean>>;
	setHasPath: Dispatch<SetStateAction<boolean>>;
	triggerMarkerEvent: RefObject<LayerMarkerTriggerEvent | null>;
	triggerPathEvent: RefObject<PathTriggerEvent | null>;
	setCoordinates: Dispatch<SetStateAction<Position[]>>;
}> = ({
	mapWidth,
	mapHeight,
	lastMarkerEvent,
	lastPathEvent,
	setHasMarker,
	setHasPath,
	hasMarker,
	hasPath,
	triggerMarkerEvent,
	triggerPathEvent,
	setCoordinates,
}) => {
	const centerX = PixelRatio.getPixelSizeForLayoutSize(mapWidth) / 2;
	const centerY = PixelRatio.getPixelSizeForLayoutSize(mapHeight) / 2;

	return (
		<View style={[styles.controls, { width: mapWidth }]}>
			<View style={styles.section}>
				<View style={styles.flexRow}>
					<Button
						title={hasMarker ? 'hide marker' : 'show marker'}
						onPress={() => setHasMarker(!hasMarker)}
					/>
					<Button
						title={hasPath ? 'hide path' : 'show path'}
						onPress={() => setHasPath(!hasPath)}
					/>
					<Button
						title={'randomize coordinates'}
						onPress={() =>
							setCoordinates(getRandomCoordinates(coordsCount))
						}
					/>
				</View>
			</View>

			<View style={styles.section}>
				<View style={styles.flexRow}>
					<Text style={sharedStyles.text}>Marker events</Text>
					<Text style={sharedStyles.text}>{lastMarkerEvent}</Text>
				</View>
				<View style={styles.flexRow}>
					<Button
						onPress={() => {
							triggerMarkerEvent?.current &&
								triggerMarkerEvent?.current({
									x: centerX,
									y: centerY,
									strategy: 'all',
								});
						}}
						title={'Trigger all'}
					/>
					<Button
						onPress={() => {
							triggerMarkerEvent?.current &&
								triggerMarkerEvent?.current({
									x: centerX,
									y: centerY,
									strategy: 'first',
								});
						}}
						title={'Trigger first'}
					/>
					<Button
						onPress={() => {
							triggerMarkerEvent?.current &&
								triggerMarkerEvent?.current({
									x: centerX,
									y: centerY,
									strategy: 'nearest',
								});
						}}
						title={'Trigger nearest'}
					/>
				</View>
			</View>

			<View style={styles.section}>
				<View style={styles.flexRow}>
					<Text style={sharedStyles.text}>Path events</Text>
					<Text style={sharedStyles.text}>{lastPathEvent}</Text>
				</View>
				<View style={styles.flexRow}>
					<Button
						onPress={() => {
							triggerPathEvent?.current &&
								triggerPathEvent?.current({
									x: centerX,
									y: centerY,
								});
						}}
						title={'Trigger path'}
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

	const [coordinates, setCoordinates] = useState(
		getRandomCoordinates(coordsCount)
	);
	const [hasMarker, setHasMarker] = useState(true);
	const [hasPath, setHasPath] = useState(true);

	const [lastMarkerEvent, setLastMarkerEvent] = useState('-');
	const [lastPathEvent, setLastPathEvent] = useState('-');

	const triggerMarkerEvent = useRef<LayerMarkerTriggerEvent>(null);
	const triggerPathEvent = useRef<PathTriggerEvent>(null);

	const handleMarkerEvent = useMemo(() => {
		return {
			onMarkerLayerEvent: (response?: MarkerEvent) => {
				console.log('debug onMarkerLayerEvent', response); // debug
				response &&
					setLastMarkerEvent(
						`${response.event} marker #${response.index}`
					);
			},
			onEvent: (response?: MarkerEvent) => {
				console.log('debug onEvent', response); // debug
			},
		};
	}, []);

	const handlePathEvent = useMemo(() => {
		return {
			onPress: (response: LayerPathGestureResponse) => {
				console.log('debug onPress', response); // debug
				setLastPathEvent(`press dist=${response.distance.toFixed(4)}`);
			},
			onLongPress: (
				response: LayerPathGestureResponse
			) => {
				console.log('debug onLongPress', response); // debug
				setLastPathEvent(
					`longPress dist=${response.distance.toFixed(4)}`
				);
			},
			onDoubleTap: (
				response: LayerPathGestureResponse
			) => {
				console.log('debug onDoubleTap', response); // debug
				setLastPathEvent(
					`doubleTap dist=${response.distance.toFixed(4)}`
				);
			},
			onTrigger: (response: LayerPathGestureResponse) => {
				console.log('debug onTrigger', response); // debug
				setLastPathEvent(
					`trigger dist=${response.distance.toFixed(4)}`
				);
			},
		};
	}, []);

	const symbol = useMemo(() => {
		return {
			text: 'o', // idx + '',
		};
	}, []);

	return (
		<View
			style={{
				width,
				height,
				gap: 16,
			}}
		>
			<Controls
				mapHeight={height}
				mapWidth={width}
				lastMarkerEvent={lastMarkerEvent}
				lastPathEvent={lastPathEvent}
				setHasMarker={setHasMarker}
				setHasPath={setHasPath}
				hasMarker={hasMarker}
				hasPath={hasPath}
				triggerMarkerEvent={triggerMarkerEvent}
				triggerPathEvent={triggerPathEvent}
				setCoordinates={setCoordinates}
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

					{hasPath && (
						<LayerPath
							coordinates={coordinates}
							onPress={handlePathEvent.onPress}
							onLongPress={handlePathEvent.onLongPress}
							onDoubleTap={handlePathEvent.onDoubleTap}
							onTrigger={handlePathEvent.onTrigger}
							triggerEvent={triggerPathEvent}
						/>
					)}

					<LayerMarker
						triggerEvent={triggerMarkerEvent}
						onMarkerEvent={handleMarkerEvent.onMarkerLayerEvent}
					>
						{hasMarker &&
							[...coordinates].map(
								(position: Position, idx: number) => (
									<Marker
										key={idx}
										position={position}
										onEvent={handleMarkerEvent.onEvent}
										symbol={symbol}
									/>
								)
							)}
					</LayerMarker>
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
	container: {
		flex: 1,
		alignItems: 'center',
		justifyContent: 'space-evenly',
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
	key: 'trigger',
	label: 'trigger',
} as Example;
