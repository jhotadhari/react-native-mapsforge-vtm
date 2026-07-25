/**
 * External dependencies
 */
import {
	useCallback,
	useEffect,
	useMemo,
	useRef,
	useState,
	type FC,
} from 'react';
import {
	PermissionsAndroid,
	Platform,
	ScrollView,
	StyleSheet,
	View,
	Text,
	Button,
} from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	LayerScalebar,
	MapContainer,
	Marker,
	useMap,
	type GnssFilterNativeProps,
	type GnssPosition,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlSection,
	ControlRow,
	StatusLine,
} from '../../components/ControlPanel';

const gnssFilter: GnssFilterNativeProps = {
	minDistanceMeters: 5,
	minTimeSec: 2,
	minAccuracyMeters: 20,
	provider: 'satellite',
	altitudeSource: 'dem-preferred',
};

const gnssDotSymbol = {
	text: '●',
	fillColor: '#00aaff',
	textColor: '#ffffff',
	textSize: 12,
} as const;

const GnssOverlay: FC<{
	position: Position | null;
}> = ({ position }) => {
	const map = useMap();
	const prevRef = useRef<Position | null>(null);

	useEffect(() => {
		if (
			position &&
			(prevRef.current == null ||
				prevRef.current[0] !== position[0] ||
				prevRef.current[1] !== position[1])
		) {
			map.flyTo({ center: position });
			prevRef.current = position;
		}
	}, [position, map]);

	if (!position) return null;

	return (
		<LayerMarker symbol={gnssDotSymbol}>
			<Marker position={position} />
		</LayerMarker>
	);
};

const Controls: FC<{
	width: number;
	recording: boolean;
	positions: GnssPosition[];
	permError: string | null;
	onToggle: () => void;
}> = ({ width, recording, positions, permError, onToggle }) => {
	const latest = positions[positions.length - 1];

	return (
		<ControlPanel width={width}>
			<ControlSection title="GNSS Track Recording">
				<ControlRow>
					<Button
						title={recording ? 'Stop Recording' : 'Start Recording'}
						onPress={onToggle}
					/>
				</ControlRow>
				{permError && (
					<ControlRow>
						<Text style={[sharedStyles.text, { color: '#ff6666' }]}>
							{permError}
						</Text>
					</ControlRow>
				)}
				<StatusLine
					label="Positions"
					value={`${positions.length}`}
				/>
				{latest && (
					<>
						<StatusLine
							label="Latest"
							value={`${latest.lat.toFixed(5)}°, ${latest.lng.toFixed(5)}°`}
						/>
						<StatusLine
							label="Altitude"
							value={
								latest.altitude != null
									? `${latest.altitude.toFixed(1)} m`
									: '(null)'
							}
						/>
						<StatusLine
							label="Accuracy"
							value={`${latest.accuracy.toFixed(1)} m`}
						/>
						<StatusLine
							label="Speed"
							value={`${latest.speed.toFixed(1)} m/s`}
						/>
						<StatusLine
							label="Bearing"
							value={
								latest.bearing >= 0
									? `${latest.bearing.toFixed(1)}°`
									: '(unavailable)'
							}
						/>
					</>
				)}
			</ControlSection>
			{positions.length > 0 && (
				<ControlSection title="Recorded positions (scrollable)">
					<ScrollView
						style={styles.posScroll}
						nestedScrollEnabled
					>
						<Text style={styles.posMono}>
							{positions
								.filter((p) => p != null)
								.map(
									(p) =>
										`${p.lat.toFixed(6)}	${p.lng.toFixed(6)}	alt ${p.altitude != null ? p.altitude.toFixed(0) : '—'} m	acc ${p.accuracy.toFixed(1)} m`
								)
								.join('\n')}
						</Text>
					</ScrollView>
				</ControlSection>
			)}
			<ControlSection>
				<Text style={sharedStyles.text}>
					Filter: provider={gnssFilter.provider}, altitudeSource=
					{gnssFilter.altitudeSource}, minDist=
					{gnssFilter.minDistanceMeters}m, minTime=
					{gnssFilter.minTimeSec}s, minAcc=
					{gnssFilter.minAccuracyMeters}m.
				</Text>
			</ControlSection>
			<ControlSection>
				<Text style={sharedStyles.text}>
					Requires ACCESS_FINE_LOCATION permission. On a real device
					with GPS, positions arrive every ~{gnssFilter.minTimeSec}s
					(subject to minDistance and minAccuracy guards). Altitude is
					resolved via the configured altitudeSource (dem-preferred:
					SRTM DEM first, GNSS fallback).
				</Text>
			</ControlSection>
		</ControlPanel>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [recording, setRecording] = useState(false);
	const [positions, setPositions] = useState<GnssPosition[]>([]);

	const handleGnssPosition = useCallback(
		(e: { nativeEvent: GnssPosition }) => {
			const pos = e.nativeEvent;
			setPositions((prev) => [...prev.slice(-99), pos]);
		},
		[]
	);

	const permRequestedRef = useRef(false);
	const [permError, setPermError] = useState<string | null>(null);

	const handleToggle = useCallback(async () => {
		// Reset any previous permission error
		setPermError(null);

		setRecording((prev) => {
			if (prev) {
				// Stopping — clear immediately
				setPositions([]);
				return false;
			}
			// Not yet recording — will start after permission check
			return prev;
		});

		// Request runtime permission on Android 6+
		if (Platform.OS === 'android' && !permRequestedRef.current) {
			try {
				const granted = await PermissionsAndroid.request(
					PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
					{
						title: 'Location Permission',
						message:
							'GNSS track recording needs access to your device location.',
						buttonPositive: 'OK',
					}
				);
				permRequestedRef.current = true;
				if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
					setPermError(
						'Location permission denied. Grant it in system settings and try again.'
					);
					return;
				}
			} catch (err: any) {
				permRequestedRef.current = true;
				setPermError(
					`Permission request failed: ${err?.message ?? String(err)}`
				);
				return;
			}
		}

		// Start recording
		setRecording(true);
	}, []);

	const latestPosition = positions[positions.length - 1] ?? null;
	const latestCoord: Position | null = latestPosition
		? [latestPosition.lng, latestPosition.lat]
		: null;

	const stylesDynamic = useMemo(
		() => ({
			container: { width, height, gap: 16 } as const,
			containerMap: { height, width } as const,
		}),
		[width, height]
	);

	return (
		<View style={stylesDynamic.container}>
			<Controls
				width={width}
				recording={recording}
				positions={positions}
				permError={permError}
				onToggle={handleToggle}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={[13.405, 52.52]}
					zoomLevel={14}
					gnssFilter={recording ? gnssFilter : null}
					onGnssPosition={recording ? handleGnssPosition : undefined}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
					<GnssOverlay position={latestCoord} />
					<LayerScalebar />
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

const styles = StyleSheet.create({
	posScroll: {
		maxHeight: 200,
		backgroundColor: 'rgba(0, 0, 0, 0.3)',
		borderRadius: 4,
		padding: 8,
	},
	posMono: {
		fontFamily: 'monospace',
		fontSize: 10,
		color: '#aaa',
		lineHeight: 14,
	},
});

export default {
	ExampleComponent,
	key: 'gnssTrackRecording',
	label: 'GNSS Track Recording',
	category: 'api',
} as Example;
