/**
 * External dependencies
 */
import { isArray, isObject } from "lodash-es";

/**
 * Internal dependencies
 */
import type { Location } from './types';

export const isValidPosition = ( position: Location | undefined | null ) => {
    if ( ! isObject( position ) ) {
        return false;
    }
    const keys = Object.keys( position );
    if ( ! keys.includes( 'lng' ) || ! keys.includes( 'lat' ) ) {
        return false;
    };
    if ( 'number' !== typeof position.lng || 'number' !== typeof position.lat ) {
        return false;
    }
    return true;
};

export const isValidPositions = ( positions: ( Location[] | undefined | null ) ) => {
    return isArray( positions ) && -1 === positions.findIndex( pos => ! isValidPosition( pos ) )
};