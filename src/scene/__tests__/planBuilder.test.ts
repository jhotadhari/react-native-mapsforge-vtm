/**
 * Tests for the pure plan builder — every layer-stack ordering rule.
 * These scenarios encode the historical ordering bugs (straymap lines-behind-
 * base, stale cursors, async sub-layers) plus the example-app semantics.
 */

import { buildPlan } from '../planBuilder';
import type { AnchorDescriptor, EntryDeclaration } from '../types';
import { fragmentUuidFor, runUuidFor } from '../ids';

const layer = (
	uid: string,
	layerType: string,
	extra: Partial<AnchorDescriptor> = {}
): AnchorDescriptor => ({
	uid,
	kind: 'layer',
	layerType,
	shared: true,
	...extra,
});

const dedicated = (
	uid: string,
	extra: Partial<AnchorDescriptor> = {}
): AnchorDescriptor => ({
	uid,
	kind: 'layer',
	shared: false,
	...extra,
});

const scope = (uid: string, order?: number): AnchorDescriptor => ({
	uid,
	kind: 'scope',
	...(order !== undefined ? { scopeOrder: order } : {}),
});

const owner = (
	uid: string,
	fragmentId: string,
	layerType?: string,
	extra: Partial<AnchorDescriptor> = {}
): AnchorDescriptor => ({
	uid,
	kind: 'fragment',
	fragmentId,
	...(layerType !== undefined ? { layerType } : {}),
	...extra,
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

const entryMap = (list: EntryDeclaration[]): Map<string, EntryDeclaration> =>
	new Map(list.map((e) => [e.uid, e]));

const uuidMap = (keys: string[]): Map<string, string> =>
	new Map(keys.map((key) => [key, `uuid-${key}`]));

const layerUuids = (plan: ReturnType<typeof buildPlan>): string[] =>
	plan.layers.map((l) => l.uuid);

describe('planBuilder: dedicated layers', () => {
	test('strict tree order for interleaved dedicated layers', () => {
		const walk = [
			dedicated('a'),
			dedicated('b'),
			dedicated('c'),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'a',
				'b',
				'c',
			])
		);
		expect(layerUuids(plan)).toEqual([
			'uuid-a',
			'uuid-b',
			'uuid-c',
		]);
	});

	test('unresolved dedicated layers are excluded', () => {
		const walk = [dedicated('a'), dedicated('b')];
		const plan = buildPlan(walk, new Map(), uuidMap(['a']));
		expect(layerUuids(plan)).toEqual(['uuid-a']);
	});

	test('late-mounted dedicated layer lands at its tree position (mapsforge sub-layers)', () => {
		const walk1 = [dedicated('main'), layer('p1', 'path')];
		const plan1 = buildPlan(walk1, new Map(), uuidMap(['main', 'p1']));
		expect(layerUuids(plan1)).toEqual(['uuid-main', runUuidFor('p1')]);

		const walk2 = [
			dedicated('main'),
			dedicated('buildings'),
			layer('p1', 'path'),
		];
		const plan2 = buildPlan(
			walk2,
			new Map(),
			uuidMap([
				'main',
				'buildings',
				'p1',
			])
		);
		expect(layerUuids(plan2)).toEqual([
			'uuid-main',
			'uuid-buildings',
			runUuidFor('p1'),
		]);
	});
});

