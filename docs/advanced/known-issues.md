# Known Issues & Limitations

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
