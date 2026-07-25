/**
 * External dependencies
 */
import { useCallback, useMemo, useRef, useState, type FC } from 'react';
import { ScrollView, StyleSheet, View, Button, Text } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	LayerScalebar,
	MapContainer,
	enrichCoordinatesWithElevation,
	useMap,
	type Bbox,
	type ElevationAPI,
	type GeometryStyle,
	type Position,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
// SRTM3 elevation data from SE19.zip, downloaded from:
// https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm
// Coverage: lat [-21, -17], lng [-72, -67] — southern Peru / western Bolivia
// (Lake Titicaca, Cordillera Occidental, Altiplano).
// The HGT files are extracted to /sdcard/Download/test-data/hgt on the device.
import lineGeoJSON from '../../assets/andes_se19.geo.json';

const hgtDirPath = '/sdcard/Download/test-data/hgt';

// Centered on Sajama volcano (~6500m), Bolivia — well inside the SE19 HGT
// coverage so the hillshading and elevation queries have terrain data.
const defaultCenter: Position = [-68.88, -18.11];

const Controls: FC<{
	width: number;
	enriching: boolean;
	progress: number;
	coordCount: number;
	enrichedCount: number;
	enrichedCoords: number[][] | null;
	onStart: () => void;
}> = ({
	width,
	enriching,
	progress,
	coordCount,
	enrichedCount,
	enrichedCoords,
	onStart,
}) => {
	return (
		<ControlPanel width={width}>
			<ControlSection title="Elevation enrichment">
				<StatusLine
					label="Coordinates"
					value={`${coordCount}`}
				/>
				<StatusLine
					label="Enriched"
					value={`${enrichedCount}`}
				/>
				{enriching ? (
					<StatusLine
						label="Progress"
						value={`${Math.round(progress * 100)}%`}
					/>
				) : null}
			</ControlSection>
			<ControlSection>
				<ControlRow>
					<Button
						title={enriching ? 'Enriching...' : 'Enrich elevations'}
						onPress={onStart}
						disabled={enriching}
					/>
				</ControlRow>
			</ControlSection>
			<ControlSection>
				<Text style={sharedStyles.text}>
					200-coordinate line through the Bolivian Altiplano (lat
					[-20.8,-17.8], lng [-70.3,-67.8]), enriched with SRTM3
					elevation data from SE19.zip.
				</Text>
				<Text style={sharedStyles.text}>
					The southern ~28 % of the line falls outside the HGT
					coverage — those coordinates receive fallbackElevation
					(default 0) instead of real elevation.
				</Text>
				<Text style={sharedStyles.text}>
					The native ElevationReader preloads HGT tiles in windows —
					each window triggers preloads, waits for them to land in the
					LRU cache, then collects elevations.
				</Text>
				<Text style={sharedStyles.text}>
					Download SE19.zip from
					https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm
					and extract the .hgt files to /sdcard/Download/test-data/hgt
					on the device.
				</Text>
			</ControlSection>
			{enrichedCoords && enrichedCoords.length > 0 && (
				<ControlSection title="Enriched coordinates (scrollable)">
					<ScrollView
						style={styles.coordScroll}
						nestedScrollEnabled
					>
						<Text style={styles.coordMono}>
							{enrichedCoords
								.map(
									(c) =>
										`${c[0]?.toFixed(4) ?? '—'}	${c[1]?.toFixed(4) ?? '—'}	→ ${c[2]?.toFixed(0) ?? '—'} m`
								)
								.join('\n')}
						</Text>
					</ScrollView>
				</ControlSection>
			)}
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
	const {
		getAltitudeAtPosition,
		hasDataAtPosition,
		setCacheCapacity,
		isTileCached,
		flyToBounds,
	} = useMap(nativeNodeHandle);

	const [enriching, setEnriching] = useState(false);
	const [progress, setProgress] = useState(0);
	const [enrichedCoords, setEnrichedCoords] = useState<number[][] | null>(
		null
	);
	const [coordCount] = useState(
		(lineGeoJSON as { coordinates: number[][] }).coordinates.length
	);
	const enrichedCountRef = useRef(0);
	const [enrichedCount, setEnrichedCount] = useState(0);

	// Build the ElevationAPI from useMap() bridge functions.
	// getAltitudeAtPosition: catches errors → null (best-effort).
	// hasDataAtPosition: catches errors → false.
	// setCacheCapacity: throws on failure (config command, must not fail silently).
	// isTileCached: catches errors → false.
	const api: ElevationAPI = useMemo(
		() => ({
			getAltitudeAtPosition: async (lng: number, lat: number) => {
				try {
					return await getAltitudeAtPosition(lng, lat);
				} catch {
					return null;
				}
			},
			hasDataAtPosition: async (lng: number, lat: number) => {
				try {
					return await hasDataAtPosition(lng, lat);
				} catch {
					return false;
				}
			},
			setCacheCapacity: async (capacity: number) => {
				await setCacheCapacity(capacity);
			},
			isTileCached: async (lng: number, lat: number) => {
				try {
					return await isTileCached(lng, lat);
				} catch {
					return false;
				}
			},
		}),
		[
			getAltitudeAtPosition,
			hasDataAtPosition,
			setCacheCapacity,
			isTileCached,
		]
	);

	const handleStart = useCallback(async () => {
		setEnriching(true);
		setProgress(0);
		enrichedCountRef.current = 0;
		setEnrichedCount(0);

		try {
			// Deep-clone and add elevation slot (c[2] = 0) to every coordinate.
			const coords = (
				lineGeoJSON as { coordinates: number[][] }
			).coordinates.map((c) => [
				c[0]!,
				c[1]!,
				0,
			]);

			await enrichCoordinatesWithElevation(coords, api, {
				onProgress: (fraction: number) => setProgress(fraction),
				maxCacheCapacity: 50,
			});

			// Count how many coordinates got enriched (c[2] !== 0).
			enrichedCountRef.current = coords.filter((c) => c[2] !== 0).length;
			setEnrichedCount(enrichedCountRef.current);
			setEnrichedCoords(coords);

			// Compute bounds of the line and fly to frame it.
			let west = Infinity;
			let south = Infinity;
			let east = -Infinity;
			let north = -Infinity;
			for (const c of coords) {
				if (c[0]! < west) west = c[0]!;
				if (c[0]! > east) east = c[0]!;
				if (c[1]! < south) south = c[1]!;
				if (c[1]! > north) north = c[1]!;
			}
			const bounds: Bbox = [
				west,
				south,
				east,
				north,
			];
			await flyToBounds(bounds, { paddingPx: 40 });
		} catch (e) {
			console.warn('[elevation-enrichment] enrichment failed:', e);
		} finally {
			setEnriching(false);
		}
	}, [api, flyToBounds]);

	const stylesDynamic = useMemo(
		() =>
			({
				container: { width, height, gap: 16 } as const,
				containerMap: { height, width } as const,
			}) as const,
		[width, height]
	);

	return (
		<View style={stylesDynamic.container}>
			<Controls
				width={width}
				enriching={enriching}
				progress={progress}
				coordCount={coordCount}
				enrichedCount={enrichedCount}
				enrichedCoords={enrichedCoords}
				onStart={handleStart}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					nativeNodeHandle={nativeNodeHandle}
					setNativeNodeHandle={setNativeNodeHandle}
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={5}
					hgtDirPath={hgtDirPath}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />

					{enrichedCoords ? (
						<LayerPath
							coordinates={enrichedCoords}
							style={stylePath}
						/>
					) : null}

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

const stylePath: GeometryStyle = {
	strokeColor: '#ff6600',
	strokeWidth: 2.5,
};

const styles = StyleSheet.create({
	coordScroll: {
		maxHeight: 200,
		backgroundColor: 'rgba(0, 0, 0, 0.3)',
		borderRadius: 4,
		padding: 8,
	},
	coordMono: {
		fontFamily: 'monospace',
		fontSize: 10,
		color: '#aaa',
		lineHeight: 14,
	},
});

export default {
	ExampleComponent,
	key: 'elevationEnrichment',
	label: 'Elevation Enrichment',
	category: 'gestures',
} as Example;
