/**
 * External dependencies
 */
import { createContext } from 'react';

export type SharedLayerContextValue = {
	isGrouped: boolean;
};

const SharedLayerContext = createContext<SharedLayerContextValue>({
	isGrouped: false,
});

export default SharedLayerContext;
