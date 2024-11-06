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
	LayerPathSlopeGradient,
	LayerBitmapTile,
	LayerScalebar,
	nativeMapModules,
	usePromiseQueueState,
} from 'react-native-mapsforge-vtm';
const { MapContainerModule, MapLayerPathSlopeGradientModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import Center from '../components/Center.jsx';
import { barTopPadding } from '../constants.js';
import { formatSeconds } from '../utils.js';
import TopBar from '../components/TopBar.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';
import { PlusMinusControl, ButtonControl, EventRowControl } from '../components/RowControls.jsx';

const ExampleLayerPathSlopeGradient = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );

	const [layerUuid, setLayerUuid] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

	const [filePath, setFilePath] = useState( '' );

	const [coordinates,setCoordinates] = useState( [] );
	const [coordinatesSimplified,setCoordinatesSimplified] = useState( [] );

	const [strokeWidth,setStrokeWidth] = useState( 5 );
	const [slopeSimplificationTolerance,setSlopeSimplificationTolerance] = useState( 7 );
	const [flattenWindowSize,setFlattenWindowSize] = useState( 9 );

	const [slopeColors,setSlopeColors] = useState( LayerPathSlopeGradient.slopeColorsDefault );
    const swapColors = () => {

        let newColors = [...slopeColors].reverse();
        [...slopeColors].map( ( opt, index ) => {
            newColors[index] = [
                newColors[index][0],
                opt[1],
            ]
        } );
        setSlopeColors( newColors );

    };

    if ( null === appDirs ) {
        return null;
    }

    const onChange = response => {
        if ( response.uuid ) {
            setLayerUuid( response.uuid )
        }
        if ( response.bounds ) {
            MapContainerModule.setToBounds( mapViewNativeNodeHandle, response.bounds );
        }
        if ( response.coordinates ) {
            setCoordinates( response.coordinates );
        }
        if ( response.coordinatesSimplified ) {
            setCoordinatesSimplified( response.coordinatesSimplified );
        }
    };

    let fileLabel = filePath.split( '/' );
    fileLabel = fileLabel[fileLabel.length-1];

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
                LayerPathSlopeGradient Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                A map layer to render paths from gpx file or array of positions. With colors according to the slope.
            </Text>

            <View style={ {
                marginBottom: 10,
                flexDirection: 'row',
                width: '90%',
                alignItems: 'center',
            } } >

                <View style={ { width: '100%' } } ><FilesFromDirPickerModalControl
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
                /></View>

            </View>

            <PlusMinusControl
                style={ style }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Stroke width' }
                value={ strokeWidth }
                setValue={ setStrokeWidth }
                minValue={ 1 }
            />

            <PlusMinusControl
                style={ style }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Simplification' }
                value={ slopeSimplificationTolerance }
                setValue={ setSlopeSimplificationTolerance }
                textAppend={ 'Only for colors' }
                minValue={ 0 }
            />

            <PlusMinusControl
                style={ style }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Smoothen' }
                value={ flattenWindowSize }
                setValue={ setFlattenWindowSize }
                textAppend={ 'Only for colors' }
                minValue={ 5 }
                step={ 2 }
            />

            <ButtonControl
                style={ style }
                buttonStyle={ { width: 81 } }
                containerStyle={ { marginBottom: 10 } }
                promiseQueueState={ promiseQueueState }
                label={ 'Colors' }
                onPress={ swapColors }
                buttonLabel="swap"
            />

            <EventRowControl
                style={ style }
                promiseQueueState={ promiseQueueState }
                mapViewNativeNodeHandle={ mapViewNativeNodeHandle }
                layerUuid={ layerUuid }
                width={ width }
                mapHeight={ mapHeight }
                module={ MapLayerPathSlopeGradientModule }
            />

            { coordinates && coordinates.length > 0 && <View>
                <Text style={ style }>Number of points: { coordinates.length }</Text>
                <Text style={ style }>Number of points simplified: { coordinatesSimplified.length }</Text>
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
                nativeNodeHandle={ mapViewNativeNodeHandle }          // Moves the state up into this example component.
                setNativeNodeHandle={ setMapViewNativeNodeHandle }    // Moves the state up into this example component.
            >

                <LayerBitmapTile
                    url={ tileOptions[0].value }
                    cacheSize={ 10 * 1024 * 1024 }  // 10 mb
                />

                <LayerPathSlopeGradient
                    onCreate={ onChange }
                    filePath={ filePath }
                    style={ {
                        strokeWidth,
                    } }
                    slopeColors={ slopeColors }
                    slopeSimplificationTolerance={ slopeSimplificationTolerance }
                    flattenWindowSize={ flattenWindowSize }
                    responseInclude={ {
                        bounds: 1,
                        coordinates: 1,
                        coordinatesSimplified: 2,
                    } }
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

export default ExampleLayerPathSlopeGradient;
