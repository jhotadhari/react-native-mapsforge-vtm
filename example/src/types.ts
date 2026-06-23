import type { ElementType } from 'react';

export interface Example {
	key: string;
	label: string;
	ExampleComponent: ElementType<{
		height: number;
		width: number;
	}>;
}
