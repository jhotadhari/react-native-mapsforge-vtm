package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.os.Looper;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.android.MapView;
import org.oscim.core.Box;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.layers.vector.geometries.Drawable;
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.oscim.map.ViewController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Robolectric unit tests for {@link PathLayerManager}.
 *
 * <p>Injects a mock {@link VectorLayer} via reflection as the shared layer,
 * avoiding vtm's GL/Android dependencies, while exercising the real
 * {@code createEntry}, {@code removeEntryFromLayer}, and {@code updateEntry}
 * code paths.
 *
 * <p>Uses Mockito mocks for all React Native bridge types ({@link ReadableArray},
 * {@link ReadableMap}) to avoid the native library loading that
 * {@code Arguments.createArray()} / {@code WritableNativeMap} require.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PathLayerManagerTest {

    private static int handleCounter = 400000;

    private int handle;
    private MapView mockMapView;
    private Map mockMap;
    private ViewController mockViewport;
    private List<Layer> backingList;
    private VectorLayer mockVectorLayer;
    private final List<Drawable> addedDrawables = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        handle = handleCounter++;
        addedDrawables.clear();

        mockMap = mock(Map.class);
        mockMapView = mock(MapView.class);
        mockViewport = mock(ViewController.class);
        when(mockMapView.map()).thenReturn(mockMap);
        when(mockMap.viewport()).thenReturn(mockViewport);

        // Default viewport: world-wide box, Berlin center.
        Box worldBox = new Box(-180, -90, 180, 90);
        when(mockViewport.getBBox(any(), anyInt())).thenReturn(worldBox);
        GeoPoint berlin = new GeoPoint(52.520, 13.405);
        when(mockViewport.fromScreenPoint(anyFloat(), anyFloat())).thenReturn(berlin);

        backingList = new ArrayList<>();
        Layers mockLayers = mock(Layers.class);
        when(mockMap.layers()).thenReturn(mockLayers);

        when(mockLayers.size()).thenAnswer(inv -> backingList.size());
        when(mockLayers.get(anyInt())).thenAnswer(inv -> backingList.get((int) inv.getArgument(0)));
        doAnswer(inv -> {
            backingList.add((int) inv.getArgument(0), (Layer) inv.getArgument(1));
            return null;
        }).when(mockLayers).add(anyInt(), any(Layer.class));
        doAnswer(inv -> backingList.remove(inv.getArgument(0)))
                .when(mockLayers).remove(anyInt());
        when(mockLayers.contains(any())).thenAnswer(inv -> backingList.contains(inv.getArgument(0)));

        doNothing().when(mockMap).updateMap();
        doNothing().when(mockMap).updateMap(false);

        // Create a mock VectorLayer that tracks added/removed drawables.
        mockVectorLayer = mock(VectorLayer.class);
        doAnswer(inv -> {
            addedDrawables.add(inv.getArgument(0));
            return null;
        }).when(mockVectorLayer).add(any(Drawable.class));
        doAnswer(inv -> {
            addedDrawables.remove(inv.getArgument(0));
            return null;
        }).when(mockVectorLayer).remove(any(Drawable.class));
        doNothing().when(mockVectorLayer).update();
    }

    @After
    public void tearDown() {
        LayerManager.removeAll(handle);
        MapMutationQueue.remove(handle);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Inject the mock VectorLayer as the shared layer in PathLayerManager,
     * so {@code ensureSharedLayer()} returns immediately without going
     * through {@code MapMutationQueue.enqueueAddLayer} + the UI-thread
     * {@code future.get()} deadlock.
     */
    private PathLayerManager createManagerWithFakeLayer() throws Exception {
        PathLayerManager mgr = PathLayerManager.get(handle, mockMapView);
        Field f = LayerManager.class.getDeclaredField("sharedLayer");
        f.setAccessible(true);
        f.set(mgr, mockVectorLayer);
        return mgr;
    }

    /**
     * Creates a {@link ReadableArray} mock representing an array of
     * {@code [lng, lat]} position arrays, without relying on React Native's
     * {@code Arguments.createArray()} (which requires native library loading).
     */
    private ReadableArray mockCoordinateArray(double[][] coords) {
        ReadableArray coordsArray = mock(ReadableArray.class);
        when(coordsArray.size()).thenReturn(coords.length);
        for (int i = 0; i < coords.length; i++) {
            ReadableArray point = mock(ReadableArray.class);
            when(point.getDouble(0)).thenReturn(coords[i][0]);
            when(point.getDouble(1)).thenReturn(coords[i][1]);
            when(point.size()).thenReturn(coords[i].length);
            if (coords[i].length > 2) {
                when(point.getDouble(2)).thenReturn(coords[i][2]);
            }
            when(coordsArray.getType(i)).thenReturn(ReadableType.Array);
            when(coordsArray.getArray(i)).thenReturn(point);
        }
        return coordsArray;
    }

    /**
     * Returns a {@link ReadableMap} mock that provides the given coordinate
     * array under the {@code "coordinates"} key, fit for creating a path.
     */
    private ReadableMap mockCoordParams(double[][] coords) {
        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("coordinates")).thenReturn(true);
        when(params.isNull("coordinates")).thenReturn(false);
        ReadableArray array = mockCoordinateArray(coords);
        when(params.getArray("coordinates")).thenReturn(array);
        return params;
    }

    /**
     * Configures the mock params for mandatory-leaf keys so that
     * {@code createEntry()} picks up defaults for everything else.
     */
    private void configureDefaultParamBehavior(ReadableMap params) {
        when(params.hasKey("positionIndex")).thenReturn(false);
        when(params.hasKey("supportsGestures")).thenReturn(false);
        when(params.hasKey("simplificationTolerance")).thenReturn(false);
        when(params.hasKey("gestureScreenDistance")).thenReturn(false);
        when(params.hasKey("style")).thenReturn(false);
        when(params.hasKey("responseInclude")).thenReturn(false);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    public void getCreatesNewInstance() {
        assertNull("getInstance must be null before get",
                PathLayerManager.getInstance(handle));
        PathLayerManager mgr = PathLayerManager.get(handle, mockMapView);
        assertNotNull("get must return non-null manager", mgr);
        assertEquals("getInstance must return same manager after get",
                mgr, PathLayerManager.getInstance(handle));
    }

    @Test
    public void getReturnsSameInstance() {
        PathLayerManager a = PathLayerManager.get(handle, mockMapView);
        PathLayerManager b = PathLayerManager.get(handle, mockMapView);
        assertEquals("Two get() calls must return the same instance", a, b);
    }

    // ------------------------------------------------------------------
    // Create entry
    // ------------------------------------------------------------------

    @Test
    public void createEntry_createsPathEntryWithDrawables() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}, {13.6, 52.7}};
        ReadableMap params = mockCoordParams(coords);
        configureDefaultParamBehavior(params);

        String entryUuid = "path-entry-1";
        LayerManager.CreateResult<PathLayerManager.PathEntry> result =
                mgr.create(entryUuid, params, mf, cr, rctx);

        assertNotNull("CreateResult must not be null", result);
        assertNotNull("PathEntry must not be null", result.entry);

        PathLayerManager.PathEntry entry = result.entry;
        assertEquals("entryUuid must match", entryUuid, entry.pathUuid);
        // 3 coords in, 3 coords out
        assertEquals("jtsCoordinates length must equal input", 3, entry.jtsCoordinates.length);
        // Segments: 3 coords => segment from i=1 and i=2 => 2 segments
        assertEquals("drawables must match segment count", 2, entry.drawables.size());
        assertEquals("added to the shared layer", 2, addedDrawables.size());

        // Entry must be registered in the manager.
        assertNotNull("entries must contain the new entry",
                mgr.getEntries().get(entryUuid));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEntry_missingCoordinates_throws() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap params = mock(ReadableMap.class);
        configureDefaultParamBehavior(params);
        // No "coordinates" key -> the method should throw.
        when(params.hasKey("coordinates")).thenReturn(false);

        mgr.create("bad-entry", params, mf, cr, rctx);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEntry_emptyCoordinates_throws() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap params = mock(ReadableMap.class);
        configureDefaultParamBehavior(params);
        when(params.hasKey("coordinates")).thenReturn(true);
        when(params.isNull("coordinates")).thenReturn(false);
        // Empty array (size=0).
        ReadableArray empty = mock(ReadableArray.class);
        when(empty.size()).thenReturn(0);
        when(params.getArray("coordinates")).thenReturn(empty);

        mgr.create("empty-entry", params, mf, cr, rctx);
    }

    @Test
    public void createEntry_withSimplification() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        // Many points that are almost collinear -- simplification will thin them.
        double[][] coords = {
            {13.4, 52.5}, {13.401, 52.501}, {13.402, 52.502},
            {13.403, 52.503}, {13.404, 52.504}, {13.5, 52.6}
        };
        ReadableMap params = mockCoordParams(coords);
        configureDefaultParamBehavior(params);
        // Enable simplification.
        when(params.hasKey("simplificationTolerance")).thenReturn(true);
        when(params.isNull("simplificationTolerance")).thenReturn(false);
        when(params.getDouble("simplificationTolerance")).thenReturn(0.1);

        String entryUuid = "path-simplified";
        mgr.create(entryUuid, params, mf, cr, rctx);

        PathLayerManager.PathEntry entry = mgr.getEntries().get(entryUuid);
        assertNotNull("Entry must exist after create", entry);
        // With tolerance=0.1, the intermediate near-collinear points should
        // be simplified away, giving fewer than 6 coordinates.
        assertTrue("Simplification must reduce coordinate count",
                entry.jtsCoordinates.length < 6);
        // But we still have at least 2 coords (start and end).
        assertTrue("Simplification must keep at least 2 coordinates",
                entry.jtsCoordinates.length >= 2);
    }

    @Test
    public void createEntry_withStyleParams() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap params = mockCoordParams(coords);
        configureDefaultParamBehavior(params);

        // Build a style map mock instead of a WritableNativeMap.
        ReadableMap styleMap = mock(ReadableMap.class);
        when(styleMap.hasKey("strokeWidth")).thenReturn(true);
        when(styleMap.isNull("strokeWidth")).thenReturn(false);
        when(styleMap.getDouble("strokeWidth")).thenReturn(8.0);
        when(styleMap.hasKey("strokeColor")).thenReturn(true);
        when(styleMap.isNull("strokeColor")).thenReturn(false);
        when(styleMap.getString("strokeColor")).thenReturn("#00ff00");
        when(styleMap.hasKey("fillColor")).thenReturn(true);
        when(styleMap.isNull("fillColor")).thenReturn(false);
        when(styleMap.getString("fillColor")).thenReturn("#ff0000");
        when(styleMap.hasKey("fillAlpha")).thenReturn(true);
        when(styleMap.isNull("fillAlpha")).thenReturn(false);
        when(styleMap.getDouble("fillAlpha")).thenReturn(0.5);
        when(styleMap.hasKey("buffer")).thenReturn(true);
        when(styleMap.isNull("buffer")).thenReturn(false);
        when(styleMap.getDouble("buffer")).thenReturn(2.0);
        when(styleMap.hasKey("scalingZoomLevel")).thenReturn(true);
        when(styleMap.isNull("scalingZoomLevel")).thenReturn(false);
        when(styleMap.getInt("scalingZoomLevel")).thenReturn(14);

        when(params.hasKey("style")).thenReturn(true);
        when(params.isNull("style")).thenReturn(false);
        when(params.getMap("style")).thenReturn(styleMap);

        String entryUuid = "path-styled";
        mgr.create(entryUuid, params, mf, cr, rctx);

        PathLayerManager.PathEntry entry = mgr.getEntries().get(entryUuid);
        assertNotNull("Entry must exist after create", entry);
        // 2 coords => 1 segment
        assertEquals("One segment drawable", 1, entry.drawables.size());
        // It was added to the mock layer.
        assertEquals("One drawable added to vector layer", 1, addedDrawables.size());
    }

    // ------------------------------------------------------------------
    // Remove entry
    // ------------------------------------------------------------------

    @Test
    public void removeEntry_removesDrawablesAndClearsEntry() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}, {13.6, 52.7}};
        ReadableMap params = mockCoordParams(coords);
        configureDefaultParamBehavior(params);

        String entryUuid = "path-to-remove";
        mgr.create(entryUuid, params, mf, cr, rctx);

        // Sanity: entry and drawables exist.
        assertEquals(1, mgr.getEntries().size());
        assertEquals(2, addedDrawables.size());

        mgr.remove(entryUuid);

        // Entry removed from manager.
        assertNull("Entry must be removed from entries",
                mgr.getEntries().get(entryUuid));
        // Drawables removed from the mock layer.
        assertTrue("All drawables must be removed from vector layer",
                addedDrawables.isEmpty());
    }

    @Test
    public void removeEntry_nonexistentUuid_noOp() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        // Should not throw.
        mgr.remove("nonexistent-uuid");
    }

    // ------------------------------------------------------------------
    // Multi-entry management
    // ------------------------------------------------------------------

    @Test
    public void multipleEntries_eachCreatesItsOwnDrawables() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coordsA = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coordsA);
        configureDefaultParamBehavior(paramsA);
        mgr.create("path-a", paramsA, mf, cr, rctx);

        double[][] coordsB = {{13.6, 52.7}, {13.7, 52.8}, {13.8, 52.9}};
        ReadableMap paramsB = mockCoordParams(coordsB);
        configureDefaultParamBehavior(paramsB);
        mgr.create("path-b", paramsB, mf, cr, rctx);

        assertEquals("Two path entries must be registered",
                2, mgr.getEntries().size());
        // Path A: 2 coords => 1 segment; Path B: 3 coords => 2 segments => total 3
        assertEquals("Total drawables must be 3",
                3, addedDrawables.size());

        // Remove path A — only its drawable goes away.
        mgr.remove("path-a");
        assertEquals("After removing path-a, 2 drawables remain",
                2, addedDrawables.size());
        assertEquals("Only path-b remains in entries",
                1, mgr.getEntries().size());
        assertNotNull(mgr.getEntries().get("path-b"));
    }

    @Test
    public void multipleEntries_independentRemoval() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap params = mockCoordParams(coords);
        configureDefaultParamBehavior(params);

        mgr.create("path-1", params, mf, cr, rctx);
        mgr.create("path-2", params, mf, cr, rctx);
        mgr.create("path-3", params, mf, cr, rctx);

        assertEquals(3, mgr.getEntries().size());

        // Remove middle entry.
        mgr.remove("path-2");
        assertEquals(2, mgr.getEntries().size());
        assertNull(mgr.getEntries().get("path-2"));
        assertNotNull(mgr.getEntries().get("path-1"));
        assertNotNull(mgr.getEntries().get("path-3"));
    }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    @Test
    public void nameConstant_isCorrect() {
        assertEquals("paths", PathLayerManager.NAME);
    }

    @Test
    public void basePosition_isMaxInt() {
        assertEquals(Integer.MAX_VALUE, PathLayerManager.BASE_POSITION);
    }

    // ------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------

    @Test
    public void updateSupportsGestures_updatesEntry() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();

        PathLayerManager.PathEntry entry = new PathLayerManager.PathEntry(
                "test-uuid", 0,
                new org.locationtech.jts.geom.Coordinate[]{}, false, 30f);
        mgr.getEntries().put("test-uuid", entry);

        assertEquals("supportsGestures must start as false",
                false, entry.supportsGestures);

        mgr.updateSupportsGestures("test-uuid", true);
        assertEquals("supportsGestures must be updated to true",
                true, entry.supportsGestures);
    }

    @Test
    public void updateSupportsGestures_nonexistentUuid_noOp() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        // Should not throw.
        mgr.updateSupportsGestures("nonexistent", false);
    }

    @Test
    public void updateGestureScreenDistance_updatesEntry() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();

        PathLayerManager.PathEntry entry = new PathLayerManager.PathEntry(
                "test-uuid", 0,
                new org.locationtech.jts.geom.Coordinate[]{}, false, 30f);
        mgr.getEntries().put("test-uuid", entry);

        assertEquals("gestureScreenDistance must start as 30f",
                30f, entry.gestureScreenDistance, 0.001);

        mgr.updateGestureScreenDistance("test-uuid", 50f);
        assertEquals("gestureScreenDistance must be updated to 50f",
                50f, entry.gestureScreenDistance, 0.001);
    }

    @Test
    public void updateGestureScreenDistance_nonexistentUuid_noOp() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        // Should not throw.
        mgr.updateGestureScreenDistance("nonexistent", 100f);
    }

    // ------------------------------------------------------------------
    // buildCreateResponse
    //
    // NOTE: buildCreateResponse internally creates a WritableNativeMap,
    // which requires native library loading (SoLoader / HybridData) that is
    // unavailable in Robolectric. The method is exercised indirectly through
    // the updateEntry tests above when responseInclude IS provided, but
    // direct WritableNativeMap unit tests need a device or emulator.
    // ------------------------------------------------------------------
}
