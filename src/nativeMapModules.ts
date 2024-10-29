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

export const MapLayerPathSlopeGradientModule = NativeModules.MapLayerPathSlopeGradientModule
	? NativeModules.MapLayerPathSlopeGradientModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);

export const MapLayerHillshadingModule = NativeModules.MapLayerHillshadingModule
	? NativeModules.MapLayerHillshadingModule
	: new Proxy(
		{},
		{
			get() {
				throw new Error( LINKING_ERROR );
			},
		},
	);
