import { codegenNativeComponent } from 'react-native';
import type {
	Double,
	DirectEventHandler,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { ViewProps } from 'react-native';
import type { Dispatch, SetStateAction } from 'react';

/*
 * Type should be redeclared because of codegen ts parser doesn't allow imported type
 * [comments](https://github.com/reactwg/react-native-new-architecture/discussions/91#discussioncomment-4282452)
 *
 * Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
 */
type Position = ReadonlyArray<Double>;

export interface MapEventResponse {
	zoomLevel?: Double;
	bearing?: Double;
	tilt?: Double;
	center?: Double[]; // type Position - named aliases aren't resolved for event payloads by codegen.
	viewportWidth?: Double;
	viewportHeight?: Double;
}

interface ErrorWithErrorMsg {
	errorMsg: string;
}

export interface TapEventResponse {
	lng: Double;
	lat: Double;
	x: Double;
	y: Double;
}

// Reuse the same shape for longPress (codegen can't follow type aliases across
// interfaces, so we declare a separate interface with the same fields).
export interface LongPressEventResponse {
	lng: Double;
	lat: Double;
	x: Double;
	y: Double;
}

interface MapViewProps extends ViewProps {
	width?: Double;
	height?: Double;
	center?: Position;
	zoomLevel?: Double;
	zoomMin?: Double;
	zoomMax?: Double;
	moveEnabled?: boolean;
	tiltEnabled?: boolean;
	rotationEnabled?: boolean;
	zoomEnabled?: boolean;
	tilt?: Double;
	minTilt?: Double;
	maxTilt?: Double;
	bearing?: Double;
	minBearing?: Double;
	maxBearing?: Double;
	roll?: Double;
	minRoll?: Double;
	maxRoll?: Double;
	emitsMapUpdateEvents: boolean;
	onMapCreated?: DirectEventHandler<Readonly<{}>> | null;
	onMapUpdate?: DirectEventHandler<Readonly<MapEventResponse>> | null;
	onPause?: DirectEventHandler<Readonly<MapEventResponse>> | null;
	onResume?: DirectEventHandler<Readonly<MapEventResponse>> | null;
	onError?: DirectEventHandler<Readonly<ErrorWithErrorMsg>> | null;
	onTap?: DirectEventHandler<Readonly<TapEventResponse>> | null;
	onLongPress?: DirectEventHandler<Readonly<LongPressEventResponse>> | null;
}

export type MapContainerProps = {
	children?: React.ReactNode;
	nativeNodeHandle?: null | number;
	setNativeNodeHandle?: null | Dispatch<SetStateAction<number | null>>;
	width?: null | MapViewProps['width'];
	height?: null | MapViewProps['height'];
	center?: MapViewProps['center'];
	zoomLevel?: MapViewProps['zoomLevel'];
	zoomMin?: MapViewProps['zoomMin'];
	zoomMax?: MapViewProps['zoomMax'];
	moveEnabled?: MapViewProps['moveEnabled'];
	tiltEnabled?: MapViewProps['tiltEnabled'];
	rotationEnabled?: MapViewProps['rotationEnabled'];
	zoomEnabled?: MapViewProps['zoomEnabled'];
	tilt?: MapViewProps['tilt'];
	minTilt?: MapViewProps['minTilt'];
	maxTilt?: MapViewProps['maxTilt'];
	bearing?: MapViewProps['bearing'];
	minBearing?: MapViewProps['minBearing'];
	maxBearing?: MapViewProps['maxBearing'];
	roll?: MapViewProps['roll'];
	minRoll?: MapViewProps['minRoll'];
	maxRoll?: MapViewProps['maxRoll'];
	emitsMapUpdateEvents?: null | MapViewProps['emitsMapUpdateEvents'];
	onMapUpdate?: MapViewProps['onMapUpdate'];
	onPause?: MapViewProps['onPause'];
	onResume?: MapViewProps['onResume'];
	onError?: MapViewProps['onError'];
	onTap?: MapViewProps['onTap'];
	onLongPress?: MapViewProps['onLongPress'];
};

export default codegenNativeComponent<MapViewProps>('MapsforgeVtmView');
