package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;

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
import org.oscim.core.Box;
import org.oscim.core.GeoPoint;
import org.oscim.core.Point;
import org.oscim.core.Tile;
import org.oscim.layers.Layer;
import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Viewport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

	public static final String NAME = "markers";
	/** Position in map.layers(): above paths (0), below any future layers. */
	public static final int BASE_POSITION = Integer.MAX_VALUE;

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
		public final MarkerItem markerItem;
		public int positionIndex;

		public MarkerEntry(
			@NonNull String entryUuid,
			@NonNull String groupUuid,
			@NonNull MarkerItem markerItem,
			int positionIndex
		) {
			this.entryUuid = entryUuid;
			this.groupUuid = groupUuid;
			this.markerItem = markerItem;
			this.positionIndex = positionIndex;
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
		@Nullable
		public MarkerSymbol defaultSymbol;
		@NonNull
		public final Set<String> memberMarkerUuids = ConcurrentHashMap.newKeySet();
		public int positionIndex;

		public MarkerGroup(
			@NonNull String groupUuid,
			@Nullable MarkerSymbol defaultSymbol,
			int positionIndex
		) {
			this.groupUuid = groupUuid;
			this.defaultSymbol = defaultSymbol;
			this.positionIndex = positionIndex;
		}
	}

	// ── Instance state ──────────────────────────────────────────────────

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

	private MarkerLayerManager(int nativeNodeHandle, @NonNull MapView mapView, @NonNull String name) {
		super(nativeNodeHandle, mapView, name, BASE_POSITION);
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

		// Ensure the group exists (creates root group lazily if needed).
		ensureGroup(groupUuid, null, resolvePositionIndex(params));

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

		int positionIndex = resolvePositionIndex(params);

		// Add marker to shared ItemizedLayer at the correct sorted position.
		ItemizedLayer itemizedLayer = (ItemizedLayer) sharedLayer;
		insertMarkerSorted(markerItem, positionIndex, itemizedLayer);

		// Track.
		MarkerEntry entry = new MarkerEntry(entryUuid, groupUuid, markerItem, positionIndex);
		allMarkers.put(entryUuid, entry);
		if (group != null) {
			group.memberMarkerUuids.add(entryUuid);
		}

		WritableMap responseData = new WritableNativeMap();
		responseData.putInt("index", itemizedLayer.getItemList().indexOf(markerItem));
		responseData.putString("uuid", entryUuid);

		return new CreateResult<>(entry, responseData);
	}

	@Override
	protected void removeEntryFromLayer(@NonNull MarkerEntry entry) {
		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
		if (layer != null) {
			layer.removeItem(entry.markerItem);
			allMarkers.remove(entry.entryUuid);
			MarkerGroup group = groups.get(entry.groupUuid);
			if (group != null) {
				group.memberMarkerUuids.remove(entry.entryUuid);
			}
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
		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
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
		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
		if (symbol == null && layer != null) {
			symbol = layer.getDefaultMarker();
		}
		if (symbol == null) {
			return null;
		}

		int eventX = (int) x - mapView.map().getWidth() / 2;
		int eventY = (int) y - mapView.map().getHeight() / 2;
		float dx = (float)(eventX - tmpPoint.x);
		float dy = (float)(eventY - tmpPoint.y);

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
		ensureSharedLayer();

		int count = markersArray.size();
		ItemizedLayer itemizedLayer = (ItemizedLayer) sharedLayer;

		// Per-marker result tracking.
		String[] entryUuids = new String[count];
		int[] resultIndices = new int[count];
		String[] errors = new String[count];

		// Collect successfully-validated markers for sorted insertion.
		List<MarkerItem> itemsToAdd = new ArrayList<>(count);
		List<Integer> positionIndices = new ArrayList<>(count);
		List<Integer> sourceIndices = new ArrayList<>(count); // index into markersArray

		for (int i = 0; i < count; i++) {
			ReadableMap markerParams = markersArray.getMap(i);
			String entryUuid = UUID.randomUUID().toString();
			entryUuids[i] = entryUuid;
			resultIndices[i] = -1;

			try {
				// Resolve group.
				String groupUuid = ROOT_GROUP_UUID;
				if (Utils.rMapHasKey(markerParams, "markerLayerUuid")
					&& !markerParams.isNull("markerLayerUuid")) {
					groupUuid = markerParams.getString("markerLayerUuid");
				}

				// Ensure group exists (creates root group lazily if needed).
				ensureGroup(groupUuid, null, resolvePositionIndex(markerParams));

				// Validate position.
				if (!Utils.rMapHasKey(markerParams, "position")) {
					throw new IllegalArgumentException("Marker does not have a position");
				}
				ReadableArray position = markerParams.getArray("position");

				String title = Utils.rMapHasKey(markerParams, "title")
					? markerParams.getString("title") : "";
				String description = Utils.rMapHasKey(markerParams, "description")
					? markerParams.getString("description") : "";

				int positionIndex = resolvePositionIndex(markerParams);

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
				positionIndices.add(positionIndex);
				sourceIndices.add(i);
			} catch (Exception e) {
				errors[i] = e.getMessage();
			}
		}

		// Sort successfully-validated markers by positionIndex for correct
		// z-order insertion.
		List<Integer> sortedLocalIndices = new ArrayList<>(itemsToAdd.size());
		for (int li = 0; li < itemsToAdd.size(); li++) {
			sortedLocalIndices.add(li);
		}
		sortedLocalIndices.sort((a, b) -> {
			int pa = positionIndices.get(a);
			int pb = positionIndices.get(b);
			if (pa != pb) return Integer.compare(pa, pb);
			return Integer.compare(a, b); // stable: preserve source order for ties
		});

		// Insert sorted markers into the shared ItemizedLayer.
		List<MarkerInterface> itemList = itemizedLayer.getItemList();
		for (int si = 0; si < sortedLocalIndices.size(); si++) {
			int li = sortedLocalIndices.get(si);
			MarkerItem markerItem = itemsToAdd.get(li);
			int positionIndex = positionIndices.get(li);

			// Find the insertion point: first existing marker with
			// positionIndex > ours.
			int insertAt = itemList.size();
			for (int j = 0; j < itemList.size(); j++) {
				MarkerInterface existing = itemList.get(j);
				MarkerEntry existingEntry = allMarkers.get(
					((MarkerItem) existing).getUid().toString());
				if (existingEntry != null
					&& existingEntry.positionIndex > positionIndex) {
					insertAt = j;
					break;
				}
			}
			itemList.add(insertAt, markerItem);
		}
		if (!sortedLocalIndices.isEmpty()) {
			itemizedLayer.populate();
		}

		// Track all successfully created entries and assign result indices.
		for (int si = 0; si < sortedLocalIndices.size(); si++) {
			int li = sortedLocalIndices.get(si);
			int i = sourceIndices.get(li);
			String entryUuid = entryUuids[i];
			MarkerItem markerItem = itemsToAdd.get(li);
			int positionIndex = positionIndices.get(li);

			// Resolve group uuid (re-read from source params).
			ReadableMap markerParams = markersArray.getMap(i);
			String groupUuid = ROOT_GROUP_UUID;
			if (Utils.rMapHasKey(markerParams, "markerLayerUuid")
				&& !markerParams.isNull("markerLayerUuid")) {
				groupUuid = markerParams.getString("markerLayerUuid");
			}

			MarkerEntry entry = new MarkerEntry(
				entryUuid, groupUuid, markerItem, positionIndex);
			entries.put(entryUuid, entry);
			allMarkers.put(entryUuid, entry);

			MarkerGroup group = groups.get(groupUuid);
			if (group != null) {
				group.memberMarkerUuids.add(entryUuid);
			}

			resultIndices[i] = itemList.indexOf(markerItem);
		}

		// Single updateMap for the entire batch.
		mapView.map().updateMap();

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
		ItemizedLayer layer = (ItemizedLayer) sharedLayer;

		for (int i = 0; i < count; i++) {
			String markerUuid = markerUuids.getString(i);
			WritableMap resultItem = Arguments.createMap();
			resultItem.putString("uuid", markerUuid);

			try {
				MarkerEntry entry = allMarkers.remove(markerUuid);
				entries.remove(markerUuid);
				if (entry != null) {
					if (layer != null) {
						layer.removeItem(entry.markerItem);
					}
					MarkerGroup group = groups.get(entry.groupUuid);
					if (group != null) {
						group.memberMarkerUuids.remove(markerUuid);
					}
				}
			} catch (Exception e) {
				resultItem.putString("error", e.getMessage());
			}

			results.pushMap(resultItem);
		}

		if (layer != null && count > 0) {
			layer.populate();
			mapView.map().updateMap();
		}

		WritableMap response = Arguments.createMap();
		response.putArray("results", results);
		return response;
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
		ensureSharedLayer();

		String groupUuid = UUID.randomUUID().toString();
		int positionIndex = resolvePositionIndex(params);

		MarkerGroup group = new MarkerGroup(groupUuid, defaultSymbol, positionIndex);
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
		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
		for (String markerUuid : group.memberMarkerUuids) {
			MarkerEntry entry = allMarkers.remove(markerUuid);
			entries.remove(markerUuid);
			if (entry != null && layer != null) {
				layer.removeItem(entry.markerItem);
			}
		}
		if (layer != null) {
			mapView.map().updateMap();
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

		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
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
			mapView.map().updateMap();
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
			mapView.map().updateMap();
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

		ItemizedLayer layer = (ItemizedLayer) sharedLayer;
		if (layer == null) {
			return null;
		}

		int size = layer.getItemList().size();
		if (size == 0) {
			return null;
		}

		int eventX = (int) x - mapView.map().getWidth() / 2;
		int eventY = (int) y - mapView.map().getHeight() / 2;

		Viewport viewport = mapView.map().viewport();
		Box box = viewport.getBBox(null, Tile.SIZE / 2);
		box.map2mercator();
		box.scale(1E6);

		double dist = (20 * CanvasAdapter.getScale()) * (20 * CanvasAdapter.getScale());
		double distNearest = dist;
		MarkerInterface itemNearest = null;
		int iNearest = 0;
		int inside = -1;

		int i = 0;
		while (i < size && (
			!"first".equals(strategy)
			|| ("first".equals(strategy) && inside == -1)
		)) {
			MarkerInterface item = layer.getItemList().get(i);
			MarkerEntry entry = allMarkers.get(((MarkerItem) item).getUid().toString());
			// Only consider markers in this group.
			if (entry == null || !groupUuid.equals(entry.groupUuid)) {
				i++;
				continue;
			}
			if (box.contains(item.getPoint().longitudeE6, item.getPoint().latitudeE6)) {
				viewport.toScreenPoint(item.getPoint(), tmpPoint);
				MarkerSymbol it = item.getMarker();
				it = it != null ? it : layer.getDefaultMarker();
				if (it == null) {
					i++;
					continue;
				}
				float dx = (float)(eventX - tmpPoint.x);
				float dy = (float)(eventY - tmpPoint.y);
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

	// ── Internal ────────────────────────────────────────────────────────

	/**
	 * Ensures a group exists, creating the root group lazily if needed.
	 */
	private void ensureGroup(@NonNull String groupUuid, @Nullable MarkerSymbol defaultSymbol, int positionIndex) {
		if (!groups.containsKey(groupUuid)) {
			groups.put(groupUuid, new MarkerGroup(groupUuid, defaultSymbol, positionIndex));
		}
	}

	/**
	 * Inserts a marker into the shared ItemizedLayer at the position that
	 * maintains ascending positionIndex order.
	 */
	private void insertMarkerSorted(
		@NonNull MarkerItem markerItem,
		int positionIndex,
		@NonNull ItemizedLayer layer
	) {
		List<MarkerInterface> itemList = layer.getItemList();
		// Find the first existing marker whose positionIndex > ours.
		int insertAt = itemList.size();
		for (int i = 0; i < itemList.size(); i++) {
			MarkerInterface existing = itemList.get(i);
			MarkerEntry existingEntry = allMarkers.get(((MarkerItem) existing).getUid().toString());
			if (existingEntry != null && existingEntry.positionIndex > positionIndex) {
				insertAt = i;
				break;
			}
		}
		// addItem(int, MarkerInterface) internally calls populate(), so no
		// need for an explicit populate() here.
		layer.addItem(insertAt, markerItem);
	}

	/**
	 * Creates the single OnItemGestureListener for the shared ItemizedLayer.
	 */
	@NonNull
	private ItemizedLayer.OnItemGestureListener<MarkerInterface> createGestureListener() {
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
