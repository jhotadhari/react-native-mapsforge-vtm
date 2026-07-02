/**
 * Multi-Map example: two MapContainers side-by-side (stacked vertically) with
 * an optional position-synchronization toggle.
 *
 * ## ⚠ Known limitation
 *
 * **Rendering does NOT work.**  vtm 0.28.0 — the underlying rendering engine —
 * stores OpenGL VBO handles (`mQuadIndicesID`, `mQuadVerticesID`) and a buffer
 * pool (`mBufferPool`) in **static fields** inside `MapRenderer.java`.  When a
 * second `MapView` initialises, it overwrites these static fields with VBOs
 * from its own EGL context, leaving the first map with invalid buffer handles
 * → black / corrupted tiles on one map, blank surface on the other.
 *
 * See {@file docs/advanced/known-issues.md} for the full investigation and
 * resolution path.  Until vtm is patched, this example is **for reference
 * only** — it demonstrates the correct sync-source pattern, state lifting
 * (`nativeNodeHandle` / `setNativeNodeHandle`), and `useMap()` multi-map
 * control, all of which will Just Work once the upstream bug is fixed.
 *
 * ## Sync model: source → mirror
 *
 * When sync is enabled, one map is the *sync source* — its position drives
 * the other map (the *mirror*) via `useMap().jumpTo()` on every `onMapUpdate`
 * event.  The mirror's own `onMapUpdate` events are ignored — this prevents
 * the infinite feedback loop that a naive bidirectional sync would create.
 *
 * This pattern works entirely with the existing public API — no native changes
 * are needed.  Applications that need "whichever map was touched last becomes
 * the source" behaviour can implement it by listening to map-level `onTap`
 * events (or wrapping each MapContainer in a Pressable) and updating the sync
 * source accordingly.
 *
 * ## What is synced
 *
 * | Field       | Synced | Notes |
 * |-------------|--------|-------|
 * | center      | yes    | The map's geographic centre (lng, lat) |
 * | zoomLevel   | yes    | Integer zoom level |
 * | bearing     | yes    | Map rotation in degrees |
 * | tilt        | yes    | Map pitch/tilt in degrees |
 * | roll        | no     | Rarely used; sync if needed |
 *
 * ## Layer independence
 *
 * Each MapContainer has its own layer tree — sync only affects the map
 * position (camera).  This means you can compare two completely different
 * data sources (e.g. OpenStreetMap tiles vs a mapsforge vector map, or the
 * same area with different overlay configurations) while keeping them locked
 * to the same viewport.
 */

import {
	useCallback,
	useState,
	type Dispatch,
	type FC,
	type SetStateAction,
} from 'react';
import {
	Button,
	StyleSheet,
	Text,
	View,
	type NativeSyntheticEvent,
} from 'react-native';
import {
	LayerBitmapTile,
	LayerScalebar,
	MapContainer,
	useMap,
	type MapEventResponse,
	type MapPositionTarget,
	type Position,
} from 'react-native-mapsforge-vtm';
import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import {
	ControlPanel,
	ControlSection,
	ControlRow,
	StatusLine,
} from '../../components/ControlPanel';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Starting position for both maps — Berlin, a recognisable urban area. */
const defaultCenter: Position = [13.405, 52.52]; // [ lng, lat ]

/**
 * Fields that must appear in `onMapUpdate` events for sync to work.
 * Level 2 = included in lifeCycle AND onMapEvent responses.
 */
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type MapId = 'A' | 'B';

/**
 * Extract position fields from an `onMapUpdate` event into a
 * `MapPositionTarget` suitable for `jumpTo()`.
 *
 * Only includes fields that were actually present in the event
 * (controlled by `responseInclude`).  Passing `undefined` for an
 * optional field tells `jumpTo` / `animateTo` to leave it unchanged.
 */
const eventToTarget = (
	e: NativeSyntheticEvent<Readonly<MapEventResponse>>
): MapPositionTarget => {
	const { center, zoomLevel, bearing, tilt } = e.nativeEvent;
	return {
		center: (center as unknown as Position | undefined) ?? undefined,
		zoomLevel: zoomLevel ?? undefined,
		bearing: bearing ?? undefined,
		tilt: tilt ?? undefined,
	};
};

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

