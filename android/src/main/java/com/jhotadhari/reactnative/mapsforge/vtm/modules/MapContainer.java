package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.jhotadhari.reactnative.mapsforge.vtm.LayerHelper;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeMapContainerSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.layers.Layer;
import org.oscim.utils.animation.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ReactModule( name = MapContainer.NAME )
public class MapContainer extends NativeMapContainerSpec {

	public static final String NAME = "MapContainer";

	// Tracks, per nativeNodeHandle, which Layers were already part of the order as of the last
	// reorderLayers call -- so a layer that's genuinely new to the tracked set (and so has never
	// had a chance to schedule its own tile jobs) can be kicked individually, instead of
	// broadcasting a map-wide clear that would also needlessly flash every other, already-loaded
	// tile layer on the same map.
	private final Map<Integer, Set<Layer>> previouslyOrderedLayers = new HashMap<>();

	// Tracks, per nativeNodeHandle, the not-yet-resolved animateTo call's listener+promise. vtm's
	// org.oscim.map.Map.ANIM_END is a single event per map (not scoped to one animateTo call), so
	// a new animateTo started before a previous one finishes would otherwise leave the earlier
	// promise hanging until *this* animation happens to end too. Resolving it immediately instead
	// treats "superseded by a newer call" the same as "done".
	private final Map<Integer, PendingAnimateTo> pendingAnimateTo = new HashMap<>();

	private static class PendingAnimateTo {
		final org.oscim.map.Map.UpdateListener listener;
		final Promise promise;
		PendingAnimateTo( org.oscim.map.Map.UpdateListener listener, Promise promise ) {
			this.listener = listener;
			this.promise = promise;
		}
	}

	public MapContainer( ReactApplicationContext reactContext ) {
		super( reactContext );
	}

	@NonNull
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected Map<String, Object> getTypedExportedConstants() {
		final Map<String, Object> constants = new HashMap<>();
		constants.put( "width", null );
		constants.put( "height", 200 );
		constants.put( "center", Utils.positionToWritableArray( -77.605, -9.118, null ) );
		constants.put( "zoomLevel", 12 );
		constants.put( "zoomMin", 1 );
		constants.put( "zoomMax", 20 );
		constants.put( "moveEnabled", true );
		constants.put( "tiltEnabled", true );
		constants.put( "rotationEnabled", true );
		constants.put( "zoomEnabled", true );
		constants.put( "tilt", 0 );
		constants.put( "minTilt", 0 );
		constants.put( "maxTilt", 65 );
		constants.put( "bearing", 0 );
		constants.put( "minBearing", -180 );
		constants.put( "maxBearing", 180 );
		constants.put( "roll", 0 );
		constants.put( "minRoll", -180 );
		constants.put( "maxRoll", 180 );
		constants.put( "hgtDirPath", null );
		constants.put( "hgtInterpolation", true );
		constants.put( "hgtReadFileRate", 100 );
		constants.put( "hgtFileInfoPurgeThreshold", 3 );
		WritableMap responseInclude = new WritableNativeMap();
		responseInclude.putInt( "zoomLevel", 0 );
		responseInclude.putInt( "zoom", 0 );
		responseInclude.putInt( "scale", 0 );
		responseInclude.putInt( "zoomScale", 0 );
		responseInclude.putInt( "bearing", 0 );
		responseInclude.putInt( "roll", 0 );
		responseInclude.putInt( "tilt", 0 );
		responseInclude.putInt( "center", 0 );
		constants.put( "responseInclude", responseInclude );
		constants.put( "mapEventRate", 40 );
		constants.put( "emitsMapUpdateEvents", null );
		WritableArray emitsHardwareKeyUp = new WritableNativeArray();
		emitsHardwareKeyUp.pushString( "KEYCODE_VOLUME_UP" );
		emitsHardwareKeyUp.pushString( "KEYCODE_VOLUME_DOWN" );
		constants.put( "emitsHardwareKeyUp", emitsHardwareKeyUp );
		return constants;
	}

