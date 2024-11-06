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
	LayerScalebar,
} from 'react-native-mapsforge-vtm';

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import TopBar from '../components/TopBar.jsx';
import PickerModalControl from '../components/PickerModalControl.jsx';

export const tileOptions = [
    {
        label: 'OpenStreetMap',
        value: 'https://tile.openstreetmap.org/{Z}/{X}/{Y}.png',
        maxZoom: 19,
        attribution: ( { style } ) => <Text style={ style } onPress={ () => Linking.openURL( 'https://www.openstreetmap.org/copyright' ) }>
            &copy; OpenStreetMap contributors
        </Text>
    },
    {
        label: 'OpenTopoMap',
        value: 'https://a.tile.opentopomap.org/{Z}/{X}/{Y}.png',
	    maxZoom: 17,
        attribution: ( { style } ) => <View>
            <Text style={ style } onPress={ () => Linking.openURL( 'https://www.openstreetmap.org/copyright' ) }>
                &copy; OpenStreetMap contributors
            </Text>
            <Text style={ style } onPress={ () => Linking.openURL( 'http://viewfinderpanoramas.org' ) }>
                SRTM
            </Text>
            <Text style={ style } onPress={ () => Linking.openURL( 'https://opentopomap.org' ) }>
                Map style: &copy; OpenTopoMap
            </Text>
            <Text style={ style } onPress={ () => Linking.openURL( 'https://creativecommons.org/licenses/by-sa/3.0' ) }>
                CC-BY-SA
            </Text>
        </View>
    },
    {
        label: 'Esri World Imagery',
        value: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{Z}/{Y}/{X}',
	    maxZoom: 17,
        attribution: ( { style } ) => <Text style={ style }>
            Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community
        </Text>
    },
    {
        label: 'Google Maps',
        value: 'https://mt1.google.com/vt/lyrs=r&x={X}&y={Y}&z={Z}',
        maxZoom: 19,
        attribution: ( { style } ) => <Text style={ style } onPress={ () => Linking.openURL( 'https://cloud.google.com/maps-platform/terms' ) }>
            &copy; Map data ©2024 Google ... logo missing! sorry
        </Text>
    },
];

const ExampleLayerBitmapTile = ( {
    setSelectedExample,
    style,
} ) => {

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const [source,setSource] = useState( tileOptions[0].value );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

    const AttributionComponent = tileOptions.find( opt => opt.value === source ).attribution;

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
                LayerBitmapTile Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                A map layer to display static online raster xyz tiles
            </Text>

            <PickerModalControl
                style={ style }
                headerLabel={ 'Tile source' }
                options={ tileOptions }
                values={ [source] }
                onChange={ clickedVal => setSource( clickedVal ) }
                closeOnChange={ true }
            />
            <Text style={ { ...style, marginTop: 10 } }>{ source }</Text>
            <AttributionComponent style={ { ...style, marginTop: 10 } }/>
        </TopBar>

        <MapContainer
            height={ mapHeight }
            maxZoom={ tileOptions.find( opt => opt.value === source )?.maxZoom }
            zoomLevel={ 7 }
            center={ {
                lng: -75.239,
                lat: -7.65,
            } }
        >
            <LayerBitmapTile
                url={ source }
                cacheSize={ 10 * 1024 * 1024 }  // 10 mb
            />

            <LayerScalebar/>

        </MapContainer>

    </View>;
};

export default ExampleLayerBitmapTile;
