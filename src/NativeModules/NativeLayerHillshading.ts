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

// Exported because LayerHillshading.defaults (LayerHillshading.tsx) is typed with this, and tsc's
// declaration emit needs every type reachable from an exported value to be nameable.
export interface ModuleParams {
	hgtDirPath?: string;
	zoomMin?: Double;
	zoomMax?: Double;
	enabledZoomMin?: Double;
	enabledZoomMax?: Double;
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
	recycleEvictedBitmaps?: boolean;
	hgtMemoryCacheMaxMb?: Double; // mb
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
	enabledZoomMin?: Double;
	enabledZoomMax?: Double;
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
	/**
	 * Opt-in. Recycle HGT hillshading bitmaps as soon as they are evicted from
	 * the in-memory cache (reference counted, so never while a tile is still
	 * being shaded), and share one byte-capped cache across all tile loader
	 * threads of this layer. Without it, evicted bitmaps (native pixel memory,
	 * up to ~6 MB each at high zoom) linger until the GC finalizes them, and the
	 * cache cap is not enforced. Overrides the process-wide default from
	 * `setHillshadingMemoryDefaults`. Changing it recreates the layer.
	 */
	recycleEvictedBitmaps?: boolean;
	/**
	 * Byte cap (in MB) of the in-memory HGT bitmap cache. Only applies together
	 * with `recycleEvictedBitmaps`. Bitmaps currently in use may exceed it
	 * temporarily. Unset or 0 → mapsforge default (1/10 of the Java heap limit).
	 * Overrides the process-wide default from `setHillshadingMemoryDefaults`.
	 */
	hgtMemoryCacheMaxMb?: number;
	onCreate?: null | ((result: ResponseBase) => void);
	onRemove?: null | ((result: ResponseBase) => void);
	onChange?: null | ((result: ResponseBase) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface PruneTileMemoryCacheParams {
	nativeNodeHandle: Int32;
	uuid: string;
	maxTiles: Int32;
}

export interface ClearTileMemoryCacheParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLayer(params: CreateLayerParams): Promise<string>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
	/**
	 * Prune this layer's in-memory tile cache to at most maxTiles tiles.
	 * Tiles farthest from the current map position are evicted first.
	 */
	pruneTileMemoryCache(params: PruneTileMemoryCacheParams): Promise<string>;
	/**
	 * Clear this layer's in-memory tile cache (bitmaps and GL resources).
	 * On-disk caches are untouched.
	 */
	clearTileMemoryCache(params: ClearTileMemoryCacheParams): Promise<string>;
	updateEnabledZoomMinMax(
		params: UpdateEnabledZoomMinMaxParams
	): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerHillshading');
