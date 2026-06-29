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
		registry.notify();
		return () => {
			registry.groupingDepth--;
			registry.notify();
		};
	}, [registry]);

	return <>{children}</>;
};

export default SharedLayer;
