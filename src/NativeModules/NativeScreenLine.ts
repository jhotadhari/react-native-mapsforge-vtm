import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { Position as GeoJsonPosition } from 'geojson';
import type { ErrorBase } from '../types';
import type { ScreenPointSugar } from '../utils/screenPosition';

/*
 * Type should be redeclared because of codegen ts parser doesn't allow imported type
 * [comments](https://github.com/reactwg/react-native-new-architecture/discussions/91#discussioncomment-4282452)
 *
 * Mirrors geojson's `Position` ( `[ lng, lat, alt? ]` ), but using `Double` as required by codegen.
 */
type Position = ReadonlyArray<Double>;

/*
 * Screen-anchored point override — codegen-local mirror of the public
 * ScreenPosition in utils/screenPosition.ts. The Java side re-resolves
 * anchored points against the frame's own viewport every rendered frame
 * (no worker, no bridge) — see ScreenLineRenderer.
 */
export type ScreenPosition = {
	/** Index into the resolved point list (geo + anchored). */
	index?: Int32;
	x: Double;
	y: Double;
	unit?: string; // 'fraction' | 'dp'
};

export type ScreenLinePaint = {
	/** Hex color, e.g. '#000000'. */
	strokeColor?: string;
	/** Stroke width in dp (screen-fixed). */
	strokeWidth?: Double;
	cap?: 'SQUARE' | 'ROUND' | 'BUTT';
};

export interface ModuleParams {
	// Inlined (not the ScreenLinePaint alias): codegen rejects aliases in
	// getConstants() return types — mirrors ModuleParams in NativeLayerPath.
	paint?: {
		strokeColor?: string;
		strokeWidth?: Double;
		cap?: string;
	};
}

export interface CreateLineParams extends ModuleParams {
	layerUuids?: ReadonlyArray<string>;
	nativeNodeHandle: Int32;
	fragmentUuid?: string;
	coordinates?: ReadonlyArray<Position>; // geojson LineString-style `coordinates`
	/**
	 * Screen-anchored point overrides. Anchored points are inserted into
	 * `coordinates` at their `index` and re-resolved per frame on the Java
	 * side (zero bridge crossings, zero latency — the renderer builds its
	 * geometry from the frame's own viewport).
	 */
	screenPoints?: ReadonlyArray<ScreenPosition>;
}

export interface UpdateLineParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
	coordinates?: ReadonlyArray<Position>;
	/**
	 * Key present = replace the whole anchor set (empty array clears it);
	 * absent = keep existing anchors.
	 */
	screenPoints?: ReadonlyArray<ScreenPosition>;
	paint?: {
		// ScreenLinePaint
		strokeColor?: string;
		strokeWidth?: Double;
		cap?: string;
	};
}

export interface RemoveLineParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export interface LineResponse {
	uuid: string;
	nativeNodeHandle: Int32;
}

export type LayerScreenLineProps = {
	/**
	 * At least 2 points. Elements may be geographic tuples or
	 * screen-anchored points (`['50%', '50%']`, `['100dp', '24dp']`, or
	 * `{ x, y, unit }`) pinned to the map's screen.
	 */
	coordinates: (GeoJsonPosition | ScreenPointSugar)[];
	paint?: ScreenLinePaint;
	onCreate?: null | ((response: LineResponse) => void);
	onRemove?: null | ((response: LineResponse) => void);
	onChange?: null | ((response: LineResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLine(params: CreateLineParams): Promise<LineResponse>;
	updateLine(params: UpdateLineParams): Promise<LineResponse>;
	removeLine(params: RemoveLineParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerScreenLine');
