import MapContainer from './components/MapContainer';
import type {
	MapEventResponse,
	ResponseInclude,
	MapContainerProps,
} from './NativeViews/MapsforgeVtmViewNativeComponent';

import LayerMarker from './components/LayerMarker';
import Marker from './components/Marker';
import type {
	LayerMarkerProps,
	LayerMarkerTriggerEvent,
	MarkerEvent,
	MarkerProps,
	MarkerResponse,
	SymbolParams,
	MarkerTriggerParams,
} from './NativeModules/NativeLayerMarker';

import LayerBitmapTile from './components/LayerBitmapTile';
import type { LayerBitmapTileProps } from './NativeModules/NativeLayerBitmapTile';

import LayerPath from './components/LayerPath';
import type {
	Bounds,
	GeometryStyle,
	LayerPathGestureResponse,
	LayerPathProps,
	LayerPathResponse,
	PathResponseInclude,
	PathTriggerEvent,
	PathTriggerParams,
} from './NativeModules/NativeLayerPath';

import LayerScalebar from './components/LayerScalebar';
import type { LayerScalebarProps } from './NativeModules/NativeLayerScalebar';

import LayerMBTilesBitmap from './components/LayerMBTilesBitmap';
import type {
	LayerMBTilesBitmapResponse,
	LayerMBTilesBitmapProps,
} from './NativeModules/NativeLayerMBTilesBitmap';

import LayerHillshading from './components/LayerHillshading';
import type {
	ShadingAlgorithm,
	ShadingAlgorithmOptions,
	LayerHillshadingProps,
} from './NativeModules/NativeLayerHillshading';

import LayerMapsforge from './components/LayerMapsforge';
import type {
	RenderStyleOverlay,
	RenderStyleOption,
	LayerMapsforgeResponse,
	LayerMapsforgeProps,
} from './NativeModules/NativeLayerMapsforge';

import useRenderStyleOptions from './compose/useRenderStyleOptions';
import CanvasAdapterModule from './NativeModules/NativeCanvasAdapter';
import type { ErrorWithErrorMsg } from './types';

export {
	// MapContainer and MapsforgeVtmViewNativeComponent.
	MapContainer,
	type MapEventResponse,
	type ResponseInclude,
	type MapContainerProps,

	// LayerMarker, Marker and NativeLayerMarker.
	LayerMarker,
	Marker,
	type MarkerResponse,
	type LayerMarkerTriggerEvent,
	type MarkerEvent,
	type MarkerTriggerParams as TriggerParams,
	type SymbolParams,
	type LayerMarkerProps,
	type MarkerProps,

	// LayerBitmapTile and NativeLayerBitmapTile.
	LayerBitmapTile,
	type LayerBitmapTileProps,

	// LayerPath and NativeLayerPath.
	LayerPath,
	type PathResponseInclude,
	type GeometryStyle,
	type PathTriggerEvent,
	type PathTriggerParams,
	type Bounds,
	type LayerPathResponse,
	type LayerPathGestureResponse,
	type LayerPathProps,

	// LayerScalebar and NativeLayerScalebar.
	LayerScalebar,
	type LayerScalebarProps,

	// LayerMBTilesBitmap and NativeLayerMBTilesBitmap.
	LayerMBTilesBitmap,
	type LayerMBTilesBitmapResponse,
	type LayerMBTilesBitmapProps,

	// LayerHillshading and NativeLayerHillshading.
	LayerHillshading,
	type ShadingAlgorithm,
	type ShadingAlgorithmOptions,
	type LayerHillshadingProps,

	// LayerMapsforge and NativeLayerMapsforge.
	LayerMapsforge,
	type RenderStyleOverlay,
	type RenderStyleOption,
	type LayerMapsforgeResponse,
	type LayerMapsforgeProps,

	// Other
	useRenderStyleOptions,
	CanvasAdapterModule,
	type ErrorWithErrorMsg,
};

export type * from './types';
