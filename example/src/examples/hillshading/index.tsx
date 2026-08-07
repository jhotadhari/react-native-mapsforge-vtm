import { useState, useCallback, useMemo, type FC } from 'react';
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

// SRTM3 elevation data from SE19.zip, downloaded from:
// https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm
// Coverage: lat [-21, -17], lng [-72, -67] — southern Peru / western Bolivia.
// The HGT files are extracted to /sdcard/Download/test-data/hgt on the device.
// Centered on Sajama volcano (~6500m) — dramatic relief makes the effect obvious.
const defaultCenter: Position = [-68.88, -18.11];

const hgtDirPath = '/sdcard/Download/test-data/hgt';

const ElevationOverlay: FC<{
	elevation: number | null;
	point: { lng: number; lat: number } | null;
}> = ({ elevation, point }) => {
	if (elevation == null || point == null) {
		// Show data-source hint when no elevation has been queried yet.
		return (
			<View style={styles.elevationOverlay}>
				<Text style={styles.elevationText}>
					Tap map to query elevation.
				</Text>
				<Text style={styles.elevationText}>Data: SE19.zip from</Text>
				<Text style={styles.elevationText}>
					viewfinderpanoramas.org
				</Text>
				<Text style={styles.elevationText}>
					→ /sdcard/Download/test-data/hgt
				</Text>
			</View>
		);
	}
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

	// useMap() needs the map's nativeNodeHandle, but MapHandleContext is only provided to
	// MapContainer's own children -- use the state-lifting pattern instead.
	const [nativeNodeHandle, setNativeNodeHandle] = useState<number | null>(
		null
	);
	const { getAltitudeAtPosition } = useMap(nativeNodeHandle);

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

	const containerStyle = useMemo(() => ({ height, width }), [height, width]);

	return (
		<View style={containerStyle}>
			<MapContainer
				nativeNodeHandle={nativeNodeHandle}
				setNativeNodeHandle={setNativeNodeHandle}
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
				<LayerHillshading hgtDirPath={hgtDirPath} />
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
