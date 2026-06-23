import type { Int32 } from 'react-native/Libraries/Types/CodegenTypes';

/*
 * Re-export geojson's own `Position` type ( `[ lng, lat, alt? ]` ), so consumers of this
 * library can pass the `coordinates` of a geojson `Point`/`LineString`/etc. geometry directly.
 * https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1
 */
export type { Position } from 'geojson';

export interface ResponseBase {
	uuid: string;
	nativeNodeHandle: Int32;
}

export interface ErrorBase {
	nativeStackAndroid?: any[];
	userInfo: {
		errorMsg: string;
	};
	code?: string;
}

export interface EventError {
	errorMsg: string;
}
