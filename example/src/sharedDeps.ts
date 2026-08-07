import { StyleSheet, type NativeSyntheticEvent } from 'react-native';
import type {
	ErrorBase,
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
		zIndex: 11,
	},
	text: {
		color: '#fff',
	},
	boldText: {
		fontWeight: 'bold',
	},
});

// Native promise rejections (e.g. from useMap()'s methods) reject via Utils.promiseReject's
// `reject("error", { errorMsg })` pattern, which never sets RN's top-level rejection message --
// so err.message is always RN's generic "Error not specified." default. The real text lives at
// err.userInfo.errorMsg (same shape reportNativeError reads). Plain JS errors (e.g. useMap()'s
// own "nativeNodeHandle not ready yet" check) still have a normal err.message, so that's kept as
// a fallback.
export const formatActionError = (err: unknown): string => {
	const userInfo = (err as Partial<ErrorBase> | undefined)?.userInfo;
	if (userInfo?.errorMsg) {
		return userInfo.errorMsg;
	}
	return err instanceof Error ? err.message : String(err);
};

export const handleMapEvent = {
	onPause: (response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
		console.log('debug onPause', response?.nativeEvent); // debug
	},
	onResume: (response: NativeSyntheticEvent<Readonly<MapEventResponse>>) => {
		console.log('debug onResume', response?.nativeEvent); // debug
	},
	onError: (response: NativeSyntheticEvent<Readonly<ErrorWithErrorMsg>>) => {
		console.log('debug onError', response?.nativeEvent); // debug
	},
};
