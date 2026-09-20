/**
 * External dependencies
 */
import {
	useEffect,
	useRef,
	useState,
	useMemo,
	useCallback,
	type MutableRefObject,
} from 'react';
import { findNodeHandle, useWindowDimensions, View } from 'react-native';
import { isBoolean } from 'lodash-es';

/**
 * Internal dependencies
 */
import NativeMapContainer, {
	type TriggerParams,
} from '../NativeModules/NativeMapContainer';
import MapsforgeVtmView, {
	type MapContainerProps,
} from '../NativeViews/MapsforgeVtmViewNativeComponent';
import MapHandleContext, {
	type MapHandleContextValue,
} from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';
import { drainQueue } from '../compose/MarkerBatchQueue';
import { drainPathQueue } from '../compose/PathBatchQueue';
import { drainShapeQueue } from '../compose/ShapeBatchQueue';
import { SceneSync } from '../scene/SceneSync';

const moduleDefaults = NativeMapContainer.getConstants();

const useDefaultWidth = (propsWidth?: number | null) => {
	const { width } = useWindowDimensions();
	// Explicit null signals "use flex layout" — preserve it rather than
	// falling back to the screen width so the native view reads measured
	// dimensions from Yoga instead.
	if (propsWidth === null) return null;
	return propsWidth || width;
};

