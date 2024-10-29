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
	nativeMapModules,
	useMapEvents,
	usePromiseQueueState,
} from 'react-native-mapsforge-vtm';
const { MapContainerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import Center from '../components/Center.jsx';
import TopBar from '../components/TopBar.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';
import Button from '../components/Button.jsx';

const formatSeconds = secNum => {
    secNum = Math.round( secNum );
    let hours   = Math.floor(secNum / 3600);
    let minutes = Math.floor((secNum - (hours * 3600)) / 60);
    let seconds = secNum - (hours * 3600) - (minutes * 60);
    if ( hours   < 10 ) { hours   = "0" + hours; }
    if ( minutes < 10 ) { minutes = "0" + minutes; }
    if ( seconds < 10 ) { seconds = "0" + seconds; }
    return hours + 'h ' + minutes + 'm ' + seconds + 's';
};

const rowBtnStyle = {
    marginRight: 10,
    width: 35,
    textAlign: 'center',
};

const MapEvents = ( {
	nativeTag,
    setCurrentCenter,
} ) => {
	useMapEvents( {
		nativeTag,
		onMapEvent: event => {
            if ( event.center ) {
                setCurrentCenter( event.center );
            }
		},
	} );
	return null;
};

const PlusMinusControl = ( {
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
    return <View style={ {
        flexDirection: 'row',
        width: '90%',
        alignItems: 'center',
        ...containerStyle,
    } }>
        <Text style={ {...style, marginRight: 10, minWidth: 100 } }>{ label }:</Text>
        <Text style={ {...style, marginRight: 10, minWidth: 30  } }>{ value }</Text>
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
        { textAppend && <Text style={ { ...style, marginLeft: 10 }} >{ textAppend }</Text> }
    </View>;
};

const strokeColor = '#00ff00';
const stippleColor = '#ff0000';

const ExampleLayerPath = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mainMapViewId, setMainMapViewId] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

    const [currentCenter,setCurrentCenter] = useState( null );

	const [useGpx, setUseGpx] = useState( true );

	const [filePath, setFilePath] = useState( '' );
	const [positions, setPositions] = useState( [] );

	const [coordinates,setCoordinates] = useState( [] );

	const [strokeWidth,setStrokeWidth] = useState( 10 );
	const [stipple,setStipple] = useState( 50 );

    if ( null === appDirs ) {
        return null;
    }

    const onChange = response => {
        if ( useGpx && response.bounds ) {
            MapContainerModule.setToBounds( mainMapViewId, response.bounds );
        }
        if ( response.coordinates ) {
            setCoordinates( response.coordinates );
        }
    };

    let fileLabel = filePath.split( '/' );
    fileLabel = fileLabel[fileLabel.length-1];

    const AttributionComponent = tileOptions[0].attribution;

    const lineStyle = {
        strokeWidth,
        stipple,
        stippleColor,
        strokeColor,
    };

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
                LayerPath Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                A map layer to render paths from gpx file or array of positions.
            </Text>

            <View style={ {
                marginBottom: 10,
                // maxWidth: '90%',
                flexDirection: 'row',
                width: '90%',
                alignItems: 'center',
                // justifyContent: 'space-between',
            } } >

                <Button
                    style={ { width: 100, marginRight: 10 } }
                    disabled={ promiseQueueState > 0 }
                    onPress={ () => {
                        setUseGpx( ! useGpx );
                        setCoordinates( null )
                    } }
                    title={ useGpx ? 'gpx' : 'positions' }
                />

                { ! useGpx && <View style={ { width: '80%', flexDirection: 'row', marginLeft: 40 } } >

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
                </View> }

                { useGpx && <View style={ { width: '80%' } } ><FilesFromDirPickerModalControl
                    headerLabel={ 'gpx File' }
                    buttonLabel={ 'gpx File ' + fileLabel }
                    NoOptionsComponent={ () => <View><Text style={ { ...style, marginBottom: 10 } }>There are no gpx files in this Directory!</Text><Text style={ style }>{ appDirs.tracks }</Text></View> }
                    dir={ appDirs.tracks }
                    filePattern={ /.*\.gpx$/ }
                    values={ [filePath] }
                    onChange={ value => setFilePath( value ) }
                    closeOnChange={ true }
                    disabled={ promiseQueueState > 0 }
                /></View> }

            </View>

            <PlusMinusControl
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
            </View> }

        </TopBar>

        <View style={ {
            height: mapHeight,
            width,
        } } >
            <MapContainer
                height={ mapHeight }
                mapViewNativeTag={ mainMapViewId }          // Moves the state up into this example component.
                setMapViewNativeTag={ setMainMapViewId }    // Moves the state up into this example component.
            >

                <MapEvents
                    nativeTag={ mainMapViewId }
                    setCurrentCenter={ setCurrentCenter }
                />

                <LayerBitmapTile
                    url={ tileOptions[0].value }
                    cacheSize={ 10 * 1024 * 1024 }  // 10 mb
                />

                <LayerPath
                    positions={ ! useGpx && positions.length > 1 ? positions : null }
                    filePath={ useGpx ? filePath : null }
                    style={ lineStyle }
                    responseInclude={ {
                        bounds: 1,
                        coordinates: 1,
                    } }
                    onCreate={ onChange }
                    onChange={ onChange }
                />

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

export default ExampleLayerPath;
