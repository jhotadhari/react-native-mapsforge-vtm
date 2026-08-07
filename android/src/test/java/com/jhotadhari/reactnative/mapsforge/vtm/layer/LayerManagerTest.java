package com.jhotadhari.reactnative.mapsforge.vtm.layer;

import android.os.Looper;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.jhotadhari.reactnative.mapsforge.vtm.MapMutationQueue;
import com.jhotadhari.reactnative.mapsforge.vtm.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.android.MapView;
import org.oscim.core.GeoPoint;
import org.oscim.layers.Layer;
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Robolectric unit tests for {@link LayerManager} — the abstract base class
 * for managers that collapse many JS components into a single shared vtm Layer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LayerManagerTest {

    private static int handleCounter = 200000;

    private int handle;
    private MapView mockMapView;
    private Map mockMap;
    private List<Layer> backingList;

    // ------------------------------------------------------------------
    // Minimal concrete LayerManager subclass for testing
    // ------------------------------------------------------------------

    private static class TestLayerManager extends LayerManager<String> {
        final Layer fakeSharedLayer;

        TestLayerManager(int nh, MapView mv, String name, int basePos) {
            super(nh, mv, name, basePos);
            fakeSharedLayer = mock(Layer.class);
        }

        @Override
        protected Layer createSharedLayer() { return fakeSharedLayer; }

        @Override
        protected CreateResult<String> createEntry(String entryUuid, ReadableMap params,
                com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment mapFragment,
                android.content.ContentResolver cr,
                com.facebook.react.bridge.ReactApplicationContext ctx) {
            return new CreateResult<>("entry-" + entryUuid, null);
        }

        @Override
        protected void removeEntryFromLayer(String entry) {}

        @Override
        protected UpdateResult updateEntry(String entry, ReadableMap params,
                com.jhotadhari.reactnative.mapsforge.vtm.views.MapFragment mf,
                android.content.ContentResolver cr) {
            return new UpdateResult(null);
        }

        @Override
        protected WritableMap hitTestEntry(String entry, float x, float y,
                GeoPoint pt, float dist) {
            return null;
        }
    }

    @Before
    public void setUp() {
        handle = handleCounter++;

        mockMap = mock(Map.class);
        mockMapView = mock(MapView.class);
        when(mockMapView.map()).thenReturn(mockMap);

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
    }

    @After
    public void tearDown() {
        LayerManager.removeAll(handle);
        MapMutationQueue.remove(handle);
    }

    private void flushLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    // ------------------------------------------------------------------
    // Lifecycle: get, getInstance, remove
    // ------------------------------------------------------------------

    @Test
    public void getCreatesNewInstance() {
        String name = "test" + handle;
        assertNull(LayerManager.getInstance(handle, name));
        assertNotNull(LayerManager.get(handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0)));
        assertNotNull(LayerManager.getInstance(handle, name));
    }

    @Test
    public void getReturnsSameInstance() {
        String name = "test" + handle;
        LayerManager<?> a = LayerManager.get(handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        LayerManager<?> b = LayerManager.get(handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        assertEquals(a, b);
    }

    @Test
    public void instanceKeyIsolation() {
        String nameA = "testA" + handle;
        String nameB = "testB" + handle;
        LayerManager<?> a = LayerManager.get(handle, mockMapView, nameA,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        LayerManager<?> b = LayerManager.get(handle, mockMapView, nameB,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        assertNotNull(a);
        assertNotNull(b);
        // Same handle, different names → different instances
        assertTrue("Different names must yield different managers", a != b);
    }

    @Test
    public void removeClearsSingleton() {
        String name = "test" + handle;
        LayerManager.get(handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        assertNotNull(LayerManager.getInstance(handle, name));
        LayerManager.remove(handle, name);
        assertNull(LayerManager.getInstance(handle, name));
    }

    @Test
    public void removeAllClearsAllForHandle() {
        LayerManager.get(handle, mockMapView, "a" + handle,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        LayerManager.get(handle, mockMapView, "b" + handle,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));
        assertNotNull(LayerManager.getInstance(handle, "a" + handle));
        assertNotNull(LayerManager.getInstance(handle, "b" + handle));
        LayerManager.removeAll(handle);
        assertNull(LayerManager.getInstance(handle, "a" + handle));
        assertNull(LayerManager.getInstance(handle, "b" + handle));
    }

    // ------------------------------------------------------------------
    // Event callback
    // ------------------------------------------------------------------

    @Test
    public void setEventCallback_emitCallsIt() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        LayerManager.EventEmitterCallback cb = mock(LayerManager.EventEmitterCallback.class);
        mgr.setEventCallback(cb);

        WritableMap payload = mock(WritableMap.class);
        when(payload.getString("key")).thenReturn("value");
        mgr.emit("testEvent", payload);

        verify(cb, times(1)).emit("testEvent", payload);
    }

    @Test
    public void setEventCallback_nullSuppressesEmit() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        mgr.setEventCallback(null);

        // Must not throw NPE.
        WritableMap payload = mock(WritableMap.class);
        when(payload.getString("key")).thenReturn("value");
        mgr.emit("testEvent", payload);
    }

    // ------------------------------------------------------------------
    // resolvePositionIndex
    // ------------------------------------------------------------------

    @Test
    public void resolvePositionIndex_present() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("positionIndex")).thenReturn(true);
        when(params.isNull("positionIndex")).thenReturn(false);
        when(params.getInt("positionIndex")).thenReturn(5);

        assertEquals(5, mgr.resolvePositionIndex(params));
    }

    @Test
    public void resolvePositionIndex_absent() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        ReadableMap params = mock(ReadableMap.class);
        when(params.hasKey("positionIndex")).thenReturn(false);

        assertEquals(Integer.MAX_VALUE, mgr.resolvePositionIndex(params));
    }

    // ------------------------------------------------------------------
    // Shared layer uuid
    // ------------------------------------------------------------------

    @Test
    public void sharedLayerUuidFormat() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        assertEquals("__vtm_shared_" + name + "__", mgr.getSharedLayerUuid());
    }

    // ------------------------------------------------------------------
    // Shared layer getter and uuid
    // ------------------------------------------------------------------

    @Test
    public void sharedLayer_returnsNullBeforeEnsureSharedLayer() {
        String name = "test" + handle;
        TestLayerManager mgr = (TestLayerManager) LayerManager.get(
                handle, mockMapView, name,
                (nh, mv, n) -> new TestLayerManager(nh, mv, n, 0));

        // sharedLayer is null until ensureSharedLayer() is called (via create(), etc.)
        assertNull("getSharedLayer must be null before ensureSharedLayer",
                mgr.getSharedLayer(mgr.getSharedLayerUuid()));
    }
}
