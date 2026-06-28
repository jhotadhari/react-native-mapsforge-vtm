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

interface ResponseInclude {
	// [value: string]: Int32			 // Doesn't work with codegen
	zoomLevel?: Int32;
	zoom?: Int32;
	scale?: Int32;
	zoomScale?: Int32;
	bearing?: Int32;
	roll?: Int32;
	tilt?: Int32;
	center?: Int32;
}

export interface ModuleParams {
	width: null | Double;
	height: Double;
	center: Position;
	zoomLevel: Int32;
	zoomMin: Int32;
	zoomMax: Int32;
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
	hgtDirPath: null | string;
	hgtInterpolation: boolean;
	hgtReadFileRate: Int32;
	hgtFileInfoPurgeThreshold: Int32;
	responseInclude: ResponseInclude;
	mapEventRate: Int32;
	emitsMapUpdateEvents: null | boolean;
	emitsHardwareKeyUp: ReadonlyArray<string>;
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
	zoomLevel: Int32;
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

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	reorderLayers(params: ReorderLayersParams): Promise<void>;
	animateTo(params: AnimateToParams): Promise<void>;
	getPosition(params: GetPositionParams): Promise<GetPositionResponse>;
	triggerEvent(params: TriggerParams): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('MapContainer');
