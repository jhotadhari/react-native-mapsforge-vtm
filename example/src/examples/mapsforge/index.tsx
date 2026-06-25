import { useCallback, useState, type FC } from 'react';
import { Button, Text, View } from 'react-native';
import {
	LayerMapsforge,
	LayerScalebar,
	MapContainer,
	useRenderStyleOptions,
	type LayerMapsforgeResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Pushed onto the emulator/device via, e.g.:
//   adb push Andorra_oam.osm.map /sdcard/Download/Andorra_oam.osm.map
//   adb push Alti /sdcard/Download/Alti
// Any mapsforge .map file / vtm-compatible render theme xml works -- both can be downloaded from
// openandromaps.org/en/downloads. A theme with a <stylemenu> (like openandromaps' "Alti"/"Elevate")
// is needed to see the render style/overlay picker below do anything; built-in themes (DEFAULT
// etc.) have no selectable styles. The whole theme directory (not just the xml) needs pushing,
// since its <stylemenu> styles reference icon resources by path relative to the xml file.
// Requires the MANAGE_EXTERNAL_STORAGE permission declared in the example app's manifest -- the
// app's own sandboxed external files dir can't see files it didn't create itself.
const mapFile = '/sdcard/Download/test-data/mapfiles/Andorra_oam.osm.map';
const renderTheme = '/sdcard/Download/test-data/mapstyles/Alti/Alti.xml';

const defaultCenter: Position = [1.55, 42.55]; // Andorra

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [center, setCenter] = useState<Position>(defaultCenter);
	const [bbox, setBbox] = useState<LayerMapsforgeResponse['bbox']>();
	const [renderStyle, setRenderStyle] = useState<string | undefined>(
		undefined
	);
	const [renderOverlays, setRenderOverlays] = useState<string[]>([]);
	const [hasBuildings, setHasBuildings] = useState(true);
	const [hasLabels, setHasLabels] = useState(true);

	const { renderStyleDefaultId, renderStyleOptions } = useRenderStyleOptions({
		renderTheme,
	});

	const handleLayerCreate = useCallback(
		(response: LayerMapsforgeResponse) => {
			if (response.center) {
				setCenter([...response.center]);
			}
			setBbox(response.bbox);
		},
		[]
	);

	const selectedRenderStyle =
		renderStyle ?? renderStyleDefaultId ?? undefined;
	const selectedOverlayOptions =
		renderStyleOptions.find(
			(option) => option.value === selectedRenderStyle
		)?.overlays ?? [];

	const toggleOverlay = (value: string) => {
		setRenderOverlays((current) =>
			current.includes(value)
				? current.filter((v) => v !== value)
				: [...current, value]
		);
	};

	return (
		<View
			style={{
				height,
				width,
			}}
		>
			<MapContainer
				width={width}
				height={height}
				center={center}
				zoomLevel={12}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerMapsforge
					mapFile={mapFile}
					renderTheme={renderTheme}
					renderStyle={selectedRenderStyle}
					renderOverlays={renderOverlays}
					hasBuildings={hasBuildings}
					hasLabels={hasLabels}
					onCreate={handleLayerCreate}
					onChange={handleLayerCreate}
				/>
				<LayerScalebar />
			</MapContainer>

			<Center
				height={height}
				width={width}
			/>

			<View style={[sharedStyles.info, { bottom: undefined, top: 0 }]}>
				{!!bbox && (
					<Text style={sharedStyles.text}>
						bbox: [{bbox.map((n) => n.toFixed(4)).join(', ')}]
					</Text>
				)}
				<Button
					title={hasBuildings ? 'hide buildings' : 'show buildings'}
					onPress={() => setHasBuildings((current) => !current)}
				/>
				<Button
					title={hasLabels ? 'hide labels' : 'show labels'}
					onPress={() => setHasLabels((current) => !current)}
				/>
				{!!renderStyleOptions.length && (
					<Text style={sharedStyles.text}>Render style:</Text>
				)}
				{renderStyleOptions.map((option) => (
					<Button
						key={option.value}
						title={
							(option.value === selectedRenderStyle ? '> ' : '') +
							option.label
						}
						onPress={() => setRenderStyle(option.value)}
					/>
				))}
				{!!selectedOverlayOptions.length && (
					<Text style={sharedStyles.text}>Render overlays:</Text>
				)}
				{selectedOverlayOptions.map((overlay) => (
					<Button
						key={overlay.value}
						title={
							(renderOverlays.includes(overlay.value)
								? '[x] '
								: '[ ] ') + overlay.label
						}
						onPress={() => toggleOverlay(overlay.value)}
					/>
				))}
			</View>

			<MapInfo info={info} />
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'mapsforge',
	label: 'mapsforge',
} as Example;
