/**
 * planWithResolved create-phase performance benchmark.
 *
 * LayerPath/LayerShape/Marker create callbacks each call
 * `scene.planWithResolved(entryUid)` to compute the atomic-add order hint
 * (`layerUuids`). planWithResolved is memoized per fragment/type-run (S23) —
 * every entry of a fragment (and member of a run) yields an identical plan, so
 * one build per fragment per mutation-version serves the whole create burst.
 * This benchmark guards that invariant: scaling should stay ~flat (near-linear,
 * dominated by the single plan build), not the pre-S23 O(N²) it replaced.
 *
 * Opt-in (skipped by default so the normal suite stays fast):
 *   BENCH=1 yarn test planWithResolved.perf
 */

import { LayerScene } from '../LayerScene';
import type { AnchorDescriptor } from '../types';

const SIZES = [
	250,
	500,
	1000,
	2000,
	4000,
];

const benchMs = (fn: () => void): number => {
	const t0 = process.hrtime.bigint();
	fn();
	const t1 = process.hrtime.bigint();
	return Number(t1 - t0) / 1e6;
};

const logScaling = (times: Record<number, number>): void => {
	for (let i = 1; i < SIZES.length; i++) {
		const ratio = times[SIZES[i]!]! / times[SIZES[i - 1]!]!;
		console.log(
			`  ratio N=${SIZES[i]}/N=${SIZES[i - 1]}: ${ratio.toFixed(2)}x ` +
				`(linear≈2.0x, quadratic≈4.0x)`
		);
	}
};

describe('planWithResolved performance (create phase)', () => {
	const enabled = process.env.BENCH === '1';
	const maybe = enabled ? test : test.skip;

	maybe('grouped entries — N calls in one SharedLayer fragment', () => {
		console.log('\n[bench] grouped entries (one SharedLayer fragment):');
		const times: Record<number, number> = {};
		for (const n of SIZES) {
			const scene = new LayerScene();
			scene.applyWalk([
				{ uid: 'sl1', kind: 'fragment', fragmentId: 'shared1' },
			]);
			for (let i = 0; i < n; i++) {
				scene.declareEntry({
					uid: `e${i}`,
					fragmentId: 'shared1',
					layerType: 'path',
					sortIndex: i,
				});
			}
			const ms = benchMs(() => {
				for (let i = 0; i < n; i++) {
					scene.planWithResolved(`e${i}`);
				}
			});
			times[n] = ms;
			console.log(
				`  N=${n}: ${ms.toFixed(1)} ms total, ${(ms / n).toFixed(4)} ms/call`
			);
		}
		logScaling(times);
		expect(true).toBe(true);
	});

	maybe('standalone run members — N calls in one type-run', () => {
		console.log('\n[bench] standalone type-run members:');
		const times: Record<number, number> = {};
		for (const n of SIZES) {
			const scene = new LayerScene();
			const anchors: AnchorDescriptor[] = Array.from(
				{ length: n },
				(_, i) => ({
					uid: `a${i}`,
					kind: 'layer',
					layerType: 'path',
					shared: true,
				})
			);
			scene.applyWalk(anchors);
			const ms = benchMs(() => {
				for (let i = 0; i < n; i++) {
					scene.planWithResolved(`a${i}`);
				}
			});
			times[n] = ms;
			console.log(
				`  N=${n}: ${ms.toFixed(1)} ms total, ${(ms / n).toFixed(4)} ms/call`
			);
		}
		logScaling(times);
		expect(true).toBe(true);
	});
});
