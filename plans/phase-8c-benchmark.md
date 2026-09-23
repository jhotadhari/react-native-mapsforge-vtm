# Phase 8c — create-phase benchmark (baseline, pre-S23)

Companion docs: `layer-ordering-rewrite.md` (roadmap).

## Purpose

Quantify the cost of the create-phase before the S23 memoization. Two
benchmarks: a micro (jest, pure JS — isolates `planWithResolved`) and a macro
(device, `many-layers` example — the full press→settle pipeline).

## Micro — `planWithResolved` (pure JS, Node)

File: `src/scene/__tests__/planWithResolved.perf.test.ts` (opt-in:
`BENCH=1 yarn test planWithResolved.perf`).

Simulates the create burst: N entries in one fragment, one `planWithResolved`
per entry.

### Grouped entries (one SharedLayer fragment)

| N | total (ms) | ms/call |
|---|---|---|
| 250 | 10.6 | 0.043 |
| 500 | 27.9 | 0.056 |
| 1000 | 96.2 | 0.096 |
| 2000 | 296.7 | 0.148 |
| 4000 | 1122.8 | 0.281 |

Scaling (N→2N): 2.62× / 3.45× / 3.08× / 3.78× (linear≈2.0×, quadratic≈4.0×)
→ clearly superlinear (O(N²)-ish).

### Standalone type-run members

| N | total (ms) | ms/call |
|---|---|---|
| 250 | 8.2 | 0.033 |
| 500 | 29.7 | 0.059 |
| 1000 | 106.3 | 0.106 |
| 2000 | 362.0 | 0.181 |
| 4000 | 1563.1 | 0.391 |

Scaling: 3.61× / 3.58× / 3.41× / 4.32× → quadratic.

## Macro — device (many-layers example, Pixel 6 Pro)

`[Benchmark]` timer: `benchStart` recorded on the count-button `onPress`
(JS thread, before the burst); polls `getDebugLayerDump()` until
`appliedMatchesExpected && notInPlanCount === 0 && pendingMutations === 0`.

| count (pairs) | entries (path+marker) | settle (ms) |
|---|---|---|
| 500 | 1000 | 8558 |
| 1000 | 2000 | 21542 |

`count=3000` (6000 entries) pegged the device at ~110% CPU and did not settle
cleanly within ~60s — consistent with the same superlinear (native) growth.

## Key finding

The **native side dominates the create-phase cost**, not the JS scene model:

- Micro (JS `planWithResolved` only) ≈ 96 ms @ N=1000 grouped → **~0.1 s**.
- Macro (full pipeline) ≈ **21.5 s** @ 1000 pairs.

So `planWithResolved`'s O(N²) is a small fraction of the end-to-end cost; the
bulk is native entry creation + `applyEntryPriorities` + the vtm
`ItemizedLayer`/`VectorLayer` re-render with ~1000 items.

### Implication for S23

S23 (memoizing `planWithResolved` per-fragment) removes the JS O(N²) and is
still correct/desirable, but it will only shave ~0.1–1 s of the ~21 s. The
dominant native-side superlinear cost is a separate problem to investigate
after S23 (candidates: vtm `ItemizedLayer` distance-sort, `VectorLayer`
re-upload, per-entry `scheduleUpdate` churn, batch flush size).

## Re-run after S23

Re-run `BENCH=1 yarn test planWithResolved.perf` and the macro timer; record
the delta here.
