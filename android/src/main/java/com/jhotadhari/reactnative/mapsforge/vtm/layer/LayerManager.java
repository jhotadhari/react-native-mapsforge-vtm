package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import android.content.ContentResolver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.MapMutationQueue;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base for managers that collapse many JS layer components into a single
 * shared vtm {@link Layer} per map view.
 *
 * <h3>Extensibility for third-party plugins</h3>
 * A third-party library (e.g. polygons, circles, heatmaps) subclasses
 * {@code LayerManager<TEntry>}, implements the four abstract entry-contract methods,
 * and wires its TurboModule to the manager via {@link #setEventCallback}. The base
 * handles singleton lifecycle, shared-layer lazy creation, position-aware entry
 * ordering, {@link MapMutationQueue} integration, and event dispatch.
 *
 * <h3>Instance lifecycle</h3>
 * One manager per {@code (nativeNodeHandle, name)} pair. Obtain via
 * {@link #get(int, MapView, String, Factory)} and tear down with
 * {@link #remove(int, String)} or {@link #removeAll(int)} (called from
 * {@code MapFragment.onDestroy}).
 *
 * @param <TEntry> per-component entry type (e.g. {@code MarkerEntry}, {@code PathEntry})
 */
public abstract class LayerManager<TEntry> {

	// ── Singleton registry ──────────────────────────────────────────────

	/**
	 * Key format: {@code "<nativeNodeHandle>:<name>"} so multiple managers
	 * coexist per map view (e.g. "42:markers", "42:paths", "42:polygons").
	 */
	private static final Map<String, LayerManager<?>> instances = new ConcurrentHashMap<>();

	@NonNull
	private static String instanceKey(int nativeNodeHandle, String name) {
		return nativeNodeHandle + ":" + name;
	}

	/**
	 * Returns the existing manager for {@code (nativeNodeHandle, name)}, or creates
	 * one via {@code factory} if none exists yet.
	 */
	@SuppressWarnings("unchecked")
	@NonNull
	public static <T extends LayerManager<?>> T get(
		int nativeNodeHandle,
		@NonNull MapView mapView,
		@NonNull String name,
		@NonNull Factory<T> factory
	) {
		String key = instanceKey(nativeNodeHandle, name);
		return (T) instances.computeIfAbsent(key, k -> factory.create(nativeNodeHandle, mapView, name));
	}

	/**
	 * Returns the existing manager for {@code (nativeNodeHandle, name)}, or null.
	 */
	@Nullable
	public static LayerManager<?> getInstance(int nativeNodeHandle, @NonNull String name) {
		return instances.get(instanceKey(nativeNodeHandle, name));
	}

	/**
	 * Tears down the manager for {@code (nativeNodeHandle, name)}: removes its
	 * shared layer from the map, clears entries, and removes the singleton.
	 */
	public static void remove(int nativeNodeHandle, @NonNull String name) {
		String key = instanceKey(nativeNodeHandle, name);
		LayerManager<?> mgr = instances.remove(key);
		if (mgr != null) {
			mgr.destroy();
		}
	}

	/**
	 * Tears down ALL managers for {@code nativeNodeHandle}. Called from
	 * {@code MapFragment.onDestroy}.
	 */
	public static void removeAll(int nativeNodeHandle) {
		String prefix = nativeNodeHandle + ":";
		// Collect keys first to avoid ConcurrentModificationException.
		for (String key : instances.keySet().toArray(new String[0])) {
			if (key.startsWith(prefix)) {
				LayerManager<?> mgr = instances.remove(key);
				if (mgr != null) {
					mgr.destroy();
				}
			}
		}
	}

	// ── Factory for subclasses ──────────────────────────────────────────

	@FunctionalInterface
	public interface Factory<T extends LayerManager<?>> {
		T create(int nativeNodeHandle, MapView mapView, String name);
	}

	// ── Event callback ──────────────────────────────────────────────────

	/**
	 * Callback set by the owning TurboModule so the manager can emit events
	 * through the module's codegen EventEmitter without holding a reference to
	 * the TurboModule itself.
	 */
	@FunctionalInterface
	public interface EventEmitterCallback {
		void emit(@NonNull String eventName, @NonNull WritableMap payload);
	}

	// ── Instance state ──────────────────────────────────────────────────

	protected final int nativeNodeHandle;
	@NonNull
	protected final MapView mapView;
	@NonNull
	protected final String name;
	/** Reserved uuid for the shared layer in {@link MapMutationQueue#getKnownLayers}. */
	@NonNull
	protected final String sharedLayerUuid;

	/**
	 * Per-fragment shared vtm Layers, keyed by fragment uuid.
	 * Created lazily by {@link #ensureSharedLayer(String, List)}.
	 * <p>
	 * Replaces the old single {@code sharedLayer}; now each fragment
	 * (interleaved layer type instance) gets its own vtm Layer so
	 * z-position ordering works correctly across interleaved types.
	 */
	@NonNull
	protected final Map<String, Layer> sharedLayerFragments = new ConcurrentHashMap<>();

	/** All entries currently managed, keyed by entry uuid. */
	@NonNull
	protected final Map<String, TEntry> entries = new ConcurrentHashMap<>();

	/** Set by the owning TurboModule via {@link #setEventCallback}. */
	@Nullable
	protected volatile EventEmitterCallback eventCallback;

	// ── Constructor ─────────────────────────────────────────────────────

	protected LayerManager(
		int nativeNodeHandle,
		@NonNull MapView mapView,
		@NonNull String name
	) {
		this.nativeNodeHandle = nativeNodeHandle;
		this.mapView = mapView;
		this.name = name;
		this.sharedLayerUuid = "__vtm_shared_" + name + "__";
	}

	// ── Event plumbing ──────────────────────────────────────────────────

	/**
	 * Sets the callback the manager uses to emit events through the owning
	 * TurboModule's codegen EventEmitter. Call once, right after
	 * {@link #get(int, MapView, String, Factory)}.
	 */
	public void setEventCallback(@Nullable EventEmitterCallback callback) {
		this.eventCallback = callback;
	}

	/**
	 * Convenience: emits a named event if the callback is set.
	 */
	protected void emit(@NonNull String eventName, @NonNull WritableMap payload) {
		if (eventCallback != null) {
			eventCallback.emit(eventName, payload);
		}
	}

	// ── Subclass contract ───────────────────────────────────────────────

	/**
	 * Creates the shared vtm {@link Layer} this manager owns.
	 * Called once, lazily, from {@link #ensureSharedLayer(String, List)}.
	 */
	@NonNull
	protected abstract Layer createSharedLayer();

	/**
	 * Result of {@link #createEntry}.
	 */
	public static class CreateResult<T> {
		@NonNull
		public final T entry;
		@Nullable
		public final WritableMap responseData;

		public CreateResult(@NonNull T entry, @Nullable WritableMap responseData) {
			this.entry = entry;
			this.responseData = responseData;
		}
	}

	/**
	 * Builds an entry from TurboModule params. The returned {@link CreateResult}
	 * carries the entry (stored in {@link #entries}) and optional response data
	 * merged into the final response sent back to JS.
	 */
	@NonNull
	protected abstract CreateResult<TEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception;

	/**
	 * Removes all geometry/items belonging to {@code entry} from the shared layer.
	 * Called during {@link #remove(String)}.
	 */
	protected abstract void removeEntryFromLayer(@NonNull TEntry entry);

	/**
	 * Result of {@link #updateEntry}.
	 */
	public static class UpdateResult {
		@Nullable
		public final WritableMap responseData;

		public UpdateResult(@Nullable WritableMap responseData) {
			this.responseData = responseData;
		}
	}

	/**
	 * Updates an entry in-place (geometry, style, etc.) without removing it from
	 * the shared layer. Called during {@link #update(String, ReadableMap, MapFragment, ContentResolver)}.
	 */
	@NonNull
	protected abstract UpdateResult updateEntry(
		@NonNull TEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception;

	/**
	 * Hit-tests {@code entry} against screen coordinates. Returns a payload for
	 * the event, or null if the entry wasn't hit.
	 */
	@Nullable
	protected abstract WritableMap hitTestEntry(
		@NonNull TEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	);

	// ── Public API (called by TurboModules) ─────────────────────────────

	/**
	 * Creates a new entry and adds its geometry to the correct fragment's shared layer.
	 *
	 * @param entryUuid    the uuid that will identify this entry (generated by the caller)
	 * @param fragmentUuid the fragment's shared layer uuid (e.g. {@code "__vtm_shared_paths__1"})
	 * @param params       TurboModule params (may include "layerUuids" — the
	 *                     absolute target order applied atomically with the add)
	 * @return the entry uuid on success
	 */
	@NonNull
	public CreateResult<TEntry> create(
		@NonNull String entryUuid,
		@NonNull String fragmentUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		// Ensure the shared layer exists for this fragment before adding the first entry.
		// The create carries the desired absolute order so the new fragment
		// lands at its final position in the same flush that adds it.
		ensureSharedLayer(
			fragmentUuid,
			Utils.rMapGetStringList(params, "layerUuids")
		);

		CreateResult<TEntry> result = createEntry(entryUuid, params, mapFragment, contentResolver, reactContext);
		entries.put(entryUuid, result.entry);

		// Trigger a map update so the new geometry is picked up,
		// coalesced onto the UI thread via scheduleUpdate().
		scheduleUpdate();

		return result;
	}

	/**
	 * Removes an entry and its geometry from the shared layer.
	 */
	public void remove(@NonNull String entryUuid) {
		TEntry entry = entries.remove(entryUuid);
		if (entry != null) {
			// Skip removeEntryFromLayer if destroy() is already running —
			// its Phase 1 iteration will handle cleanup of all entries.
			// This prevents double-processing when per-layer remove()
			// races with the map-level destroy().
			if (!destroying.get()) {
				removeEntryFromLayer(entry);
			}
			scheduleUpdate();
		}
	}

	/**
	 * Updates an entry in-place.
	 *
	 * @return optional response data, or null
	 */
	@Nullable
	public WritableMap update(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		TEntry entry = entries.get(entryUuid);
		if (entry == null) {
			return null;
		}
		UpdateResult result = updateEntry(entry, params, mapFragment, contentResolver);
		scheduleUpdate();
		return result.responseData;
	}

	/**
	 * Manually hit-tests a specific entry (for programmatic triggerEvent from JS).
	 *
	 * @return event payload, or null if the entry wasn't hit
	 */
	@Nullable
	public WritableMap triggerEvent(
		@NonNull String entryUuid,
		float x,
		float y
	) {
		TEntry entry = entries.get(entryUuid);
		if (entry == null) {
			return null;
		}
		GeoPoint eventPoint = mapView.map().viewport().fromScreenPoint(x, y);
		// Default gestureScreenDistance — subclasses that track per-entry
		// sensitivity should use the entry's own value, not this parameter.
		return hitTestEntry(entry, x, y, eventPoint, 30f);
	}

	// ── Internal ────────────────────────────────────────────────────────

	/**
	 * Best-effort error message for a caught throwable — never null, so a
	 * failed batch item is always reported with some text (no phantom success).
	 */
	@NonNull
	protected static String errorMessage( @NonNull Throwable t ) {
		String msg = t.getMessage();
		if ( msg != null && !msg.isEmpty() ) {
			return msg;
		}
		// Fully-qualified name — never empty (getSimpleName() returns "" for
		// anonymous/local classes).
		return t.getClass().getName();
	}

	/**
	 * Applies a gesture-support flag to every shared {@code VectorLayer}
	 * fragment. Extracted from the Path/Shape managers' {@code syncGestureSupport}
	 * so the flag stays consistent across the entry lifecycle in one place.
	 */
	protected void applyGestureSupport( boolean supportsGestures ) {
		for ( Layer layer : sharedLayerFragments.values() ) {
			if ( layer instanceof VectorLayer ) {
				( (VectorLayer) layer ).setSupportsGestures( supportsGestures );
			}
		}
	}

	/**
	 * Ensures a shared vtm Layer exists for the given {@code fragmentUuid}
	 * and is registered in the map's layer list via {@link MapMutationQueue}.
	 * Idempotent per fragment.
	 *
	 * @param fragmentUuid unique key for this fragment (e.g. {@code "__vtm_shared_paths__1"})
	 * @param desiredOrder optional absolute target order applied atomically
	 *                     with the add (from the JS-side create params)
	 */
	protected void ensureSharedLayer(
		@NonNull String fragmentUuid,
		@Nullable List<String> desiredOrder
	) throws Exception {
		// Fast path: already exists (outside synchronized to avoid contention).
		if (sharedLayerFragments.containsKey(fragmentUuid)) {
			return;
		}

		Layer layer;
		CompletableFuture<String> future;
		MapMutationQueue queue;
		synchronized (this) {
			// Double-check inside the lock — another thread may have created
			// the fragment while we were waiting for the lock.
			if (sharedLayerFragments.containsKey(fragmentUuid)) {
				return;
			}
			layer = createSharedLayer();
			sharedLayerFragments.put(fragmentUuid, layer);

			queue = MapMutationQueue.get(nativeNodeHandle, mapView);
			// The absolute plan (from the JS side) places the fragment —
			// adds append until the plan arrives in the same or next flush.
			future = queue.enqueueAddLayer(
				layer,
				fragmentUuid,
				desiredOrder
			);
		}

		// Block the caller until the shared layer is placed on the UI thread.
		// IMPORTANT: future.get() is OUTSIDE synchronized(this) — otherwise
		// destroy() (which also acquires synchronized(this) and calls
		// removeLayerSync on the UI thread) would deadlock if the UI thread
		// is waiting for the lock while this thread holds it waiting for the UI.
		try {
			future.get();
		} catch (Exception e) {
			// Registration failed — roll back the fragment.
			synchronized (this) {
				sharedLayerFragments.remove(fragmentUuid);
			}
			throw new RuntimeException("Failed to register shared layer fragment '" + fragmentUuid + "': " + errorMessage(e), e);
		}

		// destroy() may have run while we were blocked in future.get().
		// If the fragment was removed during the wait, clean up and throw.
		synchronized (this) {
			if (!sharedLayerFragments.containsKey(fragmentUuid)) {
				// The shared layer was added to the map by the queue flush,
				// but destroy() already removed it from our tracking map.
				// Remove it from the actual map layer list so it doesn't leak.
				// This runs on the caller's (TurboModule) thread, so we must
				// route through the async queue — removeLayerSync is UI-thread
				// only and would mutate map().layers() un-serialized here.
				CompletableFuture<Void> removal = queue.enqueueRemoveLayer(fragmentUuid);
				removal.exceptionally( t -> {
					// Log rather than silently drop: if the queue was already
					// torn down, the layer stays leaked but we make it visible.
					android.util.Log.w( "LayerManager",
						"Failed to roll back shared layer fragment '" + fragmentUuid + "': " + errorMessage( t ) );
					return null;
				} );
				throw new RuntimeException(
					"Shared layer fragment '" + fragmentUuid
						+ "' was destroyed while waiting for registration");
			}
		}
	}

	/**
	 * Placeholder priority for newly created entries. JS no longer sends a
	 * create-time positionIndex — within-fragment order is established
	 * exclusively by {@code applyEntryPriorities} (sparse drawable/marker
	 * priorities) after the entries exist, so new entries simply append.
	 */
	protected static final int APPEND_PRIORITY = Integer.MAX_VALUE;

	/**
	 * Tears down this manager: removes the shared layer from the map and clears
	 * all entries. Called from {@link #remove(int, String)} or {@link #removeAll(int)}.
	 */
	private final AtomicBoolean updatePending = new AtomicBoolean(false);
	private final AtomicBoolean destroying = new AtomicBoolean(false);
	private final Handler uiHandler = new Handler(Looper.getMainLooper());

	protected void scheduleUpdate() {
		if (updatePending.compareAndSet(false, true)) {
			uiHandler.post(() -> {
				updatePending.set(false);
				// Bail if the manager has been destroyed (sharedLayerFragments
				// is cleared by destroy() — its emptiness is visible even when
				// this Runnable was posted before destroy() ran).
				if (mapView.map() != null && !sharedLayerFragments.isEmpty()) {
					mapView.map().updateMap();
				}
			});
		}
	}

	protected void destroy() {
		// Signal that we're tearing down so concurrent remove() calls
		// skip their own removeEntryFromLayer — Phase 1 handles it all.
		destroying.set(true);

		// Remove all entries' geometry from their fragment layers.
		for (TEntry entry : entries.values()) {
			try {
				removeEntryFromLayer(entry);
			} catch (Exception ignored) {
				// Best-effort cleanup.
			}
		}
		entries.clear();

		// Remove all fragment layers from the map. Synchronized to prevent
		// ensureSharedLayer from racing with this teardown.
		synchronized (this) {
			if (!sharedLayerFragments.isEmpty()) {
				try {
					MapMutationQueue queue = MapMutationQueue.getInstance(nativeNodeHandle);
					if (queue != null) {
						for (String fragmentUuid : sharedLayerFragments.keySet()) {
							queue.removeLayerSync(fragmentUuid);
						}
					}
					scheduleUpdate();
				} catch (Exception ignored) {
					// Map may already be torn down.
				}
			}
			sharedLayerFragments.clear();
		}
		eventCallback = null;
	}

	// ── Accessors ───────────────────────────────────────────────────────

	/**
	 * Returns the shared vtm Layer for the given fragment uuid.
	 *
	 * @param fragmentUuid the fragment's unique key (e.g. {@code "__vtm_shared_paths__1"})
	 * @return the fragment's Layer, or null if not yet created
	 */
	@Nullable
	protected Layer getSharedLayer(@NonNull String fragmentUuid) {
		return sharedLayerFragments.get(fragmentUuid);
	}

	@NonNull
	public String getSharedLayerUuid() {
		return sharedLayerUuid;
	}

	@NonNull
	public Map<String, TEntry> getEntries() {
		return entries;
	}
}