describe('planBuilder: type-run fragments', () => {
	test('consecutive same-type shared layers form one fragment', () => {
		const walk = [layer('p1', 'path'), layer('p2', 'path')];
		const plan = buildPlan(walk, new Map(), uuidMap(['p1', 'p2']));
		expect(layerUuids(plan)).toEqual([runUuidFor('p1')]);
		expect(plan.fragments).toEqual([
			{
				uuid: runUuidFor('p1'),
				layerType: 'path',
				entryUids: ['p1', 'p2'],
				resolvedEntryUids: ['p1', 'p2'],
			},
		]);
	});

	test('runKeysByAnchor maps every member to the run fragment uuid', () => {
		const walk = [layer('p1', 'path'), layer('p2', 'path')];
		const plan = buildPlan(walk, new Map(), uuidMap(['p1', 'p2']));
		expect(plan.runKeysByAnchor.get('p1')).toBe(runUuidFor('p1'));
		expect(plan.runKeysByAnchor.get('p2')).toBe(runUuidFor('p1'));
	});

	test('single-member run maps to its own anchor (self-key equivalent)', () => {
		const walk = [layer('p1', 'path')];
		const plan = buildPlan(walk, new Map(), uuidMap(['p1']));
		expect(plan.runKeysByAnchor.get('p1')).toBe(runUuidFor('p1'));
	});

	test('runKeysByAnchor re-keys all remaining members when the first is removed', () => {
		const walk1 = [layer('p1', 'path'), layer('p2', 'path')];
		const plan1 = buildPlan(walk1, new Map(), uuidMap(['p1', 'p2']));
		expect(plan1.runKeysByAnchor.get('p2')).toBe(runUuidFor('p1'));

		const walk2 = [layer('p2', 'path')];
		const plan2 = buildPlan(walk2, new Map(), uuidMap(['p2']));
		expect(plan2.runKeysByAnchor.get('p2')).toBe(runUuidFor('p2'));
	});

	test('type alternation splits runs (mixed-grouping, SharedLayer off)', () => {
		const walk = [
			layer('p1', 'path'),
			layer('m1', 'marker'),
			layer('p2', 'path'),
			layer('m2', 'marker'),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'p1',
				'm1',
				'p2',
				'm2',
			])
		);
		expect(layerUuids(plan)).toEqual([
			runUuidFor('p1'),
			runUuidFor('m1'),
			runUuidFor('p2'),
			runUuidFor('m2'),
		]);
	});

	test('scope boundaries split runs', () => {
		const walk = [
			layer('p1', 'path', { scopeUid: 's1' }),
			layer('p2', 'path', { scopeUid: 's2' }),
		];
		const plan = buildPlan(walk, new Map(), uuidMap(['p1', 'p2']));
		expect(layerUuids(plan)).toEqual([runUuidFor('p1'), runUuidFor('p2')]);
	});

	test('run fragment only enters the stack when a member resolved', () => {
		const walk = [layer('p1', 'path'), layer('p2', 'path')];
		const plan = buildPlan(walk, new Map(), new Map());
		expect(plan.layers).toEqual([]);
		expect(plan.fragments[0]).toEqual({
			uuid: runUuidFor('p1'),
			layerType: 'path',
			entryUids: ['p1', 'p2'],
			resolvedEntryUids: [],
		});
	});

	test('removing the first member re-keys the run (documented)', () => {
		const walk = [layer('p2', 'path')];
		const plan = buildPlan(walk, new Map(), uuidMap(['p2']));
		expect(layerUuids(plan)).toEqual([runUuidFor('p2')]);
	});
});

describe('planBuilder: SharedLayer owners', () => {
	test('one fragment per type, ordered by first occurrence', () => {
		const walk = [scope('s'), owner('sl1', 'shared1')];
		const entries = entryMap([
			entry('e1', 'shared1', 'path', 0),
			entry('e2', 'shared1', 'marker', 1),
			entry('e3', 'shared1', 'path', 2),
		]);
		const plan = buildPlan(
			walk,
			entries,
			uuidMap([
				'e1',
				'e2',
				'e3',
			])
		);
		expect(layerUuids(plan)).toEqual([
			fragmentUuidFor('shared1', 'path'),
			fragmentUuidFor('shared1', 'marker'),
		]);
		expect(plan.fragments[0]!.entryUids).toEqual(['e1', 'e3']);
		expect(plan.fragments[1]!.entryUids).toEqual(['e2']);
	});

	test('fragments are gated on resolved entries', () => {
		const walk = [owner('sl1', 'shared1')];
		const entries = entryMap([
			entry('e1', 'shared1', 'path', 0),
			entry('e2', 'shared1', 'marker', 1),
		]);
		const plan = buildPlan(walk, entries, uuidMap(['e2']));
		expect(layerUuids(plan)).toEqual([
			fragmentUuidFor('shared1', 'marker'),
		]);
	});

	test('childless owner reserves no fragment yet', () => {
		const walk = [owner('sl1', 'shared1')];
		const plan = buildPlan(walk, new Map(), new Map());
		expect(plan.layers).toEqual([]);
		expect(plan.fragments).toEqual([]);
	});

	test('unindexed entries sort after indexed, by declaration order', () => {
		const walk = [owner('sl1', 'shared1')];
		const entries = entryMap([
			{ ...entry('wrapped1', 'shared1', 'path'), declarationSeq: 2 },
			{ ...entry('direct1', 'shared1', 'path', 0), declarationSeq: 1 },
			{ ...entry('wrapped2', 'shared1', 'path'), declarationSeq: 3 },
		]);
		const plan = buildPlan(
			walk,
			entries,
			uuidMap([
				'direct1',
				'wrapped1',
				'wrapped2',
			])
		);
		expect(plan.fragments[0]!.entryUids).toEqual([
			'direct1',
			'wrapped1',
			'wrapped2',
		]);
	});
});

