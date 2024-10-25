/**
 * External dependencies
 */
import React, { useState } from 'react';
import {
	Button,
	FlatList,
	useWindowDimensions,
	View,
} from 'react-native';

/**
 * Internal dependencies
 */
import ModalWrapper from './ModalWrapper.jsx';

const PickerModalControl = ( {
	disabled,
	buttonLabel,
	buttonLabelFallback,
	options,
	values,
	onChange,
	closeOnChange,
	headerLabel,
	itemHeight,
	onSelectAllNone,
	selectAllNoneLabel,
} ) => {

	const [modalVisible, setModalVisible] = useState( false );

	const { height, width } = useWindowDimensions();

	itemHeight = itemHeight || 45;

	return <>

		<Button
			disabled={ disabled }
			key="button"
			onPress={ () => {
				setModalVisible( true );
			} }
			title={ buttonLabel || options.find( opt => values.includes( opt.value ) )?.label || buttonLabelFallback || '' }
		/>

		{ modalVisible && <ModalWrapper
			key="modal"
			setModalVisible={ setModalVisible }
			modalVisible={ modalVisible }
			headerLabel={ headerLabel }
			selectAllNoneLabel={ selectAllNoneLabel }
			onSelectAllNone={ onSelectAllNone }
			style={ { width: width * ( 2 / 3 ) } }
		>
			<FlatList
				data={ options }
				style={ { maxHeight: Math.min( height * 0.6, itemHeight * options.length ) } }
				getItemLayout={ ( data, index ) => ( {
					length: itemHeight,
					offset: itemHeight * index,
					index,
				} ) }
				renderItem={ ( { item } ) => <View
					style={ { height: itemHeight } }
				>
					<Button
						disabled={ disabled || item.disabled }
						title={ item.label }
						color={ values.includes( item.value ) ? '#841584' : '' }
						onPress={ e => {
							e.stopPropagation();
							onChange( item.value );
							if ( closeOnChange ) {
								setModalVisible( false );
							}
						} }
					/>
				</View> }
				keyExtractor={ item => item.value }
			/>

		</ModalWrapper> }
	</>;

};

export default PickerModalControl;
