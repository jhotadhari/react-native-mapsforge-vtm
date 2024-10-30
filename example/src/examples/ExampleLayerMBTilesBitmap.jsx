/**
 * External dependencies
 */
import React, {
    useState,
} from 'react';
import {
	Text,
	Linking,
	useWindowDimensions,
	View,
} from 'react-native';

/**
 * react-native-mapsforge-vtm dependencies
 */
import {
	MapContainer,
	LayerMBTilesBitmap,
	LayerScalebar,
	nativeMapModules,
	usePromiseQueueState,
} from 'react-native-mapsforge-vtm';
const { MapContainerModule } = nativeMapModules;

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import TopBar from '../components/TopBar.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';

const ExampleLayerMBTilesBitmap = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mainMapViewId, setMainMapViewId] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

	const [mapFile, setMapFile] = useState( '' );

    const [minZoom,setMinZoom] = useState( 1 );
    const [maxZoom,setMaxZoom] = useState( 18 );
    const [attribution,setAttribution] = useState( '' );
    const [description,setDescription] = useState( '' );

    if ( null === appDirs ) {
        return null;
    }

    const onChange = response => {
        if ( response.bounds ) {
            MapContainerModule.setToBounds( mainMapViewId, response.bounds );
        }
        if ( response.minZoom ) {
            setMinZoom( response.minZoom );
        }
        if ( response.maxZoom ) {
            setMaxZoom( response.maxZoom );
        }
        if ( response.attribution ) {
            setAttribution( response.attribution );
        }
        if ( response.description ) {
            setDescription( response.description );
        }
    };

    let mapFileLabel = mapFile.split( '/' );
    mapFileLabel = mapFileLabel[mapFileLabel.length-1];

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
                LayerMBTilesBitmap Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                A map layer to render offline MBtiles raster maps.
                You can download maps from <Text onPress={ () => Linking.openURL( 'https://www.openandromaps.org/en/downloads/general-maps' ) } style={ {color: '#88f',} }>openandromaps.org</Text>
            </Text>

            <View style={ { marginBottom: 10 } } ><FilesFromDirPickerModalControl
                headerLabel={ 'Map File' }
                buttonLabel={ 'Map File ' + mapFileLabel }
                NoOptionsComponent={ () => <View><Text style={ { ...style, marginBottom: 10 } }>There are no mbtiles map files in this Directory!</Text><Text style={ style }>{ appDirs.mapfiles }</Text></View> }
                dir={ appDirs.mapfiles }
                filePattern={ /.*\.mbtiles$/ }
                values={ [mapFile] }
                onChange={ value => setMapFile( value ) }
                closeOnChange={ true }
                disabled={ promiseQueueState > 0 }
            /></View>

            { attribution && attribution.length > 0 && <Text style={ { ...style, marginTop: 10 } }>{ attribution }</Text> }

            { description && description.length > 0 && <Text style={ { ...style, marginTop: 10 } }>{ description }</Text> }

        </TopBar>

        <MapContainer
            height={ mapHeight }
            minZoom={ minZoom }
            maxZoom={ maxZoom }
            mapViewNativeTag={ mainMapViewId }          // Moves the state up into this example component.
            setMapViewNativeTag={ setMainMapViewId }    // Moves the state up into this example component.
        >

            <LayerMBTilesBitmap
                mapFile={ mapFile }
                onCreate={ onChange }
                onChange={ onChange }
            />

            <LayerScalebar/>

        </MapContainer>

    </View>;
};

export default ExampleLayerMBTilesBitmap;