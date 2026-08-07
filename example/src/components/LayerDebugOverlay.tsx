/**
 * External dependencies
 */
import { type FC } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LayerDebugTree, useLayerDebugInfo } from 'react-native-mapsforge-vtm';

/**
 * Internal dependencies
 */
import { sharedStyles } from '../sharedDeps';

/**
 * Default height of the debug overlay in points. Examples use this constant
 * when calculating drawer max-height to avoid overlapping the overlay.
 */
export const LAYER_DEBUG_OVERLAY_HEIGHT = 170;

/**
 * Developer debugging overlay that shows live layer architecture diagnostics.
 *
 * Renders inside {@link MapContainer} (so it can access {@link MapHandleContext})
 * and displays JS layer count, native fragment/layer count, draw call count,
 * grouping status, and a collapsible {@link LayerDebugTree}.
 *
 * Tree-shaken in production: wrap with {@code __DEV__} at the call site.
 *
 * ```tsx
 * <MapContainer>
 *   {__DEV__ && <LayerDebugOverlay />}
 *   {children}
 * </MapContainer>
 * ```
 */
const LayerDebugOverlay: FC = () => {
	const info = useLayerDebugInfo();

	return (
		<View
			pointerEvents="box-none"
			style={styles.container}
		>
			<View style={styles.row}>
				<Text style={sharedStyles.text}>JS: {info.layerCount}</Text>
				<Text style={sharedStyles.text}>
					Native: {info.estimatedNativeLayerCount}
				</Text>
				<Text style={sharedStyles.text}>
					Grouped: {info.sharedLayerActive ? 'yes' : 'no'}
				</Text>
			</View>
			<LayerDebugTree maxHeight={130} />
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		position: 'absolute',
		bottom: 0,
		left: 0,
		right: 0,
		zIndex: 10,
		height: LAYER_DEBUG_OVERLAY_HEIGHT,
		backgroundColor: 'rgba(0,0,0,0.85)',
		paddingHorizontal: 10,
		paddingVertical: 4,
		borderTopWidth: 1,
		borderTopColor: '#333333',
	},
	row: {
		flexDirection: 'row',
		flexWrap: 'wrap',
		gap: 12,
		marginBottom: 4,
	},
});

export default LayerDebugOverlay;
