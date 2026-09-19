/**
 * Recursive vtmSortIndex injection — the fragment owner's entry-order
 * mechanism.
 *
 * Fragment owners (SharedLayer / LayerMarker) inject a sibling position into
 * their direct element children so the scene can order entries (LayerPath /
 * LayerShape / Marker) within a shared native fragment. This helper is the
 * generic walker:
 *
 * - traverses arrays and {@code <Fragment>} children (Fragments are cloned
 *   with their children walked — only key/children props, which is all a
 *   Fragment accepts)
 * - injects {@code vtmSortIndex} (a running document-order counter) into
 *   every function/class/memo/forwardRef element child
 * - leaves host components (string types) and exotic symbol types untouched —
 *   they cannot take unknown props without React warnings
 * - passes text, numbers and null/undefined/boolean children through
 *
 * Entries that receive no index (wrapped inside components or host Views)
 * sort after indexed ones by declaration order. Wrapper components should
 * forward {@code vtmSortIndex}, or consumers can pass an explicit
 * {@code order} prop to the entry component instead.
 */

import {
	cloneElement,
	Fragment,
	isValidElement,
	type ReactElement,
	type ReactNode,
} from 'react';

export const injectVtmSortIndex = (nodes: ReactNode): ReactNode => {
	let counter = 0;

	const walk = (node: ReactNode): ReactNode => {
		if (node === null || node === undefined || typeof node === 'boolean') {
			return node;
		}
		if (Array.isArray(node)) {
			return node.map(walk);
		}
		if (!isValidElement(node)) {
			// Text, number, or other primitive children.
			return node;
		}
		if (node.type === Fragment) {
			const fragment = node as ReactElement<{ children?: ReactNode }>;
			return cloneElement(fragment, {}, walk(fragment.props.children));
		}
		if (typeof node.type === 'string' || typeof node.type === 'symbol') {
			// Host components (View, Text, ...) and exotic types (Suspense,
			// Context, ...) — unknown props would warn; leave untouched.
			return node;
		}
		return cloneElement(node as ReactElement<{ vtmSortIndex?: number }>, {
			vtmSortIndex: counter++,
		});
	};

	return walk(nodes);
};
