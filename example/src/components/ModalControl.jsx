/**
 * External dependencies
 */
import React, { useState } from 'react';
import { useWindowDimensions } from 'react-native';

/**
 * Internal dependencies
 */
import ModalWrapper from './ModalWrapper.jsx';
import Button from './Button.jsx';

const ModalControl = ( {
	disabled,
	buttonLabel,
	buttonStyle,
	headerLabel,
	children,
	style,
} ) => {

	const [modalVisible, setModalVisible] = useState( false );

	const { width } = useWindowDimensions();

	return <>
		<Button
			style={ {
				zIndex: 1,
				position: 'relative',
                ...buttonStyle,
			} }
			disabled={ disabled }
			key="button"
			onPress={ () => {
				setModalVisible( true );
			} }
			title={ buttonLabel || '' }
		/>

		{ modalVisible && <ModalWrapper
			key="modal"
			setModalVisible={ setModalVisible }
			modalVisible={ modalVisible }
			headerLabel={ headerLabel }
			style={ { width: width * ( 2 / 3 ), ...( style ? style : {} ) } }
		>
			{ children }
		</ModalWrapper> }
	</>;

};

export default ModalControl;
