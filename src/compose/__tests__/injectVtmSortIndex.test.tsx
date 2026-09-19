/**
 * Tests for the recursive vtmSortIndex injector — the fragment owner's
 * entry-order mechanism. Pure element-tree assertions, no rendering.
 */

import {
	createElement,
	Fragment,
	forwardRef,
	memo,
	type ReactElement,
} from 'react';
import { injectVtmSortIndex } from '../injectVtmSortIndex';

const Layer = (_props: { vtmSortIndex?: number }) => null;
const MemoLayer = memo(Layer);
const RefLayer = forwardRef<null, { vtmSortIndex?: number }>(Layer);

const sortIndexesOf = (nodes: unknown): number[] => {
	const indexes: number[] = [];
	const walk = (node: unknown): void => {
		if (node === null || node === undefined || typeof node === 'boolean') {
			return;
		}
		if (Array.isArray(node)) {
			node.forEach(walk);
			return;
		}
		if (typeof node === 'object' && 'props' in (node as object)) {
			const el = node as ReactElement;
			const props = el.props as {
				vtmSortIndex?: number;
				children?: unknown;
			};
			if (props.vtmSortIndex !== undefined) {
				indexes.push(props.vtmSortIndex);
			}
			walk(props.children);
		}
	};
	walk(nodes);
	return indexes;
};

describe('injectVtmSortIndex', () => {
	test('injects sequential indices into a flat array', () => {
		const result = injectVtmSortIndex([
			<Layer key="a" />,
			<Layer key="b" />,
			<Layer key="c" />,
		]);
		expect(sortIndexesOf(result)).toEqual([
			0,
			1,
			2,
		]);
	});

	test('traverses Fragment children without injecting into the Fragment (many-layers case)', () => {
		const result = injectVtmSortIndex([
			<Fragment key="p1">
				<Layer />
				<Layer />
			</Fragment>,
			<Fragment key="p2">
				<Layer />
				<Layer />
			</Fragment>,
		]);
		const elements = result as ReactElement[];
		expect(sortIndexesOf(result)).toEqual([
			0,
			1,
			2,
			3,
		]);
		// The fragments themselves must not carry vtmSortIndex.
		expect(
			(elements[0]!.props as { vtmSortIndex?: number }).vtmSortIndex
		).toBeUndefined();
		expect(
			(elements[1]!.props as { vtmSortIndex?: number }).vtmSortIndex
		).toBeUndefined();
	});

	test('handles nested fragment/array combinations in document order', () => {
		const result = injectVtmSortIndex(
			<Fragment>
				<Layer />
				{[
					<Layer key="x" />,
					<Fragment key="y">
						<Layer />
					</Fragment>,
				]}
				<Layer />
			</Fragment>
		);
		expect(sortIndexesOf(result)).toEqual([
			0,
			1,
			2,
			3,
		]);
	});

	test('leaves host components untouched', () => {
		const view = createElement('View', { key: 'v' });
		const result = injectVtmSortIndex([
			<Layer key="a" />,
			view,
			<Layer key="b" />,
		]);
		expect(sortIndexesOf(result)).toEqual([0, 1]);
		const elements = result as ReactElement[];
		expect(
			(elements[1]!.props as object).hasOwnProperty('vtmSortIndex')
		).toBe(false);
	});

	test('passes primitives and null children through', () => {
		const result = injectVtmSortIndex([
			null,
			undefined,
			true,
			false,
			'text',
			42,
			<Layer key="a" />,
		]);
		expect(sortIndexesOf(result)).toEqual([0]);
	});

	test('injects into memo and forwardRef elements (object types)', () => {
		const result = injectVtmSortIndex([
			<MemoLayer key="m" />,
			<RefLayer key="r" />,
		]);
		expect(sortIndexesOf(result)).toEqual([0, 1]);
	});

	test('preserves keys on fragments and elements', () => {
		const result = injectVtmSortIndex([
			<Fragment key="frag-key">
				<Layer key="inner-key" />
			</Fragment>,
		]);
		const elements = result as ReactElement[];
		expect(elements[0]!.key).toBe('frag-key');
		const fragment = elements[0]! as ReactElement<{ children?: unknown }>;
		const inner = fragment.props.children as ReactElement;
		expect(inner.key).toBe('inner-key');
	});
});
