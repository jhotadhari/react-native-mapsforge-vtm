/**
 * External dependencies
 */
import React from 'react';
import {
	// Button,
	View,
} from 'react-native';

/**
 * Internal dependencies
 */
import Button from './Button.jsx';

const TopBar = ( {
    setBarTopHeight,
    setSelectedExample,
    width,
    children,
    barTopPadding,
    style,
} ) => {
    return <View
        onLayout={ e => {
            const { height } = e.nativeEvent.layout;
            setBarTopHeight( height );
        } }
        style={ {
            ...style,
            position: 'relative',
            alignItems: 'center',
            width,
        } }
    >
        <View
            style={ {
                flexDirection: 'row',
                width,
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                padding: barTopPadding,
            } }
        >
            <Button
                onPress={ () => setSelectedExample( null ) }
                title=" < "
            />
            <View style={ {
                flexDirection: 'column',
                flexBasis: '90%',
                flexShrink: 0,
                flexGrow: 1,
                paddingLeft: 20,
                paddingRight: 20,
                zIndex: 1,
            } }>
                { children }
            </View>
        </View>
    </View>;
};

export default TopBar;
