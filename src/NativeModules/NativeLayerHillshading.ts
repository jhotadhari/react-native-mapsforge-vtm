import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase, ResponseBase } from '../types';

export type ShadingAlgorithm =
	| 'SimpleShadingAlgorithm'
	| 'DiffuseLightShadingAlgorithm'
	| 'StandardClasyHillShading'
	| 'SimpleClasyHillShading'
	| 'HalfResClasyHillShading'
	| 'HiResClasyHillShading'
	| 'AdaptiveClasyHillShading';

export type ShadingAlgorithmOptions = {
	linearity?: Double;
	scale?: Double;
	heightAngle?: Double;
	maxSlope?: Double;
	minSlope?: Double;
	asymmetryFactor?: Double;
	readingThreadsCount?: Int32;
	computingThreadsCount?: Int32;
	isPreprocess?: boolean;
	isHqEnabled?: boolean;
	qualityScale?: Double;
};

interface ModuleParams {
	hgtDirPath?: string;
	zoomMin?: Int32;
	zoomMax?: Int32;
	enabledZoomMin?: Int32;
	enabledZoomMax?: Int32;
	shadingAlgorithm?: string; // ShadingAlgorithm
	shadingAlgorithmOptions?: {
		// ShadingAlgorithmOptions
		linearity?: Double;
		scale?: Double;
		heightAngle?: Double;
		maxSlope?: Double;
		minSlope?: Double;
		asymmetryFactor?: Double;
		readingThreadsCount?: Int32;
		computingThreadsCount?: Int32;
		isPreprocess?: boolean;
		isHqEnabled?: boolean;
		qualityScale?: Double;
	};
	magnitude?: Int32;
	cacheSize?: Int32; // mb
	cacheDirBase?: string; // empty will be handled java side.
	cacheDirChild?: string; // empty will be handled java side.
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

export type LayerHillshadingProps = {
	hgtDirPath?: `/${string}` | `content://${string}`;
	zoomMin?: number;
	zoomMax?: number;
	enabledZoomMin?: number;
	enabledZoomMax?: number;
	shadingAlgorithm?: ShadingAlgorithm;
	shadingAlgorithmOptions?: ShadingAlgorithmOptions;
	magnitude?: number;
	cacheSize?: number;
	cacheDirBase?: `/${string}`;
	cacheDirChild?: string;
	onCreate?: null | ((result: ResponseBase) => void);
	onRemove?: null | ((result: ResponseBase) => void);
	onChange?: null | ((result: ResponseBase) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<string>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	updateEnabledZoomMinMax(
		params: UpdateEnabledZoomMinMaxParams
	): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerHillshading');
