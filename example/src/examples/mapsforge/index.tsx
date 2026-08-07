import { useCallback, useMemo, useState, type FC } from 'react';
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
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Vector map (.map) and render theme from openandromaps.org:
//   https://www.openandromaps.org/en/downloads/countrys-and-regions
// Download a country/region .map file and its accompanying render theme
// (e.g. "Alti"/"Elevate").  Push to the device via adb:
//   adb push Andorra_oam.osm.map /sdcard/Download/test-data/mapfiles/Andorra_oam.osm.map
//   adb push Alti/            /sdcard/Download/test-data/mapstyles/Alti/
// The whole theme directory (not just the .xml) must be pushed since
// <stylemenu> styles reference icon resources by path relative to the xml.
// A theme with a <stylemenu> is needed for the render style/overlay picker
// below; built-in themes (DEFAULT etc.) have no selectable styles.
// Requires the MANAGE_EXTERNAL_STORAGE permission declared in the example
// app's manifest — the app's sandboxed external files dir can't see files
// it didn't create itself.
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

	const containerStyle = useMemo(() => ({ height, width }), [height, width]);

	return (
		<View style={containerStyle}>
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

			<ControlPanel width={width}>
				<ControlSection>
					<StatusLine
						label={'bbox'}
						value={
							bbox
								? bbox.map((n) => n.toFixed(4)).join(', ')
								: '-'
						}
					/>
				</ControlSection>

				<ControlSection title={'Layers'}>
					<ControlRow>
						<Button
							title={
								hasBuildings
									? 'hide buildings'
									: 'show buildings'
							}
							onPress={() =>
								setHasBuildings((current) => !current)
							}
						/>
						<Button
							title={hasLabels ? 'hide labels' : 'show labels'}
							onPress={() => setHasLabels((current) => !current)}
						/>
					</ControlRow>
				</ControlSection>

				{!!renderStyleOptions.length && (
					<ControlSection title={'Render style'}>
						{renderStyleOptions.map((option) => (
							<ControlRow key={option.value}>
								<Button
									title={
										(option.value === selectedRenderStyle
											? '> '
											: '') + option.label
									}
									onPress={() => setRenderStyle(option.value)}
								/>
							</ControlRow>
						))}
					</ControlSection>
				)}

				{!!selectedOverlayOptions.length && (
					<ControlSection title={'Render overlays'}>
						{selectedOverlayOptions.map((overlay) => (
							<ControlRow key={overlay.value}>
								<Button
									title={
										(renderOverlays.includes(overlay.value)
											? '[x] '
											: '[ ] ') + overlay.label
									}
									onPress={() => toggleOverlay(overlay.value)}
								/>
							</ControlRow>
						))}
					</ControlSection>
				)}
			</ControlPanel>

			<ControlPanel width={width}>
				<ControlSection>
					<Text style={sharedStyles.text}>
						Vector map (.map) and render theme from
						https://www.openandromaps.org/en/downloads/countrys-and-regions
						→ /sdcard/Download/test-data/mapfiles/ →
						/sdcard/Download/test-data/mapstyles/
					</Text>
				</ControlSection>
			</ControlPanel>

			<MapInfo info={info} />
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'mapsforge',
	label: 'mapsforge',
	category: 'layers',
} as Example;
