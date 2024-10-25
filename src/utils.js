/**
 * External dependencies
 */
import { isArray, isObject } from "lodash-es";

export const isValidPosition = position => {
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

export const isValidPositions = positions => {
    return isArray( positions ) && -1 === positions.findIndex( pos => ! isValidPosition( pos ) )
};