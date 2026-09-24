/**
 * Tests for the path-update batch queue: microtask-flush collapsing,
 * per-item resolution/errors, batch failure, and drain.
 */

import {
	enqueueUpdatePath,
	drainPathUpdateQueue,
} from '../PathUpdateBatchQueue';
import NativeLayerPath from '../../NativeModules/NativeLayerPath';

jest.mock('../../NativeModules/NativeLayerPath', () => ({
	__esModule: true,
	default: { updateLayers: jest.fn(() => Promise.resolve({ results: [] })) },
}));

const mockUpdateLayers = NativeLayerPath.updateLayers as jest.Mock;

const flushMicrotasks = async () => {
	await Promise.resolve();
	await Promise.resolve();
};

describe('PathUpdateBatchQueue', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	test('collapses multiple updates into one updateLayers call', async () => {
		mockUpdateLayers.mockResolvedValue({
			results: [
				{ uuid: 'u1', response: { uuid: 'u1' } },
				{ uuid: 'u2', response: { uuid: 'u2' } },
			],
		});

		const p1 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u1' });
		const p2 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u2' });
		await flushMicrotasks();

		expect(mockUpdateLayers).toHaveBeenCalledTimes(1);
		expect(mockUpdateLayers).toHaveBeenCalledWith({
			nativeNodeHandle: 7,
			paths: [
				{ nativeNodeHandle: 7, uuid: 'u1' },
				{ nativeNodeHandle: 7, uuid: 'u2' },
			],
		});

		await expect(p1).resolves.toEqual({ uuid: 'u1' });
		await expect(p2).resolves.toEqual({ uuid: 'u2' });
	});

	test('resolves a synthesized response when an item carries no response', async () => {
		mockUpdateLayers.mockResolvedValue({
			results: [{ uuid: 'u1' }],
		});

		const p1 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u1' });
		await flushMicrotasks();

		await expect(p1).resolves.toEqual({ uuid: 'u1', nativeNodeHandle: 7 });
	});

	test('rejects an update whose batch item carries an error', async () => {
		mockUpdateLayers.mockResolvedValue({
			results: [{ uuid: 'u1', error: 'nope' }],
		});

		const p1 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u1' });
		await flushMicrotasks();

		await expect(p1).rejects.toThrow('nope');
	});

	test('batch failure rejects all pending updates', async () => {
		mockUpdateLayers.mockRejectedValue(new Error('boom'));

		const p1 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u1' });
		const p2 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u2' });
		await flushMicrotasks();

		await expect(p1).rejects.toThrow('boom');
		await expect(p2).rejects.toThrow('boom');
	});

	test('drain rejects pending updates', async () => {
		mockUpdateLayers.mockResolvedValue({ results: [] });

		const p1 = enqueueUpdatePath({ nativeNodeHandle: 7, uuid: 'u1' });
		drainPathUpdateQueue(7);
		await flushMicrotasks();

		await expect(p1).rejects.toThrow('Map view destroyed');
		expect(mockUpdateLayers).not.toHaveBeenCalled();
	});
});
