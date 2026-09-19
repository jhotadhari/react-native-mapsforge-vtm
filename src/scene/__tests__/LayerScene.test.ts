/**
 * Tests for LayerScene — commit-phase mutation model, plan caching,
 * command log and observer notifications.
 */

import { LayerScene } from '../LayerScene';
import type { AnchorDescriptor, EntryDeclaration } from '../types';

const dedicated = (uid: string): AnchorDescriptor => ({
	uid,
	kind: 'layer',
	shared: false,
});

const entry = (
	uid: string,
	fragmentId: string,
	layerType: string,
	sortIndex?: number
): EntryDeclaration => ({
	uid,
	fragmentId,
	layerType,
	...(sortIndex !== undefined ? { sortIndex } : {}),
});

describe('LayerScene', () => {
	test('plan is cached until a mutation invalidates it', () => {
		const scene = new LayerScene();
		scene.applyWalk([dedicated('a')]);
		const first = scene.plan();
		expect(scene.plan()).toBe(first);

		scene.attachUuid('a', 'uuid-a');
		expect(scene.plan()).not.toBe(first);
	});

	test('attachUuid makes dedicated layers appear in the plan', () => {
		const scene = new LayerScene();
		scene.applyWalk([dedicated('a')]);
		expect(scene.plan().layers).toEqual([]);

		scene.attachUuid('a', 'uuid-a');
		expect(scene.plan().layers.map((l) => l.uuid)).toEqual(['uuid-a']);

		scene.detachUuid('a');
		expect(scene.plan().layers).toEqual([]);
	});

	test('declareEntry assigns monotonic declarationSeq', () => {
		const scene = new LayerScene();
		scene.declareEntry(entry('e1', 'shared1', 'path', 0));
		scene.declareEntry(entry('e2', 'shared1', 'path'));

		const plan = scene.plan();
		expect(plan.fragments).toHaveLength(0);

		scene.applyWalk([
			{ uid: 'sl1', kind: 'fragment', fragmentId: 'shared1' },
		]);
		const withOwner = scene.plan();
		expect(withOwner.fragments[0]!.entryUids).toEqual(['e1', 'e2']);
	});

	test('undeclareEntry removes the entry from fragment plans', () => {
		const scene = new LayerScene();
		scene.applyWalk([
			{ uid: 'sl1', kind: 'fragment', fragmentId: 'shared1' },
		]);
		scene.declareEntry(entry('e1', 'shared1', 'path', 0));
		scene.declareEntry(entry('e2', 'shared1', 'path', 1));
		expect(scene.plan().fragments[0]!.entryUids).toEqual(['e1', 'e2']);

		scene.undeclareEntry('e1');
		expect(scene.plan().fragments[0]!.entryUids).toEqual(['e2']);
	});

	test('redeclaring an entry updates its sortIndex in place', () => {
		const scene = new LayerScene();
		scene.applyWalk([
			{ uid: 'sl1', kind: 'fragment', fragmentId: 'shared1' },
		]);
		scene.declareEntry(entry('e1', 'shared1', 'path', 0));
		scene.declareEntry(entry('e2', 'shared1', 'path', 1));
		scene.declareEntry(entry('e1', 'shared1', 'path', 2));

		expect(scene.plan().fragments[0]!.entryUids).toEqual(['e2', 'e1']);
	});

	test('listeners are notified on every mutation and can unsubscribe', () => {
		const scene = new LayerScene();
		const listener = jest.fn();
		const unsubscribe = scene.subscribe(listener);

		scene.applyWalk([dedicated('a')]);
		scene.attachUuid('a', 'uuid-a');
		scene.declareEntry(entry('e1', 'shared1', 'path'));
		scene.undeclareEntry('e1');
		scene.detachUuid('a');
		expect(listener).toHaveBeenCalledTimes(5);

		unsubscribe();
		scene.attachUuid('a', 'uuid-a');
		expect(listener).toHaveBeenCalledTimes(5);
	});

	test('command log records every mutation for replay/debug', () => {
		const scene = new LayerScene();
		scene.applyWalk([dedicated('a')]);
		scene.attachUuid('a', 'uuid-a');
		scene.declareEntry(entry('e1', 'shared1', 'path'));

		expect(scene.commandLog().map((c) => c.type)).toEqual([
			'applyWalk',
			'attachUuid',
			'declareEntry',
		]);
	});
});
