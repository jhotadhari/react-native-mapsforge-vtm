import { useState, type FC } from 'react';
import { View, Button } from 'react-native';
import {
	LayerBitmapTile,
	MapContainer,
	useMap,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { formatActionError, handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlSection,
	ControlRow,
	StatusLine,
} from '../../components/ControlPanel';

const defaultCenter: Position = [13.405, 52.52]; // Berlin [ lng, lat ]

// Two real cities, far enough apart that a pan/zoom between them is obviously visible.
const berlin: Position = [13.405, 52.52];
const tokyo: Position = [139.6917, 35.6895];

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
	lastAction: string;
	positionReadout: string;
	onPanToBerlin: () => void;
	onPanToTokyo: () => void;
	onPanByLng: () => void;
	onJumpToBerlin: () => void;
	onJumpToTokyo: () => void;
	onZoomOut: () => void;
	onZoomTo14: () => void;
	onGetPosition: () => void;
}> = ({
	mapWidth,
	lastAction,
	positionReadout,
	onPanToBerlin,
	onPanToTokyo,
	onPanByLng,
	onJumpToBerlin,
	onJumpToTokyo,
	onZoomOut,
	onZoomTo14,
	onGetPosition,
}) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection>
				<ControlRow>
					<Button
						title={'Pan to Berlin'}
						onPress={onPanToBerlin}
					/>
					<Button
						title={'Pan to Tokyo'}
						onPress={onPanToTokyo}
					/>
					<Button
						title={'Pan by +1°lng'}
						onPress={onPanByLng}
					/>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Jump to Berlin @ z14'}
						onPress={onJumpToBerlin}
					/>
					<Button
						title={'Jump to Tokyo @ z14'}
						onPress={onJumpToTokyo}
					/>
					<Button
						title={'Zoom out'}
						onPress={onZoomOut}
					/>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Zoom to 14'}
						onPress={onZoomTo14}
					/>
					<Button
						title={'Get position'}
						onPress={onGetPosition}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<StatusLine
					label="Last action"
					value={lastAction}
				/>
				<StatusLine
					label="Position"
					value={positionReadout}
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

	const [lastAction, setLastAction] = useState('-');
	const [positionReadout, setPositionReadout] = useState('-');

	const runAction = async (label: string, action: () => Promise<void>) => {
		try {
			await action();
			setLastAction(label);
		} catch (err) {
			setLastAction(`${label} failed: ${formatActionError(err)}`);
		}
	};

	const handlePanToBerlin = () =>
		runAction('panTo(Berlin)', () => map.panTo(berlin));

	const handlePanToTokyo = () =>
		runAction('panTo(Tokyo)', () => map.panTo(tokyo));

	const handlePanByLng = () =>
		runAction('panBy(+1°lng)', () => map.panBy([1, 0]));

	const handleJumpToBerlin = () =>
		runAction('jumpTo(Berlin @ z14)', () =>
			map.jumpTo({ center: berlin, zoomLevel: 14 })
		);

	const handleJumpToTokyo = () =>
		runAction('jumpTo(Tokyo @ z14)', () =>
			map.jumpTo({ center: tokyo, zoomLevel: 14 })
		);

	const handleZoomOut = () => runAction('zoomOut()', () => map.zoomOut());

	const handleZoomTo14 = () =>
		runAction('setZoom(14)', () => map.setZoom(14));

	const handleGetPosition = async () => {
		const label = 'getPosition()';
		try {
			const { center, zoomLevel } = await map.getPosition();
			setLastAction(label);
			setPositionReadout(
				`center: ${JSON.stringify(center)}, zoomLevel: ${zoomLevel}`
			);
		} catch (err) {
			setLastAction(`${label} failed: ${formatActionError(err)}`);
		}
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
				lastAction={lastAction}
				positionReadout={positionReadout}
				onPanToBerlin={handlePanToBerlin}
				onPanToTokyo={handlePanToTokyo}
				onPanByLng={handlePanByLng}
				onJumpToBerlin={handleJumpToBerlin}
				onJumpToTokyo={handleJumpToTokyo}
				onZoomOut={handleZoomOut}
				onZoomTo14={handleZoomTo14}
				onGetPosition={handleGetPosition}
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
	key: 'panZoom',
	label: 'pan / zoom',
	category: 'mapControls',
} as Example;
