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

	test('planWithResolved virtually resolves a key without mutating the scene', () => {
		const scene = new LayerScene();
		scene.applyWalk([
			{ uid: 'p1', kind: 'layer', layerType: 'path', shared: true },
		]);

		// The real plan has no resolved layers yet.
		expect(scene.plan().layers).toEqual([]);

		// The virtual plan contains the type-run fragment at its position.
		const virtual = scene.planWithResolved('p1');
		expect(virtual.layers.map((l) => l.uuid)).toEqual(['run:p1']);

		// The scene itself is untouched — the cached plan is still empty.
		expect(scene.plan().layers).toEqual([]);
		expect(scene.plan()).toBe(scene.plan());
	});

	test('planWithResolved places a shared entry at its tree position', () => {
		const scene = new LayerScene();
		scene.applyWalk([
			{ uid: 'sl1', kind: 'fragment', fragmentId: 'shared1' },
			dedicated('main'),
			{ uid: 'sl2', kind: 'fragment', fragmentId: 'shared2' },
		]);
		scene.attachUuid('main', 'uuid-main');
		scene.declareEntry(entry('e1', 'shared2', 'path', 0));

		// Virtually resolving shared2's entry places its fragment AFTER
		// the dedicated layer, in tree order.
		const virtual = scene.planWithResolved('e1');
		expect(virtual.layers.map((l) => l.uuid)).toEqual([
			'uuid-main',
			'frag:shared2:path',
		]);
	});

	test('planWithResolved is uncached — later mutations are reflected', () => {
		const scene = new LayerScene();
		scene.applyWalk([
			{ uid: 'p1', kind: 'layer', layerType: 'path', shared: true },
			{ uid: 'p2', kind: 'layer', layerType: 'path', shared: true },
		]);
		scene.attachUuid('p1', 'uuid-p1');

		const virtual = scene.planWithResolved('p2');
		expect(virtual.layers.map((l) => l.uuid)).toEqual(['run:p1']);

		// After p1 leaves the walk (unmount), a virtual p2 plan re-keys
		// the run — no stale cache anywhere.
		scene.applyWalk([
			{ uid: 'p2', kind: 'layer', layerType: 'path', shared: true },
		]);
		const virtual2 = scene.planWithResolved('p2');
		expect(virtual2.layers.map((l) => l.uuid)).toEqual(['run:p2']);
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

	test('version counter bumps on every mutation', () => {
		const scene = new LayerScene();
		expect(scene.version()).toBe(0);

		scene.applyWalk([dedicated('a')]);
		expect(scene.version()).toBe(1);

		scene.attachUuid('a', 'uuid-a');
		expect(scene.version()).toBe(2);

		scene.declareEntry(entry('e1', 'shared1', 'path'));
		expect(scene.version()).toBe(3);

		// A no-op detach (unknown key) must not bump the version.
		scene.detachUuid('unknown');
		expect(scene.version()).toBe(3);
	});
});
