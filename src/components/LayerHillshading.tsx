/**
 * External dependencies
 */
import { useContext, useEffect, useRef } from 'react';

/**
 * Internal dependencies
 */
import LayerHillshadingModule, {
	type LayerHillshadingProps,
	type ShadingAlgorithm,
	type ShadingAlgorithmOptions,
} from '../NativeModules/NativeLayerHillshading';
import type { ErrorBase } from '../types';
import useLayerOrder from '../compose/useLayerOrder';
import useNativeLayerLifecycle from '../compose/useNativeLayerLifecycle';
import reportNativeError from '../reportNativeError';
import MapHandleContext from '../context/MapHandleContext';

export const shadingAlgorithms: { [value: string]: ShadingAlgorithm } = {
	CLASY_ADAPTIVE: 'AdaptiveClasyHillShading',
	CLASY_STANDARD: 'StandardClasyHillShading',
	CLASY_SIMPLE: 'SimpleClasyHillShading',
	CLASY_HALF_RES: 'HalfResClasyHillShading',
	CLASY_HI_RES: 'HiResClasyHillShading',
	SIMPLE: 'SimpleShadingAlgorithm',
	DIFFUSE_LIGHT: 'DiffuseLightShadingAlgorithm',
};

const clasyParamsKeys = [
	'maxSlope',
	'minSlope',
	'asymmetryFactor',
	'readingThreadsCount',
	'computingThreadsCount',
	'isPreprocess',
];

export const shadingAlgorithmsOptionKeys: { [value: string]: string[] } = {
	CLASY_ADAPTIVE: [
		...clasyParamsKeys,
		'isHqEnabled',
		'qualityScale',
	],
	CLASY_STANDARD: clasyParamsKeys,
	CLASY_SIMPLE: clasyParamsKeys,
	CLASY_HALF_RES: clasyParamsKeys,
	CLASY_HI_RES: clasyParamsKeys,
	SIMPLE: ['linearity', 'scale'],
	DIFFUSE_LIGHT: ['heightAngle'],
};

export const shadingAlgorithmOptionsDefaults: ShadingAlgorithmOptions = {
	linearity: 0.1,
	scale: 0.666,
	heightAngle: 50,
	maxSlope: 80,
	minSlope: 0,
	asymmetryFactor: 0.5,
	readingThreadsCount: -1,
	computingThreadsCount: -1,
	isPreprocess: true,
	isHqEnabled: true,
	qualityScale: 1,
};

const LayerHillshading = ({
	hgtDirPath,
	zoomMin,
	zoomMax,
	enabledZoomMin,
	enabledZoomMax,
	shadingAlgorithm,
	shadingAlgorithmOptions,
	magnitude,
	cacheSize,
	cacheDirBase,
	cacheDirChild,
	onCreate,
	onRemove,
	onChange,
	onError,
}: LayerHillshadingProps) => {
	const { nativeNodeHandle } = useContext(MapHandleContext);

	const positionIndexRef = useRef<number>(-1);
	const { uuid, triggerCreate, triggerRemove } = useNativeLayerLifecycle({
		enabled: !!nativeNodeHandle && !!hgtDirPath,
		create: ({ triggerOnCreate, triggerOnChange }) => {
			if (!nativeNodeHandle || !hgtDirPath) {
				return Promise.reject<string>({
					userInfo: {
						errorMsg: 'Missing nativeNodeHandle or hgtDirPath',
					},
				} as ErrorBase);
			}
			return LayerHillshadingModule.createLayer({
				nativeNodeHandle,
				positionIndex: positionIndexRef.current,
				hgtDirPath,
				...(zoomMin !== undefined && { zoomMin }),
				...(zoomMax !== undefined && { zoomMax }),
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
				...(shadingAlgorithm && { shadingAlgorithm }),
				...(shadingAlgorithmOptions && {
					shadingAlgorithmOptions: {
						...shadingAlgorithmOptionsDefaults,
						...shadingAlgorithmOptions,
					},
				}),
				...(magnitude !== undefined && {
					magnitude: Math.round(magnitude),
				}),
				...(cacheSize !== undefined && {
					cacheSize: Math.round(cacheSize),
				}),
				...(cacheDirBase && { cacheDirBase: cacheDirBase.trim() }),
				...(cacheDirChild && { cacheDirChild: cacheDirChild.trim() }),
			}).then((newUuid) => {
				triggerOnCreate && onCreate
					? onCreate({ nativeNodeHandle, uuid: newUuid })
					: null;
				triggerOnChange && onChange
					? onChange({ nativeNodeHandle, uuid: newUuid })
					: null;
				return newUuid;
			});
		},
		remove: (currentUuid, { triggerOnRemove }) => {
			if (!nativeNodeHandle) {
				return Promise.resolve(false);
			}
			return LayerHillshadingModule.removeLayer({
				nativeNodeHandle,
				uuid: currentUuid,
			})
				.then((removedUuid) => {
					triggerOnRemove && onRemove
						? onRemove({ nativeNodeHandle, uuid: removedUuid })
						: null;
					return true;
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
					return false;
				});
		},
		onError,
	});

	const { positionIndex } = useLayerOrder(uuid);
	positionIndexRef.current = positionIndex;

	// enabledZoomMin enabledZoomMax changed.
	useEffect(() => {
		if (nativeNodeHandle && uuid) {
			LayerHillshadingModule.updateEnabledZoomMinMax({
				nativeNodeHandle,
				uuid,
				...(enabledZoomMin !== undefined && {
					enabledZoomMin,
				}),
				...(enabledZoomMax !== undefined && {
					enabledZoomMax,
				}),
			}).catch((err: ErrorBase) => {
				reportNativeError(err, onError);
			});
		}
	}, [
		enabledZoomMin,
		enabledZoomMax,
		nativeNodeHandle,
		uuid,
		onError,
	]);

	// There's no native "update in place" for these -- changing any of them requires tearing down
	// and recreating the layer. triggerRemove resets uuid to null on success, which is what lets
	// the hook's own mount logic re-trigger creation via triggerCreate below.
	const shadingAlgorithmOptionsKey = JSON.stringify(shadingAlgorithmOptions);
	useEffect(() => {
		triggerRemove({ triggerOnRemove: false }).then((success) => {
			if (success) {
				triggerCreate({
					triggerOnCreate: false,
					triggerOnChange: true,
				});
			}
		});
	}, [
		hgtDirPath,
		zoomMin,
		zoomMax,
		shadingAlgorithm,
		magnitude,
		cacheSize,
		cacheDirBase,
		cacheDirChild,
		shadingAlgorithmOptionsKey,
		triggerRemove,
		triggerCreate,
	]);

	return null;
};

LayerHillshading.defaults = LayerHillshadingModule.getConstants();
LayerHillshading.shadingAlgorithms = shadingAlgorithms;
LayerHillshading.shadingAlgorithmsOptionKeys = shadingAlgorithmsOptionKeys;
LayerHillshading.shadingAlgorithmOptionsDefaults =
	shadingAlgorithmOptionsDefaults;

export default LayerHillshading;
