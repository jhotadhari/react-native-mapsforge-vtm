import type { FC } from 'react';
import { View } from 'react-native';
import {
	LayerHillshading,
	LayerScalebar,
	MapContainer,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Pushed onto the emulator/device via:
//   adb push /home/jhotadhari/Development/android/test-data/hgt/*.hgt /sdcard/Download/hgt/
// Requires the MANAGE_EXTERNAL_STORAGE permission declared in the example app's manifest --
// the app's own sandboxed external files dir can't see files it didn't create itself.
const hgtDirPath = '/sdcard/Download/hgt';

// Centered on Sajama volcano (~6500m), inside the downloaded SRTM3 tile coverage
// (S17W067..S20W071, Bolivia/Peru border) -- there's no terrain data outside this area in the
// test set, and this spot's dramatic relief makes the effect obvious at a glance.
const defaultCenter: Position = [-68.88, -18.11];

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
				zoomLevel={12}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerHillshading hgtDirPath={hgtDirPath} />
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
	key: 'hillshading',
	label: 'hillshading',
} as Example;
