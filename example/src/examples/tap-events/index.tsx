/**
 * External dependencies
 */
import { useCallback, useMemo, useRef, useState, type FC } from 'react';
import { View, Button, Text, type NativeSyntheticEvent } from 'react-native';
import {
	LayerBitmapTile,
	MapContainer,
	Marker,
	type Position,
	type SymbolParams,
	type TapEventResponse,
	type LongPressEventResponse,
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

const MAX_TAP_MARKERS = 10;

const Controls: FC<{
	width: number;
	lastTapInfo: string;
	lastLongPressInfo: string;
	tapMarkerCount: number;
	onClearMarkers: () => void;
}> = ({
	width,
	lastTapInfo,
	lastLongPressInfo,
	tapMarkerCount,
	onClearMarkers,
}) => {
	return (
		<ControlPanel width={width}>
			<ControlSection title="Tap events">
				<StatusLine
					label="Last tap"
					value={lastTapInfo}
				/>
				<StatusLine
					label="Tap markers"
					value={`${tapMarkerCount} / ${MAX_TAP_MARKERS}`}
				/>
			</ControlSection>
			<ControlSection title="Long-press events">
				<StatusLine
					label="Last long-press"
					value={lastLongPressInfo}
				/>
			</ControlSection>
			<ControlSection>
				<ControlRow>
					<Button
						title="Clear markers"
						onPress={onClearMarkers}
					/>
				</ControlRow>
			</ControlSection>
			<ControlSection>
				<Text style={sharedStyles.text}>
					Tap an empty area of the map to place a marker. Long-press
					to see the coordinates without placing a marker. Markers and
					paths already on the map will still receive their own
					press/longPress handlers — the map-level events only fire
					when nothing else consumed the gesture.
				</Text>
			</ControlSection>
		</ControlPanel>
	);
};

type TapMarker = {
	key: number;
	position: Position;
	label: string; // "tap" or "longPress"
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [lastTapInfo, setLastTapInfo] = useState('-');
	const [lastLongPressInfo, setLastLongPressInfo] = useState('-');
	const [tapMarkers, setTapMarkers] = useState<TapMarker[]>([]);
	const nextMarkerKey = useRef(0);

	const handleTap = useCallback(
		(event: NativeSyntheticEvent<Readonly<TapEventResponse>>) => {
			const { lng, lat } = event.nativeEvent;
			setLastTapInfo(`lng=${lng.toFixed(5)} lat=${lat.toFixed(5)}`);
			setTapMarkers((prev) => {
				if (prev.length >= MAX_TAP_MARKERS) {
					return prev;
				}
				const key = nextMarkerKey.current++;
				return [
					...prev,
					{
						key,
						position: [lng, lat],
						label: 'tap',
					},
				];
			});
		},
		[]
	);

	const handleLongPress = useCallback(
		(event: NativeSyntheticEvent<Readonly<LongPressEventResponse>>) => {
			const { lng, lat } = event.nativeEvent;
			setLastLongPressInfo(`lng=${lng.toFixed(5)} lat=${lat.toFixed(5)}`);
		},
		[]
	);

	const handleClearMarkers = useCallback(() => {
		setTapMarkers([]);
		nextMarkerKey.current = 0;
	}, []);

	const tapSymbol: SymbolParams = useMemo(
		() => ({ text: '✕', fillColor: '#ff4444', fontSize: 18 }),
		[]
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
				lastTapInfo={lastTapInfo}
				lastLongPressInfo={lastLongPressInfo}
				tapMarkerCount={tapMarkers.length}
				onClearMarkers={handleClearMarkers}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={8}
					onTap={handleTap}
					onLongPress={handleLongPress}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{tapMarkers.map((m) => (
						<Marker
							key={m.key}
							position={m.position}
							symbol={tapSymbol}
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
	key: 'tapEvents',
	label: 'Tap & LongPress',
	category: 'gestures',
} as Example;
