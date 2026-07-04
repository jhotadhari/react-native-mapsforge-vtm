#include "MapPositionWriter.h"

#include <jsi/jsi.h>
#include <worklets/SharedItems/Serializable.h>
#include <worklets/SharedItems/Synchronizable.h>

#include <android/log.h>
#include <jni.h>
#include <memory>
#include <mutex>

#define TAG "MapsforgeVtmPositionWriter"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace mapsforgevtm {

std::mutex registryMutex;
std::unordered_map<int, std::shared_ptr<PositionWriter>> registry;

std::shared_ptr<PositionWriter> getWriter(int handle) {
    auto it = registry.find(handle);
    if (it == registry.end()) {
        return nullptr;
    }
    return it->second;
}

} // namespace mapsforgevtm

using namespace mapsforgevtm;

// ---------------------------------------------------------------------------
// Helper: set a single synchronizable field, skipping if null
// ---------------------------------------------------------------------------
static void setSyncIfBound(
    const std::shared_ptr<worklets::Synchronizable> &sync,
    double value) {
    if (sync) {
        sync->setBlocking(std::make_shared<worklets::SerializableScalar>(value));
    }
}

// ---------------------------------------------------------------------------
// JNI exports
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT void JNICALL
Java_com_jhotadhari_reactnative_mapsforge_vtm_MapPositionWriter_nativeCreateWriter(
    JNIEnv *env,
    jclass clazz,
    jint handle) {
    std::lock_guard<std::mutex> lock(registryMutex);
    if (registry.find(handle) != registry.end()) {
        LOGD("Writer for handle %d already exists (created lazily by JSI)",
             handle);
        return;
    }
    registry[handle] = std::make_shared<PositionWriter>();
    LOGD("Created writer for handle %d (total writers: %zu)",
         handle, registry.size());
}

JNIEXPORT void JNICALL
Java_com_jhotadhari_reactnative_mapsforge_vtm_MapPositionWriter_nativeReleaseWriter(
    JNIEnv *env,
    jclass clazz,
    jint handle) {
    std::lock_guard<std::mutex> lock(registryMutex);
    registry.erase(handle);
    LOGD("Released writer for handle %d (total writers: %zu)",
         handle, registry.size());
}

JNIEXPORT void JNICALL
Java_com_jhotadhari_reactnative_mapsforge_vtm_MapPositionWriter_nativeSetPosition(
    JNIEnv *env,
    jclass clazz,
    jint handle,
    jdouble lng,
    jdouble lat,
    jdouble zoom,
    jdouble bearing,
    jdouble tilt,
    jdouble viewportWidth,
    jdouble viewportHeight) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto writer = getWriter(handle);
    if (!writer) {
        return;
    }

    // Each setSyncIfBound call: construct SerializableScalar (no JSI runtime
    // needed — just stores a double in a union) → synchronizable->setBlocking
    // (lock, assign shared_ptr, unlock, notify).  Thread-safe via
    // SynchronizableAccess's std::mutex + std::condition_variable.
    setSyncIfBound(writer->lng, lng);
    setSyncIfBound(writer->lat, lat);
    setSyncIfBound(writer->zoom, zoom);
    setSyncIfBound(writer->bearing, bearing);
    setSyncIfBound(writer->tilt, tilt);
    setSyncIfBound(writer->viewportWidth, viewportWidth);
    setSyncIfBound(writer->viewportHeight, viewportHeight);
}

JNIEXPORT void JNICALL
Java_com_jhotadhari_reactnative_mapsforge_vtm_MapPositionWriter_nativeInstallJSI(
    JNIEnv *env,
    jclass clazz,
    jlong jsContext) {
    auto &rt = *reinterpret_cast<facebook::jsi::Runtime *>(jsContext);

    try {
    // Install a global function that creates Synchronizable primitives for
    // a given map view handle and returns them to JS.  C++ owns the
    // synchronizables — the render thread writes to them directly via
    // nativeSetPosition, and the JS worklet poller reads them via
    // getBlocking().
    auto getSyncFn = facebook::jsi::Function::createFromHostFunction(
        rt,
        facebook::jsi::PropNameID::forAscii(rt, "__getMapPositionSynchronizables"),
        1, // handle
        [](facebook::jsi::Runtime &rt,
           const facebook::jsi::Value &,
           const facebook::jsi::Value *args,
           size_t count) -> facebook::jsi::Value {
            if (count < 1) {
                LOGE("__getMapPositionSynchronizables: expected 1 argument");
                return facebook::jsi::Value::undefined();
            }

            int handle = static_cast<int>(args[0].asNumber());

            std::lock_guard<std::mutex> lock(registryMutex);
            auto writer = getWriter(handle);
            if (!writer) {
                // Writer not created yet (createFragment hasn't run).
                // Create it now so synchronizables are ready when the
                // render thread starts writing.
                writer = std::make_shared<PositionWriter>();
                registry[handle] = writer;
                LOGD("Created writer lazily for handle %d (createFragment "
                     "hasn't run yet)", handle);
            }

            // Create Synchronizable instances (one per scalar field).
            // Each holds an initial value of 0.0 — consumers see zeroes
            // until the first nativeSetPosition call on the render thread.
            writer->lng = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->lat = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->zoom = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->bearing = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->tilt = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->viewportWidth = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));
            writer->viewportHeight = std::make_shared<worklets::Synchronizable>(
                std::make_shared<worklets::SerializableScalar>(0.0));

            // Return the synchronizables as a JS object.  Each value is
            // converted via Synchronizable::toJSValue() which creates a
            // SerializableJSRef + calls __synchronizableUnpacker — the
            // returned JS object has getBlocking() / setBlocking() methods
            // that worklets can read on the UI thread.
            auto result = facebook::jsi::Object(rt);
            result.setProperty(rt, "lng", writer->lng->toJSValue(rt));
            result.setProperty(rt, "lat", writer->lat->toJSValue(rt));
            result.setProperty(rt, "zoom", writer->zoom->toJSValue(rt));
            result.setProperty(rt, "bearing", writer->bearing->toJSValue(rt));
            result.setProperty(rt, "tilt", writer->tilt->toJSValue(rt));
            result.setProperty(rt, "viewportWidth",
                              writer->viewportWidth->toJSValue(rt));
            result.setProperty(rt, "viewportHeight",
                              writer->viewportHeight->toJSValue(rt));

            LOGD("Created synchronizables for handle %d", handle);
            return result;
        });

    rt.global().setProperty(rt, "__getMapPositionSynchronizables",
                            std::move(getSyncFn));
    LOGD("Installed __getMapPositionSynchronizables on JS runtime");
    } catch (const std::exception &e) {
        LOGE("Failed to install JSI function: %s", e.what());
    }
}

} // extern "C"
