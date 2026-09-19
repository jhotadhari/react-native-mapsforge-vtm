/**
 * Tests for planDiff — the presenter's decision payload.
 */

import { diffPlans } from '../planDiff';
import { PriorityAllocator } from '../PriorityAllocator';
import { buildPlan } from '../planBuilder';
import { fragmentUuidFor } from '../ids';
import type { AnchorDescriptor, EntryDeclaration } from '../types';

const dedicated = (uid: string): AnchorDescriptor => ({
	uid,
	kind: 'layer',
	shared: false,
});

const owner = (uid: string, fragmentId: string): AnchorDescriptor => ({
	uid,
	kind: 'fragment',
	fragmentId,
});

const entry = (
	uid: string,
	fragmentId: string,
	sortIndex: number
): EntryDeclaration => ({ uid, fragmentId, layerType: 'path', sortIndex });

const makePlan = (
	walk: AnchorDescriptor[],
	entries: EntryDeclaration[],
	uuids: Map<string, string>
) => buildPlan(walk, new Map(entries.map((e) => [e.uid, e])), uuids);

describe('diffPlans', () => {
	test('identical plans need no sync', () => {
		const allocator = new PriorityAllocator();
		const plan = makePlan([dedicated('a')], [], new Map([['a', 'uuid-a']]));
		const diff = diffPlans(plan, plan, allocator);
		expect(diff.orderedUuids).toEqual(['uuid-a']);
		expect(diff.layerOrderChanged).toBe(false);
		expect(diff.removedUuids).toEqual([]);
		expect(diff.addedUuids).toEqual([]);
		expect(diff.entryPriorityChanges.size).toBe(0);
	});

	test('reorder produces layerOrderChanged', () => {
		const allocator = new PriorityAllocator();
		const prev = makePlan(
			[dedicated('a'), dedicated('b')],
			[],
			new Map([
				['a', 'uuid-a'],
				['b', 'uuid-b'],
			])
		);
		const next = makePlan(
			[dedicated('b'), dedicated('a')],
			[],
			new Map([
				['a', 'uuid-a'],
				['b', 'uuid-b'],
			])
		);
		const diff = diffPlans(prev, next, allocator);
		expect(diff.orderedUuids).toEqual(['uuid-b', 'uuid-a']);
		expect(diff.layerOrderChanged).toBe(true);
		expect(diff.removedUuids).toEqual([]);
	});

	test('added and removed uuids are reported', () => {
		const allocator = new PriorityAllocator();
		const prev = makePlan([dedicated('a')], [], new Map([['a', 'uuid-a']]));
		const next = makePlan(
			[dedicated('a'), dedicated('b')],
			[],
			new Map([
				['a', 'uuid-a'],
				['b', 'uuid-b'],
			])
		);
		const diff = diffPlans(prev, next, allocator);
		expect(diff.addedUuids).toEqual(['uuid-b']);
		expect(diff.removedUuids).toEqual([]);

		const back = diffPlans(next, prev, allocator);
		expect(back.removedUuids).toEqual(['uuid-b']);
		expect(back.addedUuids).toEqual([]);
	});

	test('entry priority changes only cover changed entries', () => {
		const allocator = new PriorityAllocator();
		const walk = [owner('sl1', 'shared1')];
		const uuids = (ids: string[]) =>
			new Map(ids.map((id) => [id, `uuid-${id}`]));

		const prev = makePlan(
			walk,
			[entry('e1', 'shared1', 0), entry('e2', 'shared1', 1)],
			uuids(['e1', 'e2'])
		);
		const initial = diffPlans(prev, prev, allocator);
		// First diff assigns initial sparse priorities for both entries.
		expect([
			...initial.entryPriorityChanges
				.get(fragmentUuidFor('shared1', 'path'))!
				.keys(),
		]).toEqual([
			'e1',
			'e2',
		]);
		expect(diffPlans(prev, prev, allocator).entryPriorityChanges.size).toBe(
			0
		);

		const next = makePlan(
			walk,
			[
				entry('e1', 'shared1', 0),
				entry('e2', 'shared1', 1),
				entry('e3', 'shared1', 2),
			],
			uuids([
				'e1',
				'e2',
				'e3',
			])
		);
		const diff = diffPlans(prev, next, allocator);
		const changes = diff.entryPriorityChanges.get(
			fragmentUuidFor('shared1', 'path')
		);
		expect(changes).toBeDefined();
		expect([...changes!.keys()]).toEqual(['e3']);
	});
});
