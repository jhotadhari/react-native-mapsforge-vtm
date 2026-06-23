import { useCallback, useMemo, useState } from 'react';
import {
	View,
	StyleSheet,
	Button,
	useWindowDimensions,
	Text,
	type LayoutChangeEvent,
} from 'react-native';

import * as examples from './examples';
import { get } from 'lodash-es';
import type { Example } from './types';

export default function App() {
	const [selectedExampleKey, setSelectedExampleKey] = useState<
		undefined | string
	>(undefined);

	const [contentHeight, setContentHeight] = useState<undefined | number>(
		undefined
	);

	const { width } = useWindowDimensions();

	const ExampleComponent = useMemo(
		() =>
			selectedExampleKey
				? (get(examples, [selectedExampleKey, 'ExampleComponent']) as
						| undefined
						| Example['ExampleComponent'])
				: undefined,
		[selectedExampleKey]
	);

	const handleContentLayout = useCallback((e: LayoutChangeEvent) => {
		const { height } = e.nativeEvent.layout;
		setContentHeight(height);
	}, []);

	return (
		<View style={styles.container}>
			<View style={styles.topBar}>
				<Button
					title={'Back'}
					onPress={() => setSelectedExampleKey(undefined)}
				/>
			</View>

			<View
				style={styles.content}
				onLayout={handleContentLayout}
			>
				{undefined === ExampleComponent && (
					<View style={styles.examplesList}>
						<Text style={styles.text}>{'Choose example'}</Text>

						{Object.values(examples).map(({ key, label }) => {
							return (
								<View key={key}>
									<Button
										title={label}
										onPress={() =>
											setSelectedExampleKey(key)
										}
									/>
								</View>
							);
						})}
					</View>
				)}

				{undefined !== ExampleComponent && contentHeight && (
					<ExampleComponent
						height={contentHeight}
						width={width}
					/>
				)}
			</View>
		</View>
	);
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		alignItems: 'center',
		justifyContent: 'space-between',
	},
	topBar: {
		alignItems: 'center',
		width: '100%',
		flexDirection: 'row',
		height: 75,
		zIndex: 99,
	},
	content: {
		alignItems: 'center',
		justifyContent: 'space-around',
		gap: 16,
		width: '100%',
		flexGrow: 1,
	},
	examplesList: {
		alignItems: 'center',
		justifyContent: 'space-between',
		gap: 16,
	},
	text: {
		color: '#fff',
	},
});
