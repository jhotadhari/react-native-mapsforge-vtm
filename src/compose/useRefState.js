/**
 * External dependencies
 */
import { useRef } from 'react';

/**
 * Like useState, returns a stateful value, and a function to update it.
 * But uses a ref instead of a state.
 * Like this react manages to "update the state" of many sibling components concurrently. Well, it's not updating the state actually, it's updating the ref.
 */
const useRefState = initial => {
	const stateRef = useRef( initial );
	return [
		stateRef?.current,
		newVal => {
			stateRef.current = newVal;
		},
	];
};

export default useRefState;