const Controls: FC<{
	mapWidth: number;
	syncEnabled: boolean;
	syncSource: MapId;
	onToggleSync: () => void;
	onSetSyncSource: (id: MapId) => void;
	statusA: string;
	statusB: string;
}> = ({
	mapWidth,
	syncEnabled,
	syncSource,
	onToggleSync,
	onSetSyncSource,
	statusA,
	statusB,
}) => {
	return (
		<ControlPanel width={mapWidth}>
			<ControlSection title="Synchronization">
				<ControlRow>
					<Button
						title={syncEnabled ? 'Sync: ON' : 'Sync: OFF'}
						onPress={onToggleSync}
					/>
				</ControlRow>
				<StatusLine
					label="Direction"
					value={
						syncEnabled
							? `${syncSource} → ${syncSource === 'A' ? 'B' : 'A'}`
							: 'independent'
					}
				/>
			</ControlSection>

			<ControlSection title="Sync source">
				<ControlRow>
					<Button
						title={`Map A${syncSource === 'A' ? ' ◀' : ''}`}
						onPress={() => onSetSyncSource('A')}
					/>
					<Button
						title={`Map B${syncSource === 'B' ? ' ◀' : ''}`}
						onPress={() => onSetSyncSource('B')}
					/>
				</ControlRow>
				<Text style={sharedStyles.text}>
					The sync source map drives the other. Only the source's
					movements are mirrored — the other map never syncs back,
					preventing feedback loops.
				</Text>
			</ControlSection>

			<ControlSection title="Status">
				<StatusLine
					label="Map A"
					value={statusA}
				/>
				<StatusLine
					label="Map B"
					value={statusB}
				/>
			</ControlSection>
		</ControlPanel>
	);
};

// ---------------------------------------------------------------------------
// Per-map wrapper
// ---------------------------------------------------------------------------

/**
 * Props for a single map panel in the multi-map layout.
 */
type MapPanelProps = {
	width: number;
	height: number;
	nativeNodeHandle: number | null;
	setNativeNodeHandle: Dispatch<SetStateAction<number | null>>;
	onMapUpdate: (e: NativeSyntheticEvent<Readonly<MapEventResponse>>) => void;
	isSyncSource: boolean;
	syncEnabled: boolean;
	label: string;
	center?: Position;
	zoomLevel?: number;
};

const MapPanel: FC<MapPanelProps> = ({
	width,
	height,
	nativeNodeHandle,
	setNativeNodeHandle,
	onMapUpdate,
	isSyncSource,
	syncEnabled,
	label,
	center = defaultCenter,
	zoomLevel = 8,
}) => {
	const borderColor = syncEnabled
		? isSyncSource
			? '#4A90D9' // blue — sync source
			: '#666666' // gray — mirror
		: '#333333'; // dark — independent

	return (
		<View
			style={[
				styles.mapPanel,
				{
					width,
					height,
					borderColor,
				},
			]}
		>
			{/* Sync-source indicator badge */}
			<View
				style={[
					styles.mapLabel,
					{ backgroundColor: borderColor },
				]}
			>
				<Text style={styles.mapLabelText}>
					{label}
					{syncEnabled && isSyncSource ? ' (Source)' : ''}
				</Text>
			</View>

			<MapContainer
				nativeNodeHandle={nativeNodeHandle}
				setNativeNodeHandle={setNativeNodeHandle}
				width={width}
				height={height}
				center={center}
				responseInclude={responseInclude}
				zoomLevel={zoomLevel}
				onMapUpdate={onMapUpdate}
				onPause={handleMapEvent.onPause}
				onResume={handleMapEvent.onResume}
				onError={handleMapEvent.onError}
			>
				<LayerBitmapTile />
				<LayerScalebar />
			</MapContainer>
		</View>
	);
};

