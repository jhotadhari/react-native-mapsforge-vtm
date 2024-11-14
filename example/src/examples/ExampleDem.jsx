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
	LayerBitmapTile,
	LayerHillshading,
	LayerScalebar,
} from 'react-native-mapsforge-vtm';

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import TopBar from '../components/TopBar.jsx';
import Center from '../components/Center.jsx';
import useDirInfo from '../compose/useDirInfo.js';
import { tileOptions } from './ExampleLayerBitmapTile.jsx';

const ExampleDem = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

    const [currentCenter,setCurrentCenter] = useState( null );

	const { width, height } = useWindowDimensions();

	const { navChildren } = useDirInfo( appDirs.dem );

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

    const AttributionComponent = tileOptions[0].attribution;

    const onMapEvent = event => event.center
        ? setCurrentCenter( event.center )
        : null

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
                Digital Elevation Model Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                With hillshading layer. And Elevation at map center.
            </Text>

            <Text style={ { ...style } }>Current directory for dem hgt files: { appDirs.dem }</Text>
            <Text style={ { ...style, marginTop: 10 } }>You can download dem hgt files from: <Text style={ style } onPress={ () => Linking.openURL( 'https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm' ) }>
            https://viewfinderpanoramas.org/dem3.html
            </Text></Text>
            { ! navChildren || ( Array.isArray( navChildren ) && navChildren.length === 0 ) && <Text style={ { ...style, marginTop: 10 } }>There are no hgt files in dem directory</Text> }
            <Text style={ { ...style, marginTop: 10 } }>Elevation map center: { currentCenter && currentCenter.alt ? currentCenter.alt : 'hgt file missing for this position' }</Text>

        </TopBar>

        <View style={ {
            height: mapHeight,
            width,
        } } >

            <MapContainer
                height={ mapHeight }
                zoomLevel={ 10 }
                hgtDirPath={ appDirs.dem }
                responseInclude={ { center: 2 } }
                nativeNodeHandle={ mapViewNativeNodeHandle }          // Moves the state up into this example component.
                setNativeNodeHandle={ setMapViewNativeNodeHandle }    // Moves the state up into this example component.
                onPause={ onMapEvent }
                onResume={ onMapEvent }
                onMapEvent={ onMapEvent }
                mapEventRate={ 50 }
            >

                <LayerBitmapTile
                    url={ tileOptions[0].value }
                    cacheSize={ 10 * 1024 * 1024 }  // 10 mb
                />

                <LayerHillshading
                    hgtDirPath={ appDirs.dem }
                    zoomMin={ 2 }
                    shadingAlgorithm={ LayerHillshading.shadingAlgorithms.SIMPLE }
                    magnitude={ 90 }
                    shadingAlgorithmOptions={ {
                        linearity: -1,
                        scale: 1,
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

export default ExampleDem;
