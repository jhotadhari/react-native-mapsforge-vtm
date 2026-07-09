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
 * Type should be redeclared because of codegen ts parser doesn't allow imported type
 * [comments](https://github.com/reactwg/react-native-new-architecture/discussions/91#discussioncomment-4282452)
 *
 * Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
 */
type Position = ReadonlyArray<Double>;

// 0	never include in response.
// 1	include in lifeCycle response.
// 2	include in lifeCycle and onMapEvent response.
export interface PathResponseInclude {
	// [value: string]: Int32			 // Doesn't work with codegen
	coordinates?: Int32;
	bounds?: Int32;
}

export type GeometryStyle = {
	strokeWidth?: Double;
	strokeColor?: `#${string}`;
	fillColor?: `#${string}`;
	fillAlpha?: Double;
	buffer?: Double;
	scalingZoomLevel?: Double;
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
};

export interface ModuleParams {
	style?: {
		strokeWidth?: Double;
		strokeColor?: string;
	};
	responseInclude?: {
		// ResponseInclude
		coordinates?: Int32;
		bounds?: Int32;
	};
	// supportsGestures?: boolean;
	gestureScreenDistance?: Double;
	simplificationTolerance?: Double;
}

interface CreateLayerParams extends ModuleParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	fragmentUuid?: string;
	coordinates?: ReadonlyArray<Position>; // geojson LineString-style `coordinates`
	supportsGestures?: boolean;
}

interface UpdateCoordinatesParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	uuid?: string;
	coordinates?: ReadonlyArray<Position>; // geojson LineString-style `coordinates`
	simplificationTolerance?: Double;
	style?: {
		// GeometryStyle
		strokeWidth?: Double;
		strokeColor?: string;
		fillColor?: string;
		fillAlpha?: Double;
		buffer?: Double;
		scalingZoomLevel?: Double;
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
	};
	responseInclude?: {
		// ResponseInclude
		coordinates?: Int32;
		bounds?: Int32;
	};
}

interface UpdateGestureScreenDistanceParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	uuid?: string;
	gestureScreenDistance?: Double;

	responseInclude?: {
		// ResponseInclude
		coordinates?: Int32;
		bounds?: Int32;
	};
}

interface UpdateSupportsGesturesParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	uuid?: string;
	supportsGestures?: boolean;

	responseInclude?: {
		// ResponseInclude
		coordinates?: Int32;
		bounds?: Int32;
	};
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export type PathTriggerEvent = (params: PathTriggerParams) => void;

export interface PathTriggerParams {
	x?: Double;
	y?: Double;
}

interface TriggerParamsCG {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	uuid?: string;
	x?: Double;
	y?: Double;
}
// Mirrors geojson's `bbox` member: `[ west, south, east, north ]`. https://datatracker.ietf.org/doc/html/rfc7946#section-5.1
export type Bbox = ReadonlyArray<Double>;

interface ResponseBase {
	uuid: string;
	nativeNodeHandle: Int32;
}

export interface LayerPathResponse extends ResponseBase {
	coordinates?: Position[];
	bbox?: Bbox;
}

export interface LayerPathGestureResponse extends ResponseBase {
	type: string; // 'press' | 'longPress' | 'doubleTap' | 'trigger'
	distance: Double;
	nearestPoint: Position;
	eventPosition: Position;
}

export type LayerPathProps = {
	filePath?: null | `/${string}` | `content://${string}`;
	coordinates?: GeoJsonPosition[]; // e.g. a geojson LineString's `coordinates`
	responseInclude?: PathResponseInclude;
	gestureScreenDistance?: number;
	simplificationTolerance?: number;
	style?: GeometryStyle;
	onRemove?: null | ((response: ResponseBase) => void);
	onCreate?: null | ((response: LayerPathResponse) => void);
	onChange?: null | ((response: LayerPathResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
	onPress?: null | ((response: LayerPathGestureResponse) => void);
	onLongPress?: null | ((response: LayerPathGestureResponse) => void);
	onDoubleTap?: null | ((response: LayerPathGestureResponse) => void);
	onTrigger?: null | ((response: LayerPathGestureResponse) => void);
	triggerEvent?: RefObject<null | PathTriggerEvent>;
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<LayerPathResponse>;
	removeLayer(params: RemoveLayerParams): Promise<string>;

	updateCoordinates(
		params: UpdateCoordinatesParams
	): Promise<LayerPathResponse>;
	updateGestureScreenDistance(
		params: UpdateGestureScreenDistanceParams
	): Promise<LayerPathResponse>;
	updateSupportsGestures(
		params: UpdateSupportsGesturesParams
	): Promise<LayerPathResponse>;
	triggerEvent(params: TriggerParamsCG): void;
	onPathEvent: EventEmitter<LayerPathGestureResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerPath');
