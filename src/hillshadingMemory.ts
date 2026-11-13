/**
 * Internal dependencies
 */
import NativeMapContainer from './NativeModules/NativeMapContainer';

/**
 * Opt-in, process-wide defaults for the memory handling of hillshading
 * layers' HGT bitmap cache. Read when a `LayerHillshading` is created (call
 * this before the layers mount); the layer props of the same name override
 * them.
 *
 * - `recycleEvictedBitmaps`: recycle evicted HGT bitmaps right away (ref
 *   counted, safe against in-use bitmaps) instead of leaving their native
 *   pixel memory to the GC, and share one byte-capped cache per layer.
 *   Default `false` (stock mapsforge behaviour).
 * - `hgtMemoryCacheMaxMb`: byte cap of that cache, only effective together
 *   with `recycleEvictedBitmaps`. `0` → mapsforge default (1/10 of the Java
 *   heap limit).
 */
export const setHillshadingMemoryDefaults = (defaults: {
	recycleEvictedBitmaps?: boolean;
	hgtMemoryCacheMaxMb?: number;
}): Promise<void> =>
	NativeMapContainer.setHillshadingMemoryDefaults({
		...(defaults.recycleEvictedBitmaps !== undefined && {
			recycleEvictedBitmaps: defaults.recycleEvictedBitmaps,
		}),
		...(defaults.hgtMemoryCacheMaxMb !== undefined && {
			hgtMemoryCacheMaxMb: defaults.hgtMemoryCacheMaxMb,
		}),
	});
