import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type {
	Double,
	EventEmitter,
	Int32,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { Position as GeoJsonPosition } from 'geojson';
import type { RefObject } from 'react';
import type { ErrorBase } from '../types';

/*
 * Types must be redeclared inline because react-native-codegen's TS parser cannot
 * follow imported types. Mirrors NativeLayerPath.ts.
 */

// Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
type Position = ReadonlyArray<Double>;

// 0	never include in response.
// 1	include in lifeCycle response.
// 2	include in lifeCycle and onMapEvent response.
export interface PathJtsResponseInclude {
	coordinates?: Int32;
	bounds?: Int32;
}

/**
 * Style properties for the JTS PathLayer.
 *
 * Extends the existing GeometryStyle with JTS-specific additions:
 * - `generalization`: Douglas-Peucker simplification level
 *   (1 = NONE, 2 = SMALL, 4 = MEDIUM, 8 = HIGH)
 */
export type GeometryStyleJts = {
	strokeWidth?: Double;
	strokeColor?: `#${string}`;
	fillColor?: `#${string}`;
	fillAlpha?: Double;
	buffer?: Double;
	scalingZoomLevel?: Int32;
	cap?: 'SQUARE' | 'ROUND' | 'BUTT';
	fixed?: boolean;
	strokeIncrease?: Double;
	blur?: Double;
	stipple?: Int32;
	stippleColor?: `#${string}`;
	stippleWidth?: Double;
	dropDistance?: Double;
	textureRepeat?: boolean;
	heightOffset?: Double;
	randomOffset?: boolean;
	transparent?: boolean;
	/** Douglas-Peucker simplification: 1=NONE, 2=SMALL, 4=MEDIUM, 8=HIGH */
	generalization?: Int32;
};

interface ModuleParams {
	style?: {
		strokeWidth?: Double;
		strokeColor?: string;
	};
	responseInclude?: {
		coordinates?: Int32;
		bounds?: Int32;
	};
	gestureScreenDistance?: Double;
	simplificationTolerance?: Double;
}

interface CreateLayerParams extends ModuleParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	coordinates?: ReadonlyArray<Position>;
	supportsGestures?: boolean;
}

interface UpdateCoordinatesParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
	coordinates?: ReadonlyArray<Position>;
	simplificationTolerance?: Double;
	style?: {
		strokeWidth?: Double;
		strokeColor?: string;
		fillColor?: string;
		fillAlpha?: Double;
		buffer?: Double;
		scalingZoomLevel?: Int32;
		cap?: string;
		fixed?: boolean;
		strokeIncrease?: Double;
		blur?: Double;
		stipple?: Int32;
		stippleColor?: string;
		stippleWidth?: Double;
		dropDistance?: Double;
		textureRepeat?: boolean;
		heightOffset?: Double;
		randomOffset?: boolean;
		transparent?: boolean;
		generalization?: Int32;
	};
	responseInclude?: {
		coordinates?: Int32;
		bounds?: Int32;
	};
}

interface AddGreatCircleParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
	from: Position;
	to: Position;
	numPoints?: Int32;
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export type PathJtsTriggerEvent = (params: PathJtsTriggerParams) => void;

export interface PathJtsTriggerParams {
	x?: Double;
	y?: Double;
}

interface TriggerParamsCG {
	nativeNodeHandle?: Int32;
	uuid?: string;
	x?: Double;
	y?: Double;
}

// Mirrors geojson's `bbox` member: `[ west, south, east, north ]`.
export type Bbox = ReadonlyArray<Double>;

interface ResponseBase {
	uuid: string;
	nativeNodeHandle: Int32;
}

export interface LayerPathJtsResponse extends ResponseBase {
	coordinates?: Position[];
	bbox?: Bbox;
}

export interface LayerPathJtsGestureResponse extends ResponseBase {
	type: string; // 'press' | 'longPress' | 'doubleTap' | 'trigger'
	distance: Double;
	nearestPoint: Position;
	eventPosition: Position;
}

export type LayerPathJtsProps = {
	coordinates?: GeoJsonPosition[];
	responseInclude?: PathJtsResponseInclude;
	gestureScreenDistance?: number;
	simplificationTolerance?: number;
	style?: GeometryStyleJts;
	onRemove?: null | ((response: ResponseBase) => void);
	onCreate?: null | ((response: LayerPathJtsResponse) => void);
	onChange?: null | ((response: LayerPathJtsResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
	onPress?: null | ((response: LayerPathJtsGestureResponse) => void);
	onLongPress?: null | ((response: LayerPathJtsGestureResponse) => void);
	onDoubleTap?: null | ((response: LayerPathJtsGestureResponse) => void);
	onTrigger?: null | ((response: LayerPathJtsGestureResponse) => void);
	triggerEvent?: RefObject<null | PathJtsTriggerEvent>;
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<LayerPathJtsResponse>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateCoordinates(
		params: UpdateCoordinatesParams
	): Promise<LayerPathJtsResponse>;
	addGreatCircle(params: AddGreatCircleParams): Promise<LayerPathJtsResponse>;
	triggerEvent(params: TriggerParamsCG): void;
	onPathJtsEvent: EventEmitter<LayerPathJtsGestureResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerPathJts');
