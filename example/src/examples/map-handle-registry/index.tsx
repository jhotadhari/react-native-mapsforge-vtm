/**
 * External dependencies
 */
import { useMemo, useState, type FC } from 'react';
import { View, Text, Button } from 'react-native';
import {
	createMapHandleRegistry,
	LayerBitmapTile,
	MapContainer,
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

// Create a singleton registry — shared between the React component
// (which wires it) and the non-React Controls (which use it).
const mapRegistry = createMapHandleRegistry();

// Non-React helper — can be called from thunks, services, etc.
const jumpToBerlin = async () => {
	const h = mapRegistry.getHandle();
	if (!h) return 'Map not ready';
	await h.flyTo({ center: [13.405, 52.52], zoomLevel: 14 });
	return 'Flew to Berlin';
};

const jumpToParis = async () => {
	const h = mapRegistry.getHandle();
	if (!h) return 'Map not ready';
	await h.flyTo({ center: [2.349, 48.853], zoomLevel: 14 });
	return 'Flew to Paris';
};

const jumpToTokyo = async () => {
	const h = mapRegistry.getHandle();
	if (!h) return 'Map not ready';
	await h.flyTo({ center: [139.767, 35.682], zoomLevel: 14 });
	return 'Flew to Tokyo';
};

const resetNorth = async () => {
	const h = mapRegistry.getHandle();
	if (!h) return 'Map not ready';
	await h.resetNorthPitch();
	return 'Reset north + pitch';
};

const Controls: FC<{
	width: number;
	status: string;
	isBusy: boolean;
	onAction: (action: () => Promise<string>) => void;
}> = ({ width, status, isBusy, onAction }) => (
	<ControlPanel width={width}>
		<ControlSection title="Non-React map control">
			<ControlRow>
				<Button
					title="Fly to Berlin"
					disabled={isBusy}
					onPress={() => onAction(jumpToBerlin)}
				/>
				<Button
					title="Fly to Paris"
					disabled={isBusy}
					onPress={() => onAction(jumpToParis)}
				/>
				<Button
					title="Fly to Tokyo"
					disabled={isBusy}
					onPress={() => onAction(jumpToTokyo)}
				/>
				<Button
					title="Reset north + pitch"
					disabled={isBusy}
					onPress={() => onAction(resetNorth)}
				/>
			</ControlRow>
		</ControlSection>
		<ControlSection>
			<StatusLine
				label="Status"
				value={status}
				busy={isBusy}
			/>
		</ControlSection>
		<ControlSection>
			<Text style={sharedStyles.text}>
				This example shows createMapHandleRegistry() — a simple
				wire/unwire singleton that lets non-React code (thunks,
				services, background tasks) control the map. The button handlers
				above call standalone async functions that use{' '}
				mapRegistry.getHandle() — no React context needed. The React
				component only wires the handle via useEffect.
			</Text>
		</ControlSection>
	</ControlPanel>
);

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [nativeNodeHandle, setNativeNodeHandle] = useState<number | null>(
		null
	);
	const [isBusy, setIsBusy] = useState(false);
	const [status, setStatus] = useState('Ready');

	// Wire the registry when the map's nativeNodeHandle is available.
	useMemo(() => {
		if (nativeNodeHandle) {
			mapRegistry.wire(nativeNodeHandle);
		}
		return () => {
			mapRegistry.unwire();
		};
	}, [nativeNodeHandle]);

	const handleAction = async (action: () => Promise<string>) => {
		setIsBusy(true);
		try {
			const result = await action();
			setStatus(result);
		} catch (e: unknown) {
			setStatus(`Error: ${e instanceof Error ? e.message : String(e)}`);
		} finally {
			setIsBusy(false);
		}
	};

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
				status={status}
				isBusy={isBusy}
				onAction={handleAction}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					nativeNodeHandle={nativeNodeHandle}
					setNativeNodeHandle={setNativeNodeHandle}
					width={width}
					height={height}
					center={[13.405, 52.52]}
					zoomLevel={14}
					tiltEnabled={true}
					rotationEnabled={true}
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
	key: 'mapHandleRegistry',
	label: 'createMapHandleRegistry — non-React map control',
	category: 'api',
} as Example;
