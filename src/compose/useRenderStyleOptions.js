/**
 * External dependencies
 */
import {
	useEffect,
	useState,
} from 'react';
import {
	NativeModules,
	NativeEventEmitter,
} from 'react-native';

/**
 * Internal dependencies
 */
const { MapLayerMapsforgeModule } = NativeModules;

const useRenderStyleOptions = ( {
	renderTheme,
	nativeTag,
} ) => {

	const [renderStyleOptions, setRenderStyleOptions] = useState( [] );

	const [renderStyleDefaultId, setRenderStyleDefault] = useState( null );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'RenderThemeParsed', result => {
			if ( result && renderTheme === result.filePath ) {
				setRenderStyleOptions( Object.values( result.collection ) );
				if ( ! renderStyleDefaultId ) {
					const defaultStyle = Object.values( result.collection ).find( obj => obj.default );
					if ( undefined !== defaultStyle && !! defaultStyle ) {
						setRenderStyleDefault( defaultStyle.value );
					}
				}

			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [nativeTag] );

	useEffect( () => {
		if ( renderTheme ) {
			MapLayerMapsforgeModule.getRenderThemeOptions( renderTheme ).then( res => {
				if ( res ) {
					setRenderStyleOptions( Object.values( res ) );
					const defaultStyle = Object.values( res ).find( obj => obj.default );
					if ( undefined !== defaultStyle && !! defaultStyle ) {
						setRenderStyleDefault( defaultStyle.value );
					}
				}
			} ).catch( err => console.log( 'ERROR', err ) );
		}
	}, [nativeTag, renderTheme] );

	return {
		renderStyleDefaultId,
		renderStyleOptions,
	};
};

export default useRenderStyleOptions;
