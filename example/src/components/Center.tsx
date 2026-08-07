import type { FC } from 'react';
import { View, Text, StyleSheet } from 'react-native';

const Center: FC<{ width: number; height: number }> = ({ width, height }) => {
	return (
		<View
			pointerEvents="none"
			style={[styles.container, { width, height }]}
		>
			<Text style={styles.text}>X</Text>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		position: 'absolute',
		top: 0,
		left: 0,
		justifyContent: 'center',
		alignItems: 'center',
	},
	text: {
		color: 'red',
		fontSize: 25,
		fontWeight: 'bold',
	},
});

export default Center;
