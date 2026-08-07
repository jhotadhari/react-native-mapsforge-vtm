import { useMemo, useRef, useState, type FC } from 'react';
import { View, PixelRatio, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	Marker,
	type LayerPathGestureResponse,
	type MarkerEvent,
	type PathTriggerEvent,
	type Position,
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

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

const markerPositions: Position[] = [
	[-77, -9],
	[-76.9, -8.95],
];

const pathCoordinates: Position[] = [
	[-77.1, -9.1],
	[-76.95, -9],
	[-76.85, -8.9],
];

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [lastMarkerEvent, setLastMarkerEvent] = useState('-');
	const [lastPathEvent, setLastPathEvent] = useState('-');

	// triggerEvent on MapContainer triggers ALL markers (no LayerMarker needed).
	const triggerMarkerEvent = useRef<
		null | ((params: { x: number; y: number; strategy?: string }) => void)
	>(null);
	const triggerPathEvent = useRef<PathTriggerEvent>(null);

	const centerX = PixelRatio.getPixelSizeForLayoutSize(width) / 2;
	const centerY = PixelRatio.getPixelSizeForLayoutSize(height) / 2;

	const handleMarkerEvent = useMemo(() => {
		return {
			// Per-marker event handler (bare Marker, no LayerMarker wrapper).
			onEvent: (response?: MarkerEvent) => {
				console.log('debug onEvent', response);
				response &&
					setLastMarkerEvent(
						`${response.event} marker #${response.index}`
					);
			},
		};
	}, []);

	const handlePathEvent = useMemo(() => {
		return {
			onPress: (response: LayerPathGestureResponse) => {
				console.log('debug onPress', response);
				setLastPathEvent(`press dist=${response.distance.toFixed(4)}`);
			},
			onLongPress: (response: LayerPathGestureResponse) => {
				console.log('debug onLongPress', response);
				setLastPathEvent(
					`longPress dist=${response.distance.toFixed(4)}`
				);
			},
			onDoubleTap: (response: LayerPathGestureResponse) => {
				console.log('debug onDoubleTap', response);
				setLastPathEvent(
					`doubleTap dist=${response.distance.toFixed(4)}`
				);
			},
			onTrigger: (response: LayerPathGestureResponse) => {
				console.log('debug onTrigger', response);
				setLastPathEvent(
					`trigger dist=${response.distance.toFixed(4)}`
				);
			},
		};
	}, []);

	const paint = useMemo(() => {
		return {
			text: 'o',
		};
	}, []);

	const stylesDynamic = useMemo(
		() => ({
			container: {
				width,
				height,
				gap: 16,
			} as const,
			containerMap: {
				height,
				width,
			} as const,
		}),
		[width, height]
	);

	return (
		<View style={stylesDynamic.container}>
			<ControlPanel width={width}>
				<ControlSection>
					<StatusLine
						label="Marker events"
						value={lastMarkerEvent}
					/>
					<ControlRow>
						<Button
							onPress={() => {
								console.log(!!triggerMarkerEvent?.current);
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
								console.log(!!triggerMarkerEvent?.current);
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
								console.log(!!triggerMarkerEvent?.current);
								triggerMarkerEvent?.current &&
									triggerMarkerEvent?.current({
										x: centerX,
										y: centerY,
										strategy: 'nearest',
									});
							}}
							title={'Trigger nearest'}
						/>
					</ControlRow>
				</ControlSection>

				<ControlSection>
					<StatusLine
						label="Path events"
						value={lastPathEvent}
					/>
					<ControlRow>
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
					</ControlRow>
				</ControlSection>
			</ControlPanel>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={8}
					triggerEvent={triggerMarkerEvent}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					<LayerPath
						coordinates={pathCoordinates}
						onPress={handlePathEvent.onPress}
						onLongPress={handlePathEvent.onLongPress}
						onDoubleTap={handlePathEvent.onDoubleTap}
						onTrigger={handlePathEvent.onTrigger}
						triggerEvent={triggerPathEvent}
					/>

					{markerPositions.map((position, idx) => (
						<Marker
							key={idx}
							position={position}
							onEvent={handleMarkerEvent.onEvent}
							paint={paint}
						/>
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
	key: 'trigger',
	label: 'Trigger events',
	category: 'gestures',
} as Example;
