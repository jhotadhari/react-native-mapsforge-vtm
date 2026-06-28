import type { FC } from 'react';
import { View, Text } from 'react-native';

const Center: FC<{ width: number; height: number }> = ({ width, height }) => {
	const size = 25;

	return (
		<View
			pointerEvents="none"
			style={{
				position: 'absolute',
				top: 0,
				left: 0,
				justifyContent: 'center',
				alignItems: 'center',
				width,
				height,
			}}
		>
			<Text
				style={{
					color: 'red',
					fontSize: size,
					fontWeight: 'bold',
				}}
			>
				X
			</Text>
		</View>
	);
};

export default Center;
