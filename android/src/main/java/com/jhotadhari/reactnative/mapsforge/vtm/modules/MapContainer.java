package com.jhotadhari.reactnative.mapsforge.vtm.modules;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

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
import com.jhotadhari.reactnative.mapsforge.vtm.ElevationReader;
import com.jhotadhari.reactnative.mapsforge.vtm.MapMutationQueue;
import com.jhotadhari.reactnative.mapsforge.vtm.NativeMapContainerSpec;
import com.jhotadhari.reactnative.mapsforge.vtm.MarkerLayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.modules.LayerMarker;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager.EventEmitterCallback;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.mapsforge.map.layer.hills.DemFolder;

import java.util.concurrent.ConcurrentHashMap;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.core.MapPosition;
import org.oscim.event.Event;
import org.oscim.utils.animation.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ReactModule( name = MapContainer.NAME )
public class MapContainer extends NativeMapContainerSpec {

	public static final String NAME = "MapContainer";

	// Tracks, per nativeNodeHandle, the not-yet-resolved animateTo call(s)' listener+promises.
	// vtm's org.oscim.map.Map.ANIM_END is a single event per map (not scoped to one animateTo
	// call), so a new animateTo started before a previous one finishes would otherwise leave the
	// earlier promise hanging until *this* animation happens to end too. Rather than resolving a
	// superseded call's promise immediately (which would be a false "success" while the camera is
	// actually still moving towards a *different*, newer target), it's carried over and resolved
	// together with the call that superseded it -- so by the time any of them resolves, the camera
	// genuinely reflects whichever target was requested last.
	private final Map<Integer, PendingAnimateTo> pendingAnimateTo = new HashMap<>();

	// Elevation readers keyed by nativeNodeHandle, lazily created by setHgtDirPath.
	// ConcurrentHashMap — accessed from Native Modules thread (setHgtDirPath, getAltitudeAtPosition),
	// render thread (getElevationReader), and UI thread (removeElevationReader).
	private final Map<Integer, ElevationReader> elevationReaders = new ConcurrentHashMap<>();

