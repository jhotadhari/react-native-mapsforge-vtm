import { useState, type FC } from 'react';
import { View, Text, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	useMap,
	type Bbox,
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

// Three real, visually-distinct bounding boxes around Berlin, each `[ west, south, east, north ]`
// (GeoJSON bbox order -- same convention as LayerPath's response `bbox`).
const boundsA: Bbox = [
	13.3765,
	52.5145,
	13.3805,
	52.5165,
]; // Brandenburg Gate -- city-block sized
const boundsB: Bbox = [
	13.0884,
	52.3382,
	13.7612,
	52.6755,
]; // Berlin city limits -- whole-city sized
const boundsC: Bbox = [
	11.2649,
	51.3592,
	14.7654,
	53.5587,
]; // Berlin-Brandenburg state -- region sized

// Turns a `Bbox` into a closed-ring `Position[]` so it can be rendered as a `LayerPath` rectangle
// outline -- lets you visually confirm a fit/fly lines the map's viewport edges up with the box.
const bboxToRing = (bounds: Bbox): Position[] => {
	const [
		west,
		south,
		east,
		north,
	] = bounds;
	return [
		[west!, south!],
		[east!, south!],
		[east!, north!],
		[west!, north!],
		[west!, south!],
	];
};

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
	isBusy: boolean;
	status: string;
	onFitBoundsA: () => void;
	onFitBoundsB: () => void;
	onFlyToBoundsA: () => void;
	onPanAwayFromBoundsA: () => void;
	onPanInsideBoundsA: () => void;
	onPanInsidePoint: () => void;
}> = ({
	mapWidth,
	isBusy,
	status,
	onFitBoundsA,
	onFitBoundsB,
	onFlyToBoundsA,
	onPanAwayFromBoundsA,
	onPanInsideBoundsA,
	onPanInsidePoint,
}) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						fitBounds (instant) -- resets bearing/tilt/roll to 0
					</Text>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Fit bounds A (block)'}
						disabled={isBusy}
						onPress={onFitBoundsA}
					/>
					<Button
						title={'Fit bounds B (city)'}
						disabled={isBusy}
						onPress={onFitBoundsB}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						flyToBounds (1200ms, expo_out) -- also resets
						bearing/tilt/roll
					</Text>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Fly to bounds A (block)'}
						disabled={isBusy}
						onPress={onFlyToBoundsA}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>
						panInsideBounds / panInside (approx. -- simple center
						clamp, not a true minimal-pan-to-reveal)
					</Text>
				</ControlRow>
				<ControlRow>
					<Button
						title={'1. Pan away from bounds A'}
						disabled={isBusy}
						onPress={onPanAwayFromBoundsA}
					/>
					<Button
						title={'2. Pan inside bounds A (approx.)'}
						disabled={isBusy}
						onPress={onPanInsideBoundsA}
					/>
					<Button
						title={'Pan inside point (approx.)'}
						disabled={isBusy}
						onPress={onPanInsidePoint}
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

	const [isBusy, setIsBusy] = useState(false);
	const [status, setStatus] = useState('-');

	const run = async (label: string, action: () => Promise<void>) => {
		setIsBusy(true);
		const startedAt = Date.now();
		try {
			await action();
			const elapsedMs = Date.now() - startedAt;
			setStatus(`${label} resolved after ${elapsedMs}ms`);
		} catch (err) {
			setStatus(`${label} failed: ${formatActionError(err)}`);
		} finally {
			setIsBusy(false);
		}
	};

	const handleFitBoundsA = () =>
		run('fitBounds(A)', () => map.fitBounds(boundsA, { paddingPx: 32 }));

	const handleFitBoundsB = () =>
		run('fitBounds(B)', () => map.fitBounds(boundsB, { paddingPx: 32 }));

	const handleFlyToBoundsA = () =>
		run('flyToBounds(A)', () =>
			map.flyToBounds(boundsA, { paddingPx: 32 })
		);

	// Pans well outside all three boxes first, so "pan inside bounds A" afterwards has an
	// obvious, visible effect.
	const handlePanAwayFromBoundsA = () =>
		run('panTo(away from A)', () => map.panTo([0, 0]));

	const handlePanInsideBoundsA = () =>
		run('panInsideBounds(A) (approx.)', () => map.panInsideBounds(boundsA));

	const handlePanInsidePoint = () =>
		run('panInside(point) (approx.)', () =>
			map.panInside([boundsA[0]!, boundsA[1]!])
		);

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
				isBusy={isBusy}
				status={status}
				onFitBoundsA={handleFitBoundsA}
				onFitBoundsB={handleFitBoundsB}
				onFlyToBoundsA={handleFlyToBoundsA}
				onPanAwayFromBoundsA={handlePanAwayFromBoundsA}
				onPanInsideBoundsA={handlePanInsideBoundsA}
				onPanInsidePoint={handlePanInsidePoint}
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

					{/* Outline each hardcoded bbox so a fit/fly can be visually checked against it. */}
					<LayerPath
						coordinates={bboxToRing(boundsA)}
						style={{ strokeColor: '#ff0000', strokeWidth: 3 }}
					/>
					<LayerPath
						coordinates={bboxToRing(boundsB)}
						style={{ strokeColor: '#00ff00', strokeWidth: 3 }}
					/>
					<LayerPath
						coordinates={bboxToRing(boundsC)}
						style={{ strokeColor: '#0000ff', strokeWidth: 3 }}
					/>
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
	key: 'fitBounds',
	label: 'fitBounds / panInsideBounds',
	category: 'mapControls',
} as Example;
