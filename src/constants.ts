
export const LINKING_ERROR =
	'The package \'react-native-mapsforge-vtm\' doesn\'t seem to be linked. Make sure: \n\n' +
	'- You rebuilt the app after installing the package\n' +
	'- You are not using Expo Go\n';

export const BUILT_IN_THEMES = [
	'DEFAULT',
	'BIKER',
	'MOTORIDER',
	'MOTORIDER_DARK',
	'NEWTRON',
	'OSMAGRAY',
	'OSMARENDER',
	'TRONRENDER',
] as const;

export const MarkerHotspotPlaces = [
    'NONE',
    'CENTER',
    'BOTTOM_CENTER',
    'TOP_CENTER',
    'RIGHT_CENTER',
    'LEFT_CENTER',
    'UPPER_RIGHT_CORNER',
    'LOWER_RIGHT_CORNER',
    'UPPER_LEFT_CORNER',
    'LOWER_LEFT_CORNER',
] as const;