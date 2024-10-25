/**
 * External dependencies
 */
import Queue from 'queue-promise';

// Init the promise queue and export the instance.
const promiseQueue = new Queue( {
	concurrent: 400,
	interval: 1,
} );

export default promiseQueue;
