/**
 * External dependencies
 */
import { NativeModules } from 'react-native';

/**
 * Internal dependencies
 */
import { LINKING_ERROR } from './constants';

export const MapContainerModule = NativeModules.MapContainerModule
	? NativeModules.MapContainerModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerMapsforgeModule = NativeModules.MapLayerMapsforgeModule
	? NativeModules.MapLayerMapsforgeModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerBitmapTileModule = NativeModules.MapLayerBitmapTileModule
	? NativeModules.MapLayerBitmapTileModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerMBTilesBitmapModule = NativeModules.MapLayerMBTilesBitmapModule
	? NativeModules.MapLayerMBTilesBitmapModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerScalebarModule = NativeModules.MapLayerScalebarModule
	? NativeModules.MapLayerScalebarModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerPathModule = NativeModules.MapLayerPathModule
	? NativeModules.MapLayerPathModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

// export const MapMarkerModule = NativeModules.MapMarkerModule
// 	? NativeModules.MapMarkerModule
// 	: new Proxy(
// 		{},
// 		{
// 			get() {
// 				throw new Error( LINKING_ERROR );
// 			},
// 		},
// 	);

// export const MapFeatureReactModule = NativeModules.MapFeatureReactModule
// 	? NativeModules.MapFeatureReactModule
// 	: new Proxy(
// 		{},
// 		{
// 			get() {
// 				throw new Error( LINKING_ERROR );
// 			},
// 		},
// 	);

// export const MapPolylineModule = NativeModules.MapPolylineModule
// 	? NativeModules.MapPolylineModule
// 	: new Proxy(
// 		{},
// 		{
// 			get() {
// 				throw new Error( LINKING_ERROR );
// 			},
// 		},
// 	);
