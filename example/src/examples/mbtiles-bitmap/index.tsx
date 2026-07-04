import { useCallback, useState, type FC } from 'react';
import { View } from 'react-native';
import {
	LayerMBTilesBitmap,
	LayerScalebar,
	MapContainer,
	type LayerMBTilesBitmapResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import {
	ControlPanel,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Pushed onto the emulator/device via:
//   adb push OAM-World-1-8-J80.mbtiles /sdcard/Download/test-data/mbtiles/OAM-World-1-8-J80.mbtiles
// Requires the MANAGE_EXTERNAL_STORAGE permission declared in the example app's manifest --
// the app's own sandboxed external files dir can't see files it didn't create itself.
const mapFile = '/sdcard/Download/test-data/mbtiles/OAM-World-1-8-J80.mbtiles';

const defaultCenter: Position = [0, 0]; // [ lng, lat ]

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [center, setCenter] = useState<Position>(defaultCenter);
	const [bbox, setBbox] = useState<LayerMBTilesBitmapResponse['bbox']>();
	const [attribution, setAttribution] = useState('');
	const [description, setDescription] = useState('');

	const handleLayerCreate = useCallback(
		(response: LayerMBTilesBitmapResponse) => {
			if (response.center) {
				setCenter([...response.center]);
			}
			setBbox(response.bbox);
			setAttribution(response.attribution || '');
			setDescription(response.description || '');
		},
		[]
	);

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
				zoomLevel={3}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerMBTilesBitmap
					mapFile={mapFile}
					cacheSize={10}
					onCreate={handleLayerCreate}
				/>
				<LayerScalebar />
			</MapContainer>

			<Center
				height={height}
				width={width}
			/>

			{!!(attribution || description || bbox) && (
				<ControlPanel width={width}>
					<ControlSection>
						{!!description && (
							<StatusLine
								label={'Description'}
								value={description}
							/>
						)}
						{!!attribution && (
							<StatusLine
								label={'Attribution'}
								value={attribution}
							/>
						)}
						{!!bbox && (
							<StatusLine
								label={'bbox'}
								value={bbox.map((n) => n.toFixed(4)).join(', ')}
							/>
						)}
					</ControlSection>
				</ControlPanel>
			)}

			<MapInfo info={info} />
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'mbtilesBitmap',
	label: 'mbtilesBitmap',
	category: 'layers',
} as Example;
