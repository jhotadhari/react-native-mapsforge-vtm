/**
 * External dependencies
 */
import React, {
    useState,
} from 'react';
import {
	Text,
	useWindowDimensions,
	ToastAndroid,
	PixelRatio,
	View,
} from 'react-native';

/**
 * react-native-mapsforge-vtm dependencies
 */
import {
	MapContainer,
	LayerBitmapTile,
	LayerScalebar,
	LayerMarker,
	Marker,
	usePromiseQueueState,
    nativeMapModules,
} from 'react-native-mapsforge-vtm';
const { MapLayerMarkerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import { randomNumber } from '../utils.js';
import Center from '../components/Center.jsx';
import TopBar from '../components/TopBar.jsx';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';
import Button from '../components/Button.jsx';

const getRandomPositions = length => Array.apply( null, Array( length ) ).map( () => ( {
    lng: randomNumber( -77, -76 ),
    lat: randomNumber( -10, -9 ),
} ) );

const ExampleMarker = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );
	const [markerLayerUuid, setMarkerLayerUuid] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

    const [positions,setPositions] = useState( getRandomPositions( 100 ) );
    const randomizePositions = () => {
        setPositions( getRandomPositions( positions.length ) );
    };

    if ( null === appDirs ) {
        return null;
    }

    const symbols = [
        {},
        {
            height: 100,
            textMargin: 20,
            textPositionY: 0,
            textStrokeWidth: 3,
            filePath: appDirs.marker + '/marker_red.png',
            hotspotPlace: 'BOTTOM_CENTER',
        },
        {
            width: 80,
            height: 80,
            textMargin: 20,
            textStrokeWidth: 3,
            textPositionY: 7,
            strokeColor: '#ff0000',
            fillColor: '#eeeeee',
            strokeWidth: 5,
            hotspotPlace: 'CENTER',
        },
        {
            height: 100,
            textMargin: 20,
            textPositionY: 0,
            textStrokeWidth: 3,
            filePath: appDirs.marker + '/marker.svg',
            hotspotPlace: 'BOTTOM_CENTER',
        },
    ];

    const AttributionComponent = tileOptions[0].attribution;

	return <View style={ {
        height,
        width,
    } }>

        <TopBar
            setBarTopHeight={ setBarTopHeight }
            setSelectedExample={ setSelectedExample }
            barTopPadding={ barTopPadding }
            width={ width }
            style={ style }
        >
            <Text style={ { ...style, marginBottom: 10 } }>
                Marker Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                100 random markers. With raster and svg symbols. Or different fallback circle symbols. Symbols support labels, here they are labelled by index.
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                Markers support press and longPress events. Furthermore an event can be triggered at any map position, here the center.
            </Text>

            <View style={ {
                marginBottom: 10,
                flexDirection: 'row',
                width: '90%',
                alignItems: 'center',
            } } >

                <Button
                    style={ { marginRight: 10 } }
                    disabled={ promiseQueueState > 0 }
                    onPress={ () => {
                        randomizePositions()
                    } }
                    title={ 'Randomize Markers' }
                />

                <Button
                    style={ { marginRight: 10 } }
                    disabled={ promiseQueueState > 0 || ! markerLayerUuid }
                    onPress={ () => {
                        if ( mapViewNativeNodeHandle && markerLayerUuid ) {
                            MapLayerMarkerModule.triggerEvent(
                                mapViewNativeNodeHandle,
                                markerLayerUuid,
                                PixelRatio.getPixelSizeForLayoutSize( width ) / 2,
                                PixelRatio.getPixelSizeForLayoutSize( mapHeight ) / 2
                            ).catch( err => console.log( 'ERROR', err ) );
                        }
                    } }
                    title={ 'Trigger Event' }
                />

            </View>

        </TopBar>

        <View style={ {
            height: mapHeight,
            width,
        } } >
            <MapContainer
                height={ mapHeight }
                zoomLevel={ 9 }
                center={ {
                    lng: -76.5,
                    lat: -9.5,
                } }
                responseInclude={ { center: 2 } }
                nativeNodeHandle={ mapViewNativeNodeHandle }          // Moves the state up into this example component.
                setNativeNodeHandle={ setMapViewNativeNodeHandle }    // Moves the state up into this example component.
            >

                <LayerBitmapTile
                    url={ tileOptions[0].value }
                    cacheSize={ 10 * 1024 * 1024 }  // 10 mb
                />

                <LayerMarker
                    onCreate={ response => response.uuid ? setMarkerLayerUuid( response.uuid ) : null }
                >
                    { [...positions].map( ( pos, index ) => {
                        return <Marker
                            key={ index }
                            position={ pos }
                            symbol={ {
                                ...symbols[index % symbols.length],
                                text: index + '',
                            } }
                            onPress={ response => {
                                ToastAndroid.show( 'Marker pressed. index: ' + index, ToastAndroid.SHORT );
                            } }
                            onLongPress={ response => {
                                ToastAndroid.show( 'Marker long pressed. index: ' + index, ToastAndroid.SHORT );
                            } }
                            onTrigger={ response => {
                                ToastAndroid.show( 'Marker triggered. index: ' + index, ToastAndroid.SHORT );
                            } }
                        />;
                    } ) }
                </LayerMarker>

                <LayerScalebar/>

            </MapContainer>

            <Center
                height={ mapHeight }
                width={ width }
            />

        </View>

        <AttributionComponent style={ {
            margin: 5,
            marginBottom: 25,
            padding: 10,
            position: 'absolute',
            right: 0,
            bottom: 0,
        } }/>

    </View>;
};

export default ExampleMarker;
