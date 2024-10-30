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
import { BUILT_IN_THEMES } from '../constants';

export type XmlRenderTheme = {
	value: string,
	label: string,
	default: undefined | boolean,
	options: object,	// ???
};

export type RenderStyleOptionsCollection = {
	[value: string]: XmlRenderTheme;
};

const useRenderStyleOptions = ( {
	renderTheme,
	nativeNodeHandle,
} : {
	renderTheme?: `/${string}` | typeof BUILT_IN_THEMES[number];
	nativeNodeHandle?: number | null,
} ) : {
	renderStyleDefaultId: string | null,
	renderStyleOptions: XmlRenderTheme[],
} => {

	const [renderStyleOptions, setRenderStyleOptions] = useState<XmlRenderTheme[]>( [] );

	const [renderStyleDefaultId, setRenderStyleDefault] = useState<string | null>( null );

	useEffect( () => {
		const eventEmitter = new NativeEventEmitter();
		let eventListener = eventEmitter.addListener( 'RenderThemeParsed', ( result : {
			nativeNodeHandle: number,
			filePath: string,
			collection: RenderStyleOptionsCollection,
		} ) => {
			if ( result && renderTheme === result.filePath ) {
				setRenderStyleOptions( Object.values( result.collection ) );
				if ( null == renderStyleDefaultId ) {
					const defaultStyle : undefined | XmlRenderTheme = Object.values( result.collection ).find( ( obj : XmlRenderTheme ) : boolean => !! obj.default );
					if ( undefined !== defaultStyle ) {
						setRenderStyleDefault( defaultStyle.value );
					}
				}

			}
		} );
		return () => {
			eventListener.remove();
		};
	}, [nativeNodeHandle] );


	// ??? should reset on prop change !!!

	useEffect( () => {
		if ( renderTheme ) {
			MapLayerMapsforgeModule.getRenderThemeOptions( renderTheme ).then( ( collection : RenderStyleOptionsCollection ) => {
				setRenderStyleOptions( Object.values( collection ) );
				const defaultStyle = Object.values( collection ).find( obj => obj.default );
				if ( undefined !== defaultStyle && !! defaultStyle ) {
					setRenderStyleDefault( defaultStyle.value );
				}
			} ).catch( ( err: any ) => console.log( 'ERROR', err ) );
		}
	}, [nativeNodeHandle, renderTheme] );

	return {
		renderStyleDefaultId,
		renderStyleOptions,
	};
};

export default useRenderStyleOptions;
