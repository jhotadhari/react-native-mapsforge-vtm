/**
 * External dependencies
 */
import React, { useState } from 'react';
import {
	FlatList,
	useWindowDimensions,
	View,
} from 'react-native';

/**
 * Internal dependencies
 */
import ModalWrapper from './ModalWrapper.jsx';
import Button from './Button.jsx';

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
	NoOptionsComponent,
	extraOptions,
	ExtraOptionsHeader,
	OptionsHeader,
} ) => {

	const [modalVisible, setModalVisible] = useState( false );

	const { height, width } = useWindowDimensions();

	itemHeight = itemHeight || 45;

	const OptionsFlatList = ( {
		style,
		options,
	} ) => {
		return <FlatList
			data={ options }
			style={ {
				...style,
				maxHeight: Math.min( height * 0.7, itemHeight * options.length ),
				zIndex: 999999,
			} }
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
					active={ values.includes( item.value ) }
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
	};

	return <>

		<Button
			style={ {
				zIndex: 1,
				position: 'relative',
			} }
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

			<View style={ {
				maxHeight: Math.min( height * 0.8, itemHeight * ( options.length + ( extraOptions ? extraOptions.length : 0 ) ) ),
			} } >
				{ extraOptions && extraOptions.length > 0 && ExtraOptionsHeader && <ExtraOptionsHeader/> }
				{ extraOptions && extraOptions.length > 0 && <OptionsFlatList style={ { marginBottom: 10 } } options={ extraOptions } /> }

				{ options.length > 0 && OptionsHeader && <OptionsHeader/> }
				{ options.length > 0 && <OptionsFlatList options={ options } /> }
			</View>

			{ ! options.length && NoOptionsComponent && <NoOptionsComponent/>}

		</ModalWrapper> }
	</>;

};

export default PickerModalControl;
