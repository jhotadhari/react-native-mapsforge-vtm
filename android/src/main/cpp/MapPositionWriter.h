#pragma once

#include <jsi/jsi.h>
#include <worklets/SharedItems/Serializable.h>
#include <worklets/SharedItems/Synchronizable.h>

#include <memory>
#include <mutex>
#include <unordered_map>

namespace mapsforgevtm {

/**
 * Thread-safe registry of per-map-view writers that push position data
 * into reanimated Synchronizable primitives directly from the vtm render
 * thread, bypassing the JS bridge entirely.
 *
 * Lifecycle:
 *   1. Java MapsforgeVtmView.createFragment() calls nativeCreateWriter(handle)
 *      → creates an empty entry (null synchronizables).  This runs before
 *      the JS component mounts, so the writer slot is ready when the render
 *      thread starts firing.
 *   2. JS useMapPosition() creates Synchronizable instances and calls the
 *      global JSI function __bindMapPositionSynchronizables(handle, …)
 *      installed by nativeInstallJSI().
 *      → fills in the synchronizable pointers.
 *   3. MapFragment.bindUpdateListener() calls nativeSetPosition(handle, …)
 *      on every vtm frame (60 fps, render thread).
 *      → constructs SerializableScalar values (NO jsi::Runtime needed) and
 *        calls sync->setBlocking(scalar) — thread-safe via std::mutex.
 *   4. Java MapFragment.onDestroy() calls nativeReleaseWriter(handle).
 *      → removes the entry from the registry.
 *
 * When nativeSetPosition fires before the JS side has bound synchronizables
 * (steps 3 before 2), writes are silently skipped because synchronizables
 * are null.  No crash, no data corruption — just a brief window of stale
 * initial values (zeroes) until the JS side catches up.
 */
struct PositionWriter {
    std::shared_ptr<worklets::Synchronizable> lng;
    std::shared_ptr<worklets::Synchronizable> lat;
    std::shared_ptr<worklets::Synchronizable> zoom;
    std::shared_ptr<worklets::Synchronizable> bearing;
    std::shared_ptr<worklets::Synchronizable> tilt;
    std::shared_ptr<worklets::Synchronizable> viewportWidth;
    std::shared_ptr<worklets::Synchronizable> viewportHeight;
};

// Registry of writers, keyed by nativeNodeHandle (the Android view ID).
// Protected by registryMutex — writers are created on the UI thread,
// bound on the JS thread, written on the render thread, and released
// on the UI thread.
extern std::mutex registryMutex;
extern std::unordered_map<int, std::shared_ptr<PositionWriter>> registry;

// Returns the writer for `handle`, or nullptr if not found.
// Caller must hold registryMutex.
std::shared_ptr<PositionWriter> getWriter(int handle);

} // namespace mapsforgevtm
