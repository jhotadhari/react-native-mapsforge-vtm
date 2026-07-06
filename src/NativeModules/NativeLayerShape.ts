import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type {
	Double,
	EventEmitter,
	Int32,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { RefObject } from 'react';
import type { ErrorBase } from '../types';

/*
 * Types must be redeclared inline because react-native-codegen's TS parser cannot
 * follow imported types.
 */

// Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
type Position = ReadonlyArray<Double>;

/**
 * Reuses the same GeometryStyle shape as LayerPath — all 18 properties
 * plus JTS generalization. fillColor and fillAlpha control fill for
 * polygon/circle/rectangle/hexagon shapes.
 */
export type ShapeStyle = {
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
	generalization?: Int32;
};

// ── Shape definitions ──────────────────────────────────────────────────

export interface PolygonShape {
	type: 'polygon';
	/** Outer ring of the polygon: array of Position tuples. */
	rings: ReadonlyArray<Position>;
	/** Optional holes — each hole is an array of Position tuples. */
	holes?: ReadonlyArray<ReadonlyArray<Position>>;
}

export interface CircleShape {
	type: 'circle';
	center: Position;
	/** Radius in kilometers. */
	radiusKm: Double;
	/** Number of segments (default 48). More = smoother circle. */
	numSegments?: Int32;
}

export interface RectangleShape {
	type: 'rectangle';
	/** Southwest corner. */
	min: Position;
	/** Northeast corner. */
	max: Position;
}

export interface HexagonShape {
	type: 'hexagon';
	center: Position;
	/** Radius in kilometers (distance from center to vertex). */
	radiusKm: Double;
}

export interface PointShape {
	type: 'point';
	position: Position;
}

export type ShapeDefinition =
	| PolygonShape
	| CircleShape
	| RectangleShape
	| HexagonShape
	| PointShape;

// ── Module params ──────────────────────────────────────────────────────

interface ModuleParams {
	style?: {
		strokeWidth?: Double;
		strokeColor?: string;
	};
	gestureScreenDistance?: Double;
}

interface CreateLayerParams extends ModuleParams {
	nativeNodeHandle?: Int32;
	positionIndex?: Int32;
	fragmentUuid?: string;
	shape?: {
		type: string;
		rings?: ReadonlyArray<Position>;
		holes?: ReadonlyArray<ReadonlyArray<Position>>;
		center?: Position;
		radiusKm?: Double;
		numSegments?: Int32;
		min?: Position;
		max?: Position;
		position?: Position;
	};
	supportsGestures?: boolean;
}

interface UpdateShapeParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
	shape?: {
		type: string;
		rings?: ReadonlyArray<Position>;
		holes?: ReadonlyArray<ReadonlyArray<Position>>;
		center?: Position;
		radiusKm?: Double;
		numSegments?: Int32;
		min?: Position;
		max?: Position;
		position?: Position;
	};
	style?: {
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
		generalization?: Int32;
	};
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export type ShapeTriggerEvent = (params: ShapeTriggerParams) => void;

export interface ShapeTriggerParams {
	x?: Double;
	y?: Double;
}

interface TriggerParamsCG {
	nativeNodeHandle?: Int32;
	uuid?: string;
	x?: Double;
	y?: Double;
}

// ── Response types ─────────────────────────────────────────────────────

interface ResponseBase {
	uuid: string;
	nativeNodeHandle: Int32;
}

export interface LayerShapeResponse extends ResponseBase {
	/** The shape definition as created on the native side. */
	shape?: {
		type: string;
	};
}

export interface LayerShapeGestureResponse extends ResponseBase {
	type: string; // 'press' | 'longPress' | 'doubleTap' | 'trigger'
	distance: Double;
	eventPosition: Position;
}

// ── Props type ─────────────────────────────────────────────────────────

export type LayerShapeProps = {
	shape?: ShapeDefinition;
	style?: ShapeStyle;
	gestureScreenDistance?: number;
	onRemove?: null | ((response: ResponseBase) => void);
	onCreate?: null | ((response: LayerShapeResponse) => void);
	onChange?: null | ((response: LayerShapeResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
	onPress?: null | ((response: LayerShapeGestureResponse) => void);
	onLongPress?: null | ((response: LayerShapeGestureResponse) => void);
	onDoubleTap?: null | ((response: LayerShapeGestureResponse) => void);
	onTrigger?: null | ((response: LayerShapeGestureResponse) => void);
	triggerEvent?: RefObject<null | ShapeTriggerEvent>;
};

// ── Spec ───────────────────────────────────────────────────────────────

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<LayerShapeResponse>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateShape(params: UpdateShapeParams): Promise<LayerShapeResponse>;
	triggerEvent(params: TriggerParamsCG): void;
	onShapeEvent: EventEmitter<LayerShapeGestureResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerShape');
