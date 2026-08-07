import { useCallback, useEffect, useMemo, useState } from 'react';
import {
	BackHandler,
	View,
	StyleSheet,
	Button,
	ScrollView,
	useWindowDimensions,
	Text,
	type LayoutChangeEvent,
} from 'react-native';

import * as examples from './examples';
import type { Example, ExampleCategory } from './types';

const categories: { key: ExampleCategory; label: string }[] = [
	{ key: 'layers', label: 'Layers' },
	{ key: 'mapControls', label: 'Map controls (useMap)' },
	{ key: 'gestures', label: 'Gestures & performance' },
	{ key: 'api', label: 'API & utilities' },
];

const allExamples = Object.values(examples) as Example[];

export default function App() {
	const [selectedExampleKey, setSelectedExampleKey] = useState<
		undefined | string
	>(undefined);

	const [contentHeight, setContentHeight] = useState<undefined | number>(
		undefined
	);

	const { width, height: windowHeight } = useWindowDimensions();
	const topBarHeight = 75;
	// Explicit height for the content area — avoids flex layout ambiguities
	// that prevent ScrollView from recognizing overflow.
	const contentViewHeight = windowHeight - topBarHeight;

	const selectedExample = useMemo(
		() => allExamples.find((example) => example.key === selectedExampleKey),
		[selectedExampleKey]
	);

	const handleContentLayout = useCallback((e: LayoutChangeEvent) => {
		const { height } = e.nativeEvent.layout;
		setContentHeight(height);
	}, []);

	// On the home screen, let the hardware/gesture back button fall through to its default
	// behavior (exiting the app); inside an example, intercept it to go back to the home screen
	// instead, mirroring what the on-screen Back button already does.
	useEffect(() => {
		const subscription = BackHandler.addEventListener(
			'hardwareBackPress',
			() => {
				if (undefined === selectedExampleKey) {
					return false;
				}
				setSelectedExampleKey(undefined);
				return true;
			}
		);
		return () => subscription.remove();
	}, [selectedExampleKey]);

	return (
		<View style={styles.container}>
			{!!selectedExample && (
				<View style={styles.topBar}>
					<Button
						title={'Back'}
						onPress={() => setSelectedExampleKey(undefined)}
					/>
					<Text style={styles.title}>{selectedExample.label}</Text>
				</View>
			)}

			<View
				style={[styles.content, { height: contentViewHeight }]}
				onLayout={handleContentLayout}
			>
				{undefined === selectedExample && (
					<ScrollView
						style={styles.examplesScroll}
						contentContainerStyle={styles.examplesList}
					>
						{categories.map(({ key, label }) => {
							const categoryExamples = allExamples.filter(
								(example) => example.category === key
							);
							if (!categoryExamples.length) {
								return null;
							}
							return (
								<View
									key={key}
									style={styles.categorySection}
								>
									<Text style={styles.categoryTitle}>
										{label}
									</Text>
									{categoryExamples.map((example) => (
										<View key={example.key}>
											<Button
												title={example.label}
												onPress={() =>
													setSelectedExampleKey(
														example.key
													)
												}
											/>
										</View>
									))}
								</View>
							);
						})}
					</ScrollView>
				)}

				{!!selectedExample && contentHeight && (
					<selectedExample.ExampleComponent
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
	},
	topBar: {
		alignItems: 'center',
		width: '100%',
		flexDirection: 'row',
		height: 75,
		zIndex: 99,
		gap: 16,
		paddingHorizontal: 16,
	},
	title: {
		color: '#fff',
		fontSize: 16,
		fontWeight: 'bold',
	},
	content: {
		width: '100%',
		flex: 1,
	},
	examplesScroll: {
		width: '100%',
	},
	examplesList: {
		alignItems: 'center',
		gap: 24,
		paddingVertical: 16,
	},
	categorySection: {
		alignItems: 'center',
		gap: 8,
	},
	categoryTitle: {
		color: '#fff',
		fontSize: 18,
		fontWeight: 'bold',
	},
});
