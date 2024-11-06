/**
 * External dependencies
 */
import React from 'react';
import {
	Text,
	useColorScheme,
	useWindowDimensions,
	View,
	Pressable,
} from 'react-native';
import Modal from 'react-native-root-modal';

/**
 * Internal dependencies
 */
import Button from './Button.jsx';

const ModalWrapper = ( {
	modalVisible,
	setModalVisible,
	headerLabel,
	children,
	style,
	onSelectAllNone,
	selectAllNoneLabel,
} ) => {

	const isDarkMode = useColorScheme() === 'dark';

	const { height, width } = useWindowDimensions();

	const absFullStyle = {
		top: 0,
		right: 0,
		bottom: 0,
		left: 0,
		position: 'absolute',
		width,
		height,
	};

	return <Modal
		style={ {
			...absFullStyle,
			zIndex: 99999999,
		} }
		visible={ modalVisible }

	>
		<Pressable
			onPress={ () => {
				setModalVisible( false );
			} }
			style={ {
				...absFullStyle,
				backgroundColor: 'rgba(0, 0, 0, 0.5)',
				justifyContent: 'center',
				alignItems: 'center',

			} }
		>
			<Pressable
				onPress={ event => {
					event.stopPropagation();
				} }
			>

				<View
					style={ {
						backgroundColor: isDarkMode ? 'black' : '#eee',
						minHeight: 200,
						minWidth: width * ( 2 / 3 ),
						maxWidth: width * 0.9,
						maxHeight: height * 0.9,
						padding: 20,
						...style,
					} }
				>

					<View
						style={ {
							flexDirection: 'row',
							justifyContent: 'space-between',
							alignItems: 'center',
						} }
					>

						<Text style={ {
							...( style.color && { color: style.color } ),
							...( style.backgroundColor && { backgroundColor: style.backgroundColor } ),
						} }>{ headerLabel || '' }</Text>

						{ onSelectAllNone && <Button
							onPress={ onSelectAllNone }
							title={ selectAllNoneLabel || 'All/None' }
						/> }
						<Button
							onPress={ () => setModalVisible( false ) }
							title="Close"
						/>
					</View>

					<View
						style={ { marginTop: 20 } }
					>
						{ children }
					</View>

				</View>

			</Pressable>

		</Pressable>

	</Modal>;

};

export default ModalWrapper;
