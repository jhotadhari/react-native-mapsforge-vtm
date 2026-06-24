import { useCallback, useState, type FC } from 'react';
import { Text, View } from 'react-native';
import {
	LayerMBTilesBitmap,
	LayerScalebar,
	MapContainer,
	type LayerMBTilesBitmapResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Pushed onto the emulator/device via:
//   adb push OAM-World-1-8-J80.mbtiles /sdcard/Download/OAM-World-1-8-J80.mbtiles
// Requires the MANAGE_EXTERNAL_STORAGE permission declared in the example app's manifest --
// the app's own sandboxed external files dir can't see files it didn't create itself.
const mapFile = '/sdcard/Download/OAM-World-1-8-J80.mbtiles';

const defaultCenter: Position = [0, 0]; // [ lng, lat ]

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

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [center, setCenter] = useState<Position>(defaultCenter);
	const [attribution, setAttribution] = useState('');
	const [description, setDescription] = useState('');

	const handleLayerCreate = useCallback(
		(response: LayerMBTilesBitmapResponse) => {
			if (response.center) {
				setCenter([response.center.lng, response.center.lat]);
			}
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
				responseInclude={responseInclude}
				zoomLevel={3}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerMBTilesBitmap
					mapFile={mapFile}
					onCreate={handleLayerCreate}
				/>
				<LayerScalebar />
			</MapContainer>

			<Center
				height={height}
				width={width}
			/>

			{!!(attribution || description) && (
				<View
					style={[sharedStyles.info, { bottom: undefined, top: 0 }]}
				>
					{!!description && (
						<Text style={sharedStyles.text}>{description}</Text>
					)}
					{!!attribution && (
						<Text style={sharedStyles.text}>{attribution}</Text>
					)}
				</View>
			)}

			<MapInfo info={info} />
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'mbtilesBitmap',
	label: 'mbtilesBitmap',
} as Example;
