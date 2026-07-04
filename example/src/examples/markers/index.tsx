import { useMemo, useState, type FC } from 'react';
import { View, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	Marker,
	MapContainer,
	type MarkerEvent,
	type Position,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

const defaultCenter: Position = [10, 35]; // [ lng, lat ] -- frames most of the cities below

// Real cities, spread across continents, each with a distinct symbol (first letter + a
// per-city fill color) so it's obvious at a glance which marker is which.
const cities: {
	key: string;
	name: string;
	position: Position;
	fillColor: `#${string}`;
}[] = [
	{
		key: 'london',
		name: 'London',
		position: [-0.1278, 51.5074],
		fillColor: '#ff0000',
	},
	{
		key: 'paris',
		name: 'Paris',
		position: [2.3522, 48.8566],
		fillColor: '#0000ff',
	},
	{
		key: 'cairo',
		name: 'Cairo',
		position: [31.2357, 30.0444],
		fillColor: '#ffaa00',
	},
	{
		key: 'nairobi',
		name: 'Nairobi',
		position: [36.8219, -1.2921],
		fillColor: '#00aa00',
	},
	{
		key: 'moscow',
		name: 'Moscow',
		position: [37.6173, 55.7558],
		fillColor: '#aa00aa',
	},
	{
		key: 'delhi',
		name: 'Delhi',
		position: [77.1025, 28.7041],
		fillColor: '#00aaaa',
	},
	{
		key: 'tokyo',
		name: 'Tokyo',
		position: [139.6917, 35.6895],
		fillColor: '#ff00ff',
	},
	{
		key: 'sydney',
		name: 'Sydney',
		position: [151.2093, -33.8688],
		fillColor: '#aaff00',
	},
	{
		key: 'saoPaulo',
		name: 'Sao Paulo',
		position: [-46.6333, -23.5505],
		fillColor: '#ff5500',
	},
	{
		key: 'newYork',
		name: 'New York',
		position: [-74.006, 40.7128],
		fillColor: '#5500ff',
	},
];

const extraCity = {
	key: 'capeTown',
	name: 'Cape Town',
	position: [18.4241, -33.9249] as Position,
	fillColor: '#ffffff' as `#${string}`,
};

const symbolForCity = (city: {
	name: string;
	fillColor: `#${string}`;
}): SymbolParams => ({
	text: city.name.charAt(0),
	fillColor: city.fillColor,
});

const Controls: FC<{
	mapWidth: number;
	lastMarkerEvent: string;
	hasExtraCity: boolean;
	onToggleExtraCity: () => void;
}> = ({ mapWidth, lastMarkerEvent, hasExtraCity, onToggleExtraCity }) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection
				title={'Markers (one LayerMarker, one Marker per city)'}
			>
				<ControlRow>
					<Button
						title={
							hasExtraCity
								? `Remove ${extraCity.name}`
								: `Add ${extraCity.name}`
						}
						onPress={onToggleExtraCity}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<StatusLine
					label={'Last marker event'}
					value={lastMarkerEvent}
				/>
			</ControlSection>
		</ControlPanel>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [hasExtraCity, setHasExtraCity] = useState(false);
	const [lastMarkerEvent, setLastMarkerEvent] = useState('-');

	// Keyed by city key so each marker's onEvent handler can report which city fired it,
	// without relying on the LayerMarker-level index (which shifts as the extra city is
	// added/removed).
	const handleMarkerEvent = useMemo(() => {
		const handlers: Record<string, (response: MarkerEvent) => void> = {};
		[...cities, extraCity].forEach((city) => {
			handlers[city.key] = (response: MarkerEvent) => {
				setLastMarkerEvent(`${response.event} ${city.name}`);
			};
		});
		return handlers;
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
				mapWidth={width}
				lastMarkerEvent={lastMarkerEvent}
				hasExtraCity={hasExtraCity}
				onToggleExtraCity={() => setHasExtraCity((v) => !v)}
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
					zoomLevel={2}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					<LayerMarker>
						{cities.map((city) => (
							<Marker
								key={city.key}
								position={city.position}
								title={city.name}
								symbol={symbolForCity(city)}
								onEvent={handleMarkerEvent[city.key]}
							/>
						))}
						{hasExtraCity && (
							<Marker
								key={extraCity.key}
								position={extraCity.position}
								title={extraCity.name}
								symbol={symbolForCity(extraCity)}
								onEvent={handleMarkerEvent[extraCity.key]}
							/>
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

export default {
	ExampleComponent,
	key: 'markers',
	label: 'Markers',
	category: 'layers',
} as Example;
