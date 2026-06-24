import { StyleSheet, type NativeSyntheticEvent } from 'react-native';
import type {
	MapEventResponse,
	ErrorWithErrorMsg,
} from 'react-native-mapsforge-vtm';

export const sharedStyles = StyleSheet.create({
	info: {
		position: 'absolute',
		width: 300,
		backgroundColor: '#000000',
		bottom: 0,
		right: 0,
	},
	text: {
		color: '#fff',
	},
});

export const handleMapEvent = {
	onPause: (response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
		console.log('debug onPause', response?.nativeEvent); // debug
	},
	onResume: (response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
		console.log('debug onResume', response?.nativeEvent); // debug
	},
	onError: (
		response: NativeSyntheticEvent<Readonly<ErrorWithErrorMsg>>
	) => {
		console.log('debug onError', response?.nativeEvent); // debug
	},
};
