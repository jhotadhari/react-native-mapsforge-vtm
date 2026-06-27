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
	/** Where the shared layer sits among JS-managed layers in {@code map.layers()}. */
	protected final int basePositionIndex;

	/** The shared vtm Layer. Created lazily by {@link #ensureSharedLayer()}. */
	@Nullable
	protected volatile Layer sharedLayer;

	/** All entries currently managed, keyed by entry uuid. */
	@NonNull
	protected final Map<String, TEntry> entries = new ConcurrentHashMap<>();

	/** Set by the owning TurboModule via {@link #setEventCallback}. */
	@Nullable
	protected EventEmitterCallback eventCallback;

	// ── Constructor ─────────────────────────────────────────────────────

	protected LayerManager(
		int nativeNodeHandle,
		@NonNull MapView mapView,
		@NonNull String name,
		int basePositionIndex
	) {
		this.nativeNodeHandle = nativeNodeHandle;
		this.mapView = mapView;
		this.name = name;
		this.basePositionIndex = basePositionIndex;
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
	 * Called once, lazily, from {@link #ensureSharedLayer()}.
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
	 * Creates a new entry and adds its geometry to the shared layer.
	 *
	 * @param entryUuid  the uuid that will identify this entry (generated by the caller)
	 * @param params     TurboModule params (must include "positionIndex" or "nativeNodeHandle")
	 * @return the entry uuid on success
	 */
	@NonNull
	public CreateResult<TEntry> create(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		// Ensure the shared layer exists before adding the first entry.
		ensureSharedLayer();

		CreateResult<TEntry> result = createEntry(entryUuid, params, mapFragment, contentResolver, reactContext);
		entries.put(entryUuid, result.entry);

		// Trigger a map update so the new geometry is picked up.
		mapView.map().updateMap();

		return result;
	}

	/**
	 * Removes an entry and its geometry from the shared layer.
	 */
	public void remove(@NonNull String entryUuid) {
		TEntry entry = entries.remove(entryUuid);
		if (entry != null) {
			removeEntryFromLayer(entry);
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
		mapView.map().updateMap();
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
		// Default gestureScreenDistance — subclasses can override if they track this per-entry.
		return hitTestEntry(entry, x, y, eventPoint, 30f);
	}

	// ── Internal ────────────────────────────────────────────────────────

	/**
	 * Ensures the shared vtm Layer exists and is registered in the map's layer
	 * list via {@link MapMutationQueue}. Idempotent.
	 */
	protected synchronized void ensureSharedLayer() throws Exception {
		if (sharedLayer != null) {
			return;
		}

		sharedLayer = createSharedLayer();

		// Register the shared layer via MapMutationQueue at basePositionIndex.
		MapMutationQueue queue = MapMutationQueue.get(nativeNodeHandle, mapView);
		// Use the synchronous path for now — the shared layer must be on the map
		// before entries can be added to it. Position-aware insertion ensures it
		// lands at the right spot.
		CompletableFuture<String> future = queue.enqueueAddLayer(
			sharedLayer,
			sharedLayerUuid,
			basePositionIndex
		);

		// Block the caller until the shared layer is placed. Since the flush runs
		// on the UI thread and create() is called from a TurboModule thread, this
		// is a brief cross-thread wait.
		try {
			future.get();
		} catch (Exception e) {
			sharedLayer = null;
			throw new RuntimeException("Failed to register shared layer '" + name + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Resolves the positionIndex from TurboModule params.
	 * Defaults to {@link Integer#MAX_VALUE} (append) when absent.
	 */
	protected int resolvePositionIndex(@NonNull ReadableMap params) {
		if (Utils.rMapHasKey(params, "positionIndex")) {
			return params.getInt("positionIndex");
		}
		return Integer.MAX_VALUE;
	}

	/**
	 * Tears down this manager: removes the shared layer from the map and clears
	 * all entries. Called from {@link #remove(int, String)} or {@link #removeAll(int)}.
	 */
	private final AtomicBoolean updatePending = new AtomicBoolean(false);
	private final Handler uiHandler = new Handler(Looper.getMainLooper());

	private void scheduleUpdate() {
		if (updatePending.compareAndSet(false, true)) {
			uiHandler.post(() -> {
				updatePending.set(false);
				// Bail if the manager has been destroyed (sharedLayer
				// is volatile so the null written by destroy() is visible
				// even when this Runnable was posted before destroy() ran).
				if (mapView.map() != null && sharedLayer != null) {
					mapView.map().updateMap();
				}
			});
		}
	}

	protected void destroy() {
		// Remove all entries' geometry from the shared layer.
		for (TEntry entry : entries.values()) {
			try {
				removeEntryFromLayer(entry);
			} catch (Exception ignored) {
				// Best-effort cleanup.
			}
		}
		entries.clear();

		// Remove the shared layer from the map.
		if (sharedLayer != null && mapView != null && mapView.map() != null) {
			try {
				mapView.map().layers().remove(sharedLayer);
				scheduleUpdate();
				MapMutationQueue queue = MapMutationQueue.getInstance(nativeNodeHandle);
				if (queue != null) {
					queue.getKnownLayers().remove(sharedLayerUuid);
				}
			} catch (Exception ignored) {
				// Map may already be torn down.
			}
		}
		sharedLayer = null;
		eventCallback = null;
	}

	// ── Accessors ───────────────────────────────────────────────────────

	@Nullable
	public Layer getSharedLayer() {
		return sharedLayer;
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
