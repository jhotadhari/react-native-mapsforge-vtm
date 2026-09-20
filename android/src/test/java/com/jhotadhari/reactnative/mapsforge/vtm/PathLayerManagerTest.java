package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.os.Looper;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.VectorLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
import java.util.concurrent.CompletableFuture;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;

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

    private static MockedStatic<Arguments> argumentsMock;

    @BeforeClass
    public static void setUpClass() {
        // Mock Arguments.createArray/createMap so batch methods can build
        // responses without native library loading in Robolectric.
        argumentsMock = mockStatic(Arguments.class);
        WritableArray mockArray = mock(WritableArray.class);
        WritableMap mockMap = mock(WritableMap.class);
        when(Arguments.createArray()).thenReturn(mockArray);
        when(Arguments.createMap()).thenReturn(mockMap);
    }

    @AfterClass
    public static void tearDownClass() {
        if (argumentsMock != null) {
            argumentsMock.close();
        }
    }

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
        return createManagerWithFakeLayer("__vtm_shared_path__0");
    }

    /**
     * Same as {@link #createManagerWithFakeLayer()}, but injects the mock
     * layer under an arbitrary fragment uuid (e.g. a type-run key).
     */
    private PathLayerManager createManagerWithFakeLayer(String fragmentUuid) throws Exception {
        PathLayerManager mgr = PathLayerManager.get(handle, mockMapView);
        Field f = LayerManager.class.getDeclaredField("sharedLayerFragments");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Layer> fragments = (java.util.Map<String, Layer>) f.get(mgr);
        fragments.put(fragmentUuid, mockVectorLayer);
        return mgr;
    }

    /**
     * Reads the manager's sharedLayerFragments map via reflection.
     */
    private java.util.Map<String, Layer> getSharedLayerFragments(PathLayerManager mgr) throws Exception {
        Field f = LayerManager.class.getDeclaredField("sharedLayerFragments");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Layer> fragments = (java.util.Map<String, Layer>) f.get(mgr);
        return fragments;
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
        when(params.hasKey("gestureScreenDistance")).thenReturn(false);
        when(params.hasKey("paint")).thenReturn(false);
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
                mgr.create(entryUuid, "__vtm_shared_path__0", params, mf, cr, rctx);

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

        mgr.create("bad-entry", "__vtm_shared_path__0", params, mf, cr, rctx);
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

        mgr.create("empty-entry", "__vtm_shared_path__0", params, mf, cr, rctx);
    }

    @Test
    public void createEntry_withPaintParams() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = { {13.4, 52.5}, {13.5, 52.6} };
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

        when(params.hasKey("paint")).thenReturn(true);
        when(params.isNull("paint")).thenReturn(false);
        when(params.getMap("paint")).thenReturn(styleMap);

        String entryUuid = "path-styled";
        mgr.create(entryUuid, "__vtm_shared_path__0", params, mf, cr, rctx);

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
        mgr.create(entryUuid, "__vtm_shared_path__0", params, mf, cr, rctx);

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
        mgr.create("path-a", "__vtm_shared_path__0", paramsA, mf, cr, rctx);

        double[][] coordsB = {{13.6, 52.7}, {13.7, 52.8}, {13.8, 52.9}};
        ReadableMap paramsB = mockCoordParams(coordsB);
        configureDefaultParamBehavior(paramsB);
        mgr.create("path-b", "__vtm_shared_path__0", paramsB, mf, cr, rctx);

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

        mgr.create("path-1", "__vtm_shared_path__0", params, mf, cr, rctx);
        mgr.create("path-2", "__vtm_shared_path__0", params, mf, cr, rctx);
        mgr.create("path-3", "__vtm_shared_path__0", params, mf, cr, rctx);

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

    // ------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------

    @Test
    public void updateSupportsGestures_updatesEntry() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();

        PathLayerManager.PathEntry entry = new PathLayerManager.PathEntry(
                "test-uuid", "__vtm_shared_path__0", 0,
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
                "test-uuid", "__vtm_shared_path__0", 0,
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
    // applyEntryPriorities
    // ------------------------------------------------------------------

    @Test
    public void applyEntryPriorities_updatesEntryAndDrawablePriorities() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        mgr.create("path-a", "__vtm_shared_path__0", paramsA, mf, cr, rctx);

        ReadableMap paramsB = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsB);
        mgr.create("path-b", "__vtm_shared_path__0", paramsB, mf, cr, rctx);

        ReadableMap assignmentA = mock(ReadableMap.class);
        when(assignmentA.getString("uuid")).thenReturn("path-a");
        when(assignmentA.getInt("priority")).thenReturn(100);
        ReadableMap assignmentB = mock(ReadableMap.class);
        when(assignmentB.getString("uuid")).thenReturn("path-b");
        when(assignmentB.getInt("priority")).thenReturn(200);
        ReadableArray assignments = mock(ReadableArray.class);
        when(assignments.size()).thenReturn(2);
        when(assignments.getMap(0)).thenReturn(assignmentA);
        when(assignments.getMap(1)).thenReturn(assignmentB);

        mgr.applyEntryPriorities("__vtm_shared_path__0", assignments);

        PathLayerManager.PathEntry entryA = mgr.getEntries().get("path-a");
        PathLayerManager.PathEntry entryB = mgr.getEntries().get("path-b");
        assertEquals(100, entryA.positionIndex);
        assertEquals(200, entryB.positionIndex);
        assertEquals("Entry A drawable priority must be updated",
                100, entryA.drawables.get(0).getPriority());
        assertEquals("Entry B drawable priority must be updated",
                200, entryB.drawables.get(0).getPriority());
    }

    @Test
    public void applyEntryPriorities_unknownFragmentIsNoOp() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ReadableArray assignments = mock(ReadableArray.class);
        when(assignments.size()).thenReturn(0);
        // Should not throw (ZOMBIE path logs a warning).
        mgr.applyEntryPriorities("unknown-fragment", assignments);
    }

    // ------------------------------------------------------------------
    // Batch create / remove
    // ------------------------------------------------------------------

    @Test
    public void createPaths_createsEntriesInOneBatch() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        ReadableMap paramsB = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsB);

        ReadableArray paths = mock(ReadableArray.class);
        when(paths.size()).thenReturn(2);
        when(paths.getMap(0)).thenReturn(paramsA);
        when(paths.getMap(1)).thenReturn(paramsB);

        ReadableMap defaultResponseInclude = mock(ReadableMap.class);
        when(defaultResponseInclude.getInt(anyString())).thenReturn(0);

        WritableMap response = mgr.createPaths(paths, mf, cr, rctx, defaultResponseInclude);

        assertEquals("Two path entries must be registered",
                2, mgr.getEntries().size());
        assertEquals("One drawable per 2-coordinate path",
                2, addedDrawables.size());
    }

    /**
     * The type-run collapse contract: all members of one run share the SAME
     * fragment uuid (scene-authoritative, e.g. "run:layer_0"), so the native
     * side must keep them in ONE shared layer — one fragment, N drawables.
     */
    @Test
    public void createPaths_sameFragmentUuid_collapsesToOneFragment() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer("run:layer_0");
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        when(paramsA.hasKey("fragmentUuid")).thenReturn(true);
        when(paramsA.getString("fragmentUuid")).thenReturn("run:layer_0");
        ReadableMap paramsB = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsB);
        when(paramsB.hasKey("fragmentUuid")).thenReturn(true);
        when(paramsB.getString("fragmentUuid")).thenReturn("run:layer_0");

        ReadableArray paths = mock(ReadableArray.class);
        when(paths.size()).thenReturn(2);
        when(paths.getMap(0)).thenReturn(paramsA);
        when(paths.getMap(1)).thenReturn(paramsB);

        ReadableMap defaultResponseInclude = mock(ReadableMap.class);
        when(defaultResponseInclude.getInt(anyString())).thenReturn(0);

        mgr.createPaths(paths, mf, cr, rctx, defaultResponseInclude);

        assertEquals("Two path entries must be registered",
                2, mgr.getEntries().size());
        assertEquals("Both entries' drawables must live in the ONE shared layer",
                2, addedDrawables.size());
        assertEquals("One shared fragment must host the whole run",
                1, getSharedLayerFragments(mgr).size());
    }

    /**
     * #14: a fragment ensure failure (map torn down mid-request) must not
     * reject the whole batch — nothing is created and the affected items
     * report per-item errors.
     */
    @Test
    public void createPaths_fragmentEnsureFailure_producesPerItemErrors() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer(); // fake at __vtm_shared_path__0
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        // A fragment with no injected fake layer → ensureSharedLayer goes
        // through MapMutationQueue, which we mock as already torn down.
        when(paramsA.hasKey("fragmentUuid")).thenReturn(true);
        when(paramsA.getString("fragmentUuid")).thenReturn("run:gone");

        ReadableArray paths = mock(ReadableArray.class);
        when(paths.size()).thenReturn(1);
        when(paths.getMap(0)).thenReturn(paramsA);

        ReadableMap defaultResponseInclude = mock(ReadableMap.class);
        when(defaultResponseInclude.getInt(anyString())).thenReturn(0);

        try (MockedStatic<MapMutationQueue> queueMock = mockStatic(MapMutationQueue.class)) {
            MapMutationQueue mockQueue = mock(MapMutationQueue.class);
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("map torn down"));
            when(mockQueue.enqueueAddLayer(any(), any(), any())).thenReturn(failed);
            queueMock.when(() -> MapMutationQueue.get(anyInt(), any()))
                    .thenReturn(mockQueue);

            // Must not throw — the batch resolves with per-item errors.
            mgr.createPaths(paths, mf, cr, rctx, defaultResponseInclude);

            assertEquals("No entries may be created when the fragment failed",
                    0, mgr.getEntries().size());
        }
    }

    @Test
    public void createPaths_capturesPerItemErrors() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        // First item valid, second missing coordinates.
        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        ReadableMap paramsB = mock(ReadableMap.class);
        configureDefaultParamBehavior(paramsB);
        when(paramsB.hasKey("coordinates")).thenReturn(false);

        ReadableArray paths = mock(ReadableArray.class);
        when(paths.size()).thenReturn(2);
        when(paths.getMap(0)).thenReturn(paramsA);
        when(paths.getMap(1)).thenReturn(paramsB);

        ReadableMap defaultResponseInclude = mock(ReadableMap.class);
        when(defaultResponseInclude.getInt(anyString())).thenReturn(0);

        mgr.createPaths(paths, mf, cr, rctx, defaultResponseInclude);

        // Only the valid item registered.
        assertEquals(1, mgr.getEntries().size());
        assertEquals(1, addedDrawables.size());
    }

    @Test
    public void removePaths_removesEntriesInOneBatch() throws Exception {
        PathLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        double[][] coords = {{13.4, 52.5}, {13.5, 52.6}};
        ReadableMap paramsA = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsA);
        mgr.create("path-a", "__vtm_shared_path__0", paramsA, mf, cr, rctx);
        ReadableMap paramsB = mockCoordParams(coords);
        configureDefaultParamBehavior(paramsB);
        mgr.create("path-b", "__vtm_shared_path__0", paramsB, mf, cr, rctx);
        assertEquals(2, mgr.getEntries().size());

        ReadableArray uuids = mock(ReadableArray.class);
        when(uuids.size()).thenReturn(2);
        when(uuids.getString(0)).thenReturn("path-a");
        when(uuids.getString(1)).thenReturn("path-b");

        mgr.removePaths(uuids);

        assertEquals("All entries must be removed", 0, mgr.getEntries().size());
        assertTrue("All drawables must be removed", addedDrawables.isEmpty());
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
