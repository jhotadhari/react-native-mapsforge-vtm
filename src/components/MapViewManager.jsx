/**
 * External dependencies
 */
import {
	requireNativeComponent,
	ViewPropTypes,
} from 'react-native';
import PropTypes from 'prop-types';

export const MapViewManager = requireNativeComponent(
	'MapViewManager',
	{
		name: 'MapViewManager',
		propTypes: {
			width: PropTypes.number,
			height: PropTypes.number,
			center: PropTypes.object,
			zoom: PropTypes.number,
			minZoom: PropTypes.number,
			maxZoom: PropTypes.number,
			hgtDirPath: PropTypes.string,
			...ViewPropTypes,
		},
	},
);
