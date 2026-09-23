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

---

# Resolution (post-S23 + notification batching)

## Corrected root cause

The "native side dominates" conclusion above was **wrong** — it was based on a
confounded macro. Two confounders were removed first:

1. The `many-layers` example re-rendered 2N elements on every `onMapUpdate`
   (`useMapInfo` `setInfo` per frame, un-memoized `layerPairs.map`) — sustained
   JS churn at 25–60 Hz. Fixed by memoizing `pairElements`.
2. The BenchmarkTimer polled the full `getDebugLayerDump()` every 50 ms
   (serializing 2000 registry entries per poll — observer effect). Replaced
   with `useSceneBusy()` as the settle signal + one final dump.

After de-confounding, the real bottleneck was two JS-side O(N²) costs:

- **`planWithResolved` per create** — fixed by S23 memoization (per-fragment +
  per-run-key, invalidated by `mutationVersion`).
- **The scene notification storm** — every mutation (declareEntry/attachUuid)
  synchronously notified all N `useSceneFragmentReady` subscribers (≈2N²
  listener invocations), and the first subscriber's `plan()` call rebuilt the
  O(N log N) plan per mutation. Fixed by coalescing `LayerScene.mutated()`
  notification into a single `Promise.resolve().then` flush (mutationVersion
  still bumps synchronously, so memo invalidation stays exact).

Native was **already coalesced** (`SimpleWorker.submit` dedupes; `createMarkers`
already batches via direct `getItemList().add` + one `populate()` per fragment).

## Results

### Micro (`planWithResolved`, Node)

| N | before (ms) | after (ms) |
|---|---|---|
| 1000 | 96.2 | 0.8 |
| 4000 | 1122.8 | 2.7 |

~415× faster; now ~O(1) amortized (one build per burst, memo hits thereafter).

### Macro (device, `many-layers`)

| transition | before | after |
|---|---|---|
| 50→1000 pairs | 21 501 ms | **1 919 ms** |
| 1000→3000 pairs | pegged / never settled | **9 385 ms** |

~11× faster on the clean create phase; 3000 pairs (6000 entries) now completes
instead of pegging the CPU.

### Remaining cost

The residual ~9 s at 6000 entries is **native/bridge** (vtm `ItemizedLayer`/
`VectorLayer` re-render of 6000 drawables + JS→native param serialization), not
the JS scene model — a separate vtm-throughput concern, tracked as a possible
follow-up.
