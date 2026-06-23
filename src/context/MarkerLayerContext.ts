/**
 * External dependencies
 */
import { createContext } from 'react';

export type MarkerLayerContextValue = {
	markerLayerUuid: null | false | string;
};

const MarkerLayerContext = createContext<MarkerLayerContextValue>({
	markerLayerUuid: null,
});

export default MarkerLayerContext;
