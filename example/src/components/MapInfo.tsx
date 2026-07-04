import { type FC, useState, useCallback } from 'react';
import {
	Pressable,
	StyleSheet,
	View,
	Text,
	type NativeSyntheticEvent,
} from 'react-native';
import type { MapEventResponse } from 'react-native-mapsforge-vtm';
import { useMapPosition } from 'react-native-mapsforge-vtm/reanimated';
import { sharedStyles } from '../sharedDeps';

// Collapsed by default — the raw onMapUpdate JSON dump is mostly useful while actively debugging
// a specific example, not something that should cover map content from the moment a screen opens.
const MapInfo: FC<{
	info?: MapEventResponse;
}> = ({ info }) => {
	const [open, setOpen] = useState(false);

	if (!open) {
		return (
			<Pressable
				style={[styles.toggle, { zIndex: 11 }]}
				onPress={() => setOpen(true)}
			>
				<Text style={styles.toggleText}>{'ℹ️ Info'}</Text>
			</Pressable>
		);
	}

	return (
		<View style={[sharedStyles.info, { zIndex: 11 }]}>
			<View style={styles.header}>
				<Text style={styles.toggleText}>{'Info'}</Text>
				<Pressable onPress={() => setOpen(false)}>
					<Text style={styles.toggleIcon}>{'✕'}</Text>
				</Pressable>
			</View>
			<Text style={sharedStyles.text}>
				{JSON.stringify(info, null, 4)}
			</Text>
		</View>
	);
};

/**
 * Hook that subscribes to map position updates for debug display.
 *
 * Internally composes {@link useMapPosition} so that every example
 * automatically wires reanimated shared values alongside the debug
 * overlay. The returned `handleMapUpdate` writes to shared values
 * first, then stores the raw event for the JSON dump.
 *
 * ## For library consumers: when to use which API
 *
 * | API | Best for |
 * |-----|----------|
 * | `MapContainer.onMapUpdate` prop | One-shot reactions, debug logging, any code that already calls `setState` |
 * | `useMapPosition()` (from `/reanimated`) | Smooth 60fps position displays, worklet-driven overlays — zero React re-renders for reads |
 * | `useMap().getPosition()` (imperative) | Button-triggered snapshots ("save current position"), non-continuous queries |
 *
 * `onMapUpdate` and `useMapPosition()` coexist — you can use both in the
 * same component. The bridge event fires every vtm frame at 60fps
 * (unthrottled); shared values receive the same writes and worklet
 * consumers read them on the UI thread without crossing the bridge.
 */
export const useMapInfo = () => {
	const {
		centerSv,
		zoomSv,
		bearingSv,
		tiltSv,
		handleMapUpdate: reanimatedHandleMapUpdate,
	} = useMapPosition();

	const [info, setInfo] = useState<MapEventResponse | undefined>(undefined);

	const handleMapUpdate = useCallback(
		(response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
			// Write to shared values (UI-thread-accessible, zero React re-renders).
			reanimatedHandleMapUpdate(response);
			// Mirror raw event for the JSON debug overlay.
			// console.log('debug onMapUpdate', response?.nativeEvent); // debug
			setInfo(response?.nativeEvent);
		},
		[reanimatedHandleMapUpdate]
	);

	return {
		handleMapUpdate,
		info,
		// Shared values — available for worklet consumers
		// (useDerivedValue, useAnimatedStyle, etc.) in any example.
		centerSv,
		zoomSv,
		bearingSv,
		tiltSv,
	};
};

const styles = StyleSheet.create({
	toggle: {
		position: 'absolute',
		bottom: 12,
		right: 12,
		zIndex: 9,
		backgroundColor: '#000000',
		paddingVertical: 8,
		paddingHorizontal: 12,
		borderRadius: 8,
	},
	toggleText: {
		color: '#fff',
		fontWeight: 'bold',
	},
	toggleIcon: {
		color: '#fff',
		fontWeight: 'bold',
		fontSize: 20,
		paddingVertical: 4,
		paddingHorizontal: 10,
		borderWidth: 1,
		borderColor: '#fff',
	},
	header: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'space-between',
		padding: 12,
	},
});

export default MapInfo;
