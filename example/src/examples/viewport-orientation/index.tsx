import { useState, type Dispatch, type FC, type SetStateAction } from 'react';
import { View, Text, Button, Switch } from 'react-native';
import {
	LayerBitmapTile,
	MapContainer,
	useMap,
	type MapPositionTarget,
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

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

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

type MapApi = ReturnType<typeof useMap>;

const Controls: FC<{
	mapWidth: number;
	lastAction: string;
	setLastAction: Dispatch<SetStateAction<string>>;
	map: MapApi;
	animate: boolean;
	setAnimate: Dispatch<SetStateAction<boolean>>;
}> = ({ mapWidth, lastAction, setLastAction, map, animate, setAnimate }) => {
	// useMap()'s methods reject if nativeNodeHandle isn't ready yet (map not created) -- catch
	// rather than crash, and surface either outcome in the status line below.
	const run = (label: string, target: MapPositionTarget) => () => {
		setLastAction(`${label} ...`);
		const action = animate ? map.easeTo(target) : map.jumpTo(target);
		action
			.then(() => setLastAction(label))
			.catch((err: unknown) => {
				setLastAction(`${label} failed: ${formatActionError(err)}`);
			});
	};

	return (
		<ControlPanel width={mapWidth}>
			<ControlSection>
				<ControlRow>
					<Text style={sharedStyles.text}>Animate</Text>
					<Switch
						value={animate}
						onValueChange={setAnimate}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<ControlRow>
					<Button
						title={'Rotate to 90°'}
						onPress={run('Rotate to 90°', { bearing: 90 })}
					/>
					<Button
						title={'Rotate to 180°'}
						onPress={run('Rotate to 180°', { bearing: 180 })}
					/>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Reset north'}
						onPress={run('Reset north', { bearing: 0 })}
					/>
					<Button
						title={'Reset north + pitch'}
						onPress={run('Reset north + pitch', {
							bearing: 0,
							tilt: 0,
						})}
					/>
				</ControlRow>
				<ControlRow>
					<Button
						title={'Tilt to 45°'}
						onPress={run('Tilt to 45°', { tilt: 45 })}
					/>
					<Button
						title={'Roll to 20°'}
						onPress={run('Roll to 20°', { roll: 20 })}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<StatusLine
					label="Last action"
					value={lastAction}
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
	const [lastAction, setLastAction] = useState('-');
	const [animate, setAnimate] = useState(false);

	// useMap() needs the map's nativeNodeHandle, but MapHandleContext is only provided to
	// MapContainer's own children -- this component renders MapContainer as a sibling of
	// Controls, not a parent of it, so the handle is lifted up via MapContainer's existing
	// nativeNodeHandle/setNativeNodeHandle controlled props instead.
	const [nativeNodeHandle, setNativeNodeHandle] = useState<number | null>(
		null
	);
	const map = useMap(nativeNodeHandle);

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
				setLastAction={setLastAction}
				map={map}
				animate={animate}
				setAnimate={setAnimate}
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
	key: 'viewportOrientation',
	label: 'bearing / tilt / roll',
	category: 'mapControls',
} as Example;
