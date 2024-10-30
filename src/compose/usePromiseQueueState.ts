/**
 * External dependencies
 */
import {
	useEffect,
	useState,
} from 'react';

/**
 * Internal dependencies
 */
import promiseQueue from '../promiseQueue';

const State = {
	IDLE: 0,
	RUNNING: 1,
	STOPPED: 2,
};

const usePromiseQueueState = () : number => {

	const [state, setState] = useState( -1 );

	const handleStart = () => setState( State.RUNNING );
	const handleStop = () => setState( State.STOPPED );
	const handleEnd = () => setState( State.IDLE );

	useEffect( () => {
		promiseQueue.on( 'start', handleStart );
		promiseQueue.on( 'stop', handleStop );
		promiseQueue.on( 'end', handleEnd );

		switch ( true ) {
			case ( 1 === promiseQueue?.state?.RUNNING ):
			case ( promiseQueue.shouldRun ):
				setState( State.RUNNING );
				break;
			case ( 2 === promiseQueue?.state?.STOPPED ):
				setState( State.STOPPED );
				break;
			default:
				setState( State.IDLE );
		}

		return () => {
			promiseQueue.removeListener( 'start', handleStart );
			promiseQueue.removeListener( 'stop', handleStop );
			promiseQueue.removeListener( 'end', handleEnd );
		};
	}, [] );

	return state;
};

export default usePromiseQueueState;

