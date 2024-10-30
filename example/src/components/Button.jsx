/**
 * External dependencies
 */
import React from 'react';
import {
	TouchableOpacity,
	useColorScheme,
	Text,
} from 'react-native';

const Button = ( {
    onPress,
    style,
    title,
    disabled,
    active,
} ) => {
	const isDarkMode = useColorScheme() === 'dark';
    return <TouchableOpacity
        onPress={ disabled ? null : onPress }
        disabled={ disabled }
    >
        <Text style={ {
            padding: 10,
            backgroundColor: ! isDarkMode
                ? ( active ? '#841584' : 'black' )
                : ( active ? '#841584' : '#eee' ),
            color: ! isDarkMode ? '#eee' : 'black',
            opacity: disabled ? 0.5 : 1,
            ...style,
        } }>{ title }</Text>
    </TouchableOpacity>;
};

export default Button;
