/**
 * External dependencies
 */
import { useEffect, useRef, useState } from 'react';

/**
 * Internal dependencies
 */
import LayerMapsforgeModule, {
	type RenderStyleOption,
} from '../NativeModules/NativeLayerMapsforge';
import type { ErrorBase } from '../types';
import reportNativeError from '../reportNativeError';

/**
 * Reads the selectable render styles (and their overlay sub-options) exposed by a render theme's
 * <stylemenu>, e.g. to build a style/overlay picker UI for LayerMapsforge. Pure function of the
 * theme file -- no nativeNodeHandle needed, since this isn't tied to any particular map instance.
 * Native side caches by file path + last-modified and parses only the <stylemenu> block (see
 * RenderThemeMenuLoader.java), not the whole theme, so calling this is cheap even on every render.
 */
const useRenderStyleOptions = ({
	renderTheme,
	onError,
}: {
	renderTheme?: string;
	onError?: null | ((err: ErrorBase) => void);
}): {
	renderStyleDefaultId: string | null;
	renderStyleOptions: RenderStyleOption[];
} => {
	const [renderStyleOptions, setRenderStyleOptions] = useState<
		RenderStyleOption[]
	>([]);
	const [renderStyleDefaultId, setRenderStyleDefaultId] = useState<
		string | null
	>(null);

	// Guards against a stale in-flight response (for a renderTheme that's no longer current)
	// clobbering state after a fast prop change -- requesting theme A then quickly switching to B
	// should never let A's slower response land after B's and overwrite it.
	const currentRenderThemeRef = useRef(renderTheme);
	currentRenderThemeRef.current = renderTheme;

	useEffect(() => {
		setRenderStyleOptions([]);
		setRenderStyleDefaultId(null);

		if (!renderTheme) {
			return;
		}

		LayerMapsforgeModule.getRenderThemeOptions({ renderTheme })
			.then((options) => {
				if (currentRenderThemeRef.current !== renderTheme) {
					return;
				}
				setRenderStyleOptions(options);
				const defaultOption = options.find(
					(option) => option.isDefault
				);
				setRenderStyleDefaultId(
					defaultOption ? defaultOption.value : null
				);
			})
			.catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
	}, [renderTheme, onError]);

	return {
		renderStyleDefaultId,
		renderStyleOptions,
	};
};

export default useRenderStyleOptions;
