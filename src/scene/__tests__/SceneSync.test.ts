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

		// 10 retries with exponential backoff (250, 500, 1000, 2000, then
		// capped at 4000 for the rest).
		const delays = [
			250,
			500,
			1000,
			2000,
			4000,
			4000,
			4000,
			4000,
			4000,
			4000,
		];
		for (const delay of delays) {
			jest.advanceTimersByTime(delay);
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

	test('concurrent walk signals coalesce into one in-flight enumeration', async () => {
		const sync = new SceneSync();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });

		let resolveFirst!: (value: { anchors: string[] }) => void;
		mockEnumerate.mockImplementationOnce(
			() =>
				new Promise<{ anchors: string[] }>((resolve) => {
					resolveFirst = resolve;
				})
		);

		sync.scheduleWalk();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(1);

		// More signals while the enumeration is in-flight — no second
		// concurrent call.
		sync.scheduleWalk();
		sync.scheduleWalk();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(1);

		// Resolution re-schedules a fresh walk for the pending signals.
		resolveFirst({ anchors: ['ded'] });
		await flush();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(2);
	});

	test('destroy re-arms on the next non-null handle (StrictMode remount)', async () => {
		const sync = new SceneSync();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });
		mockEnumerate.mockResolvedValue({ anchors: ['ded'] });

		sync.scheduleWalk();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(1);

		sync.destroy();
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(1);

		// Same handle, after destroy — the presenter re-arms and walks.
		sync.setNativeNodeHandle(7);
		await flush();
		expect(mockEnumerate).toHaveBeenCalledTimes(2);
	});

	test('reorder failures retry with backoff and give up after the cap', async () => {
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
		mockReorder.mockRejectedValue(new Error('reorder failed'));

		sync.scheduleWalk();
		await flush(); // walk
		await flush(); // sync → reorder fails (attempt 1)
		expect(mockReorder).toHaveBeenCalledTimes(1);

		// 5 retries with exponential backoff: 250, 500, 1000, 2000, 4000.
		const delays = [
			250,
			500,
			1000,
			2000,
			4000,
		];
		for (const delay of delays) {
			jest.advanceTimersByTime(delay);
			await Promise.resolve();
			await Promise.resolve();
			await Promise.resolve();
		}
		expect(mockReorder).toHaveBeenCalledTimes(6);

		// Cap reached — no further retries without a scene mutation.
		jest.advanceTimersByTime(20000);
		await Promise.resolve();
		expect(mockReorder).toHaveBeenCalledTimes(6);

		// A fresh mutation resets the streak and retries.
		mockReorder.mockResolvedValue(undefined);
		scene.declareEntry({
			uid: 'e2',
			fragmentId: 'shared1',
			layerType: 'path',
			sortIndex: 1,
		});
		await flush();
		await flush();
		expect(mockReorder).toHaveBeenCalledTimes(7);
	});

	test('failed priority application is re-sent on the next mutation', async () => {
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

		// First application fails — the allocator must NOT commit.
		mockPathPriorities.mockRejectedValueOnce(new Error('apply failed'));
		sync.scheduleWalk();
		await flush();
		await flush();
		expect(mockPathPriorities).toHaveBeenCalledTimes(1);
		expect(mockPathPriorities).toHaveBeenLastCalledWith({
			nativeNodeHandle: 7,
			fragmentUuid: 'frag:shared1:path',
			assignments: [{ uuid: 'e1', priority: 0 }],
		});

		// Next mutation: the failed e1 assignment is re-sent alongside the
		// new entry (with commit-on-success, nothing was dropped).
		scene.declareEntry({
			uid: 'e2',
			fragmentId: 'shared1',
			layerType: 'path',
			sortIndex: 1,
		});
		scene.attachUuid('e2', 'uuid-e2');
		await flush();
		await flush();
		expect(mockPathPriorities).toHaveBeenCalledTimes(2);
		expect(mockPathPriorities).toHaveBeenLastCalledWith({
			nativeNodeHandle: 7,
			fragmentUuid: 'frag:shared1:path',
			assignments: [
				{ uuid: 'e1', priority: 0 },
				{ uuid: 'e2', priority: 1000 },
			],
		});
	});

	test('sustained mutation burst flushes exactly once at the max-wait', async () => {
		const sync = new SceneSync();
		const scene = sync.getScene();
		sync.setNativeNodeHandle(7);
		sync.registerAnchor({ uid: 'ded', kind: 'layer' });
		mockEnumerate.mockResolvedValue({ anchors: ['ded'] });

		sync.scheduleWalk();
		await flush(); // walk lands → scene mutated → sync scheduled (16ms)

		// Burst: keep mutating without ever letting the 16ms debounce elapse
		// (each 5ms advance resets the debounce). The 250ms max-wait must still
		// force a single flush.
		for (let i = 0; i < 8; i++) {
			scene.attachUuid('ded', `uuid-${i}`);
			jest.advanceTimersByTime(5);
			await Promise.resolve();
		}

		jest.advanceTimersByTime(250);
		await Promise.resolve();
		await Promise.resolve();

		expect(mockReorder).toHaveBeenCalledTimes(1);
	});
});
