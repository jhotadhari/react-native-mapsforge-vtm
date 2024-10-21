/**
 * External dependencies
 */
import {
	requireNativeComponent,
	ViewPropTypes,
} from 'react-native';
import PropTypes from 'prop-types';

/**
 * Internal dependencies
 */
import MapPropTypes from '../MapPropTypes';

export const MapViewManager = requireNativeComponent(
	'MapViewManager',
	{
		name: 'MapViewManager',
		propTypes: {
			width: PropTypes.number,
			height: PropTypes.number,
			center: MapPropTypes.latLong,
			zoom: PropTypes.number,
			minZoom: PropTypes.number,
			maxZoom: PropTypes.number,
			hgtDirPath: PropTypes.string,
			...ViewPropTypes,
		},
	},
);
