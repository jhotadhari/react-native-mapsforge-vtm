/**
 * External dependencies
 */
import { useContext, useEffect, type ReactNode } from 'react';

/**
 * Internal dependencies
 */
import MapHandleContext from '../context/MapHandleContext';

const SharedLayer = ({ children }: { children?: ReactNode }) => {
	const { registry } = useContext(MapHandleContext);

	useEffect(() => {
		registry.groupingDepth++;
		return () => {
			registry.groupingDepth--;
		};
	}, [registry]);

	return <>{children}</>;
};

export default SharedLayer;
