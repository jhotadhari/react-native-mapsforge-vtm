import { codegenNativeComponent } from 'react-native';
import type { ViewProps } from 'react-native';

/**
 * Invisible marker view rendered by layer components, fragment owners
 * (SharedLayer / LayerMarker) and ReindexScope wrappers.
 *
 * Anchors mount as siblings of MapsforgeVtmView under MapContainer's
 * wrapper View. The native walk (MapContainer.enumerateAnchors) collects
 * their uids in committed tree order — that order IS the React tree order,
 * regardless of when or how the components mounted.
 */
interface VtmAnchorViewProps extends ViewProps {
	/** Stable component uid; the walk returns ordered uids for the scene. */
	uid: string;
}

export default codegenNativeComponent<VtmAnchorViewProps>('VtmAnchorView');
