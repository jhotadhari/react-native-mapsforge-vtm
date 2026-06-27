import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase, ResponseBase } from '../types';

interface CreateLayerParams {
	nativeNodeHandle: Int32;
	positionIndex: Int32;
}

interface RemoveLayerParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export type LayerScalebarProps = {
	onCreate?: null | ((response: ResponseBase) => void);
	onRemove?: null | ((response: ResponseBase) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	createLayer(params: CreateLayerParams): Promise<string>;
	removeLayer(params: RemoveLayerParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerScalebar');