// ---------------------------------------------------------------------------
// Example entry point
// ---------------------------------------------------------------------------

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	// ---- state ---------------------------------------------------------
	const [syncEnabled, setSyncEnabled] = useState(true);
	const [syncSource, setSyncSource] = useState<MapId>('A');

	// Status lines shown in the control panel — updated from the sync
	// handlers so the user can see what each map is doing.
	const [statusA, setStatusA] = useState('-');
	const [statusB, setStatusB] = useState('-');

	// ---- native handles (lifted up from MapContainer) ------------------
	const [
		handleA,
		setHandleA,
	] = useState<number | null>(null);
	const [
		handleB,
		setHandleB,
	] = useState<number | null>(null);

	// ---- imperative map control (useMap with handle override) ----------
	const mapA = useMap(handleA);
	const mapB = useMap(handleB);

	// ---- sync handlers -------------------------------------------------
	/**
	 * Mirror Map A's position onto Map B.
	 * Only called when Map A is the sync source.
	 */
	const mirrorAtoB = useCallback(
		(e: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
			if (!syncEnabled || syncSource !== 'A') {
				return;
			}
			const target = eventToTarget(e);
			mapB.jumpTo(target).catch(() => {
				// Map B may not be ready yet — silently ignore.
			});
			// Update status display
			const c = target.center;
			if (c && c.length >= 2) {
				setStatusA(
					`A → B | lng=${c[0]!.toFixed(4)} lat=${c[1]!.toFixed(4)} z=${target.zoomLevel ?? '?'}`
				);
			}
		},
		[
			syncEnabled,
			syncSource,
			mapB,
		]
	);

	/**
	 * Mirror Map B's position onto Map A.
	 * Only called when Map B is the sync source.
	 */
	const mirrorBtoA = useCallback(
		(e: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
			if (!syncEnabled || syncSource !== 'B') {
				return;
			}
			const target = eventToTarget(e);
			mapA.jumpTo(target).catch(() => {
				// Map A may not be ready yet — silently ignore.
			});
			// Update status display
			const c = target.center;
			if (c && c.length >= 2) {
				setStatusB(
					`B → A | lng=${c[0]!.toFixed(4)} lat=${c[1]!.toFixed(4)} z=${target.zoomLevel ?? '?'}`
				);
			}
		},
		[
			syncEnabled,
			syncSource,
			mapA,
		]
	);

	// ---- layout --------------------------------------------------------
	// Split the available height evenly with a small gap between maps.
	const mapGap = 6;
	const mapHeight = (height - mapGap) / 2;

	return (
		<View
			style={{
				width,
				height,
				gap: mapGap,
			}}
		>
			<Controls
				mapWidth={width}
				syncEnabled={syncEnabled}
				syncSource={syncSource}
				onToggleSync={() => setSyncEnabled((v) => !v)}
				onSetSyncSource={setSyncSource}
				statusA={statusA}
				statusB={statusB}
			/>

			{/* Map A */}
			<MapPanel
				width={width}
				height={mapHeight}
				nativeNodeHandle={handleA}
				setNativeNodeHandle={setHandleA}
				onMapUpdate={mirrorAtoB}
				isSyncSource={syncSource === 'A'}
				syncEnabled={syncEnabled}
				label="Map A"
				center={defaultCenter}
				zoomLevel={8}
			/>

			{/* Map B */}
			<MapPanel
				width={width}
				height={mapHeight}
				nativeNodeHandle={handleB}
				setNativeNodeHandle={setHandleB}
				onMapUpdate={mirrorBtoA}
				isSyncSource={syncSource === 'B'}
				syncEnabled={syncEnabled}
				label="Map B"
				center={defaultCenter}
				zoomLevel={8}
			/>

			{/* ⚠ Persistent notice — vtm 0.28.0 limitation.  Remove when
			    the upstream MapRenderer static-VBO bug is fixed. */}
			<LimitationNotice />
		</View>
	);
};

// ---------------------------------------------------------------------------
// Limitation notice
// ---------------------------------------------------------------------------

/**
 * Persistent overlay warning that this example doesn't render correctly due
 * to a vtm 0.28.0 bug.  See {@file docs/advanced/known-issues.md}.
 */
const LimitationNotice: FC = () => (
	<View
		style={styles.limitationOverlay}
		pointerEvents="none"
	>
		<Text style={styles.limitationIcon}>⚠</Text>
		<Text style={styles.limitationTitle}>Rendering does not work</Text>
		<Text style={styles.limitationBody}>
			vtm 0.28.0 stores OpenGL VBO handles in static fields — two
			simultaneous MapViews corrupt each other's rendering. This example
			demonstrates the correct sync-source pattern, state lifting, and
			useMap() API; it will work once the upstream vtm bug is fixed.
		</Text>
		<Text style={styles.limitationLink}>
			See docs/advanced/known-issues.md for details.
		</Text>
	</View>
);

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
	mapPanel: {
		borderWidth: 2,
		borderRadius: 4,
		overflow: 'hidden',
	},
	mapLabel: {
		position: 'absolute',
		top: 8,
		right: 8,
		zIndex: 8,
		paddingVertical: 4,
		paddingHorizontal: 8,
		borderRadius: 4,
	},
	mapLabelText: {
		color: '#fff',
		fontSize: 12,
		fontWeight: 'bold',
	},
	limitationOverlay: {
		position: 'absolute',
		bottom: 0,
		left: 0,
		right: 0,
		zIndex: 20,
		backgroundColor: 'rgba(180, 50, 20, 0.92)',
		paddingVertical: 14,
		paddingHorizontal: 16,
		alignItems: 'center',
		gap: 4,
	},
	limitationIcon: {
		fontSize: 22,
	},
	limitationTitle: {
		color: '#fff',
		fontSize: 15,
		fontWeight: 'bold',
	},
	limitationBody: {
		color: '#fdd',
		fontSize: 12,
		textAlign: 'center',
		lineHeight: 16,
	},
	limitationLink: {
		color: '#fbb',
		fontSize: 11,
		marginTop: 4,
		fontStyle: 'italic',
	},
});

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

export default {
	ExampleComponent,
	key: 'multiMap',
	label: 'Multi-Map Sync',
	category: 'mapControls',
} as Example;
