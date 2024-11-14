/**
 * External dependencies
 */
import React, {
    useState,
    useEffect,
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
	LayerMapsforge,
	LayerScalebar,
	MapContainerModule,
	useRenderStyleOptions,
	usePromiseQueueState,
} from 'react-native-mapsforge-vtm';

/**
 * Internal dependencies
 */
import { barTopPadding } from '../constants.js';
import TopBar from '../components/TopBar.jsx';
import PickerModalControl from '../components/PickerModalControl.jsx';
import FilesFromDirPickerModalControl from '../components/FilesFromDirPickerModalControl.jsx';

const renderThemeBuiltInOptions = [...LayerMapsforge.BUILT_IN_THEMES].map( opt => ( {
    label: opt,
    value: opt,
} ) );

const ExampleLayerMapsforge = ( {
    setSelectedExample,
    style,
    appDirs,
} ) => {

	const [mapViewNativeNodeHandle, setMapViewNativeNodeHandle] = useState( null );

	const [barTopHeight,setBarTopHeight] = useState( 0 );

	const { width, height } = useWindowDimensions();

	const mapHeight = height - ( barTopHeight ? ( barTopHeight + ( 2 * barTopPadding ) ) : 0 );

	const promiseQueueState = usePromiseQueueState();

	const [mapFile, setMapFile] = useState( '' );
	const [mapFileComment, setMapFileComment] = useState( null );
	const [renderOverlayOptions, setRenderOverlayOptions] = useState( [] );
	const [renderOverlays, setRenderOverlays] = useState( [] );
	const [renderTheme, setRenderTheme] = useState( 'DEFAULT' );

	const {
		renderStyleDefaultId,
		renderStyleOptions,
	} = useRenderStyleOptions( ( {
		renderTheme,
		nativeNodeHandle: mapViewNativeNodeHandle,
	} ) );

	const [renderStyle, setRenderStyle] = useState( renderStyleDefaultId );

	useEffect( () => {
		if ( ! renderStyle && renderStyleDefaultId ) {
			setRenderStyle( renderStyleDefaultId );
		}

		if ( ! renderOverlayOptions.length ) {
			const renderStyleOptions_ = renderStyleOptions.find( opt => opt.value === renderStyle );
			if ( undefined !== renderStyleOptions_ ) {
				const newItems = Object.keys( renderStyleOptions_.options ).map( value => {
					return {
						value,
						label: renderStyleOptions_.options[value],
					};
				} );

				setRenderOverlayOptions( newItems );
			}
		}
	}, [renderStyle, renderStyleDefaultId] );

    if ( null === appDirs ) {
        return null;
    }

    const onChange = response => {
        if ( response.bounds ) {
            MapContainerModule.setToBounds( mapViewNativeNodeHandle, response.bounds )
        }
        if ( response.comment ) {
            setMapFileComment( response.comment )
        }
    };

    let renderThemeLabel = renderTheme.split( '/' );
    renderThemeLabel = renderThemeLabel[renderThemeLabel.length-1];

    let mapFileLabel = mapFile.split( '/' );
    mapFileLabel = mapFileLabel[mapFileLabel.length-1];

    let renderStyleLabel = renderStyleOptions.find( opt => opt.value === renderStyle )
    renderStyleLabel = renderStyleLabel ? renderStyleLabel.label : '';

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
                LayerMapsforge Example
            </Text>
            <Text style={ { ...style, marginBottom: 10 } }>
                A map layer to render mapsforge offline vector maps.
                You can download vector maps and render themes from <Text onPress={ () => Linking.openURL( 'https://www.openandromaps.org/en/downloads' ) } style={ {color: '#88f',} }>openandromaps.org</Text>
            </Text>

            <View style={ { marginBottom: 10 } } ><FilesFromDirPickerModalControl
                style={ style }
                headerLabel={ 'Map File' }
                buttonLabel={ 'Map File ' + mapFileLabel }
                NoOptionsComponent={ () => <View><Text style={ { ...style, marginBottom: 10 } }>There are no map files in this Directory!</Text><Text style={ style }>{ appDirs.mapfiles }</Text></View> }
                dir={ appDirs.mapfiles }
                filePattern={ /.*\.map$/ }
                values={ [mapFile] }
                onChange={ value => setMapFile( value ) }
                closeOnChange={ true }
                disabled={ promiseQueueState > 0 }
            /></View>

            <View style={ { marginBottom: 10 } } ><FilesFromDirPickerModalControl
                style={ style }
                headerLabel={ 'Render theme' }
                buttonLabel={ 'Render theme ' + renderThemeLabel }
                ExtraOptionsHeader={ () => <Text style={ { ...style, marginBottom: 10 } }>Built in render themes:</Text> }
                OptionsHeader={ () => <Text style={ { ...style, marginBottom: 10 } }>External render themes:</Text> }
                extraOptions={ renderThemeBuiltInOptions }
                NoOptionsComponent={ () => <View><Text style={ { ...style, marginBottom: 10 } }>There are no xml render themes in this Directory!</Text><Text style={ style }>{ appDirs.mapstyles }</Text></View> }
                dir={ appDirs.mapstyles }
                filePattern={ /.*\.xml$/ }
                values={ [renderTheme] }
                onChange={ value => setRenderTheme( value ) }
                closeOnChange={ true }
                disabled={ promiseQueueState > 0 }
            /></View>

            <View style={ { marginBottom: 10 } } ><PickerModalControl
                style={ style }
                headerLabel={ 'Render style' }
                disabled={ promiseQueueState > 0 || ! renderStyleOptions.length }
                buttonLabel={ 'Render style ' + renderStyleLabel }
                options={ renderStyleOptions }
                values={ [renderStyle] }
                onChange={ clickedVal => setRenderStyle( clickedVal ) }
                closeOnChange={ true }
            /></View>

            <PickerModalControl
                style={ style }
                buttonLabel={ 'Render style options' }
                headerLabel={ 'Render style options' }
                disabled={ promiseQueueState > 0 || ! renderStyleOptions.length }
                options={ renderOverlayOptions }
                values={ renderOverlays }
                selectAllNoneLabel={ renderOverlays.length !== renderOverlayOptions.length ? 'All' : 'None' }
                onSelectAllNone={ () => {
                    if ( renderOverlays.length !== renderOverlayOptions.length ) {
                        setRenderOverlays( [...renderOverlayOptions].map( o => o.value ) );
                    } else {
                        setRenderOverlays( [] );
                    }
                } }
                onChange={ clickedVal => {
                    const existingIndex = renderOverlays.findIndex( val => val === clickedVal );
                    if ( existingIndex === -1 ) {
                        setRenderOverlays( [
                            ...renderOverlays,
                            clickedVal,
                        ] );
                    } else {
                        const newSelectedItems = [...renderOverlays];
                        newSelectedItems.splice( existingIndex, 1 );
                        setRenderOverlays( newSelectedItems );
                    }
                } }
                closeOnChange={ false }
            />

            { mapFileComment && <Text style={ { ...style, marginTop: 10 } }>{ mapFileComment }</Text> }

        </TopBar>

        <MapContainer
            height={ mapHeight }
            nativeNodeHandle={ mapViewNativeNodeHandle }          // Moves the state up into this example component.
            setNativeNodeHandle={ setMapViewNativeNodeHandle }    // Moves the state up into this example component.
        >

            <LayerMapsforge
                mapFile={ mapFile }
                renderTheme={ renderTheme }
                renderStyle={ renderStyle }
                renderOverlays={ renderOverlays }
                onCreate={ onChange }
                onChange={ onChange }
            />

            <LayerScalebar/>

        </MapContainer>

    </View>;
};

export default ExampleLayerMapsforge;
