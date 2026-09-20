package com.jhotadhari.reactnative.mapsforge.vtm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;

import android.content.ContentResolver;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
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
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.oscim.map.ViewController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import org.oscim.layers.vector.geometries.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Robolectric unit tests for {@link ShapeLayerManager} — batch creation /
 * removal and sparse priority application, using a mock VectorLayer injected
 * via reflection to avoid GL dependencies.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShapeLayerManagerTest {

    private static MockedStatic<Arguments> argumentsMock;

    @BeforeClass
    public static void setUpClass() {
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

    private static int handleCounter = 500000;

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

    private ShapeLayerManager createManagerWithFakeLayer() throws Exception {
        ShapeLayerManager mgr = ShapeLayerManager.get(handle, mockMapView);
        Field f = LayerManager.class.getDeclaredField("sharedLayerFragments");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Layer> fragments =
                (java.util.Map<String, Layer>) f.get(mgr);
        fragments.put("__vtm_shared_shape__0", mockVectorLayer);
        return mgr;
    }

    /** Params for a circle shape: {type: 'circle', center: [lng, lat], radiusKm}. */
    private ReadableMap circleParams(double lng, double lat, double radiusKm) {
        ReadableMap shapeMap = mock(ReadableMap.class);
        when(shapeMap.getString("type")).thenReturn("circle");
        when(shapeMap.hasKey("center")).thenReturn(true);
        when(shapeMap.hasKey("radiusKm")).thenReturn(true);
        ReadableArray center = mock(ReadableArray.class);
        when(center.getDouble(0)).thenReturn(lng);
        when(center.getDouble(1)).thenReturn(lat);
        when(shapeMap.getArray("center")).thenReturn(center);
        when(shapeMap.getDouble("radiusKm")).thenReturn(radiusKm);

        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("shape")).thenReturn(true);
        when(params.getMap("shape")).thenReturn(shapeMap);
        return params;
    }

    // ------------------------------------------------------------------
    // Batch create / remove
    // ------------------------------------------------------------------

    @Test
    public void createShapes_createsEntriesInOneBatch() throws Exception {
        ShapeLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap paramsA = circleParams(13.4, 52.5, 5d);
        ReadableMap paramsB = circleParams(13.5, 52.6, 6d);
        ReadableArray shapes = mock(ReadableArray.class);
        when(shapes.size()).thenReturn(2);
        when(shapes.getMap(0)).thenReturn(paramsA);
        when(shapes.getMap(1)).thenReturn(paramsB);

        mgr.createShapes(shapes, mf, cr, rctx);

        assertEquals("Two shape entries must be registered",
                2, mgr.getEntries().size());
        assertEquals("One drawable per shape", 2, addedDrawables.size());
    }

    @Test
    public void createShapes_capturesPerItemErrors() throws Exception {
        ShapeLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap invalid = mock(ReadableMap.class);
        when(invalid.hasKey("shape")).thenReturn(false);

        ReadableMap paramsA = circleParams(13.4, 52.5, 5d);
        ReadableArray shapes = mock(ReadableArray.class);
        when(shapes.size()).thenReturn(2);
        when(shapes.getMap(0)).thenReturn(paramsA);
        when(shapes.getMap(1)).thenReturn(invalid);

        mgr.createShapes(shapes, mf, cr, rctx);

        assertEquals("Only the valid shape must be registered",
                1, mgr.getEntries().size());
        assertEquals(1, addedDrawables.size());
    }

    @Test
    public void removeShapes_removesEntriesInOneBatch() throws Exception {
        ShapeLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap paramsA = circleParams(13.4, 52.5, 5d);
        ReadableMap paramsB = circleParams(13.5, 52.6, 6d);
        ReadableArray shapes = mock(ReadableArray.class);
        when(shapes.size()).thenReturn(2);
        when(shapes.getMap(0)).thenReturn(paramsA);
        when(shapes.getMap(1)).thenReturn(paramsB);
        mgr.createShapes(shapes, mf, cr, rctx);
        assertEquals(2, mgr.getEntries().size());

        // Build uuids from the created entries.
        List<String> uuids = new ArrayList<>(mgr.getEntries().keySet());
        ReadableArray uuidsArray = mock(ReadableArray.class);
        when(uuidsArray.size()).thenReturn(2);
        when(uuidsArray.getString(0)).thenReturn(uuids.get(0));
        when(uuidsArray.getString(1)).thenReturn(uuids.get(1));

        mgr.removeShapes(uuidsArray);

        assertEquals("All entries must be removed", 0, mgr.getEntries().size());
        assertTrue("All drawables must be removed", addedDrawables.isEmpty());
    }

    // ------------------------------------------------------------------
    // applyEntryPriorities
    // ------------------------------------------------------------------

    @Test
    public void applyEntryPriorities_updatesDrawablePriority() throws Exception {
        ShapeLayerManager mgr = createManagerWithFakeLayer();
        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        ReadableMap paramsA = circleParams(13.4, 52.5, 5d);
        ReadableArray shapes = mock(ReadableArray.class);
        when(shapes.size()).thenReturn(1);
        when(shapes.getMap(0)).thenReturn(paramsA);
        mgr.createShapes(shapes, mf, cr, rctx);

        String uuid = mgr.getEntries().keySet().iterator().next();

        ReadableMap assignment = mock(ReadableMap.class);
        when(assignment.getString("uuid")).thenReturn(uuid);
        when(assignment.getInt("priority")).thenReturn(123);
        ReadableArray assignments = mock(ReadableArray.class);
        when(assignments.size()).thenReturn(1);
        when(assignments.getMap(0)).thenReturn(assignment);

        mgr.applyEntryPriorities("__vtm_shared_shape__0", assignments);

        ShapeLayerManager.ShapeEntry entry = mgr.getEntries().get(uuid);
        assertEquals(123, entry.positionIndex);
        assertEquals(123, entry.drawable.getPriority());
    }
}
