package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.oscim.android.MapView;
import org.oscim.layers.Layer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the ordering state of the JS-managed layer stack for one map view:
 * the uuid → Layer registry ({@link #getKnownLayers()}), absolute-plan
 * application via minimal-move reordering (LIS), and a post-apply
 * self-check.
 *
 * <p>All mutating methods run on the UI thread, called from
 * {@link MapMutationQueue#flush()} — the only place allowed to touch
 * {@code mapView.map().layers()}. {@link #getKnownLayers()} is safe for
 * read-only lookups from any thread.
 */
public class LayerStackController {

	private static final String TAG = "LayerStackController";

	@NonNull
	private final MapView mapView;

	// All JS-managed layers currently on this map (uuid -> Layer). Updated only
	// during flush, after the map manipulation is complete, so it always
	// reflects what is actually on the map. Used to distinguish JS-managed
	// from vtm-internal layers.
	@NonNull
	private final Map<String, Layer> knownLayers = new ConcurrentHashMap<>();

	// Tracks which uuids were part of the last applied plan, so a layer that's
	// genuinely new to the tracked set can receive CLEAR_EVENT (to (re-)schedule
	// its own tile jobs) without broadcasting a map-wide clear that would flash
	// already-loaded tile layers. UI-thread only.
	@NonNull
	private final Set<String> previouslyReorderedUuids = new HashSet<>();

	/** Result of the last {@link #verify(List)} call. UI-thread only. */
	@Nullable
	private VerifyResult lastVerifyResult;

	/** Signature of the last logged mismatch — dedupes the warning log. */
	@Nullable
	private String lastLoggedMismatch;

	/**
	 * Result of a self-check: does the applied stack match the target plan?
	 */
	public static class VerifyResult {
		public final boolean matches;
		@NonNull
		public final List<String> expectedUuids;
		@NonNull
		public final List<String> appliedUuids;

		public VerifyResult(
			boolean matches,
			@NonNull List<String> expectedUuids,
			@NonNull List<String> appliedUuids
		) {
			this.matches = matches;
			this.expectedUuids = expectedUuids;
			this.appliedUuids = appliedUuids;
		}
	}

	public LayerStackController( @NonNull MapView mapView ) {
		this.mapView = mapView;
	}

	// ── Registry (thread-safe reads, UI-thread writes) ─────────────────

	/**
	 * Returns the map of all JS-managed layers currently on this map
	 * (uuid → Layer). Used by {@code LayerHelper.getLayer}/{@code getLayers}
	 * for uuid-based lookups and by plan application for order resolution.
	 */
	@NonNull
	public Map<String, Layer> getKnownLayers() {
		return knownLayers;
	}

	@Nullable
	public Layer getLayer( @NonNull String uuid ) {
		return knownLayers.get( uuid );
	}

	/** Registers a layer that was just added to the map. UI-thread only. */
	public void register( @NonNull Layer layer, @NonNull String uuid ) {
		knownLayers.put( uuid, layer );
	}

	/** Unregisters a removed layer. UI-thread only. */
	public void unregister( @NonNull String uuid ) {
		knownLayers.remove( uuid );
	}

	/**
	 * Removes all listed layers from the map (bulk teardown step of a flush).
	 * UI-thread only.
	 */
	public void removeAll( @NonNull Set<String> removeUuids ) {
		if ( removeUuids.isEmpty() ) {
			return;
		}
		Map<Layer, String> layerToUuid = new HashMap<>();
		for ( Map.Entry<String, Layer> entry : knownLayers.entrySet() ) {
			layerToUuid.put( entry.getValue(), entry.getKey() );
		}
		int mapSize = mapView.map().layers().size();
		for ( int i = mapSize - 1; i >= 0; i-- ) {
			Layer layer = mapView.map().layers().get( i );
			String uuid = layerToUuid.get( layer );
			if ( uuid != null && removeUuids.contains( uuid ) ) {
				mapView.map().layers().remove( i );
			}
		}
		for ( String uuid : removeUuids ) {
			knownLayers.remove( uuid );
		}
	}

	// ── Absolute-plan application (UI-thread only) ─────────────────────

	/**
	 * Reorders the map's layers to match {@code orderedUuids} (bottom → top).
	 * Unknown uuids are silently skipped; the applied subset keeps the plan's
	 * relative order.
	 */
	public void applyPlan( @NonNull List<String> orderedUuids ) {
		List<Layer> orderedLayers = new ArrayList<>();
		for ( String uuid : orderedUuids ) {
			Layer layer = knownLayers.get( uuid );
			if ( layer != null && mapView.map().layers().contains( layer ) ) {
				orderedLayers.add( layer );
			}
		}
		if ( orderedLayers.isEmpty() ) {
			return;
		}

		// Send CLEAR_EVENT to layers new to this ordered set so their own
		// tile jobs (re-)schedule without a map-wide clear that would flash
		// already-loaded tile layers.
		for ( Layer layer : orderedLayers ) {
			String layerUuid = getLayerUuidForLayer( layer );
			if (
				layerUuid != null
					&& ! previouslyReorderedUuids.contains( layerUuid )
					&& layer instanceof org.oscim.map.Map.UpdateListener
			) {
				( (org.oscim.map.Map.UpdateListener) layer ).onMapEvent(
					org.oscim.map.Map.CLEAR_EVENT,
					mapView.map().getMapPosition()
				);
			}
		}
		previouslyReorderedUuids.clear();
		previouslyReorderedUuids.addAll( orderedUuids );

		reorderMinimalMoves( orderedLayers );

		verify( orderedUuids );
	}

	// ── Self-check ──────────────────────────────────────────────────────

	/**
	 * Compares the JS-managed layer sequence actually on the map against the
	 * target plan and stores the result (surfaced by
	 * {@code MapContainer.getDebugLayerDump}). Logs a warning on mismatch,
	 * deduped by mismatch signature so repeated identical mismatches don't
	 * spam the log. UI-thread only.
	 */
	@NonNull
	public VerifyResult verify( @NonNull List<String> target ) {
		List<String> appliedUuids = new ArrayList<>();
		Map<Layer, String> layerToUuid = new HashMap<>();
		for ( Map.Entry<String, Layer> entry : knownLayers.entrySet() ) {
			layerToUuid.put( entry.getValue(), entry.getKey() );
		}
		for ( int i = 0; i < mapView.map().layers().size(); i++ ) {
			String uuid = layerToUuid.get( mapView.map().layers().get( i ) );
			if ( uuid != null ) {
				appliedUuids.add( uuid );
			}
		}

		boolean matches = target.size() == appliedUuids.size();
		if ( matches ) {
			for ( int i = 0; i < target.size(); i++ ) {
				if ( !target.get( i ).equals( appliedUuids.get( i ) ) ) {
					matches = false;
					break;
				}
			}
		}

		VerifyResult result = new VerifyResult(
			matches,
			new ArrayList<>( target ),
			appliedUuids
		);
		lastVerifyResult = result;

		if ( !matches ) {
			String signature =
				result.expectedUuids + " != " + result.appliedUuids;
			if ( !signature.equals( lastLoggedMismatch ) ) {
				lastLoggedMismatch = signature;
				Log.w( TAG, "Stack mismatch: expected=" + result.expectedUuids
					+ " applied=" + result.appliedUuids );
			}
		}
		return result;
	}

	/** Result of the last self-check, or null when no plan was applied yet. */
	@Nullable
	public VerifyResult getLastVerifyResult() {
		return lastVerifyResult;
	}

	// ── Teardown ────────────────────────────────────────────────────────

	/**
	 * Synchronously removes a layer from the map, bypassing the async queue.
	 * Must only be called from the UI thread (e.g. during Fragment.onDestroy)
	 * when the async flush may never run because the map is being torn down.
	 */
	public void removeLayerSync( @NonNull String uuid ) {
		Layer layer = knownLayers.remove( uuid );
		if ( layer != null && mapView.map() != null ) {
			mapView.map().layers().remove( layer );
		}
	}

	/** Clears all registry + self-check state. UI-thread only. */
	public void clear() {
		knownLayers.clear();
		previouslyReorderedUuids.clear();
		lastVerifyResult = null;
		lastLoggedMismatch = null;
	}

	// ── Internal helpers ────────────────────────────────────────────────

	/**
	 * Returns the uuid for a given Layer by reverse-searching {@link #getKnownLayers()}.
	 */
	@Nullable
	private String getLayerUuidForLayer( @NonNull Layer layer ) {
		for ( Map.Entry<String, Layer> entry : knownLayers.entrySet() ) {
			if ( entry.getValue() == layer ) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Reorders mapView's layers to match orderedLayers using the minimum number of
	 * remove+add moves. mapView.map().layers() is backed by a CopyOnWriteArrayList
	 * where every add/remove/contains is O(n) — touching only out-of-place layers
	 * keeps this O(n) instead of O(n²) per reorder call.
	 */
	private void reorderMinimalMoves( @NonNull List<Layer> orderedLayers ) {
		int n = orderedLayers.size();
		if ( n == 0 ) {
			return;
		}

		// Snapshot which of the map's current layers are part of the target set.
		Set<Layer> orderedSet = new HashSet<>( orderedLayers );
		List<Layer> trackedCurrent = new ArrayList<>( n );
		int currentSize = mapView.map().layers().size();
		for ( int i = 0; i < currentSize; i++ ) {
			Layer layer = mapView.map().layers().get( i );
			if ( orderedSet.contains( layer ) ) {
				trackedCurrent.add( layer );
			}
		}

		Map<Layer, Integer> posInTrackedCurrent = new HashMap<>();
		for ( int i = 0; i < trackedCurrent.size(); i++ ) {
			posInTrackedCurrent.put( trackedCurrent.get( i ), i );
		}

		// values[i] = where orderedLayers.get(i) currently sits within trackedCurrent.
		// Longest increasing run = layers that don't need to move.
		int[] values = new int[n];
		for ( int i = 0; i < n; i++ ) {
			values[i] = posInTrackedCurrent.get( orderedLayers.get( i ) );
		}

		boolean[] keep = longestIncreasingSubsequenceMask( values );

		Layer afterLayer = null;
		for ( int i = 0; i < n; i++ ) {
			Layer layer = orderedLayers.get( i );
			if ( keep[i] ) {
				afterLayer = layer;
				continue;
			}
			mapView.map().layers().remove( layer );
			int index;
			if ( afterLayer != null ) {
				index = mapView.map().layers().indexOf( afterLayer ) + 1;
			} else {
				// Find the position of the first JS-managed layer
				// so we insert before it, not before vtm-internal
				// layers (GestureLayer, etc.) at index 0.
				index = 0;
				for ( int j = 0; j < mapView.map().layers().size(); j++ ) {
					if ( orderedSet.contains( mapView.map().layers().get( j ) ) ) {
						index = j;
						break;
					}
				}
			}
			mapView.map().layers().add( index, layer );
			afterLayer = layer;
		}
	}

	/**
	 * Standard O(n log n) patience-sorting longest increasing subsequence, returning
	 * which indices of {@code values} belong to one such strictly increasing subsequence.
	 */
	private static boolean[] longestIncreasingSubsequenceMask( int[] values ) {
		int n = values.length;
		int[] tails = new int[n];
		int[] predecessors = new int[n];
		int len = 0;
		for ( int i = 0; i < n; i++ ) {
			int lo = 0, hi = len;
			while ( lo < hi ) {
				int mid = ( lo + hi ) / 2;
				if ( values[tails[mid]] < values[i] ) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			predecessors[i] = lo > 0 ? tails[lo - 1] : -1;
			tails[lo] = i;
			if ( lo == len ) {
				len++;
			}
		}
		boolean[] keep = new boolean[n];
		int k = len == 0 ? -1 : tails[len - 1];
		while ( k >= 0 ) {
			keep[k] = true;
			k = predecessors[k];
		}
		return keep;
	}
}
