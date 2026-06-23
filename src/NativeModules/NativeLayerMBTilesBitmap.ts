import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase, ResponseBase } from '../types';

interface ModuleParams {
	mapFile?: string;
	transparentColor?: string;
	alpha?: Double; // float between 0 and 1.
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

interface UpdateEnabledZoomMinMaxParams {
	nativeNodeHandle: Int32;
	uuid: string;
	enabledZoomMin?: Int32;
	enabledZoomMax?: Int32;
}

interface SetAlphaParams {
	nativeNodeHandle: Int32;
	uuid: string;
	alpha?: Double;
}

export type Bounds = {
	minLat: Double;
	minLng: Double;
	maxLat: Double;
	maxLng: Double;
};

export type Location = {
	lng: Double;
	lat: Double;
};

export interface LayerMBTilesBitmapResponse extends ResponseBase {
	bounds?: Bounds;
	center?: Location;
	supportedFormats?: string[];
	format?: string;
	attribution?: string;
	description?: string;
	version?: string;
	zoomMin?: Int32; // the .mbtiles file's own declared zoom range, unrelated to enabledZoomMin/Max.
	zoomMax?: Int32;
}

export type LayerMBTilesBitmapProps = {
	mapFile?: `/${string}`;
	transparentColor?: `#${string}`;
	alpha?: CreateLayerParams['alpha'];
	enabledZoomMin?: CreateLayerParams['enabledZoomMin'];
	enabledZoomMax?: CreateLayerParams['enabledZoomMax'];
	onCreate?: null | ((response: LayerMBTilesBitmapResponse) => void);
	onRemove?: null | ((response: ResponseBase) => void);
	onChange?: null | ((response: LayerMBTilesBitmapResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<LayerMBTilesBitmapResponse>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateEnabledZoomMinMax(
		params: UpdateEnabledZoomMinMaxParams
	): Promise<string>;
	setAlpha(params: SetAlphaParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerMBTilesBitmap');
