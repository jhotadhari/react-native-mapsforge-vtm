/**
 * External dependencies
 */
import React, {
    useState,
} from 'react';
import {
	Text,
	useWindowDimensions,
	View,
} from 'react-native';

/**
 * react-native-mapsforge-vtm dependencies
 */
import {
	MapContainer,
	LayerPath,
	LayerBitmapTile,
	LayerScalebar,
	LayerMarker,
	Marker,
	nativeMapModules,
	useMapEvents,
	usePromiseQueueState,
} from 'react-native-mapsforge-vtm';
const { MapContainerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import { formatSeconds, randomNumber } from '../utils.js';
import Center from '../components/Center.jsx';
import TopBar from '../components/TopBar.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';
import Button from '../components/Button.jsx';
import { PlusMinusControl, rowBtnStyle } from '../components/RowControls.jsx';

// const strokeColor = '#00ff00';
// const stippleColor = '#ff0000';


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

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

    const [positions,setPositions] = useState( getRandomPositions( 100 ) );
    const randomizePositions = () => {
        setPositions( getRandomPositions( positions.length ) );
    };

	// const [useGpx, setUseGpx] = useState( true );

	// const [filePath, setFilePath] = useState( '' );
	// const [positions, setPositions] = useState( [] );

	// const [coordinates,setCoordinates] = useState( [] );

	// const [strokeWidth,setStrokeWidth] = useState( 10 );
	// const [stipple,setStipple] = useState( 50 );

    // const onMapEvent = event => event.center
    //     ? setCurrentCenter( event.center )
    //     : null

	// useMapEvents( {
	// 	nativeNodeHandle: mapViewNativeNodeHandle,
	// 	onMapEvent,
	// } );

    if ( null === appDirs ) {
        return null;
    }

    const symbols = [
        {},
        {
            width: 30,
            height: 30,
            fillColor: '#dddddd',
            strokeColor: '#000000',
        },
        {
            width: 30,
            height: 30,
            strokeColor: '#ff0000',
            strokeWidth: 10,
        },
        {
            width: 37,
            height: 64,
            filePath: appDirs.marker + '/marker.svg',
            hotspotPlace: 'BOTTOM_CENTER',
        },
        {
            width: 37,
            height: 64,
            filePath: appDirs.marker + '/marker_red.png',
            hotspotPlace: 'BOTTOM_CENTER',
        },
    ];

    // const onChange = response => {
    //     if ( useGpx && response.bounds ) {
    //         MapContainerModule.setToBounds( mapViewNativeNodeHandle, response.bounds );
    //     }
    //     if ( response.coordinates ) {
    //         setCoordinates( response.coordinates );
    //     }
    // };

    // let fileLabel = filePath.split( '/' );
    // fileLabel = fileLabel[fileLabel.length-1];

    const AttributionComponent = tileOptions[0].attribution;

    // const lineStyle = {
    //     strokeWidth,
    //     stipple,
    //     stippleColor,
    //     strokeColor,
    // };


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
                ???
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
                    title={ 'Randomize positions' }
                />

                {/* { ! useGpx && <View style={ { width: '80%', flexDirection: 'row', marginLeft: 40 } } >

                <Button
                    style={ rowBtnStyle }
                    disabled={ promiseQueueState > 0 }
                    onPress={ () => {
                        if ( currentCenter ) {
                            setPositions( [...positions, currentCenter ] );
                        }
                    } }
                    title=' + '
                />
                <Button
                    style={ rowBtnStyle }
                    disabled={ promiseQueueState > 0 || positions.length === 0 }
                    onPress={ () => {
                        if ( currentCenter && positions.length > 0 ) {
                            const newPositions = [...positions];
                            newPositions.pop()
                            setPositions( newPositions );
                        }
                    } }
                    title=' - '
                />
                </View> } */}

                {/* { useGpx && <View style={ { width: '80%' } } ><FilesFromDirPickerModalControl
                    headerLabel={ 'gpx File' }
                    buttonLabel={ 'gpx File ' + fileLabel }
                    NoOptionsComponent={ () => <View><Text style={ { ...style, marginBottom: 10 } }>There are no gpx files in this Directory!</Text><Text style={ style }>{ appDirs.tracks }</Text></View> }
                    dir={ appDirs.tracks }
                    filePattern={ /.*\.gpx$/ }
                    values={ [filePath] }
                    onChange={ value => setFilePath( value ) }
                    closeOnChange={ true }
                    disabled={ promiseQueueState > 0 }
                /></View> } */}

            </View>

            {/* <PlusMinusControl
                style={ style }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Stroke width' }
                value={ strokeWidth }
                setValue={ setStrokeWidth }
                minValue={ 1 }
                textAppend={ strokeColor }
            />

            <PlusMinusControl
                style={ style }
                promiseQueueState={ promiseQueueState }
                label={ 'Stripple width' }
                value={ stipple }
                setValue={ setStipple }
                step={ 10 }
                minValue={ 0 }
                textAppend={ stippleColor }
            />

            { coordinates && coordinates.length > 0 && <View style={ { marginTop: 10 } }>
                <Text style={ style }>Number of points: { coordinates.length }</Text>
                <Text style={ style }>Total distance: { Math.round( coordinates[coordinates.length-1].distance ) / 1000 } km</Text>
                { coordinates[0].hasOwnProperty( 'time' ) && <Text style={ style }>Total time: { formatSeconds( coordinates[coordinates.length-1].time - coordinates[0].time ) }</Text> }
            </View> } */}

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
                // onPause={ onMapEvent }
                // onResume={ onMapEvent }
            >

                <LayerBitmapTile
                    url={ tileOptions[0].value }
                    cacheSize={ 10 * 1024 * 1024 }  // 10 mb
                />

                <LayerMarker
                    symbol={ symbols[1] }
                >
                    { [...positions].map( ( pos, index ) => {

                        return <Marker
                            key={ index }
                            position={ pos }
                            symbol={ symbols[index % symbols.length] }
                        />;
                    } ) }
                </LayerMarker>

                {/* <LayerPath
                    positions={ ! useGpx && positions.length > 1 ? positions : null }
                    filePath={ useGpx ? filePath : null }
                    style={ lineStyle }
                    responseInclude={ {
                        bounds: 1,
                        coordinates: 1,
                    } }
                    onCreate={ onChange }
                    onChange={ onChange }
                /> */}

                <LayerScalebar/>

            </MapContainer>

            {/* <Center
                height={ mapHeight }
                width={ width }
            /> */}
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
