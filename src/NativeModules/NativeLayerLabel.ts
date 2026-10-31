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
 * Screen position of the label — codegen-local mirror of the public
 * ScreenPosition in utils/screenPosition.ts. 'fraction' (default) of the
 * viewport, or 'dp', top-left origin. The label is re-positioned by the
 * Java side on every map movement.
 */
export type LabelPosition = {
	x: Double;
	y: Double;
	unit?: string; // 'fraction' | 'dp'
};

export type LabelPaint = {
	/** Text size in dp. */
	fontSize?: Int32;
	textColor?: string; // `#${string}`
	backgroundColor?: string; // `#${string}`
	backgroundAlpha?: Double; // 0..1
	borderRadius?: Int32;
	paddingHorizontal?: Int32;
	paddingVertical?: Int32;
	fontFamily?: string; // FontFamily
	fontStyle?: string; // FontStyle
};

export interface ModuleParams {
	// Inlined (not the LabelPaint alias): codegen rejects aliases in
	// getConstants() return types — mirrors ModuleParams in NativeLayerPath.
	paint?: {
		fontSize?: Int32;
		textColor?: string;
	};
}

export interface CreateLabelParams extends ModuleParams {
	layerUuids?: ReadonlyArray<string>;
	nativeNodeHandle: Int32;
	fragmentUuid?: string;
	text?: string;
	/** Screen anchor the label is positioned relative to (its center). */
	position?: LabelPosition;
	/**
	 * Geographic point the label follows: the label stays on the line
	 * between this point and `position` (inside the line with a `gapDp`
	 * gap when long enough, beyond `position` otherwise), re-computed per
	 * frame on the Java side.
	 */
	followPosition?: Position;
	/** Distance (dp) from `position` along the follow direction. */
	gapDp?: Double;
	/**
	 * Offset (dp) beyond `position`, away from the follow point, when the
	 * line is too short to hold the label inside it.
	 */
	fallbackOffsetDp?: Double;
}

export interface UpdateLabelParams {
	nativeNodeHandle?: Int32;
	uuid?: string;
	text?: string;
	position?: LabelPosition;
	followPosition?: Position;
	gapDp?: Double;
	fallbackOffsetDp?: Double;
	paint?: {
		// LabelPaint
		fontSize?: Int32;
		textColor?: string;
		backgroundColor?: string;
		backgroundAlpha?: Double;
		borderRadius?: Int32;
		paddingHorizontal?: Int32;
		paddingVertical?: Int32;
		fontFamily?: string;
		fontStyle?: string;
	};
}

export interface RemoveLabelParams {
	nativeNodeHandle: Int32;
	uuid: string;
}

export interface LabelResponse {
	uuid: string;
	nativeNodeHandle: Int32;
}

export type LayerLabelProps = {
	text?: string;
	/**
	 * Screen anchor: `['50%', '50%']`, `['100dp', '24dp']`, or
	 * `{ x, y, unit }`.
	 */
	position?: ScreenPointSugar;
	followPosition?: GeoJsonPosition;
	gapDp?: number;
	fallbackOffsetDp?: number;
	paint?: LabelPaint;
	onCreate?: null | ((response: LabelResponse) => void);
	onRemove?: null | ((response: LabelResponse) => void);
	onChange?: null | ((response: LabelResponse) => void);
	onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
	getConstants(): ModuleParams;
	createLabel(params: CreateLabelParams): Promise<LabelResponse>;
	updateLabel(params: UpdateLabelParams): Promise<LabelResponse>;
	removeLabel(params: RemoveLabelParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LayerLabel');
