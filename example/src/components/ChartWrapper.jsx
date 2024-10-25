/**
 * External dependencies
 */
import React, {
    useState,
} from 'react';
import {
	Button,
	Text,
	useColorScheme,
	useWindowDimensions,
	View,
} from 'react-native';
// import Modal from 'react-native-root-modal';

import { Gesture, GestureDetector } from 'react-native-gesture-handler';

import {
    Chart,
    VerticalAxis,
    HorizontalAxis,
    Line
} from 'react-native-responsive-linechart'

const ChartWrapper = ( {
    coordinates,
    coordinatesSimplified,
} ) => {

    const [show,setShow] = useState( false );

    let yMin = null;
    let yMax = null;

    const coordsToData = coords => [...coords].map( ( coord, index ) => {
        const x = coord[3] / 1000;    // accumulatedDistance
        if ( yMin === null || yMax === null ) {
            yMin = coord[2];
            yMax = coord[2];
        } else {
            yMin = Math.min( yMin, coord[2] );
            yMax = Math.max( yMax, coord[2] );
        }
        return {
            x,
            y: coord[2],    // altitude
        };
    } );

    const data1 = coordsToData( coordinates );
    const data2 = coordsToData( Array.isArray( coordinatesSimplified ) ? coordinatesSimplified : [] );

    let xMin = 0;
    let xMax = data1.length > 0 ? Math.round( data1[data1.length-1].x ) + 1 : 0;

    let tickValuesX = Array.apply( null, Array( Math.round( xMax / 10 ) + 1 ) ).map( ( a, index ) => index * 10 );
    let tickValuesY = Array.apply( null, Array( Math.round( ( yMax - yMin ) / 100 ) + 1 ) ).map( ( a, index ) => ( index * 100 ) + ( Math.round( yMin / 100 ) * 100 ) );


    const tap = Gesture.Tap()
        // .numberOfTaps(2)
        .onStart(() => {
            console.log('debug e Yay, double tap!');
        });

	return  <View style={ {
        height: 200
    } }>

        <View style={ {
            transform: [{translateY: -65}],
            position: 'absolute',
            right: 0,
            top: 0,
            zIndex: 99,
            backgroundColor: '#eee',
        } }>
            <View
                style={ {
                    width: 150,
                    // alignItems: 'center',
                    backgroundColor: '#000',
                    padding: 10,
                } }
            >
                <View style={ {
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                } } >
                    <Text>coordinates</Text>
                    <Text>{ data1.length }</Text>
                </View>

                <View style={ {
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                } } >
                    <Text>simplified </Text>
                    <Text>{ data2 && data2.length }</Text>
                </View>
            </View>
        </View>

        { ! show && <View style={ {
            width: '100%',
            height: '100%',
            justifyContent: 'space-around',
            alignItems: 'center',
        } }>
            <Button
                onPress={ () => setShow( true ) }
                title="show chart"
            />
        </View> }


        { show && ( data1.length > 0 || data1.length > 0 ) && <Chart
            style={ {
                height: 200,
                width: '100%',
                // color: '#fff',
                backgroundColor: '#eee',
            } }
            xDomain={ {
                min: xMin,
                max: xMax,
            } }
            yDomain={ {
                min: Math.round( yMin ) - 1,
                max: Math.round( yMax ) + 1,
            } }
            padding={ {
                left: 32.5,
                top: 15,
                bottom: 15,
                right: 15,
            } }
            // viewport={ {
            //     size: {
            //         width: xMax-10,
            //         height: yMax-yMin-100,
            //     },
            // } }
        >
            <VerticalAxis
                tickValues={ tickValuesY }
            />
            <HorizontalAxis
                tickValues={ tickValuesX }
            />
            { data1 && data1.length > 0 && <Line data={ data1 } smoothing="none" theme={ { stroke: { color: 'red', width: 5 } } } /> }
            { data2 && data2.length > 0 && <Line data={ data2 } smoothing="none" theme={ { stroke: { color: 'blue', width: 3 } } } /> }
        </Chart> }
    </View>;

};

export default ChartWrapper;
