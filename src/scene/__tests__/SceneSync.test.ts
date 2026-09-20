/**
 * Tests for SceneSync — the presenter: walk → plan → diff → native sync,
 * including entry-priority dispatch with layerType routing.
 */

import { SceneSync } from '../SceneSync';
import NativeMapContainer from '../../NativeModules/NativeMapContainer';
import NativeLayerPath from '../../NativeModules/NativeLayerPath';
import NativeLayerShape from '../../NativeModules/NativeLayerShape';
import NativeLayerMarker from '../../NativeModules/NativeLayerMarker';

jest.mock('../../NativeModules/NativeMapContainer', () => ({
	__esModule: true,
	default: {
		enumerateAnchors: jest.fn(() => Promise.resolve({ anchors: [] })),
		reorderLayers: jest.fn(() => Promise.resolve()),
	},
}));

jest.mock('../../NativeModules/NativeLayerPath', () => ({
	__esModule: true,
	default: { applyEntryPriorities: jest.fn(() => Promise.resolve()) },
}));

jest.mock('../../NativeModules/NativeLayerShape', () => ({
	__esModule: true,
	default: { applyEntryPriorities: jest.fn(() => Promise.resolve()) },
}));

jest.mock('../../NativeModules/NativeLayerMarker', () => ({
	__esModule: true,
	default: { applyEntryPriorities: jest.fn(() => Promise.resolve()) },
}));

const mockEnumerate = NativeMapContainer.enumerateAnchors as jest.Mock;
const mockReorder = NativeMapContainer.reorderLayers as jest.Mock;
const mockPathPriorities = NativeLayerPath.applyEntryPriorities as jest.Mock;

const flush = async () => {
	jest.advanceTimersByTime(20);
	// Drain the promise-chain microtasks (then-propagation + catch) so
	// retry timers are armed at the current fake time.
	await Promise.resolve();
	await Promise.resolve();
	await Promise.resolve();
};

describe('SceneSync', () => {
	beforeEach(() => {
		jest.useFakeTimers();
		jest.clearAllMocks();
		mockEnumerate.mockResolvedValue({ anchors: [] });
	});

	afterEach(() => {
		jest.useRealTimers();
	});

	test('walk + plan + reorder with shared fragments and entry priorities', async () => {
		const sync = new SceneSync();
		const scene = sync.getScene();
		sync.setNativeNodeHandle(7);

		// Anchor descriptors: a SharedLayer fragment owner + a dedicated layer.
		sync.registerAnchor({
			uid: 'sl1',
			kind: 'fragment',
			fragmentId: 'shared1',
		});
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });

		// One path entry inside the SharedLayer, resolved.
		scene.declareEntry({
			uid: 'e1',
			fragmentId: 'shared1',
			layerType: 'path',
			sortIndex: 0,
		});
		scene.attachUuid('e1', 'uuid-e1');

		mockEnumerate.mockResolvedValue({ anchors: ['sl1', 'ded'] });

		sync.scheduleWalk();
		await flush(); // walk timer
		await flush(); // sync timer (debounced after applyWalk notification)

		expect(mockEnumerate).toHaveBeenCalledWith({ nativeNodeHandle: 7 });

		// The shared fragment is resolved (one entry uuid); the dedicated
		// layer has no uuid yet and stays out of the plan.
		expect(mockReorder).toHaveBeenCalledWith({
			nativeNodeHandle: 7,
			layerUuids: ['frag:shared1:path'],
		});

		// Entry priorities dispatched to the correct module with
		// changed-only assignments (initial assignment for the new entry).
		expect(mockPathPriorities).toHaveBeenCalledWith({
			nativeNodeHandle: 7,
			fragmentUuid: 'frag:shared1:path',
			assignments: [{ uuid: 'e1', priority: 0 }],
		});
		expect(NativeLayerShape.applyEntryPriorities).not.toHaveBeenCalled();
		expect(NativeLayerMarker.applyEntryPriorities).not.toHaveBeenCalled();
	});

	test('unchanged plan skips native calls', async () => {
		const sync = new SceneSync();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });

		mockEnumerate.mockResolvedValue({ anchors: ['ded'] });
		sync.scheduleWalk();
		await flush();
		await flush();
		expect(mockReorder).not.toHaveBeenCalled();

		// Second identical sync: nothing changed.
		sync.scheduleWalk();
		await flush();
		await flush();
		expect(mockReorder).not.toHaveBeenCalled();
	});

	test('detached uuid removes the fragment from the plan', async () => {
		const sync = new SceneSync();
		const scene = sync.getScene();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({
			uid: 'sl1',
			kind: 'fragment',
			fragmentId: 'shared1',
		});
		scene.declareEntry({
			uid: 'e1',
			fragmentId: 'shared1',
			layerType: 'path',
			sortIndex: 0,
		});
		scene.attachUuid('e1', 'uuid-e1');
		mockEnumerate.mockResolvedValue({ anchors: ['sl1'] });

		sync.scheduleWalk();
		await flush();
		await flush();
		expect(mockReorder).toHaveBeenCalledWith({
			nativeNodeHandle: 7,
			layerUuids: ['frag:shared1:path'],
		});

		scene.detachUuid('e1');
		await flush();
		await flush();
		expect(mockReorder).toHaveBeenLastCalledWith({
			nativeNodeHandle: 7,
			layerUuids: [],
		});
	});

	test('walk failure retries with backoff and recovers', async () => {
		const sync = new SceneSync();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });

		mockEnumerate.mockRejectedValueOnce(new Error('walk failed'));
		sync.scheduleWalk();
		await flush(); // debounce fires; enumerate rejects; retry armed

		expect(mockEnumerate).toHaveBeenCalledTimes(1);

		// The max-wait must NOT re-fire the already-run walk (pending-burst
		// guard) — only the retry fires at +250ms.
		jest.advanceTimersByTime(250);
		await Promise.resolve();
		expect(mockEnumerate).toHaveBeenCalledTimes(2);

		// Retry succeeded — no further walks are scheduled.
		jest.advanceTimersByTime(500);
		await Promise.resolve();
		expect(mockEnumerate).toHaveBeenCalledTimes(2);
	});

	test('walk failures give up after the retry cap until a fresh scheduleWalk', async () => {
		const sync = new SceneSync();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });

		mockEnumerate.mockRejectedValue(new Error('persistent failure'));
		sync.scheduleWalk();
		await flush(); // attempt 1

		// 10 retries, 250ms apart.
		for (let i = 0; i < 10; i++) {
			jest.advanceTimersByTime(250);
			await Promise.resolve();
			await Promise.resolve();
		}
		expect(mockEnumerate).toHaveBeenCalledTimes(11);

		// No further retries without a new schedule.
		jest.advanceTimersByTime(2000);
		await Promise.resolve();
		expect(mockEnumerate).toHaveBeenCalledTimes(11);

		// A fresh scheduleWalk resets the failure streak.
		mockEnumerate.mockResolvedValue({ anchors: [] });
		sync.scheduleWalk();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(12);
	});
});
