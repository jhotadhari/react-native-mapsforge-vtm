// export { default as MapsforgeVtmView } from './NativeViews/MapsforgeVtmViewNativeComponent';

import MapContainer from './components/MapContainer';
import * as MapContainerTypes from './NativeViews/MapsforgeVtmViewNativeComponent';

import LayerMarker from './components/LayerMarker';
import Marker from './components/Marker';
import * as MarkerTypes from './NativeModules/NativeLayerMarker';

import LayerBitmapTile from './components/LayerBitmapTile';
import * as LayerBitmapTileTypes from './NativeModules/NativeLayerBitmapTile';

import LayerPath from './components/LayerPath';
import * as LayerPathTypes from './NativeModules/NativeLayerPath';

import LayerScalebar from './components/LayerScalebar';
import * as LayerScalebarTypes from './NativeModules/NativeLayerScalebar';

import LayerMBTilesBitmap from './components/LayerMBTilesBitmap';
import * as LayerMBTilesBitmapTypes from './NativeModules/NativeLayerMBTilesBitmap';

import LayerHillshading from './components/LayerHillshading';
import * as LayerHillshadingTypes from './NativeModules/NativeLayerHillshading';

import LayerMapsforge from './components/LayerMapsforge';
import * as LayerMapsforgeTypes from './NativeModules/NativeLayerMapsforge';
import useRenderStyleOptions from './compose/useRenderStyleOptions';

import CanvasAdapterModule from './NativeModules/NativeCanvasAdapter';

// import LayerPathSlopeGradient from './components/LayerPathSlopeGradient';

export {
	MapContainer,
	MapContainerTypes,
	LayerMarker,
	Marker,
	MarkerTypes,
	LayerBitmapTile,
	LayerBitmapTileTypes,
	LayerPath,
	LayerPathTypes,
	LayerScalebar,
	LayerScalebarTypes,
	LayerMBTilesBitmap,
	LayerMBTilesBitmapTypes,
	LayerHillshading,
	LayerHillshadingTypes,
	LayerMapsforge,
	LayerMapsforgeTypes,
	useRenderStyleOptions,
	CanvasAdapterModule,

	// LayerPathSlopeGradient,
};

export type * from './types';
export type * from './NativeViews/MapsforgeVtmViewNativeComponent';
