/**
 * External dependencies
 */
import React, {
    useState,
} from 'react';
import {
	Text,
	ToastAndroid,
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
const { MapContainerModule, MapLayerPathModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import { formatSeconds } from '../utils.js';
import Center from '../components/Center.jsx';
import TopBar from '../components/TopBar.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';
import Button from '../components/Button.jsx';
import { PlusMinusControl, rowBtnStyle, EventRowControl } from '../components/RowControls.jsx';

const strokeColor = '#ff0000';

const ExampleLayerPath = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );

	const [layerUuid, setLayerUuid] = useState( null );

	const [isSetToBounds, setIsSetToBounds] = useState( false );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

    const [currentCenter,setCurrentCenter] = useState( null );

	const [useGpx, setUseGpx] = useState( true );

	const [filePath, setFilePath] = useState( '' );
	const [positions, setPositions] = useState( [] );

	const [coordinates,setCoordinates] = useState( [] );

	const [strokeWidth,setStrokeWidth] = useState( 5 );
	const [simplificationTolerance,setSimplificationTolerance] = useState( 0.00001 );

    const onMapEvent = event => event.center
        ? setCurrentCenter( event.center )
        : null
	useMapEvents( {
		nativeNodeHandle: mapViewNativeNodeHandle,
		onMapEvent,
	} );

    if ( null === appDirs ) {
        return null;
    }

    const onChange = response => {
        if ( response.uuid ) {
            setLayerUuid( response.uuid )
        }
        if ( useGpx && response.bounds && ! isSetToBounds ) {
            setIsSetToBounds( true );
            MapContainerModule.setToBounds( mapViewNativeNodeHandle, response.bounds );
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
                flexDirection: 'row',
                width: '90%',
                alignItems: 'center',
            } } >

                <Button
                    style={ { width: 100, marginRight: 10 } }
                    disabled={ promiseQueueState > 0 }
                    onPress={ () => {
                        setUseGpx( ! useGpx );
                        setIsSetToBounds( false );
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
                    style={ style }
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
                valueMinWidth={ 55 }
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
                valueMinWidth={ 55 }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Simplify' }
                value={ simplificationTolerance }
                setValue={ newVal => setSimplificationTolerance( Math.round( newVal * 100000 ) / 100000 ) }
                minValue={ 0 }
                step={ 0.00001 }
            />

            <EventRowControl
                style={ style }
                valueMinWidth={ 55 }
                promiseQueueState={ promiseQueueState }
                mapViewNativeNodeHandle={ mapViewNativeNodeHandle }
                layerUuid={ layerUuid }
                width={ width }
                mapHeight={ mapHeight }
                module={ MapLayerPathModule }
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
                responseInclude={ { center: 2 } }
                nativeNodeHandle={ mapViewNativeNodeHandle }          // Moves the state up into this example component.
                setNativeNodeHandle={ setMapViewNativeNodeHandle }    // Moves the state up into this example component.
                onPause={ onMapEvent }
                onResume={ onMapEvent }
            >

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
                    simplificationTolerance={ simplificationTolerance }
                    onCreate={ onChange }
                    onChange={ onChange }
                    onPress={ response => {
                        ToastAndroid.show( 'Path pressed', ToastAndroid.SHORT );
                    } }
                    onLongPress={ response => {
                        ToastAndroid.show( 'Path long pressed', ToastAndroid.SHORT );
                    } }
                    onDoubleTap={ response => {
                        ToastAndroid.show( 'Path double tabbed', ToastAndroid.SHORT );
                    } }
                    onTrigger={ response => {
                        ToastAndroid.show( 'Path triggered', ToastAndroid.SHORT );
                    } }
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