	private static class PendingAnimateTo {
		final org.oscim.map.Map.UpdateListener listener;
		final List<Promise> promises;
		PendingAnimateTo( org.oscim.map.Map.UpdateListener listener, List<Promise> promises ) {
			this.listener = listener;
			this.promises = promises;
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
			installMapPositionJSI();
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

			// Resolve uuids on the calling thread (read-only lookup). The actual
			// reorder — which mutates mapView.map().layers() — is enqueued into
			// MapMutationQueue and serialized on the UI thread with all other
			// layer mutations.
			ReadableArray layerUuids = params.getArray( "layerUuids" );
			List<String> uuidList = new ArrayList<>();
			for ( int i = 0; i < layerUuids.size(); i++ ) {
				uuidList.add( layerUuids.getString( i ) );
			}

			MapMutationQueue queue = MapMutationQueue.get( nativeNodeHandle, mapView );
			queue.enqueueReorderLayers( uuidList )
				.thenRun( () -> promise.resolve( null ) )
				.exceptionally( t -> {
					Utils.promiseReject( promise, t.getMessage() );
					return null;
				} );
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

			List<Promise> pendingPromises = new ArrayList<>();
			PendingAnimateTo previous = pendingAnimateTo.remove( nativeNodeHandle );
			if ( null != previous ) {
				mapView.map().events.unbind( previous.listener );
				pendingPromises.addAll( previous.promises );
			}
			pendingPromises.add( promise );

			long duration = Utils.rMapHasKey( params, "duration" ) ? (long) params.getDouble( "duration" ) : 0;
			Easing.Type easing = easingFromString( Utils.rMapHasKey( params, "easing" ) ? params.getString( "easing" ) : null );

			MapPosition current = new MapPosition();
			mapView.map().getMapPosition( current );
			MapPosition target = new MapPosition();
			target.copy( current );

			if ( Utils.rMapHasKey( params, "bounds" ) ) {
				ReadableArray bounds = params.getArray( "bounds" );
				if ( bounds.size() != 4 ) {
					Utils.promiseReject( promise, "bounds must be [ west, south, east, north ] (length 4), got length " + bounds.size() ); return;
				}
				// GeoJSON bbox order: [ west, south, east, north ].
				BoundingBox bbox = new BoundingBox(
					bounds.getDouble( 1 ), bounds.getDouble( 0 ),
					bounds.getDouble( 3 ), bounds.getDouble( 2 )
				);
				if ( mapView.getWidth() <= 0 || mapView.getHeight() <= 0 ) {
					Utils.promiseReject( promise, "Map view has not been laid out yet (width/height are 0) -- fitBounds/flyToBounds needs a real viewport size to compute a zoom level" ); return;
				}
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
				resolveAll( pendingPromises );
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
				resolveAll( pendingPromises );
				return;
			}

			org.oscim.map.Map.UpdateListener[] listenerHolder = new org.oscim.map.Map.UpdateListener[ 1 ];
			listenerHolder[ 0 ] = new org.oscim.map.Map.UpdateListener() {
				@Override
				public void onMapEvent( Event e, MapPosition mapPosition ) {
					if ( e == org.oscim.map.Map.ANIM_END ) {
						mapView.map().events.unbind( listenerHolder[ 0 ] );
						pendingAnimateTo.remove( nativeNodeHandle );
						resolveAll( pendingPromises );
					}
				}
			};
			pendingAnimateTo.put( nativeNodeHandle, new PendingAnimateTo( listenerHolder[ 0 ], pendingPromises ) );
			mapView.map().events.bind( listenerHolder[ 0 ] );

			mapView.map().animator().animateTo( duration, target, easing );
		} catch ( Exception e ) {
			e.printStackTrace();
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	private static void resolveAll( List<Promise> promises ) {
		for ( Promise p : promises ) {
			p.resolve( null );
		}
	}

	// Shortest signed difference between two degree values, e.g. angularDiff(0.005f, 359.995f) is
	// ~0.01, not ~360 -- bearing/roll wrap around (MapPosition.setBearing/setRoll both normalize
	// via FastMath.clampDegree), so a naive subtraction would treat two near-identical headings
	// that straddle the 0/360 boundary as wildly different.
	private static float angularDiff( float a, float b ) {
		float diff = ( a - b ) % 360f;
		if ( diff > 180f ) {
			diff -= 360f;
		} else if ( diff < -180f ) {
			diff += 360f;
		}
		return diff;
	}

	private static boolean positionsEqual( MapPosition a, MapPosition b ) {
		final double EPS = 1e-7;
		final float EPS_DEGREES = 0.01f;
		return Math.abs( a.x - b.x ) < EPS
			&& Math.abs( a.y - b.y ) < EPS
			&& Math.abs( a.scale - b.scale ) < EPS
			&& Math.abs( angularDiff( a.bearing, b.bearing ) ) < EPS_DEGREES
			&& Math.abs( a.tilt - b.tilt ) < EPS_DEGREES
			&& Math.abs( angularDiff( a.roll, b.roll ) ) < EPS_DEGREES;
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


	@Override
	public void setHgtDirPath( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) || ! Utils.rMapHasKey( params, "hgtDirPath" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle or hgtDirPath" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			String hgtDirPath = params.getString( "hgtDirPath" );

			// Tear down any existing reader for this handle.
			ElevationReader old = elevationReaders.remove( nativeNodeHandle );
			if ( old != null ) {
				old.close();
			}

			if ( hgtDirPath == null || hgtDirPath.isEmpty() ) {
				promise.resolve( null );
				return;
			}

			// Build a DemFolder from the path (same logic as the old
			// MapsforgeVtmView.setHgtReader and LayerHillshading.buildDemFolder).
			DemFolder demFolder = Utils.buildDemFolder( hgtDirPath, getReactApplicationContext() );
			if ( demFolder == null ) {
				Utils.promiseReject( promise, "hgtDirPath does not exist or is not a readable directory" );
				return;
			}

			ElevationReader reader = new ElevationReader( demFolder );
			elevationReaders.put( nativeNodeHandle, reader );
			promise.resolve( null );
		} catch ( Exception e ) {
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	@Override
	public void getAltitudeAtPosition( ReadableMap params, Promise promise ) {
		try {
			if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) || ! Utils.rMapHasKey( params, "lng" ) || ! Utils.rMapHasKey( params, "lat" ) ) {
				Utils.promiseReject( promise, "Undefined nativeNodeHandle, lng, or lat" ); return;
			}
			int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
			double lng = params.getDouble( "lng" );
			double lat = params.getDouble( "lat" );

			ElevationReader reader = elevationReaders.get( nativeNodeHandle );
			if ( reader == null ) {
				Utils.promiseReject( promise, "No elevation data configured — set hgtDirPath on MapContainer first" );
				return;
			}

			Short elevation = reader.getElevation( lng, lat );
			WritableMap result = new WritableNativeMap();
			if ( elevation != null ) {
				result.putDouble( "altitude", elevation.doubleValue() );
			} else {
				result.putNull( "altitude" );
			}
			promise.resolve( result );
		} catch ( Exception e ) {
			Utils.promiseReject( promise, e.getMessage() );
		}
	}

	/**
	 * Removes and closes the ElevationReader for the given nativeNodeHandle.
	 * Called from MapFragment.onDestroy() during teardown.
	 */
	public static void removeElevationReader( int nativeNodeHandle, com.facebook.react.bridge.ReactContext reactContext ) {
		if ( reactContext == null ) {
			return;
		}
		MapContainer module = reactContext.getNativeModule( MapContainer.class );
		if ( module != null ) {
			ElevationReader reader = module.elevationReaders.remove( nativeNodeHandle );
			if ( reader != null ) {
				reader.close();
			}
		}
	}

	/**
	 * Returns the ElevationReader for the given nativeNodeHandle, or null.
	 * Called from MapFragment.getResponseBase() on the render thread — the
	 * ElevationReader's getElevation() is thread-safe (synchronized LruCache
	 * access + simple array reads) and cached lookups are sub-millisecond.
	 */
	@Nullable
	public static ElevationReader getElevationReader( int nativeNodeHandle, com.facebook.react.bridge.ReactContext reactContext ) {
		if ( reactContext == null ) {
			return null;
		}
		MapContainer module = reactContext.getNativeModule( MapContainer.class );
		if ( module == null ) {
			return null;
		}
		return module.elevationReaders.get( nativeNodeHandle );
	}

	@Override
	public void triggerEvent( ReadableMap params ) {
		if ( ! Utils.rMapHasKey( params, "nativeNodeHandle" ) || ! Utils.rMapHasKey( params, "x" ) || ! Utils.rMapHasKey( params, "y" ) ) {
			return;
		}
		int nativeNodeHandle = params.getInt( "nativeNodeHandle" );
		float x = (float) params.getDouble( "x" );
		float y = (float) params.getDouble( "y" );
		String strategy = Utils.rMapHasKey( params, "strategy" ) ? params.getString( "strategy" ) : "nearest";

		MarkerLayerManager manager = MarkerLayerManager.getInstance( nativeNodeHandle );
		if ( manager == null ) {
			return;
		}

		// Ensure the MarkerLayerManager's event callback is wired to the
		// LayerMarker TurboModule's emitOnMarkerEvent.  Without this, the
		// manager's internal emit() calls are silently dropped.
		manager.setEventCallback( new EventEmitterCallback() {
			@Override
			public void emit( @NonNull String eventName, @NonNull WritableMap payload ) {
				if ( "onMarkerEvent".equals( eventName ) ) {
					LayerMarker layerMarker = getReactApplicationContext().getNativeModule( LayerMarker.class );
					if ( layerMarker != null ) {
						layerMarker.emitMarkerEvent( payload );
					}
				}
			}
		} );

		manager.triggerAllMarkers( x, y, strategy );
	}

	private void installMapPositionJSI() {
		if ( !com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter.ensureLibraryLoaded() ) {
			return;
		}
		installMapPositionJSI( null );
	}

	/**
	 * Installs the __bindMapPositionSynchronizables global function on the
	 * React Native JS runtime.  Called synchronously from the JS thread
	 * during useMapPosition() initialization.
	 *
	 * <p>When reanimated is not installed the native library won't be
	 * built - UnsatisfiedLinkError is caught and the call is a no-op.
	 */
		@Override
	public void installMapPositionJSI(ReadableMap params) {
		com.jhotadhari.reactnative.mapsforge.vtm.MapPositionWriter
			.nativeInstallJSI(
				getReactApplicationContext()
					.getJavaScriptContextHolder()
					.get()
			);
	}
}
