
import type { LayerBitmapTileResponse,LayerBitmapTileProps  } from './components/LayerBitmapTile';
import type { ShadingAlgorithm, ShadingAlgorithmOptions, LayerHillshadingResponse, LayerHillshadingProps  } from './components/LayerHillshading';
import type { LayerMapsforgeResponse, LayerMapsforgeProps  } from './components/LayerMapsforge';
import type { LayerMBTilesBitmapResponse, LayerMBTilesBitmapProps  } from './components/LayerMBTilesBitmap';
import type { LayerPathResponse, LayerPathProps  } from './components/LayerPath';
import type { GradientColors, LayerPathSlopeGradientResponse, LayerPathSlopeGradientProps  } from './components/LayerPathSlopeGradient';
import type { LayerScalebarResponse, LayerScalebarProps  } from './components/LayerScalebar';
import type { MapContainerProps, MapLifeCycleResponse } from './components/MapContainer';

export type {
    LayerBitmapTileResponse,
    LayerBitmapTileProps,
    ShadingAlgorithm,
    ShadingAlgorithmOptions,
    LayerHillshadingResponse,
    LayerHillshadingProps,
    LayerMapsforgeResponse,
    LayerMapsforgeProps,
    LayerMBTilesBitmapResponse,
    LayerMBTilesBitmapProps,
    LayerPathResponse,
    LayerPathProps,
    GradientColors,
    LayerPathSlopeGradientResponse,
    LayerPathSlopeGradientProps,
    LayerScalebarResponse,
    LayerScalebarProps,
    MapContainerProps,
    MapLifeCycleResponse,
};

export type Bounds = {
	minLat: number;
	minLng: number;
	maxLat: number;
	maxLng: number;
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
