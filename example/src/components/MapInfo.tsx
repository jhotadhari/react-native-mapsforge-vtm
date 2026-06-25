import { type FC, useState, useCallback } from 'react';
import {
	Pressable,
	StyleSheet,
	View,
	Text,
	type NativeSyntheticEvent,
} from 'react-native';
import type { MapEventResponse } from 'react-native-mapsforge-vtm';
import { sharedStyles } from '../sharedDeps';

// Collapsed by default -- the raw onMapUpdate JSON dump is mostly useful while actively debugging
// a specific example, not something that should cover map content from the moment a screen opens.
const MapInfo: FC<{
	info?: MapEventResponse;
}> = ({ info }) => {
	const [open, setOpen] = useState(false);

	if (!open) {
		return (
			<Pressable
				style={styles.toggle}
				onPress={() => setOpen(true)}
			>
				<Text style={styles.toggleText}>{'ℹ️ Info'}</Text>
			</Pressable>
		);
	}

	return (
		<View style={sharedStyles.info}>
			<View style={styles.header}>
				<Text style={styles.toggleText}>{'Info'}</Text>
				<Pressable onPress={() => setOpen(false)}>
					<Text style={styles.toggleText}>{'✕'}</Text>
				</Pressable>
			</View>
			<Text style={sharedStyles.text}>
				{JSON.stringify(info, null, 4)}
			</Text>
		</View>
	);
};

export const useMapInfo = () => {
	const [info, setInfo] = useState<MapEventResponse | undefined>(undefined);

	const handleMapUpdate = useCallback(
		(response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
			// console.log('debug onMapUpdate', response?.nativeEvent); // debug
			setInfo(response?.nativeEvent);
		},
		[]
	);
	return {
		handleMapUpdate,
		info,
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
	header: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'space-between',
		padding: 12,
	},
});

export default MapInfo;