describe('planBuilder: LayerMarker owners', () => {
	test('owner with its own native layer uuid + entries', () => {
		const walk = [owner('ml1', 'ml1', 'marker')];
		const entries = entryMap([
			entry('m1', 'ml1', 'marker', 0),
			entry('m2', 'ml1', 'marker', 1),
		]);
		const uuids = new Map([
			['ml1', 'group-uuid'],
			['m1', 'uuid-m1'],
			['m2', 'uuid-m2'],
		]);
		const plan = buildPlan(walk, entries, uuids);
		// The native identity is the deterministic fragment uuid (knownLayers
		// is keyed by it), not the resolved group uuid.
		expect(layerUuids(plan)).toEqual([fragmentUuidFor('ml1', 'marker')]);
		expect(plan.fragments[0]!.resolvedEntryUids).toEqual(['m1', 'm2']);
	});

	test('owner layer excluded until its own uuid resolves', () => {
		const walk = [owner('ml1', 'ml1', 'marker')];
		const entries = entryMap([entry('m1', 'ml1', 'marker', 0)]);
		const plan = buildPlan(walk, entries, uuidMap(['m1']));
		expect(plan.layers).toEqual([]);
	});
});

describe('planBuilder: scopes', () => {
	test('explicit order overrides tree order', () => {
		const walk = [
			scope('base', 100),
			dedicated('b1', { scopeUid: 'base' }),
			scope('lines', 50),
			dedicated('l1', { scopeUid: 'lines' }),
		];
		const plan = buildPlan(walk, new Map(), uuidMap(['b1', 'l1']));
		expect(layerUuids(plan)).toEqual(['uuid-l1', 'uuid-b1']);
	});

	test('unordered scopes sit at tree position (S2)', () => {
		const walk = [
			scope('a'),
			dedicated('a1', { scopeUid: 'a' }),
			dedicated('top'),
			scope('b'),
			dedicated('b1', { scopeUid: 'b' }),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'a1',
				'top',
				'b1',
			])
		);
		expect(layerUuids(plan)).toEqual([
			'uuid-a1',
			'uuid-top',
			'uuid-b1',
		]);
	});

	test('equal order values keep blocks intact (tiebreak by scope anchor)', () => {
		const walk = [
			scope('a', 100),
			dedicated('a1', { scopeUid: 'a' }),
			scope('b', 100),
			dedicated('b1', { scopeUid: 'b' }),
		];
		const plan = buildPlan(walk, new Map(), uuidMap(['a1', 'b1']));
		expect(layerUuids(plan)).toEqual(['uuid-a1', 'uuid-b1']);
	});

	test('nested unordered scopes keep tree order (inner block splits the outer)', () => {
		const walk = [
			scope('outer'),
			dedicated('o1', { scopeUid: 'outer' }),
			{ uid: 'inner', kind: 'scope' as const, scopeUid: 'outer' },
			dedicated('i1', { scopeUid: 'inner' }),
			dedicated('o2', { scopeUid: 'outer' }),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'o1',
				'i1',
				'o2',
			])
		);
		// S1: default is pure tree order — i1 sits between o1 and o2 at
		// its committed-tree position, even though it splits the outer block.
		expect(layerUuids(plan)).toEqual([
			'uuid-o1',
			'uuid-i1',
			'uuid-o2',
		]);
	});

	test('nested unordered scope inherits the outer explicit order', () => {
		const walk = [
			scope('outer', 100),
			dedicated('o1', { scopeUid: 'outer' }),
			{ uid: 'inner', kind: 'scope' as const, scopeUid: 'outer' },
			dedicated('i1', { scopeUid: 'inner' }),
			dedicated('o2', { scopeUid: 'outer' }),
			scope('other', 50),
			dedicated('x1', { scopeUid: 'other' }),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'o1',
				'i1',
				'o2',
				'x1',
			])
		);
		// The inner scope inherits outer's order=100: the outer block stays
		// contiguous (tree order within it) and beats the ordered sibling.
		expect(layerUuids(plan)).toEqual([
			'uuid-x1',
			'uuid-o1',
			'uuid-i1',
			'uuid-o2',
		]);
	});

	test('nested inner explicit order overrides the outer order', () => {
		const walk = [
			scope('outer', 100),
			dedicated('o1', { scopeUid: 'outer' }),
			{
				uid: 'inner',
				kind: 'scope' as const,
				scopeUid: 'outer',
				scopeOrder: 75,
			},
			dedicated('i1', { scopeUid: 'inner' }),
		];
		const plan = buildPlan(
			walk,
			new Map(),
			uuidMap([
				'o1',
				'i1',
			])
		);
		// The inner block uses its own order — explicit intent wins.
		expect(layerUuids(plan)).toEqual(['uuid-i1', 'uuid-o1']);
	});

	test('childless scope: members mounting later land at the scope position', () => {
		const walk1 = [scope('a'), dedicated('t')];
		const plan1 = buildPlan(walk1, new Map(), uuidMap(['t']));
		expect(layerUuids(plan1)).toEqual(['uuid-t']);

		const walk2 = [
			scope('a'),
			dedicated('a1', { scopeUid: 'a' }),
			dedicated('t'),
		];
		const plan2 = buildPlan(walk2, new Map(), uuidMap(['a1', 't']));
		expect(layerUuids(plan2)).toEqual(['uuid-a1', 'uuid-t']);
	});
});
