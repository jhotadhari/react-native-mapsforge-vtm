package com.jhotadhari.reactnative.mapsforge.vtm;

import android.content.ContentResolver;
import android.os.Looper;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.ItemizedLayer;
import com.jhotadhari.reactnative.mapsforge.vtm.layer.LayerManager;
import com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment;

import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.android.MapView;
import org.oscim.core.Box;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.layers.Layer;
import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.oscim.map.ViewController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;

/**
 * Robolectric unit tests for {@link MarkerLayerManager}.
 *
 * <p>Uses a mock {@link ItemizedLayer} injected via reflection to avoid
 * GL dependencies in ItemizedLayer's constructor.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MarkerLayerManagerTest {

    private static MockedStatic<Arguments> argumentsMock;

    @BeforeClass
    public static void setUpClass() {
        // Mock Arguments.createArray/createMap to avoid JNI-dependent
        // WritableNativeArray/WritableNativeMap construction in tests.
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

    private static int handleCounter = 300000;

    private int handle;
    private MapView mockMapView;
    private Map mockMap;
    private ViewController mockViewport;
    private List<Layer> backingList;
    private ItemizedLayer mockItemizedLayer;
    private List<MarkerInterface> itemList;

    @Before
    public void setUp() throws Exception {
        handle = handleCounter++;

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

        // Create a fake ItemizedLayer backed by a real ArrayList.
        itemList = new ArrayList<>();
        MarkerSymbol sym = mock(MarkerSymbol.class);
        mockItemizedLayer = mock(ItemizedLayer.class);
        when(mockItemizedLayer.getItemList()).thenReturn(itemList);
        when(mockItemizedLayer.getDefaultMarker()).thenReturn(sym);
        // Wire removeItem to actually remove from the real itemList.
        doAnswer(inv -> {
            itemList.remove(inv.getArgument(0));
            return null;
        }).when(mockItemizedLayer).removeItem(any(MarkerInterface.class));
        doNothing().when(mockItemizedLayer).populate();
        doAnswer(inv -> itemList.remove(inv.getArgument(0)))
                .when(mockItemizedLayer).removeItem(any(MarkerItem.class));
    }

    @After
    public void tearDown() {
        LayerManager.removeAll(handle);
        MapMutationQueue.remove(handle);
    }

    private void flushLooper() {
        // Only idle if the looper is in a state where it can be idled.
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        } catch (Exception ignored) {
            // Looper may not be in a mood to idle.
        }
    }

    /**
     * Inject the mock ItemizedLayer as the shared layer in MarkerLayerManager
     * and register it in MapMutationQueue.knownLayers.
     */
    private MarkerLayerManager createManagerWithFakeLayer() throws Exception {
        MarkerLayerManager mgr = MarkerLayerManager.get(handle, mockMapView);

        // Inject sharedLayer via reflection.
        Field f = LayerManager.class.getDeclaredField("sharedLayer");
        f.setAccessible(true);
        f.set(mgr, mockItemizedLayer);

        // Register in MapMutationQueue knownLayers.
        MapMutationQueue queue = MapMutationQueue.get(handle, mockMapView);
        queue.getKnownLayers().put(mgr.getSharedLayerUuid(), mockItemizedLayer);

        return mgr;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    public void getCreatesNewInstance() {
        assertNull("getInstance must be null before get",
                MarkerLayerManager.getInstance(handle));
        MarkerLayerManager mgr = MarkerLayerManager.get(handle, mockMapView);
        assertNotNull("get must return non-null manager", mgr);
        assertEquals("getInstance must return same manager after get",
                mgr, MarkerLayerManager.getInstance(handle));
    }

    @Test
    public void getReturnsSameInstance() {
        MarkerLayerManager a = MarkerLayerManager.get(handle, mockMapView);
        MarkerLayerManager b = MarkerLayerManager.get(handle, mockMapView);
        assertEquals(a, b);
    }

    // ------------------------------------------------------------------
    // Group management
    // ------------------------------------------------------------------

    @Test
    public void createGroup_registersGroup() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("positionIndex")).thenReturn(false);

        String groupUuid = mgr.createGroup(null, params);
        assertNotNull("Group uuid must not be null", groupUuid);

        // Groups accessible via reflection.
        Field groupsField = MarkerLayerManager.class.getDeclaredField("groups");
        groupsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> groups =
                (java.util.Map<String, Object>) groupsField.get(mgr);
        assertTrue("Groups must contain the created group",
                groups.containsKey(groupUuid));
    }

    @Test
    public void removeGroup_removesAllMarkers() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("positionIndex")).thenReturn(false);

        String groupUuid = mgr.createGroup(null, params);

        // Add a marker to this group via reflection.
        // Use allMarkers field (separate from entries in LayerManager).
        Field allMarkersField = MarkerLayerManager.class.getDeclaredField("allMarkers");
        allMarkersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> allMarkers =
                (java.util.Map<String, Object>) allMarkersField.get(mgr);

        MarkerItem mi = new MarkerItem(UUID.randomUUID(), "t", "d",
                new GeoPoint(52.5, 13.4));
        MarkerLayerManager.MarkerEntry entry =
                new MarkerLayerManager.MarkerEntry("marker-uuid", groupUuid, mi, 0);
        allMarkers.put("marker-uuid", entry);
        mgr.getEntries().put("marker-uuid", entry);

        // Also add to group's member set.
        Field groupsField = MarkerLayerManager.class.getDeclaredField("groups");
        groupsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> groups =
                (java.util.Map<String, Object>) groupsField.get(mgr);
        Object group = groups.get(groupUuid);
        Field memberField = group.getClass().getDeclaredField("memberMarkerUuids");
        memberField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> members = (java.util.Set<String>) memberField.get(group);
        members.add("marker-uuid");

        // Add to itemList.
        itemList.add(mi);

        mgr.removeGroup(groupUuid);

        assertTrue("allMarkers must be empty after removeGroup",
                allMarkers.isEmpty());
        assertTrue("groups must not contain the removed group",
                !groups.containsKey(groupUuid));
        assertTrue("itemList must be empty after removeGroup",
                itemList.isEmpty());
    }

    // ------------------------------------------------------------------
    // setMarkerSymbol
    // ------------------------------------------------------------------

    @Test
    public void setMarkerSymbol_updatesMarkerItem() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        MarkerItem mi = new MarkerItem(UUID.randomUUID(), "t", "d",
                new GeoPoint(52.5, 13.4));
        MarkerLayerManager.MarkerEntry entry =
                new MarkerLayerManager.MarkerEntry("entry-uuid",
                        MarkerLayerManager.ROOT_GROUP_UUID, mi, 0);

        Field allMarkersField = MarkerLayerManager.class.getDeclaredField("allMarkers");
        allMarkersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, MarkerLayerManager.MarkerEntry> allMarkers =
                (java.util.Map<String, MarkerLayerManager.MarkerEntry>)
                        allMarkersField.get(mgr);
        allMarkers.put("entry-uuid", entry);

        MarkerSymbol newSymbol = mock(MarkerSymbol.class);
        mgr.setMarkerSymbol("entry-uuid", newSymbol);

        assertEquals("MarkerItem must have the new symbol",
                newSymbol, mi.getMarker());
    }

    // ------------------------------------------------------------------
    // createMarkers batch
    // ------------------------------------------------------------------

    @Ignore("Requires native libs — test on device/emulator")
    @Test
    public void createMarkers_populatesEntries() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        // Build mocked marker array, avoiding Arguments (JNI-backed, unavailable in Robolectric).
        ReadableArray markersArray = mock(ReadableArray.class);
        when(markersArray.size()).thenReturn(3);
        for (int i = 0; i < 3; i++) {
            ReadableMap m = mock(ReadableMap.class);
            when(m.hasKey("position")).thenReturn(true);
            when(m.isNull("position")).thenReturn(false);

            ReadableArray pos = mock(ReadableArray.class);
            when(pos.size()).thenReturn(2);
            when(pos.getDouble(0)).thenReturn(13.405 + i * 0.01);
            when(pos.getDouble(1)).thenReturn(52.520 + i * 0.01);
            when(m.getArray("position")).thenReturn(pos);

            when(m.hasKey("positionIndex")).thenReturn(true);
            when(m.getInt("positionIndex")).thenReturn(i);
            when(m.hasKey("title")).thenReturn(true);
            when(m.getString("title")).thenReturn("Marker " + i);
            when(m.hasKey("description")).thenReturn(true);
            when(m.getString("description")).thenReturn("Desc " + i);
            when(m.hasKey("markerLayerUuid")).thenReturn(false);

            when(markersArray.getMap(i)).thenReturn(m);
        }

        WritableMap result = mgr.createMarkers(markersArray, mf, cr, rctx, null);
        flushLooper();

        assertEquals("entries must have 3 markers",
                3, mgr.getEntries().size());

        ReadableArray results = result.getArray("results");
        assertEquals("results array must have 3 elements", 3, results.size());
        // First marker UUID must be present and valid.
        assertNotNull(results.getMap(0).getString("uuid"));
        assertTrue(results.getMap(0).getString("uuid").length() > 0);
    }

    @Ignore("Requires native libs — test on device/emulator")
    @Test
    public void createMarkers_handlesErrorGracefully() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        ContentResolver cr = mock(ContentResolver.class);
        ReactApplicationContext rctx = mock(ReactApplicationContext.class);
        MapFragment mf = mock(MapFragment.class);

        // Build mocked marker array with 1 invalid marker (no position).
        ReadableArray markersArray = mock(ReadableArray.class);
        when(markersArray.size()).thenReturn(3);

        // Valid marker 1.
        ReadableMap m1 = mock(ReadableMap.class);
        when(m1.hasKey("position")).thenReturn(true);
        when(m1.isNull("position")).thenReturn(false);
        ReadableArray pos1 = mock(ReadableArray.class);
        when(pos1.size()).thenReturn(2);
        when(pos1.getDouble(0)).thenReturn(13.405);
        when(pos1.getDouble(1)).thenReturn(52.520);
        when(m1.getArray("position")).thenReturn(pos1);
        when(m1.hasKey("positionIndex")).thenReturn(true);
        when(m1.getInt("positionIndex")).thenReturn(0);
        when(m1.hasKey("title")).thenReturn(false);
        when(m1.hasKey("description")).thenReturn(false);
        when(m1.hasKey("markerLayerUuid")).thenReturn(false);
        when(markersArray.getMap(0)).thenReturn(m1);

        // Invalid marker 2 — no "position" key.
        ReadableMap m2 = mock(ReadableMap.class);
        when(m2.hasKey("position")).thenReturn(false);
        when(m2.hasKey("positionIndex")).thenReturn(true);
        when(m2.getInt("positionIndex")).thenReturn(1);
        when(m2.hasKey("title")).thenReturn(false);
        when(m2.hasKey("description")).thenReturn(false);
        when(m2.hasKey("markerLayerUuid")).thenReturn(false);
        when(markersArray.getMap(1)).thenReturn(m2);

        // Valid marker 3.
        ReadableMap m3 = mock(ReadableMap.class);
        when(m3.hasKey("position")).thenReturn(true);
        when(m3.isNull("position")).thenReturn(false);
        ReadableArray pos3 = mock(ReadableArray.class);
        when(pos3.size()).thenReturn(2);
        when(pos3.getDouble(0)).thenReturn(13.425);
        when(pos3.getDouble(1)).thenReturn(52.540);
        when(m3.getArray("position")).thenReturn(pos3);
        when(m3.hasKey("positionIndex")).thenReturn(true);
        when(m3.getInt("positionIndex")).thenReturn(2);
        when(m3.hasKey("title")).thenReturn(false);
        when(m3.hasKey("description")).thenReturn(false);
        when(m3.hasKey("markerLayerUuid")).thenReturn(false);
        when(markersArray.getMap(2)).thenReturn(m3);

        WritableMap result = mgr.createMarkers(markersArray, mf, cr, rctx, null);
        flushLooper();

        assertEquals("Only 2 valid entries must be registered",
                2, mgr.getEntries().size());

        ReadableArray results = result.getArray("results");
        assertEquals(3, results.size());

        // Second result should have an error.
        assertNotNull("Invalid marker must have an error string",
                results.getMap(1).getString("error"));
        assertTrue(results.getMap(1).getString("error").length() > 0);
    }

    // ------------------------------------------------------------------
    // triggerAllMarkers
    // ------------------------------------------------------------------

    @Test
    public void triggerAllMarkers_outsideViewport_skipped() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        // Set event callback spy.
        LayerManager.EventEmitterCallback cb = mock(LayerManager.EventEmitterCallback.class);
        mgr.setEventCallback(cb);

        // Add a marker far outside viewport. Viewport is world-wide (-180..180),
        // so we can't place a marker completely outside. Let's narrow the viewport.
        Box narrowBox = new Box(10, 10, 20, 20); // small bbox
        when(mockViewport.getBBox(any(), anyInt())).thenReturn(narrowBox);
        // fromScreenPoint needs to return a geo-point inside this narrow box so
        // the event point is inside.
        GeoPoint insideNarrow = new GeoPoint(15.0, 15.0);
        when(mockViewport.fromScreenPoint(anyFloat(), anyFloat())).thenReturn(insideNarrow);

        // Marker at Berlin — outside narrow box.
        MarkerItem farItem = new MarkerItem(UUID.randomUUID(), "far", "desc",
                new GeoPoint(52.520, 13.405));
        itemList.add(farItem);

        Field allMarkersField = MarkerLayerManager.class.getDeclaredField("allMarkers");
        allMarkersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> allMarkers =
                (java.util.Map<String, Object>) allMarkersField.get(mgr);
        MarkerLayerManager.MarkerEntry entry = new MarkerLayerManager.MarkerEntry(
                "far-uuid", MarkerLayerManager.ROOT_GROUP_UUID, farItem, 0);
        allMarkers.put("far-uuid", entry);

        mgr.triggerAllMarkers(100, 100, "all");

        // No event should be emitted because the marker is outside the viewport bbox.
        verify(cb, never()).emit(anyString(), any(WritableMap.class));
    }

    @Ignore("Requires native libs — test on device/emulator")
    @Test
    public void triggerAllMarkers_insideViewport_emitted() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        LayerManager.EventEmitterCallback cb = mock(LayerManager.EventEmitterCallback.class);
        mgr.setEventCallback(cb);

        // Viewport covers the marker.
        Box bigBox = new Box(-180, -90, 180, 90);
        when(mockViewport.getBBox(any(), anyInt())).thenReturn(bigBox);

        // Return different GeoPoints for x and x+30 so geoThreshold > 0.
        GeoPoint eventPoint = new GeoPoint(52.525, 13.410);
        GeoPoint thresholdPoint = new GeoPoint(52.525, 13.411); // 0.001 deg lon offset
        when(mockViewport.fromScreenPoint(eq(100f), eq(100f))).thenReturn(eventPoint);
        when(mockViewport.fromScreenPoint(eq(130f), eq(100f))).thenReturn(thresholdPoint);

        // Marker at exactly the event point — distance = 0, which is < geoThreshold (0.001).
        MarkerItem mi = new MarkerItem(UUID.randomUUID(), "near", "desc",
                eventPoint);
        itemList.add(mi);

        Field allMarkersField = MarkerLayerManager.class.getDeclaredField("allMarkers");
        allMarkersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> allMarkers =
                (java.util.Map<String, Object>) allMarkersField.get(mgr);
        MarkerLayerManager.MarkerEntry entry = new MarkerLayerManager.MarkerEntry(
                "near-uuid", MarkerLayerManager.ROOT_GROUP_UUID, mi, 0);
        allMarkers.put("near-uuid", entry);

        mgr.triggerAllMarkers(100, 100, "all");

        verify(cb, times(1)).emit(eq("onMarkerEvent"), any(WritableMap.class));
    }

    // ------------------------------------------------------------------
    // removeMarkers batch
    // ------------------------------------------------------------------

    @Ignore("Requires native libs — test on device/emulator")
    @Test
    public void removeMarkers_removesFromEntries() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();

        // Setup: add two markers to allMarkers, entries, itemList.
        MarkerItem mi1 = new MarkerItem(UUID.randomUUID(), "m1", "d",
                new GeoPoint(52.5, 13.4));
        MarkerItem mi2 = new MarkerItem(UUID.randomUUID(), "m2", "d",
                new GeoPoint(52.6, 13.5));
        itemList.add(mi1);
        itemList.add(mi2);

        Field allMarkersField = MarkerLayerManager.class.getDeclaredField("allMarkers");
        allMarkersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, MarkerLayerManager.MarkerEntry> allMarkers =
                (java.util.Map<String, MarkerLayerManager.MarkerEntry>)
                        allMarkersField.get(mgr);
        allMarkers.put("uuid-1", new MarkerLayerManager.MarkerEntry(
                "uuid-1", MarkerLayerManager.ROOT_GROUP_UUID, mi1, 0));
        allMarkers.put("uuid-2", new MarkerLayerManager.MarkerEntry(
                "uuid-2", MarkerLayerManager.ROOT_GROUP_UUID, mi2, 1));
        mgr.getEntries().put("uuid-1", allMarkers.get("uuid-1"));
        mgr.getEntries().put("uuid-2", allMarkers.get("uuid-2"));

        assertEquals(2, mgr.getEntries().size());

        // String markerUuids arg — mock array containing "uuid-1".
        ReadableArray uuidsToRemove = mock(ReadableArray.class);
        when(uuidsToRemove.size()).thenReturn(1);
        when(uuidsToRemove.getString(0)).thenReturn("uuid-1");

        mgr.removeMarkers(uuidsToRemove);

        assertEquals("entries must have 1 after removing 1 of 2",
                1, mgr.getEntries().size());
        assertNull("uuid-1 must be gone from entries",
                mgr.getEntries().get("uuid-1"));
        assertNotNull("uuid-2 must remain in entries",
                mgr.getEntries().get("uuid-2"));
    }

    // ------------------------------------------------------------------
    // getEntries
    // ------------------------------------------------------------------

    @Test
    public void getEntries_initiallyEmpty() throws Exception {
        MarkerLayerManager mgr = createManagerWithFakeLayer();
        assertTrue("entries must be empty initially",
                mgr.getEntries().isEmpty());
    }
}
