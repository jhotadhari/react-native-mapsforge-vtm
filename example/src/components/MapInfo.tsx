import { type FC, useState, useCallback } from 'react';
import { View, Text, type NativeSyntheticEvent } from 'react-native';
import type { MapEventResponse } from 'react-native-mapsforge-vtm';
import { sharedStyles } from '../sharedDeps';

const MapInfo: FC<{
	info?: MapEventResponse;
}> = ({ info }) => {
	return (
		<View style={sharedStyles.info}>
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

export default MapInfo;