	@Override
	public void reorderLayers( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) || ! Utils.rMapHasKey( params, "layerUuids" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle or layerUuids" ); return;
			}

			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			// Resolve uuids to live Layer instances that are still actually attached to the
			// map. Any tracked layer that isn't mentioned (e.g. still mid-creation on the JS
			// side) is left untouched wherever it currently sits, rather than wiping the whole
			// list, so a reorder triggered by one layer can never orphan another.
			ReadableArray layerUuids = params.getArray( "layerUuids" );
			List<Layer> orderedLayers = new ArrayList<>();
			for ( int i = 0; i < layerUuids.size(); i++ ) {
				Layer layer = LayerHelper.getLayer( nativeNodeHandle, layerUuids.getString( i ) );
				if ( null != layer && mapView.map().layers().contains( layer ) ) {
					orderedLayers.add( layer );
				}
			}

			reorderMinimalMoves( mapView, orderedLayers );

			// Removing/re-adding a tile-based layer (same as LayerHelper.addLayer) leaves its
			// TileManager without a trigger to (re-)schedule tile jobs -- plain updateMap() only
			// redraws the current frame. Layers whose own createLayer already called clearMap() once
			// can have that undone by a reorder racing in right after (e.g. a sibling layer's uuid
			// resolving moments later), so this needs the same fix -- but scoped to just the layer(s)
			// that are actually new here, by calling onMapEvent directly on each rather than
			// broadcasting org.oscim.map.Map#clearMap() to the whole map: a brand new layer has
			// nothing rendered yet, so clearing it is free, but clearing an already-loaded sibling
			// tile layer just to reorder it would visibly flash it for no reason.
			Set<Layer> previous = previouslyOrderedLayers.computeIfAbsent( nativeNodeHandle, k -> new HashSet<>() );
			for ( Layer layer : orderedLayers ) {
				if ( ! previous.contains( layer ) && layer instanceof org.oscim.map.Map.UpdateListener ) {
					( (org.oscim.map.Map.UpdateListener) layer ).onMapEvent( org.oscim.map.Map.CLEAR_EVENT, mapView.map().getMapPosition() );
				}
			}
			previous.clear();
			previous.addAll( orderedLayers );

			mapView.map().updateMap();

			promise.resolve( null );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void animateTo( ReadableMap params, Promise promise ) {
		// vtm's Animator (and most of org.oscim.map.Viewport's mutators) call
		// org.oscim.utils.ThreadUtils.assertMainThread() and throw if not -- but TurboModule
		// methods run on RN's native modules thread, not the UI thread, so the whole body has to
		// be dispatched onto the UI thread rather than called directly here.
		UiThreadUtil.runOnUiThread( () -> animateToOnUiThread( params, promise ) );
	}

	private void animateToOnUiThread( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" ); return;
			}

			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			MapView mapView = Utils.getMapView( getReactApplicationContext(), nativeNodeHandle );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			PendingAnimateTo previous = pendingAnimateTo.remove( nativeNodeHandle );
			if ( null != previous ) {
				mapView.map().events.unbind( previous.listener );
				previous.promise.resolve( null );
			}

			long duration = Utils.rMapHasKey( params, "duration" ) ? (long) params.getDouble( "duration" ) : 0;
			Easing.Type easing = easingFromString( Utils.rMapHasKey( params, "easing" ) ? params.getString( "easing" ) : null );

			MapPosition current = new MapPosition();
			mapView.map().getMapPosition( current );
			MapPosition target = new MapPosition();
			target.copy( current );

			if ( Utils.rMapHasKey( params, "bounds" ) ) {
				ReadableArray bounds = params.getArray( "bounds" );
				// GeoJSON bbox order: [ west, south, east, north ].
				BoundingBox bbox = new BoundingBox(
					bounds.getDouble( 1 ), bounds.getDouble( 0 ),
					bounds.getDouble( 3 ), bounds.getDouble( 2 )
				);
				int paddingPx = Utils.rMapHasKey( params, "boundsPaddingPx" ) ? (int) params.getDouble( "boundsPaddingPx" ) : 0;
				// vtm's animateTo(BoundingBox) overloads don't support padding, so fitting "with
				// padding" means fitting into a viewport shrunk by that padding on each side.
				target.setByBoundingBox(
					bbox,
					Math.max( 1, mapView.getWidth() - 2 * paddingPx ),
					Math.max( 1, mapView.getHeight() - 2 * paddingPx )
				);
			} else {
				if ( Utils.rMapHasKey( params, "center" ) ) {
					ReadableArray center = params.getArray( "center" );
					target.setPosition( Utils.latFromPosition( center ), Utils.lngFromPosition( center ) );
				}
				if ( Utils.rMapHasKey( params, "zoomLevel" ) ) {
					target.setZoom( params.getDouble( "zoomLevel" ) );
				}
				if ( Utils.rMapHasKey( params, "bearing" ) ) {
					target.setBearing( (float) params.getDouble( "bearing" ) );
				}
				if ( Utils.rMapHasKey( params, "tilt" ) ) {
					target.setTilt( (float) params.getDouble( "tilt" ) );
				}
				if ( Utils.rMapHasKey( params, "roll" ) ) {
					target.setRoll( (float) params.getDouble( "roll" ) );
				}
			}

