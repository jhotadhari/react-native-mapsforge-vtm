/**
 * External dependencies
 */
import {
	useRef,
	useEffect,
} from 'react';

// https://blog.logrocket.com/accessing-previous-props-state-react-hooks/
const usePrevious = ( value : any ) : any => {
	const ref = useRef<any>();
	useEffect( () => {
		ref.current = value;
	}, [value] );
	return ref.current;
};

export default usePrevious;
