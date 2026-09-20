/**
 * Tests for the generic entry-batch queue: microtask-flush collapsing,
 * per-item result resolution, per-item errors, batch failures, and drain.
 */

import { createEntryBatchQueue } from '../EntryBatchQueue';

type Item = { nativeNodeHandle: number; value: string };
type CreateResult = { uuid: string; echoed: string };

const makeQueue = (
	overrides: Partial<{
		createMany: (
			handle: number,
			items: Item[]
		) => Promise<{
			results: ReadonlyArray<{
				uuid: string;
				error?: string;
				response?: { uuid: string };
			}>;
		}>;
		removeMany: (
			handle: number,
			uuids: string[]
		) => Promise<{
			results: ReadonlyArray<{ uuid: string; error?: string }>;
		}>;
	}> = {}
) =>
	createEntryBatchQueue<Item, CreateResult>({
		createMany: overrides.createMany ?? jest.fn(),
		removeMany: overrides.removeMany ?? jest.fn(),
		resolveCreate: (result, params) => ({
			uuid: result.uuid,
			echoed: params.value,
		}),
	});

const flushMicrotasks = async () => {
	await Promise.resolve();
	await Promise.resolve();
};

describe('EntryBatchQueue', () => {
	test('collapses multiple creates into one createMany call', async () => {
		const createMany = jest.fn(() =>
			Promise.resolve({
				results: [
					{ uuid: 'u1' },
					{ uuid: 'u2' },
				],
			})
		);
		const queue = makeQueue({ createMany });

		const p1 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'a' });
		const p2 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'b' });
		await flushMicrotasks();

		expect(createMany).toHaveBeenCalledTimes(1);
		expect(createMany).toHaveBeenCalledWith(7, [
			{ nativeNodeHandle: 7, value: 'a' },
			{ nativeNodeHandle: 7, value: 'b' },
		]);
		await expect(p1).resolves.toEqual({ uuid: 'u1', echoed: 'a' });
		await expect(p2).resolves.toEqual({ uuid: 'u2', echoed: 'b' });
	});

	test('queues are isolated per nativeNodeHandle', async () => {
		const createMany = jest.fn((handle: number) =>
			Promise.resolve({
				results: [{ uuid: `u-${handle}` }],
			})
		);
		const queue = makeQueue({ createMany });

		const p1 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'a' });
		const p2 = queue.enqueueCreate({ nativeNodeHandle: 8, value: 'b' });
		await flushMicrotasks();

		expect(createMany).toHaveBeenCalledTimes(2);
		await expect(p1).resolves.toEqual({ uuid: 'u-7', echoed: 'a' });
		await expect(p2).resolves.toEqual({ uuid: 'u-8', echoed: 'b' });
	});

	test('per-item errors reject only that item', async () => {
		const createMany = jest.fn(() =>
			Promise.resolve({
				results: [
					{ uuid: 'u1' },
					{ uuid: 'u2', error: 'bad path' },
				],
			})
		);
		const queue = makeQueue({ createMany });

		const p1 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'a' });
		const p2 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'b' });
		await flushMicrotasks();

		await expect(p1).resolves.toEqual({ uuid: 'u1', echoed: 'a' });
		await expect(p2).rejects.toThrow('bad path');
	});

	test('batch-level failure rejects all pending creates', async () => {
		const createMany = jest.fn(() =>
			Promise.reject(new Error('map destroyed'))
		);
		const queue = makeQueue({ createMany });

		const p1 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'a' });
		const p2 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'b' });
		await flushMicrotasks();

		await expect(p1).rejects.toThrow('map destroyed');
		await expect(p2).rejects.toThrow('map destroyed');
	});

	test('collapses removes into one removeMany call', async () => {
		const removeMany = jest.fn(() =>
			Promise.resolve({
				results: [{ uuid: 'u1' }, { uuid: 'u2' }],
			})
		);
		const queue = makeQueue({ removeMany });

		const p1 = queue.enqueueRemove(7, 'u1');
		const p2 = queue.enqueueRemove(7, 'u2');
		await flushMicrotasks();

		expect(removeMany).toHaveBeenCalledTimes(1);
		expect(removeMany).toHaveBeenCalledWith(7, ['u1', 'u2']);
		await expect(p1).resolves.toBe('u1');
		await expect(p2).resolves.toBe('u2');
	});

	test('drainQueue rejects all pending operations', async () => {
		const createMany = jest.fn(() => new Promise<never>(() => {}));
		const removeMany = jest.fn(() => new Promise<never>(() => {}));
		const queue = makeQueue({ createMany, removeMany });

		const p1 = queue.enqueueCreate({ nativeNodeHandle: 7, value: 'a' });
		const p2 = queue.enqueueRemove(7, 'u1');
		queue.drainQueue(7);

		await expect(p1).rejects.toThrow('Map view destroyed');
		await expect(p2).rejects.toThrow('Map view destroyed');
	});
});
