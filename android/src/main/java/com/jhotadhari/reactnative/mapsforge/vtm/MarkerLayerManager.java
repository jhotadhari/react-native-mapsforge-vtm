package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.ItemizedLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.oscim.android.MapView;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.GeoPoint;
import org.oscim.core.Point;
import org.oscim.layers.Layer;
import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collapses all {@code LayerMarker} / {@code Marker} components into a single
 * shared {@link ItemizedLayer} per map view.
 *
 * <h3>Groups (LayerMarker backward compat)</h3>
 * A {@code LayerMarker} component creates a <em>named group</em> with its own
 * default symbol and layer-scoped event handlers. Bare {@code Marker} components
 * (no {@code LayerMarker} ancestor) go into the default <em>root group</em>
 * ({@link #ROOT_GROUP_UUID}).
 *
 * <h3>Event routing</h3>
 * The shared {@code ItemizedLayer}'s single {@code OnItemGestureListener} looks
 * up the tapped marker's entry to resolve both the marker uuid and its group uuid.
 * Events include both so JS-side filtering ({@code useMarkerEventSubscription})
 * can match by either.
 */
public class MarkerLayerManager extends LayerManager<MarkerLayerManager.MarkerEntry> {

	private static final String TAG = "MarkerLayerManager";

	public static final String NAME = "markers";

	/** Fallback fragment uuid when params carry none (single implicit fragment).
	 * Must equal {@code sharedLayerUuid + "0"} so {@link LayerMarker} and the
	 * single/batch create paths agree on the same fragment key. */
	public static final String DEFAULT_FRAGMENT_UUID = "__vtm_shared_markers__0";

	/** Default group for bare {@code <Marker>} components (no LayerMarker wrapper). */
	public static final String ROOT_GROUP_UUID = "__root__";

	// ── Factory ─────────────────────────────────────────────────────────

	private static final Factory<MarkerLayerManager> FACTORY = MarkerLayerManager::new;

	@NonNull
	public static MarkerLayerManager get(int nativeNodeHandle, @NonNull MapView mapView) {
		return LayerManager.get(nativeNodeHandle, mapView, NAME, FACTORY);
	}

	@Nullable
	public static MarkerLayerManager getInstance(int nativeNodeHandle) {
		return (MarkerLayerManager) LayerManager.getInstance(nativeNodeHandle, NAME);
	}

	// ── Entry types ─────────────────────────────────────────────────────

	/**
	 * Per-marker entry stored in {@link #entries} and referenced by
	 * {@link #allMarkers} for gesture lookups.
	 */
	public static class MarkerEntry {
		@NonNull
		public final String entryUuid;
		@NonNull
		public final String groupUuid;
		@NonNull
		public final String fragmentUuid;
		@NonNull
		public final MarkerItem markerItem;
		public int positionIndex;
		/**
		 * Monotonic creation sequence — the deterministic tie-break when
		 * several entries share a positionIndex (assigned in source order at
		 * validation so both createMarkers and applyEntryPriorities agree).
		 */
		public final long creationSeq;

		public MarkerEntry(
			@NonNull String entryUuid,
			@NonNull String groupUuid,
			@NonNull String fragmentUuid,
			@NonNull MarkerItem markerItem,
			int positionIndex,
			long creationSeq
		) {
			this.entryUuid = entryUuid;
			this.groupUuid = groupUuid;
			this.fragmentUuid = fragmentUuid;
			this.markerItem = markerItem;
			this.positionIndex = positionIndex;
			this.creationSeq = creationSeq;
		}
	}

	/**
	 * A named group created by a {@code LayerMarker} component. Groups hold a
	 * default symbol and track their member marker uuids so they can be torn
	 * down together on {@code removeLayer}.
	 */
	public static class MarkerGroup {
		@NonNull
		public final String groupUuid;
		@NonNull
		public final String fragmentUuid;
		@Nullable
		public MarkerSymbol defaultSymbol;
		@NonNull
		public final Set<String> memberMarkerUuids = ConcurrentHashMap.newKeySet();
		public int positionIndex;

		public MarkerGroup(
			@NonNull String groupUuid,
			@NonNull String fragmentUuid,
			@Nullable MarkerSymbol defaultSymbol,
			int positionIndex
		) {
			this.groupUuid = groupUuid;
			this.fragmentUuid = fragmentUuid;
			this.defaultSymbol = defaultSymbol;
			this.positionIndex = positionIndex;
		}
	}

	// ── Instance state ──────────────────────────────────────────────────

	/** Monotonic creation sequence for deterministic equal-priority ties. */
	private final AtomicLong entrySeqCounter = new AtomicLong(0);

	/** All markers across all groups, keyed by marker uuid (not item uid). */
	private final Map<String, MarkerEntry> allMarkers = new ConcurrentHashMap<>();

	/**
	 * Groups keyed by group uuid. The root group ({@link #ROOT_GROUP_UUID})
	 * is created lazily on first bare-marker access.
	 */
	private final Map<String, MarkerGroup> groups = new ConcurrentHashMap<>();

	/** Shared gesture-listener helper for hit-test calculations. */
	private final Point tmpPoint = new Point();

	// ── Constructor ─────────────────────────────────────────────────────

	protected MarkerLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name);
	}

	// ── LayerManager contract ───────────────────────────────────────────

	@NonNull
	@Override
	protected Layer createSharedLayer() {
		ItemizedLayer layer = new ItemizedLayer(
			mapView.map(),
			new ArrayList<>(),
			null, // no default marker — every marker specifies its own symbol
			createGestureListener()
		);
		return layer;
	}

	@NonNull
	@Override
	protected CreateResult<MarkerEntry> createEntry(
		@NonNull String entryUuid,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext
	) throws Exception {
		// Bare Marker: resolve group (root or explicit), create MarkerItem, add to shared layer.
			String groupUuid = ROOT_GROUP_UUID;
			if (Utils.rMapHasKey(params, "markerLayerUuid") && !params.isNull("markerLayerUuid")) {
				groupUuid = params.getString("markerLayerUuid");
			}

		// Resolve fragment uuid for this marker.
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		// Ensure the group exists (creates root group lazily if needed).
		ensureGroup(groupUuid, fragmentUuid, null, APPEND_PRIORITY);

		// Resolve marker position.
		if (!Utils.rMapHasKey(params, "position")) {
			throw new IllegalArgumentException("Marker does not have a position");
		}
		ReadableArray position = params.getArray("position");

		String title = Utils.rMapHasKey(params, "title") ? params.getString("title") : "";
		String description = Utils.rMapHasKey(params, "description") ? params.getString("description") : "";

		// Create MarkerItem. Use the group's default symbol if the marker doesn't
		// provide its own — the caller (LayerMarker.java) resolves the symbol and
		// passes it via the "resolvedSymbol" param.
		MarkerSymbol symbol = null;
		// The caller resolves the symbol and passes it as an extra param key.
		// We read it from a sentinel key to avoid coupling MarkerLayerManager to
		// the symbol-resolution pipeline.
		// If absent, fall back to the group's default.
		MarkerGroup group = groups.get(groupUuid);
		if (group != null) {
			symbol = group.defaultSymbol;
		}

		// Use entryUuid passed from caller (generated by LayerMarker.createMarker).
		MarkerItem markerItem = new MarkerItem(
			entryUuid,
			title,
			description,
			new GeoPoint(
				Utils.latFromPosition(position),
				Utils.lngFromPosition(position)
			)
		);

		// Symbol will be set by the caller via setMarkerSymbol, or we set the group default.
		if (symbol != null) {
			markerItem.setMarker(symbol);
		}

		// Add marker to the correct fragment's ItemizedLayer (append — the real
		// within-fragment order is applied later by applyEntryPriorities).
		ItemizedLayer itemizedLayer = (ItemizedLayer) getSharedLayer(fragmentUuid);
		if (itemizedLayer == null) {
			throw new IllegalStateException(
				"No shared ItemizedLayer for fragmentUuid '" + fragmentUuid
					+ "'. Known fragments: " + sharedLayerFragments.keySet());
		}
		appendMarker(markerItem, itemizedLayer);

		// Track.
		MarkerEntry entry = new MarkerEntry(entryUuid, groupUuid, fragmentUuid, markerItem, APPEND_PRIORITY, entrySeqCounter.incrementAndGet());
		allMarkers.put(entryUuid, entry);
		if (group != null) {
			group.memberMarkerUuids.add(entryUuid);
		}

		WritableMap responseData = new WritableNativeMap();
		int index;
		synchronized ( itemizedLayer ) {
			index = itemizedLayer.getItemList().indexOf( markerItem );
		}
		responseData.putInt( "index", index );
		responseData.putString( "uuid", entryUuid );

		return new CreateResult<>(entry, responseData);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull MarkerEntry entry) {
		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(entry.fragmentUuid);
		if (layer != null) {
			layer.removeItem(entry.markerItem);
		} else {
			Log.w(TAG,
				"ZOMBIE: getSharedLayer returned null for fragmentUuid="
					+ entry.fragmentUuid + " entry=" + entry.entryUuid
					+ " groupUuid=" + entry.groupUuid
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet());
		}
		allMarkers.remove(entry.entryUuid);
		MarkerGroup group = groups.get(entry.groupUuid);
		if (group != null) {
			group.memberMarkerUuids.remove(entry.entryUuid);
		}
	}

	@NonNull
	@Override
	protected UpdateResult updateEntry(
		@NonNull MarkerEntry entry,
		@NonNull ReadableMap params,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver
	) throws Exception {
		// Use entry.fragmentUuid, NOT group.fragmentUuid: bare
		// markers share the ROOT group but may live in different
		// fragments when interleaved with other layer types.
		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(entry.fragmentUuid);
		boolean positionChanged = false;

		if (Utils.rMapHasKey(params, "position")) {
			ReadableArray position = params.getArray("position");
			entry.markerItem.geoPoint = new GeoPoint(
				Utils.latFromPosition(position),
				Utils.lngFromPosition(position)
			);
			positionChanged = true;
		}

		if (positionChanged && layer != null) {
			layer.populate();
		}

		return new UpdateResult(null);
	}

	@Nullable
	@Override
	protected WritableMap hitTestEntry(
		@NonNull MarkerEntry entry,
		float x,
		float y,
		@NonNull GeoPoint eventPoint,
		float gestureScreenDistance
	) {
		// For triggerEvent on a specific marker: check if the point is inside
		// the marker's symbol bounds.
		Viewport viewport = mapView.map().viewport();
		viewport.toScreenPoint(entry.markerItem.getPoint(), tmpPoint);

		MarkerSymbol symbol = entry.markerItem.getMarker();
		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(entry.fragmentUuid);
		if (symbol == null && layer != null) {
			symbol = layer.getDefaultMarker();
		}
		if (symbol == null) {
			return null;
		}

		float dx = (float)(x - tmpPoint.x);
		float dy = (float)(y - tmpPoint.y);

		if (symbol.isInside(dx, dy)) {
			WritableMap payload = Arguments.createMap();
			payload.putString("uuid", entry.entryUuid);
			payload.putString("markerLayerUuid", entry.groupUuid);
			payload.putString("event", "itemTrigger");
			payload.putDouble("distance", Math.sqrt(dx * dx + dy * dy));
			return payload;
		}
		return null;
	}

	// ── Group management (LayerMarker backward compat) ──────────────────

	/**
	 * Creates multiple markers in a single batch. Validates all params
	 * upfront, resolves symbols, creates all MarkerItems, inserts them
	 * sorted into the shared ItemizedLayer, and calls
	 * {@code updateMap()} exactly once.
	 *
	 * @param markersArray      ReadableArray of per-marker ReadableMaps
	 * @param mapFragment       Current map fragment (for ContentResolver)
	 * @param contentResolver   Content resolver for bitmap loading
	 * @param reactContext      React application context
	 * @param resolvedSymbols   Map of index-in-markers-array -> pre-resolved
	 *                          MarkerSymbol (may be null for markers using
	 *                          group default; null keys = no per-marker symbol)
	 * @return WritableMap with a "results" array
	 */
	@NonNull
	public WritableMap createMarkers(
		@NonNull ReadableArray markersArray,
		@NonNull MapFragment mapFragment,
		@NonNull ContentResolver contentResolver,
		@NonNull ReactApplicationContext reactContext,
		@Nullable Map<Integer, MarkerSymbol> resolvedSymbols
	) throws Exception {
		int count = markersArray.size();

		// Per-marker result tracking.
		String[] entryUuids = new String[count];
		int[] resultIndices = new int[count];
		String[] errors = new String[count];
		String[] fragmentUuids = new String[count];
		// Creation sequence assigned in SOURCE order at validation time, so the
		// tie-break (equal positionIndex → source order) and the creationSeq
		// used by applyEntryPriorities agree by construction.
		long[] creationSeqs = new long[count];

		// Collect successfully-validated markers for insertion.
		List<MarkerItem> itemsToAdd = new ArrayList<>(count);
		List<Integer> sourceIndices = new ArrayList<>(count); // index into markersArray

		// Ensure all required fragment layers exist upfront. A fragment
		// ensure that fails (e.g. map torn down mid-request) is recorded
		// per fragment — the batch must never reject wholesale after
		// teardown; the affected items report per-item errors instead.
		// Cache all ReadableMaps to avoid redundant markersArray.getMap(i) calls
		// in the three subsequent loops.
		ReadableMap[] allMarkerParams = new ReadableMap[count];
		Map<String, String> fragmentErrors = new HashMap<>();
		Set<String> seenFragments = new HashSet<>();
		for (int i = 0; i < count; i++) {
			allMarkerParams[i] = markersArray.getMap(i);
			ReadableMap markerParams = allMarkerParams[i];
			String fragmentUuid = Utils.rMapHasKey(markerParams, "fragmentUuid")
				? markerParams.getString("fragmentUuid")
				: DEFAULT_FRAGMENT_UUID;
			fragmentUuids[i] = fragmentUuid;
			if (seenFragments.add(fragmentUuid)) {
				try {
					ensureSharedLayer(fragmentUuid, Utils.rMapGetStringList(markerParams, "layerUuids"));
				} catch (Exception e) {
					fragmentErrors.put(fragmentUuid, errorMessage(e));
				}
			}
		}

		for (int i = 0; i < count; i++) {
			ReadableMap markerParams = allMarkerParams[i];
			String entryUuid = UUID.randomUUID().toString();
			entryUuids[i] = entryUuid;
			resultIndices[i] = -1;

			String fragmentError = fragmentErrors.get(fragmentUuids[i]);
			if (fragmentError != null) {
				errors[i] = fragmentError;
				continue;
			}

			try {
				// Resolve group.
				String groupUuid = ROOT_GROUP_UUID;
				if (Utils.rMapHasKey(markerParams, "markerLayerUuid")
					&& !markerParams.isNull("markerLayerUuid")) {
					groupUuid = markerParams.getString("markerLayerUuid");
				}

				// Ensure group exists (creates root group lazily if needed).
				ensureGroup(groupUuid, fragmentUuids[i], null, APPEND_PRIORITY);

				// Validate position.
				if (!Utils.rMapHasKey(markerParams, "position")) {
					throw new IllegalArgumentException("Marker does not have a position");
				}
				ReadableArray position = markerParams.getArray("position");

				String title = Utils.rMapHasKey(markerParams, "title")
					? markerParams.getString("title") : "";
				String description = Utils.rMapHasKey(markerParams, "description")
					? markerParams.getString("description") : "";

				MarkerItem markerItem = new MarkerItem(
					entryUuid,
					title,
					description,
					new GeoPoint(
						Utils.latFromPosition(position),
						Utils.lngFromPosition(position)
					)
				);

				// Set symbol: use pre-resolved if provided, else group default.
				MarkerSymbol symbol = null;
				if (resolvedSymbols != null && resolvedSymbols.containsKey(i)) {
					symbol = resolvedSymbols.get(i);
				}
				if (symbol == null) {
					MarkerGroup group = groups.get(groupUuid);
					if (group != null && group.defaultSymbol != null) {
						symbol = group.defaultSymbol;
					}
				}
				if (symbol != null) {
					markerItem.setMarker(symbol);
				}

				itemsToAdd.add(markerItem);
				sourceIndices.add(i);
				creationSeqs[i] = entrySeqCounter.incrementAndGet();
			} catch (Exception e) {
				errors[i] = errorMessage(e);
			}
		}

		// New entries append (APPEND_PRIORITY placeholder). The real
		// within-fragment order is established later by applyEntryPriorities
		// (ascending positionIndex, ties by ascending creationSeq), whose
		// source-order creationSeq matches this append order — so the two
		// paths agree and the transient append is immediately corrected.
		for (int li = 0; li < itemsToAdd.size(); li++) {
			int i = sourceIndices.get(li);
			MarkerItem markerItem = itemsToAdd.get(li);
			String fragmentUuid = fragmentUuids[i];

			ItemizedLayer fragmentLayer = (ItemizedLayer) getSharedLayer(fragmentUuid);
			if (fragmentLayer == null) {
				// Fragment vanished (teardown race) — mark the item failed
				// so the tracking loop below skips it: no item inserted
				// into a layer that no longer tracks it (no zombie).
				errors[i] = "Fragment not found: " + fragmentUuid;
				continue;
			}

			synchronized (fragmentLayer) {
				fragmentLayer.getItemList().add(markerItem);
			}
		}
		if (!itemsToAdd.isEmpty()) {
			// Populate each affected fragment layer.
			Set<String> populatedFragments = new HashSet<>();
			for (int li = 0; li < itemsToAdd.size(); li++) {
				int i = sourceIndices.get(li);
				String fragmentUuid = fragmentUuids[i];
				if (populatedFragments.add(fragmentUuid)) {
					ItemizedLayer fl = (ItemizedLayer) getSharedLayer(fragmentUuid);
					if (fl != null) {
						synchronized (fl) {
							fl.populate();
						}
					}
				}
			}
		}

		// Track all successfully created entries and assign result indices.
		for (int li = 0; li < itemsToAdd.size(); li++) {
			int i = sourceIndices.get(li);
			if (errors[i] != null) {
				// Insertion skipped (e.g. fragment vanished mid-batch) —
				// do not track an item that never entered a layer.
				continue;
			}
			String entryUuid = entryUuids[i];
			MarkerItem markerItem = itemsToAdd.get(li);

			// Resolve group uuid (re-read from source params).
			ReadableMap markerParams = allMarkerParams[i];
			String groupUuid = ROOT_GROUP_UUID;
			if (Utils.rMapHasKey(markerParams, "markerLayerUuid")
				&& !markerParams.isNull("markerLayerUuid")) {
				groupUuid = markerParams.getString("markerLayerUuid");
			}

			MarkerEntry entry = new MarkerEntry(
				entryUuid, groupUuid, fragmentUuids[i], markerItem, APPEND_PRIORITY,
				creationSeqs[i]);
			entries.put(entryUuid, entry);
			allMarkers.put(entryUuid, entry);

			MarkerGroup group = groups.get(groupUuid);
			if (group != null) {
				group.memberMarkerUuids.add(entryUuid);
			}

			// Look up index within the marker's fragment layer (under the
			// layer monitor so a concurrent mutation can't skew the scan).
			ItemizedLayer fl = (ItemizedLayer) getSharedLayer(fragmentUuids[i]);
			if ( fl != null ) {
				synchronized ( fl ) {
					resultIndices[i] = fl.getItemList().indexOf( markerItem );
				}
			} else {
				resultIndices[i] = -1;
			}
		}

		// Single updateMap for the entire batch.
		scheduleUpdate();

		// Build result array.
		WritableArray results = Arguments.createArray();
		for (int i = 0; i < count; i++) {
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString("uuid", entryUuids[i]);
			if (errors[i] != null) {
				resultItem.putString("error", errors[i]);
			}
			resultItem.putInt("index", resultIndices[i]);
			results.pushMap(resultItem);
		}

		WritableMap response = Arguments.createMap();
		response.putArray("results", results);
		return response;
	}

	/**
	 * Removes multiple markers in a single batch. Removes all items from
	 * the shared ItemizedLayer, clears tracking, and calls
	 * {@code updateMap()} exactly once.
	 *
	 * @param markerUuids   Array of marker uuids to remove
	 * @return WritableMap with a "results" array
	 */
	@NonNull
	public WritableMap removeMarkers(
		@NonNull ReadableArray markerUuids
	) {
		int count = markerUuids.size();
		WritableArray results = Arguments.createArray();
		Set<String> affectedFragments = new HashSet<>();

		for (int i = 0; i < count; i++) {
			String markerUuid = markerUuids.getString(i);
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString("uuid", markerUuid);

			try {
				MarkerEntry entry = allMarkers.remove(markerUuid);
				entries.remove(markerUuid);
				if (entry != null) {
					MarkerGroup group = groups.get(entry.groupUuid);
					if (group != null) {
						group.memberMarkerUuids.remove(markerUuid);
					}
					// Use entry.fragmentUuid, NOT group.fragmentUuid:
					// bare markers (no LayerMarker wrapper) all
					// share the ROOT group but may live in
					// different fragments when interleaved with
					// other layer types (e.g. path, marker, path).
					ItemizedLayer layer = (ItemizedLayer) getSharedLayer(entry.fragmentUuid);
					if (layer != null) {
						layer.removeItem(entry.markerItem);
						affectedFragments.add(entry.fragmentUuid);
					} else {
						Log.w(TAG,
							"ZOMBIE (batch): getSharedLayer returned null for fragmentUuid="
								+ entry.fragmentUuid + " entry=" + markerUuid
								+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet());
					}
				}
			} catch (Exception e) {
				resultItem.putString("error", errorMessage(e));
			}

			results.pushMap(resultItem);
		}

		if (!affectedFragments.isEmpty()) {
			for (String fragmentUuid : affectedFragments) {
				ItemizedLayer fl = (ItemizedLayer) getSharedLayer(fragmentUuid);
				if (fl != null) {
					fl.populate();
				}
			}
			scheduleUpdate();
		}

		WritableMap response = Arguments.createMap();
		response.putArray("results", results);
		return response;
	}


	/**
	 * Applies sparse priorities to entries of a shared fragment and rebuilds
	 * the fragment's ItemizedLayer item list in ascending positionIndex order
	 * (lower z first), with equal priorities broken by ascending creationSeq.
	 * Only fragments with changed entries receive this call; the JS scene
	 * emits O(changed) assignments.
	 *
	 * @return true when the fragment exists and the assignments were applied;
	 *         false when the fragment is missing (the caller must REJECT so
	 *         the JS presenter keeps the state uncommitted and re-sends)
	 */
	public boolean applyEntryPriorities( @NonNull String fragmentUuid, @NonNull ReadableArray assignments ) {
		ItemizedLayer layer = (ItemizedLayer) getSharedLayer( fragmentUuid );
		if ( layer == null ) {
			Log.d( TAG,
				"ZOMBIE: applyEntryPriorities — getSharedLayer returned null for fragmentUuid="
					+ fragmentUuid
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet() );
			return false;
		}

		// Collect this fragment's items and rebuild the list.
		List<MarkerEntry> fragmentEntries = new ArrayList<>();
		for ( MarkerEntry entry : allMarkers.values() ) {
			if ( fragmentUuid.equals( entry.fragmentUuid ) ) {
				fragmentEntries.add( entry );
			}
		}

		// Pre-validate assignments — a malformed assignment that throws
		// (missing uuid/priority key) must not leave positionIndex partially
		// updated.
		final int assignmentCount = assignments.size();
		String[] assignmentUuids = new String[ assignmentCount ];
		int[] assignmentPriorities = new int[ assignmentCount ];
		for ( int i = 0; i < assignmentCount; i++ ) {
			ReadableMap assignment = assignments.getMap( i );
			assignmentUuids[i] = assignment.getString( "uuid" );
			assignmentPriorities[i] = assignment.getInt( "priority" );
		}

		// Update tracked positions + sort + build the ordered list — all
		// outside the layer lock (these only touch MarkerEntry fields and the
		// allMarkers map, never the live item list).
		for ( int i = 0; i < assignmentCount; i++ ) {
			MarkerEntry entry = allMarkers.get( assignmentUuids[i] );
			// Guard against cross-fragment assignments: never touch an entry
			// that doesn't belong to this fragment's layer.
			if ( entry != null && fragmentUuid.equals( entry.fragmentUuid ) ) {
				entry.positionIndex = assignmentPriorities[i];
			}
		}

		// Sort ascending positionIndex (lower z first — the final item-list
		// order); equal priorities break by creation sequence ascending, so
		// an earlier-created marker keeps its place ahead of a later one.
		fragmentEntries.sort( ( a, b ) -> {
			if ( a.positionIndex != b.positionIndex ) {
				return Integer.compare( a.positionIndex, b.positionIndex );
			}
			return Long.compare( a.creationSeq, b.creationSeq );
		} );

		List<MarkerInterface> ordered = new ArrayList<>( fragmentEntries.size() );
		for ( MarkerEntry entry : fragmentEntries ) {
			ordered.add( entry.markerItem );
		}

		// Swap the ordered list in under the layer monitor (serialized against
		// the hit-test path, which ItemizedLayer.onGesture synchronizes on the
		// same monitor). The sort above already ran outside the lock, so the
		// critical section is O(n), not the previous O(n²) remove/scan/re-add.
		synchronized ( layer ) {
			List<MarkerInterface> itemList = layer.getItemList();

			// Preserve any item added to the live list after the snapshot above
			// was taken (a concurrent create that hasn't yet tracked its entry
			// in allMarkers). A full clear()+addAll() would otherwise drop it
			// from the render list until the next applyEntryPriorities.
			List<MarkerInterface> preserved = null;
			Set<String> accountedUids = new HashSet<>();
			for ( MarkerEntry entry : fragmentEntries ) {
				accountedUids.add( entry.markerItem.getUid().toString() );
			}
			for ( MarkerInterface item : itemList ) {
				String uid = ( (MarkerItem) item ).getUid().toString();
				if ( ! accountedUids.contains( uid ) ) {
					if ( preserved == null ) {
						preserved = new ArrayList<>();
					}
					preserved.add( item );
				}
			}

			itemList.clear();
			itemList.addAll( ordered );
			if ( preserved != null ) {
				itemList.addAll( preserved );
			}
			if ( ! itemList.isEmpty() ) {
				layer.populate();
			}
		}

		if ( !fragmentEntries.isEmpty() ) {
			scheduleUpdate();
		}
		return true;
	}

	/**
	 * Creates a named group for a {@code LayerMarker} component.
	 *
	 * @param defaultSymbol the resolved default marker symbol (may be null)
	 * @return the group uuid
	 */
	@NonNull
	public String createGroup(
		@Nullable MarkerSymbol defaultSymbol,
		@NonNull ReadableMap params
	) throws Exception {
		// Resolve fragment uuid for this group.
		String fragmentUuid = Utils.rMapHasKey(params, "fragmentUuid")
			? params.getString("fragmentUuid")
			: DEFAULT_FRAGMENT_UUID;

		// Ensure the fragment's shared layer exists.
		ensureSharedLayer(fragmentUuid, Utils.rMapGetStringList(params, "layerUuids"));

		String groupUuid = UUID.randomUUID().toString();
		int positionIndex = APPEND_PRIORITY;

		MarkerGroup group = new MarkerGroup(groupUuid, fragmentUuid, defaultSymbol, positionIndex);
		groups.put(groupUuid, group);

		return groupUuid;
	}

	/**
	 * Removes a group and all its markers.
	 */
	public void removeGroup(@NonNull String groupUuid) {
		MarkerGroup group = groups.remove(groupUuid);
		if (group == null) {
			return;
		}
		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(group.fragmentUuid);
		if (layer == null) {
			Log.w(TAG,
				"ZOMBIE: getSharedLayer returned null for fragmentUuid="
					+ group.fragmentUuid + " groupUuid=" + groupUuid
					+ " memberCount=" + group.memberMarkerUuids.size()
					+ " sharedLayerFragments keys=" + sharedLayerFragments.keySet());
		}
		for (String markerUuid : group.memberMarkerUuids) {
			MarkerEntry entry = allMarkers.remove(markerUuid);
			entries.remove(markerUuid);
			if (entry != null && layer != null) {
				layer.removeItem(entry.markerItem);
			}
		}
		if (layer != null) {
			scheduleUpdate();
		}
	}

	/**
	 * Updates a group's default symbol and pushes it to all markers that are
	 * still using the old default (matched by object identity).
	 */
	public void updateGroup(
		@NonNull String groupUuid,
		@Nullable MarkerSymbol newDefault
	) {
		MarkerGroup group = groups.get(groupUuid);
		if (group == null) {
			return;
		}
		MarkerSymbol oldDefault = group.defaultSymbol;
		group.defaultSymbol = newDefault;

		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(group.fragmentUuid);
		if (layer != null) {
			layer.setDefaultMarker(newDefault);
		}

		// Push new default onto markers that were using the old one.
		for (String entryUuid : group.memberMarkerUuids) {
			MarkerEntry entry = allMarkers.get(entryUuid);
			if (entry != null && entry.markerItem.getMarker() == oldDefault) {
				entry.markerItem.setMarker(newDefault);
			}
		}
		if (layer != null) {
			scheduleUpdate();
		}
	}

	/**
	 * Sets the symbol on an already-created marker entry. Called by
	 * {@code LayerMarker.java} after symbol resolution.
	 */
	public void setMarkerSymbol(@NonNull String entryUuid, @NonNull MarkerSymbol symbol) {
		MarkerEntry entry = allMarkers.get(entryUuid);
		if (entry != null) {
			entry.markerItem.setMarker(symbol);
			// scheduleUpdate still works fine — updateMap refreshes all layers.
			scheduleUpdate();
		}
	}

	/**
	 * Hit-tests markers within a specific group (for LayerMarker triggerEvent).
	 */
	@Nullable
	public WritableMap triggerGroupEvent(
		@NonNull String groupUuid,
		float x,
		float y,
		@NonNull String strategy
	) {
		MarkerGroup group = groups.get(groupUuid);
		if (group == null) {
			return null;
		}

		ItemizedLayer layer = (ItemizedLayer) getSharedLayer(group.fragmentUuid);
		if (layer == null) {
			return null;
		}

		// Defensive copy under the layer monitor — applyEntryPriorities mutates
		// the item list (clear()+addAll()) on the modules thread, so an
		// unsynchronized size()/get() loop here could observe a torn list.
		List<MarkerInterface> items;
		synchronized ( layer ) {
			items = new ArrayList<>( layer.getItemList() );
		}
		if ( items.isEmpty() ) {
			return null;
		}

		Viewport viewport = mapView.map().viewport();

		double dist = (20 * CanvasAdapter.getScale()) * (20 * CanvasAdapter.getScale());
		double distNearest = dist;
		MarkerInterface itemNearest = null;
		int iNearest = 0;
		int inside = -1;

		int i = 0;
		while (i < items.size() && (
			!"first".equals(strategy)
			|| ("first".equals(strategy) && inside == -1)
		)) {
			MarkerInterface item = items.get(i);
			MarkerEntry entry = allMarkers.get(((MarkerItem) item).getUid().toString());
			// Only consider markers in this group.
			if (entry == null || !groupUuid.equals(entry.groupUuid)) {
				i++;
				continue;
			}
			viewport.toScreenPoint(item.getPoint(), tmpPoint);
			MarkerSymbol it = item.getMarker();
			it = it != null ? it : layer.getDefaultMarker();
			if (it == null) {
				i++;
				continue;
			}
			float dx = (float)(x - tmpPoint.x);
			float dy = (float)(y - tmpPoint.y);
			if (it.isInside(dx, dy)) {
				double d = dx * dx + dy * dy;
				if (d <= dist) {
					inside = i;
					if (d <= distNearest) {
						iNearest = i;
						itemNearest = item;
						distNearest = d;
					}
					if ("all".equals(strategy) || "first".equals(strategy)) {
						MarkerItem mi = (MarkerItem) item;
						WritableMap payload = Arguments.createMap();
						payload.putInt("index", i);
						payload.putString("uuid", mi.getUid().toString());
						payload.putString("markerLayerUuid", groupUuid);
						payload.putString("event", "itemTrigger");
						payload.putDouble("distance", d);
						emit("onMarkerEvent", payload);
					}
				}
			}
			i++;
		}
		if ("nearest".equals(strategy) && itemNearest != null) {
			MarkerItem mi = (MarkerItem) itemNearest;
			WritableMap payload = Arguments.createMap();
			payload.putInt("index", iNearest);
			payload.putString("uuid", mi.getUid().toString());
			payload.putString("markerLayerUuid", groupUuid);
			payload.putString("event", "itemTrigger");
			payload.putDouble("distance", distNearest);
			return payload;
		}
		return null;
	}

	/**
	 * Hit-tests ALL markers (across all groups) using geo-space distance from
	 * the event point, mirroring the path trigger's approach.  Emits events
	 * for matching markers via the event callback.
	 */
	public void triggerAllMarkers(
		float x,
		float y,
		@NonNull String strategy
	) {
		if (sharedLayerFragments.isEmpty()) {
			return;
		}

		Viewport viewport = mapView.map().viewport();

		// Convert JS screen coordinates to a GeoPoint (same approach the
		// working path trigger uses — fromScreenPoint first, then hit-test
		// in absolute geo space).
		GeoPoint eventGeoPoint = viewport.fromScreenPoint(x, y);

		// Compute a geo-distance threshold from the marker symbol's screen
		// radius (30 px for a 30×30 symbol, matching path trigger default).
		double geoThreshold = Math.abs(
			viewport.fromScreenPoint(x, y).getLongitude()
			- viewport.fromScreenPoint(x + 30f, y).getLongitude()
		);

		// No bbox pre-filter — getBBox() returns mercator coordinates,
		// but markers are in degree coordinates. The geo-distance check
		// below is correct in degree space and filters sufficiently.
		double distNearest = Double.MAX_VALUE;
		MarkerInterface itemNearest = null;
		int iNearest = 0;

		// Iterate all markers across all fragment layers.
		for (Layer fragmentLayer : sharedLayerFragments.values()) {
			ItemizedLayer layer = (ItemizedLayer) fragmentLayer;
			// Defensive copy under the layer monitor (see triggerGroupEvent).
			List<MarkerInterface> items;
			synchronized ( layer ) {
				items = new ArrayList<>( layer.getItemList() );
			}
			if ( items.isEmpty() ) {
				continue;
			}
			for (int i = 0; i < items.size(); i++) {
				MarkerInterface item = items.get(i);
				MarkerEntry entry = allMarkers.get(((MarkerItem) item).getUid().toString());
				if (entry == null) {
					continue;
				}

				GeoPoint markerGeo = item.getPoint();

				double dLon = eventGeoPoint.getLongitude() - markerGeo.getLongitude();
				double dLat = eventGeoPoint.getLatitude() - markerGeo.getLatitude();
				double geoDist = Math.sqrt(dLon * dLon + dLat * dLat);

				if (geoDist <= geoThreshold) {
					if ("all".equals(strategy) || "first".equals(strategy)) {
						MarkerItem mi = (MarkerItem) item;
						WritableMap payload = Arguments.createMap();
						payload.putInt("index", i);
						payload.putString("uuid", mi.getUid().toString());
						payload.putString("markerLayerUuid", entry.groupUuid);
						payload.putString("event", "itemTrigger");
						payload.putDouble("distance", geoDist);
						emit("onMarkerEvent", payload);
						if ("first".equals(strategy)) {
							return;
						}
					}
					if (geoDist < distNearest) {
						distNearest = geoDist;
						itemNearest = item;
						iNearest = i;
					}
				}
			}
		}

		if ("nearest".equals(strategy) && itemNearest != null) {
			MarkerItem mi = (MarkerItem) itemNearest;
			MarkerEntry entry = allMarkers.get(mi.getUid().toString());
			WritableMap payload = Arguments.createMap();
			payload.putInt("index", iNearest);
			payload.putString("uuid", mi.getUid().toString());
			payload.putString("markerLayerUuid", entry != null ? entry.groupUuid : ROOT_GROUP_UUID);
			payload.putString("event", "itemTrigger");
			payload.putDouble("distance", distNearest);
			emit("onMarkerEvent", payload);
		}
	}

	// ── Internal ────────────────────────────────────────────────────────

	/**
	 * Ensures a group exists, creating the root group lazily if needed.
	 */
	protected void ensureGroup(@NonNull String groupUuid, @NonNull String fragmentUuid, @Nullable MarkerSymbol defaultSymbol, int positionIndex) {
		if (!groups.containsKey(groupUuid)) {
			groups.put(groupUuid, new MarkerGroup(groupUuid, fragmentUuid, defaultSymbol, positionIndex));
		}
	}

	/**
	 * Appends a marker to the shared ItemizedLayer. New entries append
	 * (APPEND_PRIORITY); the real within-fragment order is applied later by
	 * {@link #applyEntryPriorities}. Serialized on the layer monitor.
	 */
	protected void appendMarker(
		@NonNull MarkerItem markerItem,
		@NonNull ItemizedLayer layer
	) {
		synchronized (layer) {
			// addItem(int, MarkerInterface) internally calls populate(), so no
			// need for an explicit populate() here.
			layer.addItem(layer.getItemList().size(), markerItem);
		}
	}

	/**
	 * Creates the single OnItemGestureListener for the shared ItemizedLayer.
	 */
	@NonNull
	protected ItemizedLayer.OnItemGestureListener<MarkerInterface> createGestureListener() {
		return new ItemizedLayer.OnItemGestureListener<MarkerInterface>() {
			@Override
			public boolean onItemSingleTapUp(int index, MarkerInterface item) {
				return dispatchGesture((MarkerItem) item, index, "itemSingleTapUp");
			}

			@Override
			public boolean onItemLongPress(int index, MarkerInterface item) {
				return dispatchGesture((MarkerItem) item, index, "itemLongPress");
			}

			private boolean dispatchGesture(@NonNull MarkerItem markerItem, int index, @NonNull String eventType) {
				String markerItemUid = markerItem.getUid().toString();
				MarkerEntry entry = allMarkers.get(markerItemUid);
				if (entry == null) {
					return false;
				}
				WritableMap payload = Arguments.createMap();
				payload.putInt("index", index);
				payload.putString("uuid", markerItemUid);
				payload.putString("markerLayerUuid", entry.groupUuid);
				payload.putString("event", eventType);
				emit("onMarkerEvent", payload);
				return false; // Don't consume — allow other gesture handlers to fire.
			}
		};
	}

	@Override
	protected void destroy() {
		allMarkers.clear();
		groups.clear();
		super.destroy();
	}
}
