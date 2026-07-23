import MapContainer from './components/MapContainer';
import type {
	MapEventResponse,
	MapContainerProps,
	TapEventResponse,
	LongPressEventResponse,
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
	Bbox,
	GeometryStyle,
	LayerPathGestureResponse,
	LayerPathProps,
	LayerPathResponse,
	PathResponseInclude,
	PathTriggerEvent,
	PathTriggerParams,
} from './NativeModules/NativeLayerPath';

import LayerPathJts from './components/LayerPathJts';
import type {
	GeometryStyleJts,
	LayerPathJtsGestureResponse,
	LayerPathJtsProps,
	LayerPathJtsResponse,
	PathJtsResponseInclude,
	PathJtsTriggerEvent,
	PathJtsTriggerParams,
} from './NativeModules/NativeLayerPathJts';

import LayerScalebar from './components/LayerScalebar';
import type { LayerScalebarProps } from './NativeModules/NativeLayerScalebar';

import LayerShape from './components/LayerShape';
import type {
	LayerShapeGestureResponse,
	LayerShapeProps,
	LayerShapeResponse,
	ShapeDefinition,
	ShapeStyle,
	ShapeTriggerEvent,
	ShapeTriggerParams,
} from './NativeModules/NativeLayerShape';

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
import useMap from './compose/useMap';
import type {
	EasingType,
	MapPositionTarget,
	AnimationOptions,
	FitBoundsOptions,
	GetPositionResponse,
	DebugLayerDump,
	FragmentSummaryEntry,
	RegistryDebugSnapshot,
	RegistryLayerEntry,
} from './compose/useMap';
import { enrichCoordinatesWithElevation } from './enrichCoordinates';
import type {
	ElevationAPI,
	EnrichCoordinatesOptions,
} from './enrichCoordinates';
import CanvasAdapterModule from './NativeModules/NativeCanvasAdapter';
import type { ErrorWithErrorMsg } from './types';
import SharedLayer from './components/SharedLayer';
import ReindexScope from './components/ReindexScope';
import type { ReindexScopeProps } from './components/ReindexScope';
import { useLayerDebugInfo } from './debug/useLayerDebugInfo';
import type {
	LayerDebugEntry,
	LayerDebugInfo,
} from './debug/useLayerDebugInfo';
import LayerDebugTree from './debug/LayerDebugTree';
import type { LayerDebugTreeProps } from './debug/LayerDebugTree';

// Extension points — stable hooks/contexts that extension libraries
// (e.g. react-native-mapsforge-vtm-ext-grib) use to create custom layer types.
import MapHandleContext, {
	createLayerOrderRegistry,
	type LayerOrderRegistry,
	type MapHandleContextValue,
} from './context/MapHandleContext';
import useLayerOrder from './compose/useLayerOrder';
import useNativeLayerLifecycle, {
	type CreateFlags,
	type RemoveFlags,
} from './compose/useNativeLayerLifecycle';

export {
	// MapContainer and MapsforgeVtmViewNativeComponent.
	MapContainer,
	type MapEventResponse,
	type MapContainerProps,
	type TapEventResponse,
	type LongPressEventResponse,

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
	type Bbox,
	type LayerPathResponse,
	type LayerPathGestureResponse,
	type LayerPathProps,

	// LayerPathJts and NativeLayerPathJts.
	LayerPathJts,
	type PathJtsResponseInclude,
	type GeometryStyleJts,
	type PathJtsTriggerEvent,
	type PathJtsTriggerParams,
	type LayerPathJtsResponse,
	type LayerPathJtsGestureResponse,
	type LayerPathJtsProps,

	// LayerScalebar and NativeLayerScalebar.
	LayerScalebar,
	type LayerScalebarProps,

	// LayerShape and NativeLayerShape.
	LayerShape,
	type ShapeStyle,
	type ShapeDefinition,
	type ShapeTriggerEvent,
	type ShapeTriggerParams,
	type LayerShapeResponse,
	type LayerShapeGestureResponse,
	type LayerShapeProps,

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

	// useMap.
	useMap,
	type EasingType,
	type MapPositionTarget,
	type AnimationOptions,
	type FitBoundsOptions,
	type GetPositionResponse,
	type DebugLayerDump,
	type FragmentSummaryEntry,
	type RegistryDebugSnapshot,
	type RegistryLayerEntry,

	// Elevation enrichment.
	enrichCoordinatesWithElevation,
	type ElevationAPI,
	type EnrichCoordinatesOptions,

	// Other
	useRenderStyleOptions,
	CanvasAdapterModule,
	SharedLayer,
	ReindexScope,
	type ReindexScopeProps,
	type ErrorWithErrorMsg,

	// Debug tools
	useLayerDebugInfo,
	LayerDebugTree,
	type LayerDebugEntry,
	type LayerDebugInfo,
	type LayerDebugTreeProps,

	// Extension points for external layer-type libraries
	// (e.g. react-native-mapsforge-vtm-ext-grib).
	MapHandleContext,
	createLayerOrderRegistry,
	type LayerOrderRegistry,
	type MapHandleContextValue,
	useLayerOrder,
	useNativeLayerLifecycle,
	type CreateFlags,
	type RemoveFlags,
};

// Shared path response type — union of both PathResponseInclude variants.
export type ResponseInclude = PathResponseInclude | PathJtsResponseInclude;

export type * from './types';
