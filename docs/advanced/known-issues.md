# Known Issues & Limitations

<!--
  Keep this page up to date as bugs are found and fixed.  Link to the
  repo's TODO.md for implementation-tracked items and to the upstream
  vtm issue tracker for dependency bugs.
-->

## Multiple MapContainer instances (vtm 0.28.0+)

### Symptom

Rendering more than one `MapContainer` simultaneously produces black /
corrupted tiles on one map and a blank (gray or white) surface on the
other.

### Root cause

vtm 0.28.0+ — the underlying rendering engine — holds critical OpenGL
state in **static fields** inside `MapRenderer.java`:

| Field | Type | Problem |
|-------|------|---------|
| `mQuadIndicesID` | `static int` | VBO handle — only valid in the EGL context that created it |
| `mQuadVerticesID` | `static int` | VBO handle — same |
| `mBufferPool` | `static NativeBufferPool` | Shared native buffer allocator |
| `rerender` | `static boolean` | Animation-trigger flag shared across all instances |
| `frametime` | `static long` | Frame timestamp shared across all instances |

Vertex Buffer Objects (VBOs) are OpenGL resources that live inside a
specific EGL context.  When you create two `MapView` instances (one per
`MapContainer`), each gets its **own** EGL context.  The first map
creates VBOs in context A and stores their handles in the static fields.
The second map then creates its own VBOs in context B, **overwriting**
the static fields.  The first map is now trying to render with buffer
handles that are only valid in context B — producing black / corrupted
tiles.

### Why EGL context sharing doesn't fix it

Configuring the two `MapView` instances to share a single EGL context
(would solve the VBO validity problem) was attempted but broke rendering
entirely (both maps white).  vtm's `MapView` constructor configures the
GL surface with a custom `EGLConfigChooser` and a specific
`EGLContextClientVersion`; replacing the default `EGLContextFactory` to
inject sharing bypasses vtm's own GL setup, producing an incorrectly
configured context.

### Resolution path

The fix must happen inside vtm's rendering engine — the static fields in
`MapRenderer` must become per-instance fields (or use a thread-safe
per-context lookup).  This requires forking / patching
[mapsforge/vtm](https://github.com/mapsforge/vtm) itself.

Until then, a single `MapContainer` may be used with layer-based
comparison (e.g. toggling between different `LayerMapsforge` /
`LayerBitmapTile` configurations at runtime) to achieve a similar
side-by-side effect without creating a second `MapView`.

### Workaround: single-map comparison

Instead of two `MapContainer`s:

```
MapContainer A            MapContainer B
   LayerMapsforge A          LayerMapsforge B
```

Use one `MapContainer` with layers that can be toggled:

```
MapContainer
   LayerMapsforge A    (visible={showA})
   LayerMapsforge B    (visible={showB})
```

The `useMap()` sync API (e.g. `jumpTo`, `fitBounds`) is unaffected —
the multi-map example's position-synchronisation logic is correct and
will work once vtm supports multiple simultaneous contexts.

---

## Pending vtm dependency upgrade

The library currently pins `vtm`/`vtm-android`/`vtm-themes`/`vtm-jts`
at version **0.29.0** (latest as of 2026-07).  See `TODO.md` for the
remaining upgrade plan items.

---

## Other known issues

See `TODO.md` in the repository root for implementation-tracked bugs,
including:

- **Layer render order at scale** (item 0) — partially addressed by sentinel
  mechanism + `order` prop on `<ReindexScope>` (branch
  `fix/reindexscope-first-mount-ordering`). Shared-layer drawable UUID
  resolution in `knownLayers` remains the remaining sub-issue
- **Thread-safety during layer creation** (item 2) — `animateTo` now
  dispatches to the UI thread, layer create/remove on native modules
  thread
- **Marker coordinate bugs** (item 4)

The `TODO.md` file is the authoritative source for implementation bugs;
this page only documents architectural limitations (like the multi-map
issue) that cannot be fixed within the current dependency versions.
