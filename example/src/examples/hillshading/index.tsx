import { useState, useCallback, type FC } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
	LayerHillshading,
	LayerScalebar,
	MapContainer,
	useMap,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Centered on Sajama volcano (~6500m), inside the downloaded SRTM3 tile coverage
// (S17W067..S20W071, Bolivia/Peru border) -- there's no terrain data outside this area in the
// test set, and this spot's dramatic relief makes the effect obvious at a glance.
const defaultCenter: Position = [-68.88, -18.11];

const hgtDirPath = '/sdcard/Download/test-data/hgt';

const ElevationOverlay: FC<{
	elevation: number | null;
	point: { lng: number; lat: number } | null;
}> = ({ elevation, point }) => {
	if (elevation == null || point == null) return null;
	return (
		<View style={styles.elevationOverlay}>
			<Text style={styles.elevationText}>
				{`${point.lat.toFixed(4)}°, ${point.lng.toFixed(4)}°`}
			</Text>
			<Text style={styles.elevationValue}>
				{`Elevation: ${elevation.toFixed(0)} m`}
			</Text>
		</View>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();
	const { getAltitudeAtPosition } = useMap();

	const [
		elevation,
		setElevation,
	] = useState<number | null>(null);
	const [
		tapPoint,
		setTapPoint,
	] = useState<{ lng: number; lat: number } | null>(null);

	const handleTap = useCallback(
		async (event: any) => {
			const { lng, lat } = event.nativeEvent;
			setTapPoint({ lng, lat });
			const alt = await getAltitudeAtPosition(lng, lat);
			setElevation(alt);
		},
		[getAltitudeAtPosition]
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
				center={defaultCenter}
				zoomLevel={12}
				onMapUpdate={handleMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
				onTap={handleTap}
				hgtDirPath={hgtDirPath}
			>
				<LayerHillshading
					hgtDirPath={'/sdcard/Download/test-data/hgt'}
				/>
				<LayerScalebar />
			</MapContainer>

			<Center
				height={height}
				width={width}
			/>

			<ElevationOverlay
				elevation={elevation}
				point={tapPoint}
			/>
			<MapInfo info={info} />
		</View>
	);
};

const styles = StyleSheet.create({
	elevationOverlay: {
		position: 'absolute',
		bottom: 60,
		left: 12,
		backgroundColor: 'rgba(0, 0, 0, 0.75)',
		paddingVertical: 8,
		paddingHorizontal: 12,
		borderRadius: 8,
		zIndex: 10,
	},
	elevationText: {
		color: '#ccc',
		fontSize: 12,
	},
	elevationValue: {
		color: '#fff',
		fontSize: 16,
		fontWeight: 'bold',
	},
});

export default {
	ExampleComponent,
	key: 'hillshading',
	label: 'hillshading',
	category: 'layers',
} as Example;
