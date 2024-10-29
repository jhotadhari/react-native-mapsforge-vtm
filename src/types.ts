
export type Bounds = {
	minLat: number;
	minLon: number;
	maxLat: number;
	maxLon: number;
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
    strokeColor?: string;
    fillColor?: string;
    fillAlpha?: number;
    buffer?: number;
    scalingZoomLevel?: number;
    cap?: 'SQUARE' | 'ROUND' | 'BUTT';
    fixed?: boolean;
    strokeIncrease?: number;
    blur?: number;
    stipple?: number;
    stippleColor?: number;
    stippleWidth?: number;
    // texture?: string;	// pathToImage	???
    dropDistance?: number;
    textureRepeat?: boolean;
    heightOffset?: number;
    randomOffset?: boolean;
    transparent?: boolean;
};