/**
 * Context provided by LayerMarker: the enclosing marker layer's native uuid
 * (for native create/update calls) and its anchor uid (the scene fragmentId
 * that Marker entries declare against). Both null when the marker renders
 * standalone.
 */

import { createContext } from 'react';

export type MarkerLayerContextValue = {
	markerLayerUuid: null | string;
	fragmentId: null | string;
};

const MarkerLayerContext = createContext<MarkerLayerContextValue>({
	markerLayerUuid: null,
	fragmentId: null,
});

export default MarkerLayerContext;