			// vtm's Animator computes `1f - millisLeft / mDuration` every frame -- with mDuration
			// stored as the float 0 (this method's default, used by every non-animated useMap()
			// verb: panTo, panBy, setZoom, setBearing, etc.), that's a division by zero, producing
			// NaN/Infinity that permanently corrupts the live MapPosition (blank/gray map, every
			// position read coming back NaN -- which WritableMap.putDouble/JSON.stringify both
			// surface as null) until the app restarts. Apply non-animated moves directly instead --
			// it's also synchronous, so the promise can resolve immediately rather than waiting on
			// an ANIM_END the animator is never even asked to fire.
			if ( duration <= 0 ) {
				mapView.map().setMapPosition( target );
				promise.resolve( null );
				return;
			}

			// vtm's Animator still fires Map.ANIM_START/ANIM_END around a no-op animation (target
			// equals the current position, e.g. flyTo-ing to where the map already is), but its
			// own update loop only keeps re-scheduling itself once per frame *as long as the
			// position keeps actually changing* -- for a no-op move nothing ever changes, so that
			// loop can stop short of the point where it would fire ANIM_END, leaving the promise
			// (and isAnimating-style UI state) pending forever. Resolve immediately instead of
			// relying on that loop at all when there's nothing to animate.
			if ( positionsEqual( current, target ) ) {
				promise.resolve( null );
				return;
			}

