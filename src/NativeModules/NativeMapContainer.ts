import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';

/*
 * Type should be redeclared because of codegen ts parser doesn't allow imported type
 * [comments](https://github.com/reactwg/react-native-new-architecture/discussions/91#discussioncomment-4282452)
 *
 * Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
 */
type Position = ReadonlyArray<Double>;

export interface ModuleParams {
	width: null | Double;
	height: Double;
	center: Position;
	zoomLevel: Double;
	zoomMin: Double;
	zoomMax: Double;
	moveEnabled: boolean;
	tiltEnabled: boolean;
	rotationEnabled: boolean;
	zoomEnabled: boolean;
	tilt: Double;
	minTilt: Double;
	maxTilt: Double;
	bearing: Double;
	minBearing: Double;
	maxBearing: Double;
	roll: Double;
	minRoll: Double;
	maxRoll: Double;
	emitsMapUpdateEvents: null | boolean;
	emitsHardwareKeyUp: ReadonlyArray<string>;
	tileSize?: Double;
	density?: Double;
}

export interface ReorderLayersParams {
	nativeNodeHandle: Int32;
	layerUuids: ReadonlyArray<string>;
}

export interface AnimateToParams {
	nativeNodeHandle: Int32;
	// Ignored when `bounds` is set.
	center?: Position;
	zoomLevel?: Double;
	bearing?: Double;
	tilt?: Double;
	roll?: Double;
	// GeoJSON bbox: [ west, south, east, north ]. Takes priority over center/zoomLevel/bearing/
	// tilt/roll, and resets bearing/tilt/roll to 0 -- mirrors vtm's own MapPosition.setByBoundingBox.
	bounds?: ReadonlyArray<Double>;
	boundsPaddingPx?: Double;
	// Milliseconds. 0 (the default) jumps instantly.
	duration?: Double;
	// One of vtm's org.oscim.utils.animation.Easing.Type names, case-insensitive
	// (e.g. 'linear', 'sine_inout', 'expo_out'). Defaults to 'linear'.
	easing?: string;
}

export interface GetPositionParams {
	nativeNodeHandle: Int32;
}

export interface GetPositionResponse {
	center: Position;
	zoomLevel: Double;
	bearing: Double;
	tilt: Double;
	roll: Double;
}

export interface TriggerParams {
	nativeNodeHandle: Int32;
	x: Double;
	y: Double;
	strategy?: string;
}

export interface SetHgtDirPathParams {
	nativeNodeHandle: Int32;
	hgtDirPath: string;
}

export interface GetAltitudeAtPositionParams {
	nativeNodeHandle: Int32;
	lng: Double;
	lat: Double;
}

export interface GetAltitudeAtPositionResponse {
	altitude: Double | null;
}

export interface HasDataAtPositionParams {
	nativeNodeHandle: Int32;
	lng: Double;
	lat: Double;
}

export interface HasDataAtPositionResponse {
	hasData: boolean;
}

export interface IsTileCachedParams {
	nativeNodeHandle: Int32;
	lng: Double;
	lat: Double;
}

export interface IsTileCachedResponse {
	cached: boolean;
}

export interface SetCacheCapacityParams {
	nativeNodeHandle: Int32;
	capacity: Int32;
}

export interface GetDebugLayerDumpParams {
	nativeNodeHandle: Int32;
}

export interface DebugLayerInfo {
	zIndex: Int32;
	className: string;
	simpleName: string;
	uuid: string | null;
	isJsManaged: boolean;
	enabled: boolean;
}

export interface GetDebugLayerDumpResponse {
	nativeNodeHandle: Int32;
	totalLayers: Int32;
	jsManagedCount: Int32;
	pendingMutations: Int32;
	layers: ReadonlyArray<DebugLayerInfo>;
}

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	reorderLayers(params: ReorderLayersParams): Promise<void>;
	animateTo(params: AnimateToParams): Promise<void>;
	getPosition(params: GetPositionParams): Promise<GetPositionResponse>;
	triggerEvent(params: TriggerParams): void;
	setHgtDirPath(params: SetHgtDirPathParams): Promise<void>;
	getAltitudeAtPosition(
		params: GetAltitudeAtPositionParams
	): Promise<GetAltitudeAtPositionResponse>;

	hasDataAtPosition(
		params: HasDataAtPositionParams
	): Promise<HasDataAtPositionResponse>;

	isTileCached(params: IsTileCachedParams): Promise<IsTileCachedResponse>;

	setCacheCapacity(params: SetCacheCapacityParams): Promise<void>;

	/**
	 * Returns a JSON dump of all layers currently on the map, including both
	 * JS-managed and vtm-internal layers, with their z-indices, class names,
	 * uuids, and enabled state.  Useful for debugging layer ordering issues.
	 */
	getDebugLayerDump(
		params: GetDebugLayerDumpParams
	): Promise<GetDebugLayerDumpResponse>;

	/**
	 * Installs the __getMapPositionSynchronizables global JSI function on the React Native JS
	 * on the React Native JS runtime.  Called once per process from the
	 * JS thread during useMapPosition() initialization.  Idempotent.
	 */
	installMapPositionJSI(params: {}): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('MapContainer');
