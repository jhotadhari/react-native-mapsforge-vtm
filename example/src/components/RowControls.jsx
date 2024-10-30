/**
 * External dependencies
 */
import React from 'react';
import {
	Text,
	View,
} from 'react-native';

/**
 * Internal dependencies
 */
import Button from './Button.jsx';

export const rowBtnStyle = {
    marginRight: 10,
    width: 35,
    textAlign: 'center',
};

export const ControlWrapper = ( {
    containerStyle,
    style,
    label,
    value,
    textAppend,
    children,
} ) => {
    return <View style={ {
        flexDirection: 'row',
        width: '90%',
        alignItems: 'center',
        ...containerStyle,
    } }>
        <Text style={ {...style, marginRight: 10, minWidth: 100 } }>{ label }:</Text>
        { value !== null && <Text style={ {...style, marginRight: 10, minWidth: 30  } }>{ value }</Text> }
        { children }
        { textAppend && <Text style={ { ...style, marginLeft: 10 }} >{ textAppend }</Text> }
    </View>;
};

export const ButtonControl = ( {
    containerStyle,
    style,
    promiseQueueState,
    label,
    textAppend,
    buttonLabel,
    buttonStyle,
    onPress,
} ) => {
    return <ControlWrapper
        containerStyle={ containerStyle }
        style={ style }
        label={ label }
        textAppend={ textAppend }
    >
        <Button
            style={ { ...rowBtnStyle, ...buttonStyle } }
            disabled={ promiseQueueState > 0 }
            onPress={ onPress }
            title={ buttonLabel }
        />
    </ControlWrapper>;
};

export const PlusMinusControl = ( {
    containerStyle,
    style,
    promiseQueueState,
    label,
    value,
    minValue,
    setValue,
    step,
    textAppend,
} ) => {
    step = step ? step : 1;
    return <ControlWrapper
        containerStyle={ containerStyle }
        style={ style }
        label={ label }
        value={ value }
        textAppend={ textAppend }
    >
        <Button
            style={ rowBtnStyle }
            disabled={ promiseQueueState > 0 }
            onPress={ () => setValue( value + step ) }
            title=' + '
        />
        <Button
            style={ rowBtnStyle }
            disabled={ promiseQueueState > 0 || ( ( value - step ) < minValue ) }
            onPress={ () => setValue( Math.max( minValue, value - step ) ) }
            title=' - '
        />

    </ControlWrapper>;
};