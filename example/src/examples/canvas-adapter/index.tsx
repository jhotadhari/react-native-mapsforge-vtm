import { useState, type FC } from 'react';
import { Button, Text, View } from 'react-native';
import {
	CanvasAdapterModule,
	LayerMapsforge,
	LayerScalebar,
	MapContainer,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Same test data as the `mapsforge` example -- see that example for how to push it onto the
// emulator/device. A render theme with text labels, lines (roads) and symbols (POI icons) is
// needed to actually see textScale/lineScale/symbolScale make a visible difference.
const mapFile = '/sdcard/Download/Andorra_oam.osm.map';
const renderTheme = '/sdcard/Download/Alti/Alti.xml';

const defaultCenter: Position = [1.55, 42.55]; // Andorra

// `CanvasAdapter`'s setters are global and only take effect for layers/themes parsed *after*
// they're called -- there's no live re-render of an already-mounted map. So this example gates
// the map behind a "(Re)start map" button: adjust the scale, press the button, the map (re)mounts
// and parses the render theme fresh with the new scale baked in.
const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [textScale, setTextScale] = useState(1);
	const [lineScale, setLineScale] = useState(1);
	const [symbolScale, setSymbolScale] = useState(1);
	const [mapKey, setMapKey] = useState(0);
	const [started, setStarted] = useState(false);

	const restart = () => {
		setStarted(false);
		CanvasAdapterModule.setTextScale(textScale);
		CanvasAdapterModule.setLineScale(lineScale);
		CanvasAdapterModule.setSymbolScale(symbolScale);
		setStarted(true);
		setMapKey((current) => current + 1);
	};

	const scaleControl = (
		label: string,
		value: number,
		setValue: (value: number) => void
	) => (
		<View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
			<Text style={[sharedStyles.text, { width: 70 }]}>{label}</Text>
			<Button
				title="-"
				onPress={() => {
					setValue(Math.max(0.25, value - 0.25));
				}}
			/>
			<Text style={sharedStyles.text}>{value.toFixed(2)}</Text>
			<Button
				title="+"
				onPress={() => {
					setValue(value + 0.25);
				}}
			/>
		</View>
	);

	return (
		<View
			style={{
				height,
				width,
			}}
		>
			{started && (
				<MapContainer
					key={mapKey}
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={14}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerMapsforge
						mapFile={mapFile}
						renderTheme={renderTheme}
						hasLabels
					/>
					<LayerScalebar />
				</MapContainer>
			)}

			<Center
				height={height}
				width={width}
			/>

			<View style={[sharedStyles.info, { bottom: undefined, top: 0 }]}>
				{scaleControl('textScale', textScale, setTextScale)}
				{scaleControl('lineScale', lineScale, setLineScale)}
				{scaleControl('symbolScale', symbolScale, setSymbolScale)}
				<Button
					title={started ? 'Restart map' : 'Start map'}
					onPress={restart}
				/>
			</View>

			{started && <MapInfo info={info} />}
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'canvasAdapter',
	label: 'canvasAdapter',
} as Example;
