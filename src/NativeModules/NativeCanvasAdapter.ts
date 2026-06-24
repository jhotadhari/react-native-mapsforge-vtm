import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double } from 'react-native/Libraries/Types/CodegenTypes';

export interface Spec extends TurboModule {
	setTextScale(scale: Double): void;
	setLineScale(scale: Double): void;
	setSymbolScale(scale: Double): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('CanvasAdapter');
