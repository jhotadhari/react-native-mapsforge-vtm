import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double } from 'react-native/Libraries/Types/CodegenTypes';

/*
 * Types are redeclared locally because the codegen TS parser doesn't
 * allow imported types. Mirrors GnssFilterNativeProps from the view
 * spec (the view-level gnssFilter prop is gone — this module is the
 * single owner of the GNSS listener now).
 */
export interface GnssFilterNativeProps {
	minDistanceMeters: Double;
	minTimeSec: Double;
	minAccuracyMeters: Double;
	provider: string;
	altitudeSource: string;
}

export interface Spec extends TurboModule {
	/**
	 * Configure or stop the GNSS listener. `null` stops it; a config
	 * (re)starts it with the given recording-gate parameters. The
	 * listener is application-scoped — it keeps running while no map
	 * view is mounted (background track recording).
	 */
	setFilter(filter: null | GnssFilterNativeProps): void;
	/**
	 * Configure the DEM source used for altitude resolution (same
	 * directory semantics as the map's setHgtDirPath). `null`/empty
	 * tears the reader down — altitude then falls back to raw GNSS.
	 */
	setHgtDirPath(path: null | string): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('GnssModule');
