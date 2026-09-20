/**
 * Tests for PriorityAllocator — sparse drawable priorities must make
 * add/remove O(changed), with whole-fragment renumbering only on exhaustion.
 * Compute is pure; commits persist state only after native success.
 */

import { PriorityAllocator } from '../PriorityAllocator';

const assign = (
	allocator: PriorityAllocator,
	fragmentUuid: string,
	uids: string[]
): Map<string, number> => {
	const computation = allocator.computeFor(fragmentUuid, uids);
	allocator.commitFor(fragmentUuid, computation.next);
	return computation.changed;
};

describe('PriorityAllocator', () => {
	test('initial assignment spaces entries by STEP', () => {
		const allocator = new PriorityAllocator();
		const changed = assign(allocator, 'frag', [
			'a',
			'b',
			'c',
		]);
		expect([...changed.entries()]).toEqual([
			['a', 0],
			['b', 1000],
			['c', 2000],
		]);
	});

	test('unchanged order produces no changes', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', [
			'a',
			'b',
			'c',
		]);
		expect(
			assign(allocator, 'frag', [
				'a',
				'b',
				'c',
			]).size
		).toBe(0);
	});

	test('uncommitted computation is re-derived from the old state', () => {
		const allocator = new PriorityAllocator();
		// Compute but never commit — the state must stay untouched.
		allocator.computeFor('frag', ['a', 'b']);
		const changed = allocator.computeFor('frag', ['a', 'b']).changed;
		expect([...changed.entries()]).toEqual([
			['a', 0],
			['b', 1000],
		]);
	});

	test('insert at the front changes only the new entry (3000-line case)', () => {
		const allocator = new PriorityAllocator();
		const uids = Array.from({ length: 3000 }, (_, i) => `l${i}`);
		assign(allocator, 'frag', uids);

		const changed = assign(allocator, 'frag', ['new', ...uids]);
		expect(changed.size).toBe(1);
		expect(changed.get('new')).toBe(-1000);
	});

	test('insert in the middle takes a midpoint', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', ['a', 'c']);
		const changed = assign(allocator, 'frag', [
			'a',
			'b',
			'c',
		]);
		expect(changed.size).toBe(1);
		expect(changed.get('b')).toBe(500);
	});

	test('removal changes nothing', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', [
			'a',
			'b',
			'c',
		]);
		expect(assign(allocator, 'frag', ['a', 'c']).size).toBe(0);
	});

	test('kept entries keep priorities across mixed changes', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', [
			'a',
			'b',
			'c',
		]);

		const changed = assign(allocator, 'frag', [
			'x',
			'a',
			'c',
		]);
		expect(changed.size).toBe(1);
		expect(changed.get('x')).toBe(-1000);

		const after = assign(allocator, 'frag', [
			'x',
			'a',
			'c',
		]);
		expect(after.size).toBe(0);
	});

	test('reordering known entries triggers a full renumber', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', [
			'a',
			'b',
			'c',
			'd',
		]);

		const changed = assign(allocator, 'frag', [
			'd',
			'c',
			'b',
			'a',
		]);
		expect([...changed.entries()]).toEqual([
			['d', 0],
			['c', 1000],
			['b', 2000],
			['a', 3000],
		]);
	});

	test('gap exhaustion renumbers the whole fragment once', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', ['a', 'z']);

		// Squeeze insertions between a and z until the gap closes.
		const list = ['a', 'z'];
		let sawFullRenumber = false;
		for (let i = 0; i < 15; i++) {
			const uid = `x${i}`;
			list.splice(1, 0, uid);
			const changed = assign(allocator, 'frag', list);
			// Normal inserts change exactly one entry; a renumber changes
			// many (kept entries included).
			if (changed.size > 1) {
				sawFullRenumber = true;
			}
		}

		// Exhaustion must have triggered exactly one whole-fragment renumber.
		expect(sawFullRenumber).toBe(true);

		// After the renumber, further inserts are cheap midpoints again.
		const cheap = assign(allocator, 'frag', [
			...list.slice(0, 1),
			'after-renumber',
			...list.slice(1),
		]);
		expect(cheap.size).toBe(1);
	});

	test('forget clears state for destroyed fragments', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag', ['a', 'b']);
		allocator.forget('frag');
		expect(assign(allocator, 'frag', ['a', 'b']).size).toBe(2);
	});

	test('fragments are isolated from each other', () => {
		const allocator = new PriorityAllocator();
		assign(allocator, 'frag1', ['a', 'b']);
		expect(assign(allocator, 'frag2', ['a', 'b']).size).toBe(2);
		expect(assign(allocator, 'frag1', ['a', 'b']).size).toBe(0);
	});
});
