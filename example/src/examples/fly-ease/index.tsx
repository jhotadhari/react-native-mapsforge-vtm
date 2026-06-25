import { useState, type FC } from 'react';
import { View, Text, Button } from 'react-native';
import {
	LayerBitmapTile,
	MapContainer,
	useMap,
	type EasingType,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import {
	formatActionError,
	handleMapEvent,
	sharedStyles,
} from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlSection,
	ControlRow,
	StatusLine,
} from '../../components/ControlPanel';

const defaultCenter: Position = [13.405, 52.52]; // Berlin [ lng, lat ]

// Three real cities, far enough apart that flyTo/easeTo between them is obviously visible,
// each with a different target zoom level.
const destinations: {
	key: string;
	label: string;
	center: Position;
	zoomLevel: number;
}[] = [
	{ key: 'berlin', label: 'Berlin', center: [13.405, 52.52], zoomLevel: 12 },
	{
		key: 'tokyo',
		label: 'Tokyo',
		center: [139.6917, 35.6895],
		zoomLevel: 15,
	},
	{
		key: 'capeTown',
		label: 'Cape Town',
		center: [18.4241, -33.9249],
		zoomLevel: 17,
	},
];

const easingsToCompare: EasingType[] = [
	'linear',
	'sine_inout',
	'expo_out',
	'quint_inout',
];

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

const Controls: FC<{
	mapWidth: number;
	isAnimating: boolean;
	status: string;
	onFlyTo: (destKey: string) => void;
	onEaseTo: (destKey: string) => void;
	onCompareEasing: (easing: EasingType) => void;
}> = ({
	mapWidth,
	isAnimating,
	status,
	onFlyTo,
	onEaseTo,
	onCompareEasing,
}) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						flyTo (1200ms, expo_out)
					</Text>
				</ControlRow>
				<ControlRow>
					{destinations.map((dest) => (
						<Button
							key={dest.key}
							title={dest.label}
							disabled={isAnimating}
							onPress={() => onFlyTo(dest.key)}
						/>
					))}
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						easeTo (300ms, sine_inout)
					</Text>
				</ControlRow>
				<ControlRow>
					{destinations.map((dest) => (
						<Button
							key={dest.key}
							title={dest.label}
							disabled={isAnimating}
							onPress={() => onEaseTo(dest.key)}
						/>
					))}
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						Compare easing -- flyTo(Tokyo) with:
					</Text>
				</ControlRow>
				<ControlRow>
					{easingsToCompare.map((easing) => (
						<Button
							key={easing}
							title={easing}
							disabled={isAnimating}
							onPress={() => onCompareEasing(easing)}
						/>
					))}
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<StatusLine
					label="Status"
					value={status}
					busy={isAnimating}
					busyValue="animating..."
				/>
			</ControlSection>
		</ControlPanel>
	);
};

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	// useMap() needs the map's nativeNodeHandle, but MapHandleContext is only provided to
	// MapContainer's own children -- this component renders MapContainer as a sibling of
	// Controls, not a parent of it, so the handle is lifted up via MapContainer's existing
	// nativeNodeHandle/setNativeNodeHandle controlled props instead.
	const [nativeNodeHandle, setNativeNodeHandle] = useState<number | null>(
		null
	);
	const map = useMap(nativeNodeHandle);

	const [isAnimating, setIsAnimating] = useState(false);
	const [status, setStatus] = useState('-');

	const findDestination = (destKey: string) =>
		destinations.find((dest) => dest.key === destKey)!;

	const runAnimation = async (label: string, action: () => Promise<void>) => {
		setIsAnimating(true);
		const startedAt = Date.now();
		try {
			await action();
			const elapsedMs = Date.now() - startedAt;
			setStatus(`${label} resolved after ${elapsedMs}ms`);
		} catch (err) {
			setStatus(`${label} failed: ${formatActionError(err)}`);
		} finally {
			setIsAnimating(false);
		}
	};

	const handleFlyTo = (destKey: string) => {
		const dest = findDestination(destKey);
		return runAnimation(`flyTo(${dest.label})`, () =>
			map.flyTo({ center: dest.center, zoomLevel: dest.zoomLevel })
		);
	};

	const handleEaseTo = (destKey: string) => {
		const dest = findDestination(destKey);
		return runAnimation(`easeTo(${dest.label})`, () =>
			map.easeTo({ center: dest.center, zoomLevel: dest.zoomLevel })
		);
	};

	const handleCompareEasing = (easing: EasingType) => {
		const dest = findDestination('tokyo');
		return runAnimation(`flyTo(Tokyo, easing=${easing})`, () =>
			map.flyTo(
				{ center: dest.center, zoomLevel: dest.zoomLevel },
				{ easing }
			)
		);
	};

	return (
		<View
			style={{
				width,
				height,
				gap: 16,
			}}
		>
			<Controls
				mapWidth={width}
				isAnimating={isAnimating}
				status={status}
				onFlyTo={handleFlyTo}
				onEaseTo={handleEaseTo}
				onCompareEasing={handleCompareEasing}
			/>

			<View
				style={{
					height,
					width,
				}}
			>
				<MapContainer
					nativeNodeHandle={nativeNodeHandle}
					setNativeNodeHandle={setNativeNodeHandle}
					width={width}
					height={height}
					center={defaultCenter}
					responseInclude={responseInclude}
					zoomLevel={8}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
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

export default {
	ExampleComponent,
	key: 'flyEase',
	label: 'flyTo / easeTo',
	category: 'mapControls',
} as Example;
