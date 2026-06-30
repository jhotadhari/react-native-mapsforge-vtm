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
	createLayerOrderRegistry,
	type LayerOrderRegistry,
	type MapHandleContextValue,
} from '../context/MapHandleContext';
import MarkerLayerContext from '../context/MarkerLayerContext';
import { drainQueue } from '../compose/MarkerBatchQueue';

const moduleDefaults = NativeMapContainer.getConstants();

const useDefaultWidth = (propsWidth?: number | null) => {
	const { width } = useWindowDimensions();
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
	hgtDirPath = moduleDefaults.hgtDirPath as MapContainerProps['hgtDirPath'],
	hgtInterpolation = moduleDefaults.hgtInterpolation,
	hgtReadFileRate = moduleDefaults.hgtReadFileRate,
	hgtFileInfoPurgeThreshold = moduleDefaults.hgtFileInfoPurgeThreshold,
	responseInclude: responseIncludeParams = moduleDefaults.responseInclude,
	mapEventRate = moduleDefaults.mapEventRate,
	emitsMapUpdateEvents = moduleDefaults.emitsMapUpdateEvents,
	onMapUpdate,
	onPause,
	onResume,
	onError,
	onTap,
	onLongPress,
}: MapContainerProps & {
	triggerEvent?: MutableRefObject<null | ((params: TriggerParams) => void)>;
}) => {
	const ref = useRef(null);

	const [nativeNodeHandle_, setNativeNodeHandle_] = useState<number | null>(
		null
	);
	nativeNodeHandle = nativeNodeHandle ? nativeNodeHandle : nativeNodeHandle_;
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

	const nativeNodeHandleRef = useRef(nativeNodeHandle);
	nativeNodeHandleRef.current = nativeNodeHandle;

	const registryRef = useRef<undefined | LayerOrderRegistry>(undefined);
	if (!registryRef.current) {
		registryRef.current = createLayerOrderRegistry();
	}
	const registry = registryRef.current;
	// Bump generation on every render so useLayerOrder can distinguish a full coherent
	// render pass (where already-registered layers must be repositioned to match the
	// current document order) from a solo re-render of a single layer (where they must not).
	registry.generation++;
	// Rebuild fragment indices from existing order so new layers added during this
	// pass see the correct continuation index for their type-run. Without this, a
	// newly-mounted same-type layer at the end of an existing run would default to
	// fragment index 1 instead of sharing the run's index, causing a z-order
	// violation (and a fragment-UUID collision with earlier layers that hold index 1).
	{
		let lastType: string | undefined;
		registry.fragmentIndices.clear();
		for (const id of registry.order) {
			const t = registry.layerTypes.get(id);
			if (t) {
				if (lastType !== t) {
					const idx = registry.fragmentIndices.get(t) ?? 0;
					registry.fragmentIndices.set(t, idx + 1);
				}
				lastType = t;
			}
		}
		// Seed cursorLayerType from the last layer in order, so a new same-type
		// layer added at the end sees a type-match and correctly shares the last
		// fragment index (no spurious increment).
		registry.cursorLayerType = lastType;
	}
	// Reset on every render (not just mount): this is what gives useLayerOrder a fresh,
	// reliable anchor at the start of each coherent render pass over `children`, so a layer
	// that mounts/remounts there (e.g. a toggled-on <LayerPath/>) can insert itself in the
	// right relative position instead of always landing at the end.
	registry.cursor = undefined;
	registry.sharedLayerActive = false;

	// Drain the MarkerBatchQueue and cancel pending debounced reorderLayers
	// timers on unmount, so they don't fire with a stale nativeNodeHandle
	// after teardown (which would produce cosmetic console errors).
	const registryRef2 = useRef(registry);
	registryRef2.current = registry;
	useEffect(() => {
		return () => {
			if (nativeNodeHandleRef.current != null) {
				drainQueue(nativeNodeHandleRef.current);
			}
			registryRef2.current.destroy();
		};
	}, []);

	const mapHandleContextValue = useMemo<MapHandleContextValue>(
		() => ({
			nativeNodeHandle,
			registry,
		}),
		[nativeNodeHandle, registry]
	);

	const handleMapCreated = useCallback(() => {
		setMapCreated(true);
	}, []);

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

	const responseInclude = useMemo(
		() => ({
			...moduleDefaults.responseInclude,
			...responseIncludeParams,
		}),
		[responseIncludeParams]
	);

	return (
		<View>
			<MapsforgeVtmView
				ref={ref}
				width={width}
				height={height}
				center={center}
				zoomLevel={Math.round(zoomLevel)}
				zoomMin={Math.round(zoomMin)}
				zoomMax={Math.round(zoomMax)}
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
				hgtDirPath={hgtDirPath || ''}
				hgtInterpolation={hgtInterpolation}
				hgtReadFileRate={Math.round(hgtReadFileRate)}
				hgtFileInfoPurgeThreshold={Math.round(
					hgtFileInfoPurgeThreshold
				)}
				responseInclude={responseInclude}
				mapEventRate={Math.round(mapEventRate)}
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
			/>
			{mapCreated && (
				<MarkerLayerContext.Provider value={{ markerLayerUuid: null }}>
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
