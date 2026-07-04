import { useMemo, useState, type FC } from 'react';
import { View, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	MapContainer,
	type Bbox,
	type LayerPathResponse,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import coastlines from '../../assets/coastlines.geo.json';

const defaultCenter: Position = [10, 20]; // [ lng, lat ] -- frames several continents at once

// All 475 features in coastlines.geo.json are already flattened to LineString (no
// MultiLineString left to handle) -- so this is just one LayerPath per feature.
const coastlineCoordinates: Position[][] = coastlines.features.map(
	(feature) => feature.geometry.coordinates as Position[]
);

const strokeColors: `#${string}`[] = [
	'#00aaff',
	'#ff8800',
	'#ffffff',
];

const simplificationTolerances: number[] = [
	0,
	0.001,
	0.01,
];

const Controls: FC<{
	mapWidth: number;
	strokeColor: string;
	simplificationTolerance: number;
	firstLineBbox: Bbox | undefined;
	onCycleColor: () => void;
	onCycleSimplification: () => void;
}> = ({
	mapWidth,
	strokeColor,
	simplificationTolerance,
	firstLineBbox,
	onCycleColor,
	onCycleSimplification,
}) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection title={'Coastline style (475 LayerPath features)'}>
				<ControlRow>
					<Button
						title={`Cycle stroke color (${strokeColor})`}
						onPress={onCycleColor}
					/>
				</ControlRow>
				<ControlRow>
					<Button
						title={`Cycle simplification (${simplificationTolerance})`}
						onPress={onCycleSimplification}
					/>
				</ControlRow>
			</ControlSection>

			<ControlSection>
				<StatusLine
					label={'First line bbox'}
					value={
						firstLineBbox
							? firstLineBbox.map((n) => n!.toFixed(2)).join(', ')
							: '-'
					}
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

	const [colorIndex, setColorIndex] = useState(0);
	const [simplificationIndex, setSimplificationIndex] = useState(0);
	const [firstLineBbox, setFirstLineBbox] = useState<Bbox | undefined>(
		undefined
	);

	const strokeColor = strokeColors[colorIndex % strokeColors.length]!;
	const simplificationTolerance =
		simplificationTolerances[
			simplificationIndex % simplificationTolerances.length
		]!;

	const style = useMemo(() => ({ strokeColor }), [strokeColor]);

	const handleFirstLineResponse = (response: LayerPathResponse) => {
		setFirstLineBbox(response.bbox);
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
				strokeColor={strokeColor}
				simplificationTolerance={simplificationTolerance}
				firstLineBbox={firstLineBbox}
				onCycleColor={() => setColorIndex((i) => i + 1)}
				onCycleSimplification={() =>
					setSimplificationIndex((i) => i + 1)
				}
			/>

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
					zoomLevel={2}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{coastlineCoordinates.map((coordinates, idx) =>
						idx === 0 ? (
							<LayerPath
								key={idx}
								coordinates={coordinates}
								style={style}
								simplificationTolerance={
									simplificationTolerance
								}
								onCreate={handleFirstLineResponse}
								onChange={handleFirstLineResponse}
							/>
						) : (
							<LayerPath
								key={idx}
								coordinates={coordinates}
								style={style}
								simplificationTolerance={
									simplificationTolerance
								}
							/>
						)
					)}
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
	key: 'coastlines',
	label: 'Coastlines (real GeoJSON)',
	category: 'layers',
} as Example;
