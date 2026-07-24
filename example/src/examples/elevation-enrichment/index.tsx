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
import { View, Button, Text } from 'react-native';
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
import lineGeoJSON from '../../assets/longLineAndes.geo.json';

const hgtDirPath = '/sdcard/Download/test-data/hgt';

const defaultCenter: Position = [-70, -35]; // Mid-Andes

const Controls: FC<{
	width: number;
	enriching: boolean;
	progress: number;
	coordCount: number;
	enrichedCount: number;
	onStart: () => void;
}> = ({ width, enriching, progress, coordCount, enrichedCount, onStart }) => {
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
					Loads a ~3000-coordinate GeoJSON line along the Andes,
					enriches it with SRTM elevation data via{' '}
					enrichCoordinatesWithElevation(), and renders the result as
					a path. The native ElevationReader preloads HGT tiles in
					windows — each window triggers preloads, waits for them to
					land in the LRU cache, then collects elevations.
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

	// Auto-start enrichment once the map view is ready (nativeNodeHandle
	// transitions from null to a number). Without this guard the effect
	// fires on the first render while nativeNodeHandle is still null,
	// handleStart captures the stale flyToBounds/getAltitudeAtPosition from
	// that render, and enrichment silently produces all-zero elevations
	// followed by a "nativeNodeHandle is not set yet" error.
	const startedRef = useRef(false);
	useEffect(() => {
		if (!startedRef.current && nativeNodeHandle) {
			startedRef.current = true;
			handleStart();
		}
	}, [handleStart, nativeNodeHandle]);

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				width={width}
				enriching={enriching}
				progress={progress}
				coordCount={coordCount}
				enrichedCount={enrichedCount}
				onStart={handleStart}
			/>

			<View style={{ height, width }}>
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

export default {
	ExampleComponent,
	key: 'elevationEnrichment',
	label: 'Elevation Enrichment',
	category: 'gestures',
} as Example;
