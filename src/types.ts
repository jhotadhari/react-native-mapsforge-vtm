import { MarkerHotspotPlaces } from './constants';

import type { LayerBitmapTileProps  } from './components/LayerBitmapTile';
import type { ShadingAlgorithm, ShadingAlgorithmOptions, LayerHillshadingProps  } from './components/LayerHillshading';
import type { LayerMapsforgeResponse, LayerMapsforgeProps  } from './components/LayerMapsforge';
import type { LayerMBTilesBitmapResponse, LayerMBTilesBitmapProps  } from './components/LayerMBTilesBitmap';
import type { LayerPathResponse, LayerPathGestureResponse, LayerPathProps  } from './components/LayerPath';
import type { GradientColors, LayerPathSlopeGradientResponse, LayerPathSlopeGradientProps  } from './components/LayerPathSlopeGradient';
import type { LayerScalebarProps  } from './components/LayerScalebar';
import type { MapContainerProps, MapLifeCycleResponse } from './components/MapContainer';
import type { LayerMarkerProps } from './components/LayerMarker';
import type { MarkerProps, MarkerResponse } from './components/Marker';

export type {
    LayerBitmapTileProps,
    ShadingAlgorithm,
    ShadingAlgorithmOptions,
    LayerHillshadingProps,
    LayerMapsforgeResponse,
    LayerMapsforgeProps,
    LayerMBTilesBitmapResponse,
    LayerMBTilesBitmapProps,
    LayerPathResponse,
    LayerPathGestureResponse,
    LayerPathProps,
    GradientColors,
    LayerPathSlopeGradientResponse,
    LayerPathSlopeGradientProps,
    LayerScalebarProps,
    MapContainerProps,
    MapLifeCycleResponse,
    LayerMarkerProps,
    MarkerProps,
    MarkerResponse,
};

export type Bounds = {
	minLat: number;
	minLng: number;
	maxLat: number;
	maxLng: number;
};

export interface ResponseBase {
    uuid: string;
};

export interface Location {
	lng: number;
	lat: number;
	alt?: number;
};

export interface LocationExtended extends Location {
	lng: number;
	lat: number;
	alt?: number;
	distance?: number;
	time?: number;
};

export type ResponseInclude = { [value: string]: 0 | 1 | 2 };

export type GeometryStyle = {
    strokeWidth?: number;
    strokeColor?: `#${string}`;
    fillColor?: `#${string}`;
    fillAlpha?: number;
    buffer?: number;
    scalingZoomLevel?: number;
    cap?: 'SQUARE' | 'ROUND' | 'BUTT';
    fixed?: boolean;
    strokeIncrease?: number;
    blur?: number;
    stipple?: number;
    stippleColor?: `#${string}`;
    stippleWidth?: number;
    // texture?: string;	// pathToImage	???
    dropDistance?: number;
    textureRepeat?: boolean;
    heightOffset?: number;
    randomOffset?: boolean;
    transparent?: boolean;
};

export interface mapEvent {
    nativeNodeHandle: number;
    zoomLevel: number;
    zoom: number;
    scale: number;
    zoomScale: number;
    bearing: number;
    roll: number;
    tilt: number;
    center: Location;
};

export type MarkerSymbol = {
	width?: number;
	height?: number;
	filePath?: `/${string}` | `content://${string}`;
	fillColor?: `#${string}`;
	strokeColor?: `#${string}`;
	strokeWidth?: number;
	hotspotPlace?: typeof MarkerHotspotPlaces[number];
    text?: string;
    textMargin?: number;
    textStrokeWidth?: number;
    textPositionX?: number;
    textPositionY?: number;
};