const MapContainer = ({
	children,
	nativeNodeHandle = null, // It's not possible to control the nativeNodeHandle. It's a prop just to lift the state up.
	setNativeNodeHandle = null,
	triggerEvent,
	width = moduleDefaults.width,
	height = moduleDefaults.height,
	center = moduleDefaults.center,
	zoomLevel = moduleDefaults.zoomLevel,
	zoomMin = moduleDefaults.zoomMin,
	zoomMax = moduleDefaults.zoomMax,
	moveEnabled = moduleDefaults.moveEnabled,
	tiltEnabled = moduleDefaults.tiltEnabled,
	rotationEnabled = moduleDefaults.rotationEnabled,
	zoomEnabled = moduleDefaults.zoomEnabled,
	tilt = moduleDefaults.tilt,
	minTilt = moduleDefaults.minTilt,
	maxTilt = moduleDefaults.maxTilt,
	bearing = moduleDefaults.bearing,
	minBearing = moduleDefaults.minBearing,
	maxBearing = moduleDefaults.maxBearing,
	roll = moduleDefaults.roll,
	minRoll = moduleDefaults.minRoll,
	maxRoll = moduleDefaults.maxRoll,
	hgtDirPath = null as string | null,
	emitsMapUpdateEvents = moduleDefaults.emitsMapUpdateEvents,
	onMapUpdate,
	onPause,
	onResume,
	onError,
	onTap,
	onLongPress,
	gnssFilter,
	onGnssPosition,
}: MapContainerProps & {
	hgtDirPath?: string | null;
	triggerEvent?: MutableRefObject<null | ((params: TriggerParams) => void)>;
}) => {
	const ref = useRef(null);

	const [nativeNodeHandle_, setNativeNodeHandle_] = useState<number | null>(
		null
	);
	const [hasMounted, setHasMounted] = useState(false);
	// On remount the prop may hold a stale handle from a previous MapContainer
	// instance that was torn down.  Ignore the prop until the first useEffect
	// has discovered the real nativeNodeHandle for *this* map view, so layers
	// never try to register with a destroyed handle (which fails and never
	// retries because the enabled boolean doesn't change).
	nativeNodeHandle =
		hasMounted && nativeNodeHandle ? nativeNodeHandle : nativeNodeHandle_;
	setNativeNodeHandle = setNativeNodeHandle
		? setNativeNodeHandle
		: setNativeNodeHandle_;

	const [mapCreated, setMapCreated] = useState(false);

	width = useDefaultWidth(width);

	useEffect(() => {
		if (ref?.current) {
			const nodeHandle = findNodeHandle(ref?.current);
			if (nodeHandle) {
				setNativeNodeHandle(nodeHandle);
			}
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [ref?.current, setNativeNodeHandle]);

	useEffect(() => {
		setHasMounted(true);
	}, []);

	const nativeNodeHandleRef = useRef(nativeNodeHandle);
	nativeNodeHandleRef.current = nativeNodeHandle;

	// The layer scene (single source of truth for ordering) and its
	// presenter. Both are stable for the lifetime of this map view.
	const syncRef = useRef<undefined | SceneSync>(undefined);
	if (!syncRef.current) {
		syncRef.current = new SceneSync();
	}
	const sync = syncRef.current;
	const scene = sync.getScene();

	useEffect(() => {
		sync.setNativeNodeHandle(nativeNodeHandle);
	}, [sync, nativeNodeHandle]);

	// Drain the MarkerBatchQueue and cancel pending walk/sync timers on
	// unmount, so nothing fires with a stale nativeNodeHandle after teardown.
	useEffect(() => {
		return () => {
			if (nativeNodeHandleRef.current != null) {
				drainQueue(nativeNodeHandleRef.current);
				drainPathQueue(nativeNodeHandleRef.current);
				drainShapeQueue(nativeNodeHandleRef.current);
			}
			sync.destroy();
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	const mapHandleContextValue = useMemo<MapHandleContextValue>(
		() => ({
			nativeNodeHandle,
			scene,
			sync,
		}),
		[
			nativeNodeHandle,
			scene,
			sync,
		]
	);

	const handleMapCreated = useCallback(() => {
		setMapCreated(true);
	}, []);

	// Native hierarchy signal: an anchor view was added/removed under the
	// wrapper (covers identity-preserved element moves) — re-walk.
	const handleAnchorsChanged = useCallback(() => {
		sync.scheduleWalk();
	}, [sync]);

	// Wire triggerEvent ref to native MapContainer.triggerEvent()
	useEffect(() => {
		if (triggerEvent && nativeNodeHandle) {
			triggerEvent.current = (params: TriggerParams) => {
				NativeMapContainer.triggerEvent({
					nativeNodeHandle,
					x: params.x,
					y: params.y,
					strategy: params.strategy,
				});
			};
		} else if (triggerEvent) {
			triggerEvent.current = null;
		}
		return () => {
			if (triggerEvent) {
				triggerEvent.current = null;
			}
		};
	}, [triggerEvent, nativeNodeHandle]);

	// Wire hgtDirPath to native MapContainer.setHgtDirPath() for altitude queries.
	useEffect(() => {
		if (nativeNodeHandle) {
			NativeMapContainer.setHgtDirPath({
				nativeNodeHandle,
				hgtDirPath: hgtDirPath ?? '',
			}).catch(() => {
				// Elevation is best-effort; silently ignore failures
				// (e.g. invalid path, missing permissions).
			});
		}
	}, [nativeNodeHandle, hgtDirPath]);

	const useFlex = width === null || height === null;

	const flexStyle = useMemo(
		() => (useFlex ? { flex: 1 } : undefined),
		[useFlex]
	);

	return (
		<View style={flexStyle}>
			<MapsforgeVtmView
				ref={ref}
				style={flexStyle}
				width={width !== null ? width : undefined}
				height={height !== null ? height : undefined}
				center={center}
				zoomLevel={zoomLevel}
				zoomMin={zoomMin}
				zoomMax={zoomMax}
				moveEnabled={moveEnabled}
				tiltEnabled={tiltEnabled}
				rotationEnabled={rotationEnabled}
				zoomEnabled={zoomEnabled}
				tilt={tilt}
				minTilt={minTilt}
				maxTilt={maxTilt}
				bearing={bearing}
				minBearing={minBearing}
				maxBearing={maxBearing}
				roll={roll}
				minRoll={minRoll}
				maxRoll={maxRoll}
				emitsMapUpdateEvents={
					isBoolean(emitsMapUpdateEvents)
						? emitsMapUpdateEvents
						: !!onMapUpdate
				}
				onMapCreated={handleMapCreated}
				onMapUpdate={onMapUpdate ? onMapUpdate : null}
				onPause={onPause ? onPause : null}
				onResume={onResume ? onResume : null}
				onError={onError ? onError : null}
				onTap={onTap ? onTap : null}
				onLongPress={onLongPress ? onLongPress : null}
				onAnchorsChanged={handleAnchorsChanged}
				gnssFilter={gnssFilter ? gnssFilter : null}
				onGnssPosition={onGnssPosition ? onGnssPosition : null}
			/>
			{mapCreated && (
				<MarkerLayerContext.Provider
					value={{ markerLayerUuid: null, fragmentId: null }}
				>
					<MapHandleContext.Provider value={mapHandleContextValue}>
						{children}
					</MapHandleContext.Provider>
				</MarkerLayerContext.Provider>
			)}
		</View>
	);
};

MapContainer.defaults = moduleDefaults;

export default MapContainer;