			org.oscim.map.Map.UpdateListener[] listenerHolder = new org.oscim.map.Map.UpdateListener[ 1 ];
			listenerHolder[ 0 ] = new org.oscim.map.Map.UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( e == org.oscim.map.Map.ANIM_END ) {
						mapView.map().events.unbind( listenerHolder[ 0 ] );
						pendingAnimateTo.remove( nativeNodeHandle );
						promise.resolve( null );
					}
				}
			};
			pendingAnimateTo.put( nativeNodeHandle, new PendingAnimateTo( listenerHolder[ 0 ], promise ) );
			mapView.map().events.bind( listenerHolder[ 0 ] );

			mapView.map().animator().animateTo( duration, target, easing );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	private static boolean positionsEqual( MapPosition a, MapPosition b ) {
		final double EPS = 1e-7;
		final float EPS_DEGREES = 0.01f;
		return Math.abs( a.x - b.x ) < EPS
			&& Math.abs( a.y - b.y ) < EPS
			&& Math.abs( a.scale - b.scale ) < EPS
			&& Math.abs( a.bearing - b.bearing ) < EPS_DEGREES
			&& Math.abs( a.tilt - b.tilt ) < EPS_DEGREES
			&& Math.abs( a.roll - b.roll ) < EPS_DEGREES;
	}

	private Easing.Type easingFromString( String easing ) {
		if ( null == easing ) {
			return Easing.Type.LINEAR;
		}
		try {
			return Easing.Type.valueOf( easing.toUpperCase().replace( '-', '_' ) );
		} catch ( IllegalArgumentException e ) {
			return Easing.Type.LINEAR;
		}
	}

	@Override
	public void getPosition( ReadableMap params, Promise promise ) {
		// See animateTo's comment -- reading vtm's live MapPosition is kept on the UI thread for
		// the same reason, even though plain reads aren't asserted by vtm itself.
		UiThreadUtil.runOnUiThread( () -> getPositionOnUiThread( params, promise ) );
	}

	private void getPositionOnUiThread( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle" ); return;
			}

			MapView mapView = Utils.getMapView( getReactApplicationContext(), params.getInt( "nativeNodeHandle" ) );
			if ( null == mapView ) {
				Utils.promiseReject( promise, "Unable to find mapView" ); return;
			}

			MapPosition mapPosition = mapView.map().getMapPosition();
			WritableMap response = new WritableNativeMap();
			response.putArray( "center", Utils.positionToWritableArray( mapPosition.getLongitude(), mapPosition.getLatitude(), null ) );
			response.putInt( "zoomLevel", mapPosition.getZoomLevel() );
			response.putDouble( "bearing", mapPosition.getBearing() );
			response.putDouble( "tilt", mapPosition.getTilt() );
			response.putDouble( "roll", mapPosition.getRoll() );

			promise.resolve( response );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	/**
	 * Reorders mapView's layers to match orderedLayers using the minimum number of remove+add
	 * moves, rather than unconditionally removing and re-adding every single one of them.
	 * mapView.map().layers() is backed by a CopyOnWriteArrayList (org.oscim.map.Layers), where
	 * every add/remove/contains call is O(current layer count) -- so touching only the layers
	 * that are actually out of place (instead of all of them, every time) is what keeps a burst
	 * of many reorderLayers calls -- e.g. while thousands of layers resolve their uuids during a
	 * bulk mount -- from degrading into O(layerCount^2) work on every single call.
	 */
	private void reorderMinimalMoves( MapView mapView, List<Layer> orderedLayers ) {
		int n = orderedLayers.size();
		if ( n == 0 ) {
			return;
		}

		// Snapshot which of the map's current layers are part of the target set, in their
		// current relative order. orderedLayers and trackedCurrent are both permutations of the
		// same n-element set.
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

		// values[i] = where orderedLayers.get(i) currently sits within trackedCurrent. Layers
		// forming the longest increasing run in `values` are already in correct relative order
		// and don't need to move at all; only layers outside that run do -- this is the standard
		// "minimum single-element moves to sort a permutation" reduction to longest increasing
		// subsequence.
		int[] values = new int[ n ];
		for ( int i = 0; i < n; i++ ) {
			values[ i ] = posInTrackedCurrent.get( orderedLayers.get( i ) );
		}

		boolean[] keep = longestIncreasingSubsequenceMask( values );

		// Walk orderedLayers in target order. Layers in the LIS are left untouched. Every other
		// layer gets removed from wherever it currently sits and reinserted right after whichever
		// layer immediately preceded it in target order -- recomputed fresh via indexOf on every
		// move, since each remove/add shifts the indices of everything after it.
		Layer afterLayer = null;
		for ( int i = 0; i < n; i++ ) {
			Layer layer = orderedLayers.get( i );
			if ( keep[ i ] ) {
				afterLayer = layer;
				continue;
			}
			mapView.map().layers().remove( layer );
			int index = null == afterLayer ? 0 : mapView.map().layers().indexOf( afterLayer ) + 1;
			mapView.map().layers().add( index, layer );
			afterLayer = layer;
		}
	}

	/**
	 * Standard O(n log n) patience-sorting longest increasing subsequence, returning which
	 * indices of `values` belong to one such (strictly increasing) subsequence, rather than just
	 * its length. `values` is always a permutation of 0..n-1 here, so ties never need handling.
	 */
	private boolean[] longestIncreasingSubsequenceMask( int[] values ) {
		int n = values.length;
		int[] tails = new int[ n ];
		int[] predecessors = new int[ n ];
		int len = 0;
		for ( int i = 0; i < n; i++ ) {
			int lo = 0, hi = len;
			while ( lo < hi ) {
				int mid = ( lo + hi ) / 2;
				if ( values[ tails[ mid ] ] < values[ i ] ) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			predecessors[ i ] = lo > 0 ? tails[ lo - 1 ] : -1;
			tails[ lo ] = i;
			if ( lo == len ) {
				len++;
			}
		}
		boolean[] keep = new boolean[ n ];
		int k = len == 0 ? -1 : tails[ len - 1 ];
		while ( k >= 0 ) {
			keep[ k ] = true;
			k = predecessors[ k ];
		}
		return keep;
	}

}
