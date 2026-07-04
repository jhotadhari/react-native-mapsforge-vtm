import type { FC } from 'react';
import { View } from 'react-native';
import {
	LayerBitmapTile,
	LayerScalebar,
	MapContainer,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

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
				center={defaultCenter}
				zoomLevel={8}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerBitmapTile />
				<LayerScalebar />
			</MapContainer>

			<Center
				height={height}
				width={width}
			/>

			<MapInfo info={info} />
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'basic',
	label: 'basic',
	category: 'layers',
} as Example;
