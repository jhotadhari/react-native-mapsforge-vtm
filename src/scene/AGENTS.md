# AGENTS.md — `src/scene/` (layer-ordering scene model)

The single source of truth for layer-stack ordering. Order is a **function of committed
state, never accumulated during render**. Components render anchors and declare entries via
commit-phase hooks; the scene turns that committed state into an immutable plan, and the
presenter applies it natively.

Files in this directory: `LayerScene.ts`, `ids.ts`, `planBuilder.ts`, `planDiff.ts`,
`PriorityAllocator.ts`, `SceneSync.ts`, `priorityHandlers.ts`, `types.ts`.

## `LayerScene` (`LayerScene.ts`)

Mutated ONLY from the React commit phase (`useLayoutEffect`/`useEffect`, never render — React
renders may be partial or discarded). Methods:

| Method | What it does |
|---|---|
| `applyWalk(sequence)` | Replaces the committed anchor sequence (a fresh walk result). Defensive-copies the array. |
| `declareEntry(entry)` / `undeclareEntry(uid)` | Declares/removes an entry (drawable) inside a fragment owner. |
| `attachUuid(key, uuid)` / `detachUuid(key)` | Records/clears a resolved native uuid for an anchor or entry uid. |
| `plan()` | Returns a cached, immutable `LayerPlan` (rebuilt when dirty). |
| `planWithResolved(key)` | Builds the plan as if `key` had a resolved uuid — used by components to compute the atomic-add order hint. **Memoized per fragment/type-run** (cache key = fragment uuid / run key / anchor uid), invalidated by `mutationVersion`: every entry of a fragment (and member of a run) yields an identical plan, so one build per fragment per mutation serves the whole create burst. |
| `version()` | Monotonic mutation counter — the presenter's "did the scene change" signal. |
| `clear()` | Resets all state (map teardown / re-arm). |

Notification fan-out is **batched**: `mutated()` bumps `mutationVersion` synchronously (memo/plan
invalidation stays exact) but defers the listener loop to a single `Promise.resolve().then` flush,
so a commit-phase burst of N mutations notifies the N subscribers once, not N times.

## `ids.ts` — deterministic fragment keys

- `fragmentUuidFor(fragmentId, layerType)` → `frag:<owner>:<type>` (SharedLayer/LayerMarker fragments).
- `runUuidFor(firstMemberUid)` → `run:<anchor>` (implicit type-run fragments, keyed by the run's first member).

Both sides (JS scene + native `knownLayers`) must produce identical strings for the same inputs.

## `planBuilder.ts` — pure plan construction

`buildPlan(walk, entries, uuids)` → `LayerPlan`:

- `layers` — ordered native-layer uuid list (bottom→top).
- `fragments` — per-fragment entry lists (all `entryUids` + resolved subset).
- `scopes` — scope info (uid, `order`, anchor index).
- `runKeysByAnchor` — the scene-authoritative fragment uuid per type-run member.

Rules encoded here:
- **Type-runs** — consecutive same-type shared anchors collapse into one `run:<anchor>` fragment.
- **Owner expansion** — `SharedLayer` (layerType `undefined`) produces one fragment per declared layer type; `LayerMarker` (layerType set) owns a single `frag:<uid>:marker` fragment that enters the plan once its own layer resolves (even with zero marker children).
- **Scope ordering** — a scope's explicit `order` (inherited through nesting) overrides tree position (`sortKeyOf`).

## `planDiff.ts`

`diffPlans(prev, next, allocator)` → `{ orderedUuids, layerOrderChanged, entryPriorityComputations }`.
`layerOrderChanged` already reflects any add/remove (length/positional change), so there is no
separate added/removed split.

## `PriorityAllocator.ts` — sparse drawable priorities

Assigns sparse integer priorities (step 1000, midpoint insertion) so a single insert/remove
re-prioritizes O(changed) entries. Renumbers the whole fragment once when a midpoint gap is
exhausted **or** priorities drift outside `[-2^30, 2^30]` (int32 safety). `computeFor` is pure;
`commitFor` persists only after native success.

## `SceneSync.ts` — presenter

Drives the native side from the scene (`scene.subscribe`/`version()`):

1. **Debounced walk** (16ms / 250ms max-wait) → `enumerateAnchors` → `scene.applyWalk`.
2. **Single-flight sync** → `scene.plan()` → `diffPlans` vs `lastPlan` → one absolute
   `reorderLayers` + per-fragment `applyEntryPriorities`, with exponential backoff on failure.
3. **Teardown** — `destroy()` cancels timers, resets in-flight flags, clears the scene, and
   resets `lastPlan` to a clean baseline so a StrictMode re-arm starts fresh.

Entry priorities commit only after the native side confirms; a failed `applyEntryPriorities`
is re-sent on the next mutation (never dropped).

## React bindings (in `src/compose/`)

The hooks that read/write this scene:

- `useLayerAnchor` — renders a `VtmAnchorView` + registers an `AnchorDescriptor` with `SceneSync`.
- `useLayerEntry` — declares an entry inside a fragment owner (commit phase, before the owner's create effect).
- `useSceneUuidBinding` — binds a resolved native uuid to an anchor/entry uid.
- `useSceneFragmentUuid` — reads a standalone type-run member's `runKeysByAnchor` fragment uuid.
- `useSceneFragmentReady` — gates a grouped entry's create on its owner fragment being in the committed plan.

## Invariant

Native layer rendering must strictly follow React component tree order. The committed-tree
walk yields tree order → the plan derives from it → `LayerStackController.applyPlan` applies it
atomically with the add. There is no cursor/sentinel/generation machinery.
