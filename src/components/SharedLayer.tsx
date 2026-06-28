/**
 * External dependencies
 */
import { type ReactNode } from 'react';

/**
 * Internal dependencies
 */
import SharedLayerContext from '../context/SharedLayerContext';

const SharedLayer = ({ children }: { children?: ReactNode }) => {
	return (
		<SharedLayerContext.Provider value={{ isGrouped: true }}>
			{children}
		</SharedLayerContext.Provider>
	);
};

export default SharedLayer;
