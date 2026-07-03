import { useCallback, useState, type FC } from 'react';
import {
	View,
	Text,
	StyleSheet,
	type NativeSyntheticEvent,
} from 'react-native';
import Animated from 'react-native-reanimated';
import {
	LayerBitmapTile,
	LayerScalebar,
	MapContainer,
	type MapEventResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import {
	useMapPosition,
	useMapOverlay,
	type MapPositionSharedValues,
} from 'react-native-mapsforge-vtm/reanimated';
import Center from '../../components/Center';
import MapInfo from '../../components/MapInfo';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';

const defaultCenter: Position = [10, 35]; // [ lng, lat ]

/**
 * One overlay per city.  Extracted as a component so each useMapOverlay
 * call sits at the top level (React Hook rules).
 */
const CityOverlay: FC<{
	name: string;
	lat: number;
	lng: number;
	emoji: string;
	color: string;
	pos: MapPositionSharedValues;
}> = ({ name, lat, lng, emoji, color, pos }) => {
	const { animatedStyle } = useMapOverlay({ lat, lng }, pos);

	return (
		<Animated.View
			style={[styles.overlay, animatedStyle]}
			pointerEvents="none"
		>
			<View style={[styles.badge, { borderColor: color }]}>
				<Text style={styles.emoji}>{emoji}</Text>
				<Text style={[styles.label, { color }]}>{name}</Text>
			</View>
		</Animated.View>
	);
};

const CITIES = [
	{
		name: 'London',
		lat: 51.5074,
		lng: -0.1278,
		emoji: '🇬🇧',
		color: '#ff4444',
	},
	{
		name: 'Paris',
		lat: 48.8566,
		lng: 2.3522,
		emoji: '🇫🇷',
		color: '#4444ff',
	},
	{
		name: 'Cairo',
		lat: 30.0444,
		lng: 31.2357,
		emoji: '🇪🇬',
		color: '#ffaa00',
	},
	{
		name: 'Nairobi',
		lat: -1.2921,
		lng: 36.8219,
		emoji: '🇰🇪',
		color: '#44aa44',
	},
	{
		name: 'Moscow',
		lat: 55.7558,
		lng: 37.6173,
		emoji: '🇷🇺',
		color: '#aa44aa',
	},
	{
		name: 'Delhi',
		lat: 28.6139,
		lng: 77.209,
		emoji: '🇮🇳',
		color: '#44aaaa',
	},
];

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	// One useMapPosition — feeds all overlay instances, zero extra cost.
	const pos = useMapPosition();

	// Debug info: mirror the raw event for the MapInfo JSON dump.
	const [info, setInfo] = useState<MapEventResponse | undefined>(undefined);
	const handleMapUpdate = useCallback(
		(response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
			pos.handleMapUpdate(response);
			setInfo(response?.nativeEvent);
		},
		[pos]
	);

	return (
		<View style={{ height, width }}>
			<MapContainer
				width={width}
				height={height}
				center={defaultCenter}
				responseInclude={pos.responseInclude}
				zoomLevel={5}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerBitmapTile />
				<LayerScalebar />
			</MapContainer>

			{/*
			 * Overlays are siblings of MapContainer (not children).
			 * Their left/top is driven by worklets on the UI thread —
			 * 60fps, zero bridge crossings, zero React re-renders.
			 */}
			{CITIES.map((city) => (
				<CityOverlay
					key={city.name}
					{...city}
					pos={pos}
				/>
			))}

			<Center
				height={height}
				width={width}
			/>
			<MapInfo info={info} />
		</View>
	);
};

const styles = StyleSheet.create({
	overlay: {
		position: 'absolute',
		// Anchor: the worklet computes top-left of the overlay view.
		// Translate back so the badge centre lands on the coordinate.
		transform: [{ translateX: -40 }, { translateY: -24 }],
	},
	badge: {
		flexDirection: 'row',
		alignItems: 'center',
		gap: 4,
		backgroundColor: 'rgba(0,0,0,0.75)',
		paddingVertical: 2,
		paddingHorizontal: 6,
		borderRadius: 12,
		borderWidth: 1,
	},
	emoji: {
		fontSize: 14,
	},
	label: {
		fontSize: 11,
		fontWeight: '600',
	},
});

export default {
	ExampleComponent,
	key: 'reanimated-overlay',
	label: 'reanimated overlay',
	category: 'mapControls',
} as Example;
