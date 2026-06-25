import type { ElementType } from 'react';

export type ExampleCategory = 'layers' | 'mapControls' | 'gestures';

export interface Example {
	key: string;
	label: string;
	category: ExampleCategory;
	ExampleComponent: ElementType<{
		height: number;
		width: number;
	}>;
}
