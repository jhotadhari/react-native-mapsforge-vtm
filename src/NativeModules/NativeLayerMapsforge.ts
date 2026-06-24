import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase, ResponseBase } from '../types';

export const BUILT_IN_THEMES = [
	'DEFAULT',
	'BIKER',
	'MOTORIDER',
	'NEWTRON',
	'OSMARENDER',
	'TRONRENDER',
] as const;

export type Bounds = {	// ??? this should be a ReadonlyArray<Double>; "west", "south", "east", and "north" according to https://datatracker.ietf.org/doc/html/rfc7946#section-5.1
	minLat: Double;
	minLng: Double;
	maxLat: Double;
	maxLng: Double;
};

export type Location = {	// ??? this should be a Position, see src/NativeViews/MapsforgeVtmViewNativeComponent.ts
	lng: Double;
	lat: Double;
};

// A selectable overlay sub-option of a RenderStyleOption, e.g. "show contour lines".
export type RenderStyleOverlay = {
	value: string;
	label: string;
};

// One selectable rendering style exposed by a render theme's <stylemenu>, alongside whichever
// overlay sub-options it allows toggling on top of it.
export type RenderStyleOption = {
	value: string;
	label: string;
	// Named isDefault, not default -- `default` is a reserved word in the C++ JSI header that RN's
	// New Architecture codegen generates for this struct, and won't compile as a member name.
	isDefault?: boolean;
	overlays: RenderStyleOverlay[];
};

// Exported because LayerMapsforge.defaults (LayerMapsforge.tsx) is typed with this, and tsc's
// declaration emit needs every type reachable from an exported value to be nameable.
export interface ModuleParams {
	mapFile?: string;
	renderTheme?: string; // one of BUILT_IN_THEMES, or a `/` or `content://` path to a theme XML file.
	renderStyle?: string;
	renderOverlays?: string[];
	hasBuildings?: boolean;
	hasLabels?: boolean;
	enabledZoomMin?: Int32;
	enabledZoomMax?: Int32;
}

interface CreateLayerParams extends ModuleParams {
	nativeNodeHandle?: Int32;
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

interface CreateSubLayerParams {
	nativeNodeHandle: Int32;
	// The uuid of the already-created main tile layer this sub-layer renders on top of.
	parentUuid: string;
	enabledZoomMin?: Int32;
	enabledZoomMax?: Int32;
}

interface UpdateEnabledZoomMinMaxParams {
	nativeNodeHandle: Int32;
	uuid: string;
	enabledZoomMin?: Int32;
	enabledZoomMax?: Int32;
}

interface GetRenderThemeOptionsParams {
	renderTheme: string;
}

export interface LayerMapsforgeResponse extends ResponseBase {
	bounds?: Bounds;
	center?: Location;	// ??? this should be a Position, see src/NativeViews/MapsforgeVtmViewNativeComponent.ts
	createdBy?: string;
	projectionName?: string;
	comment?: string;
	fileSize?: string;
	fileVersion?: Int32;
	mapDate?: string;
}

export type LayerMapsforgeProps = {
	mapFile?: `/${string}` | `content://${string}`;
	renderTheme?:
		| `/${string}`
		| `content://${string}`
		| (typeof BUILT_IN_THEMES)[number];
	renderStyle?: string;
	renderOverlays?: string[];
	hasBuildings?: boolean;
	hasLabels?: boolean;
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	onCreate?: null | ((response: LayerMapsforgeResponse) => void);
	onRemove?: null | ((response: ResponseBase) => void);
	onChange?: null | ((response: LayerMapsforgeResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<LayerMapsforgeResponse>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateEnabledZoomMinMax(
		params: UpdateEnabledZoomMinMaxParams
	): Promise<string>;
	createBuildingLayer(params: CreateSubLayerParams): Promise<string>;
	removeBuildingLayer(params: RemoveLayerParams): Promise<string>;
	createLabelLayer(params: CreateSubLayerParams): Promise<string>;
	removeLabelLayer(params: RemoveLayerParams): Promise<string>;
	// Pure function of the theme file -- not tied to any particular map instance, so callers don't
	// need a nativeNodeHandle to ask "what styles does this theme offer". Cached natively by file
	// path + last-modified, and parses only the theme's <stylemenu> block rather than the whole
	// file (see RenderThemeMenuLoader.java).
	getRenderThemeOptions(
		params: GetRenderThemeOptionsParams
	): Promise<RenderStyleOption[]>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerMapsforge');
