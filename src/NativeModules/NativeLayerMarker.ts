import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type {
	Double,
	EventEmitter,
	Int32,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase } from '../types';
import type { RefObject } from 'react';

/*
 * Type should be redeclared because of codegen ts parser doesn't allow imported type
 * [comments](https://github.com/reactwg/react-native-new-architecture/discussions/91#discussioncomment-4282452)
 *
 * Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
 */
type Position = ReadonlyArray<Double>;

interface ResponseBase {
	uuid: string;
	nativeNodeHandle: Int32;
}

export interface MarkerResponse extends ResponseBase {
	index: number;
}

export const MarkerHotspotPlaces = [
	'NONE',
	'CENTER',
	'BOTTOM_CENTER',
	'TOP_CENTER',
	'RIGHT_CENTER',
	'LEFT_CENTER',
	'UPPER_RIGHT_CORNER',
	'LOWER_RIGHT_CORNER',
	'UPPER_LEFT_CORNER',
	'LOWER_LEFT_CORNER',
] as const;

export const FontFamily = [
	'DEFAULT',
	'DEFAULT_BOLD',
	'MONOSPACE',
	'SANS_SERIF',
	'SERIF',
	'THIN',
	'LIGHT',
	'MEDIUM',
	'BLACK',
	'CONDENSED',
] as const;

export const FontStyle = [
	'BOLD',
	'BOLD_ITALIC',
	'ITALIC',
	'NORMAL',
] as const;

export type LayerMarkerTriggerEvent = (params: MarkerTriggerParams) => void;

interface ModuleLayerParams {
	symbol?: {
		// SymbolParams
		width?: Double;
		height?: Double;
		filePath?: null | string; // `/${string}` | `content://${string}`;
		fillColor?: null | string; // `#${string}`;
		strokeColor?: null | string; // `#${string}`;
		strokeWidth?: Int32;
		hotspotPlace?: string; // MarkerHotspotPlaces
		text?: null | string;
		textColor?: string; // `#${string}`;
		textSize?: Int32;
		textMargin?: Int32;
		textPositionX?: null | Double;
		textPositionY?: null | Double;
		fontFamily?: string; // FontFamily
		fontStyle?: string; // FontStyle
	};
}

export interface ModuleParams extends ModuleLayerParams {
	title?: string;
	description?: string;
	position?: null | Position;
	strategy?: string;
}

interface ErrorWithErrorMsg {
	errorMsg: string;
}

export interface MarkerEvent extends ResponseBase {
	event: string;
	index: Int32;
	distance?: Double;
	markerLayerUuid?: string; // Group uuid for per-layer filtering in shared ItemizedLayer
}

interface CreateLayerParams extends ModuleLayerParams {
	nativeNodeHandle?: Int32;
	positionIndex: Int32;
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

interface UpdateLayerParams extends ModuleLayerParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
}

export interface CreateMarkerParams extends ModuleParams {
	nativeNodeHandle: Int32;
	markerLayerUuid: string | null; // null = root group (no LayerMarker wrapper)
}

export interface CreateMarkersParams {
	nativeNodeHandle: Int32;
	markers: ReadonlyArray<CreateMarkerParams>;
}

export interface MarkerBatchResultItem {
	uuid: string;
	index: Int32;
	error?: string;
}

export interface MarkerBatchResponse {
	results: ReadonlyArray<MarkerBatchResultItem>;
}

export interface RemoveMarkersParams {
	nativeNodeHandle: Int32;
	markerUuids: ReadonlyArray<string>;
}

export interface RemoveMarkerResultItem {
	uuid: string;
	error?: string;
}

export interface RemoveMarkersResponse {
	results: ReadonlyArray<RemoveMarkerResultItem>;
}

interface RemoveMarkerParams {
	nativeNodeHandle: Int32;
	markerLayerUuid: string | null;
	uuid: string;
}

interface UpdateMarkerParams extends ModuleLayerParams {
	nativeNodeHandle?: Int32;
	markerLayerUuid?: string | null;
	uuid?: string;
	position?: null | Position;
}

interface TriggerParamsBase {
	nativeNodeHandle?: Int32;
	markerLayerUuid?: string;
	x?: Double;
	y?: Double;
}

export interface MarkerTriggerParams {
	nativeNodeHandle?: Int32;
	markerLayerUuid?: string;
	x?: Double;
	y?: Double;
	strategy?: 'first' | 'nearest' | 'all';
}

interface TriggerParamsCG extends TriggerParamsBase {
	strategy?: string;
}

export interface SymbolParams {
	width?: Double;
	height?: Double;
	filePath?: `/${string}` | `content://${string}`;
	fillColor?: `#${string}`;
	strokeColor?: `#${string}`;
	strokeWidth?: Int32;
	hotspotPlace?: (typeof MarkerHotspotPlaces)[number];
	text?: null | string;
	textColor?: `#${string}`;
	textSize?: Int32;
	textMargin?: Int32;
	textPositionX?: null | Double;
	textPositionY?: null | Double;
	fontFamily?: (typeof FontFamily)[number];
	fontStyle?: (typeof FontStyle)[number];
}

export type LayerMarkerProps = {
	children?: React.ReactNode;
	symbol?: SymbolParams;
	onCreate?: null | ((response: ResponseBase) => void);
	onRemove?: null | ((response: ResponseBase) => void);
	onChange?: null | ((response: ResponseBase) => void);
	onError?: null | ((err: ErrorBase) => void);
	onMarkerEvent?: null | ((response: MarkerEvent) => void);
	onMarkerPress?: null | ((response: MarkerEvent) => void);
	onMarkerLongPress?: null | ((response: MarkerEvent) => void);
	onMarkerTrigger?: null | ((response: MarkerEvent) => void);
	triggerEvent?: RefObject<null | LayerMarkerTriggerEvent>;
};

export type MarkerProps = {
	position: CreateMarkerParams['position'];
	title?: CreateMarkerParams['title'];
	description?: CreateMarkerParams['description'];
	symbol?: SymbolParams;
	onCreate?: null | ((response: MarkerResponse) => void);
	onRemove?: null | ((response: ResponseBase) => void);
	onChange?: null | ((response: MarkerResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
	onEvent?: null | ((response: MarkerEvent) => void);
	onPress?: null | ((response: MarkerEvent) => void);
	onLongPress?: null | ((response: MarkerEvent) => void);
	onTrigger?: null | ((response: MarkerEvent) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<string>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateLayer(params: UpdateLayerParams): Promise<string>;
	createMarker(params: CreateMarkerParams): Promise<MarkerResponse>;
	createMarkers(params: CreateMarkersParams): Promise<MarkerBatchResponse>;
	removeMarker(params: RemoveMarkerParams): Promise<string>;
	removeMarkers(params: RemoveMarkersParams): Promise<RemoveMarkersResponse>;
	updateMarker(params: UpdateMarkerParams): Promise<string>;
	triggerEvent(params: TriggerParamsCG): void;
	onError: EventEmitter<ErrorWithErrorMsg>;
	onMarkerEvent: EventEmitter<MarkerEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerMarker');
