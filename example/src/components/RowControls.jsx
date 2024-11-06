/**
 * External dependencies
 */
import React from 'react';
import {
	Text,
	View,
	PixelRatio,
} from 'react-native';

/**
 * Internal dependencies
 */
import Button from './Button.jsx';
import ModalControl from './ModalControl.jsx';

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
    valueMinWidth,
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
        { value !== null && <Text style={ {...style, marginRight: 10, minWidth: valueMinWidth || 30  } }>{ value }</Text> }
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
    valueMinWidth,
} ) => {
    return <ControlWrapper
        containerStyle={ containerStyle }
        style={ style }
        label={ label }
        textAppend={ textAppend }
        valueMinWidth={ valueMinWidth }
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
    valueMinWidth,
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
        valueMinWidth={ valueMinWidth }
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

export const EventRowControl = ( {
    style,
    promiseQueueState,
    mapViewNativeNodeHandle,
    layerUuid,
    width,
    valueMinWidth,
    mapHeight,
    module,
} ) => <ControlWrapper
    containerStyle={ { marginBottom: 10 } }
    style={ style }
    label={ 'Events' }
    value={ '' }
    valueMinWidth={ valueMinWidth }
>
    <Button
        style={ {
            marginRight: 10,
            width: 81,
            textAlign: 'center',
        } }
        disabled={ promiseQueueState > 0 || ! layerUuid }
        onPress={ () => {
            if ( mapViewNativeNodeHandle && layerUuid ) {
                module.triggerEvent(
                    mapViewNativeNodeHandle,
                    layerUuid,
                    PixelRatio.getPixelSizeForLayoutSize( width ) / 2,
                    PixelRatio.getPixelSizeForLayoutSize( mapHeight ) / 2
                ).catch( err => console.log( 'ERROR', err ) );
            }
        } }
        title={ 'trigger' }
    />

    <ModalControl
        style={ style }
        buttonStyle={ rowBtnStyle }
        buttonLabel={ '?' }
        headerLabel={ 'Events' }
        disabled={ promiseQueueState > 0 }
    >
        <Text style={ {...style, marginBottom: 10} }>
            This layer supports press, longPress and doubleTab events.
        </Text>
        <Text style={ {...style, marginBottom: 10} }>
            Furthermore an event can be triggered at any map position, here the center.
        </Text>
        <Text style={ {...style, marginBottom: 10} }>
            The gesture buffer distance can be controlled.
        </Text>
        <Text style={ style }>
            The response includes: event distance to the path, nearest point at path and the event position.
        </Text>
    </ModalControl>

</ControlWrapper>;