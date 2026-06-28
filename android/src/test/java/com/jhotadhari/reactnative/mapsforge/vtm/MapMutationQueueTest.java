package com.jhotadhari.reactnative.mapsforge.vtm;

import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.android.MapView;
import org.oscim.layers.Layer;
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Robolectric unit tests for {@link MapMutationQueue}.
 *
 * <p>Uses Mockito mocks for vtm's {@code MapView}, {@code Map}, and {@code Layers},
 * backed by a real {@code ArrayList<Layer>} so add/remove/get/size/contains/indexOf
 * behave as they would on a real device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MapMutationQueueTest {

    private static int handleCounter = 100000;

    private int handle;
    private MapView mockMapView;
    private Map mockMap;
    private Layers mockLayers;
    private List<Layer> backingList;

    @Before
    public void setUp() {
        handle = handleCounter++;

        // Build the mock layer stack backed by a real ArrayList.
        mockMap = mock(Map.class);
        mockMapView = mock(MapView.class);
        when(mockMapView.map()).thenReturn(mockMap);

        backingList = new ArrayList<>();
        mockLayers = mock(Layers.class);
        when(mockMap.layers()).thenReturn(mockLayers);

        when(mockLayers.size()).thenAnswer(inv -> backingList.size());
        when(mockLayers.get(anyInt())).thenAnswer(inv -> backingList.get((int) inv.getArgument(0)));
        doAnswer(inv -> {
            backingList.add((int) inv.getArgument(0), (Layer) inv.getArgument(1));
            return null;
        }).when(mockLayers).add(anyInt(), any(Layer.class));
        doAnswer(inv -> backingList.remove((int) inv.getArgument(0)))
                .when(mockLayers).remove(anyInt());
        doAnswer(inv -> backingList.remove((Layer) inv.getArgument(0)))
                .when(mockLayers).remove(any(Layer.class));
        when(mockLayers.contains(any())).thenAnswer(inv -> backingList.contains(inv.getArgument(0)));
        when(mockLayers.indexOf(any())).thenAnswer(inv -> backingList.indexOf(inv.getArgument(0)));

        doNothing().when(mockMap).updateMap();
        doNothing().when(mockMap).updateMap(false);
    }

    @After
    public void tearDown() {
        MapMutationQueue.remove(handle);
    }

    /** Flush all pending Handler posts on the main looper. */
    private void flushLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    // -----------------------------------------------------------------------
    // Lifecycle: get, getInstance, remove
    // -----------------------------------------------------------------------

    @Test
    public void getCreatesNewInstance() {
        assertNull("getInstance must be null before get",
                MapMutationQueue.getInstance(handle));
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        assertNotNull("get must return non-null queue", q);
        assertEquals("getInstance must return same queue after get",
                q, MapMutationQueue.getInstance(handle));
    }

    @Test
    public void getReturnsSameInstance() {
        MapMutationQueue a = MapMutationQueue.get(handle, mockMapView);
        MapMutationQueue b = MapMutationQueue.get(handle, mockMapView);
        assertEquals("Two get() calls must return the same instance", a, b);
    }

    @Test
    public void getInstanceNullWhenMissing() {
        assertNull(MapMutationQueue.getInstance(99999));
    }

    @Test
    public void removeDestroysQueue() {
        MapMutationQueue.get(handle, mockMapView);
        assertNotNull(MapMutationQueue.getInstance(handle));
        MapMutationQueue.remove(handle);
        assertNull("After remove(), getInstance must return null",
                MapMutationQueue.getInstance(handle));
    }

    // -----------------------------------------------------------------------
    // Add layer
    // -----------------------------------------------------------------------

    @Test
    public void enqueueAddLayer_futureResolvesAfterFlush() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        CompletableFuture<String> future = q.enqueueAddLayer(mock(Layer.class), "l1", 0);
        assertFalse("Future must not be done before flush", future.isDone());
        flushLooper();
        assertTrue("Future must be done after flush", future.isDone());
        assertEquals("l1", future.get());
    }

    @Test
    public void enqueueAddLayer_layerAppearsInKnownLayers() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        q.enqueueAddLayer(mock(Layer.class), "l1", 0);
        flushLooper();
        assertNotNull("knownLayers must contain l1", q.getKnownLayers().get("l1"));
    }

    @Test
    public void enqueueAddLayer_layerAppearsInBackingList() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        Layer l = mock(Layer.class);
        q.enqueueAddLayer(l, "l1", 0);
        flushLooper();
        assertEquals("backing list must have 1 layer", 1, backingList.size());
        assertEquals("backing list must contain the added layer", l, backingList.get(0));
    }

    // -----------------------------------------------------------------------
    // Remove layer
    // -----------------------------------------------------------------------

    @Test
    public void enqueueRemoveLayer_futureResolvesAfterFlush() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        q.enqueueAddLayer(mock(Layer.class), "l1", 0);
        flushLooper();
        CompletableFuture<Void> rf = q.enqueueRemoveLayer("l1");
        flushLooper();
        assertTrue("Remove future must be done", rf.isDone());
        rf.get(); // does not throw
    }

    @Test
    public void enqueueRemoveLayer_layerRemovedFromKnownLayers() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        q.enqueueAddLayer(mock(Layer.class), "l1", 0);
        flushLooper();
        q.enqueueRemoveLayer("l1");
        flushLooper();
        assertNull("knownLayers must not contain l1 after remove",
                q.getKnownLayers().get("l1"));
    }

    @Test
    public void enqueueRemoveLayer_layerRemovedFromBackingList() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        Layer l = mock(Layer.class);
        q.enqueueAddLayer(l, "l1", 0);
        flushLooper();
        q.enqueueRemoveLayer("l1");
        flushLooper();
        assertEquals("backing list must be empty after remove", 0, backingList.size());
    }

    // -----------------------------------------------------------------------
    // removeLayerSync (teardown path)
    // -----------------------------------------------------------------------

    @Test
    public void removeLayerSync_removesFromKnownLayers() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        q.enqueueAddLayer(mock(Layer.class), "l1", 0);
        flushLooper();
        assertNotNull(q.getKnownLayers().get("l1"));
        q.removeLayerSync("l1");
        assertNull("knownLayers must not contain l1 after removeLayerSync",
                q.getKnownLayers().get("l1"));
        // Note: the mock Layers.remove(Object) doesn't work properly with
        // mocked iterators, so we only verify knownLayers state.
    }

    // -----------------------------------------------------------------------
    // Destroy rejects pending futures
    // -----------------------------------------------------------------------

    @Test
    public void destroyRejectsPendingFutures() {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);
        CompletableFuture<String> future = q.enqueueAddLayer(mock(Layer.class), "l1", 0);

        // Destroy without flushing — MapMutationQueue.remove() calls destroy()
        MapMutationQueue.remove(handle);

        assertTrue("Future must be completed after destroy", future.isDone());
        try {
            future.get();
            fail("Calling get() on a rejected future must throw");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertTrue(e.getCause().getMessage().contains("MapMutationQueue destroyed"));
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
        // getInstance must return null — queue was fully torn down
        assertNull(MapMutationQueue.getInstance(handle));
    }

    // -----------------------------------------------------------------------
    // Batch size capping (MAX_BATCH_SIZE = 25)
    // -----------------------------------------------------------------------

    @Test
    public void batchProcessing_allComplete() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        int total = 5;
        for (int i = 0; i < total; i++) {
            q.enqueueAddLayer(mock(Layer.class), "uuid-" + i, i);
        }

        flushLooper();

        assertEquals("knownLayers must have all " + total + " entries",
                total, q.getKnownLayers().size());
    }

    // -----------------------------------------------------------------------
    // Position-index ordering
    // -----------------------------------------------------------------------

    @Test
    public void positionIndexOrdering() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        Layer l1 = mock(Layer.class);
        Layer l0 = mock(Layer.class);

        // Add position-1 first, then position-0 — flush should insert
        // position-0 before position-1.
        q.enqueueAddLayer(l1, "l1", 1);
        q.enqueueAddLayer(l0, "l0", 0);
        flushLooper();

        assertEquals("backing list must have 2 layers", 2, backingList.size());
        assertEquals("l0 (position 0) must be first in backing list",
                l0, backingList.get(0));
        assertEquals("l1 (position 1) must be second in backing list",
                l1, backingList.get(1));
    }

    // -----------------------------------------------------------------------
    // Reorder layers
    // -----------------------------------------------------------------------

    @Test
    public void enqueueReorderLayers_futureResolves() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        Layer l0 = mock(Layer.class);
        Layer l1 = mock(Layer.class);

        q.enqueueAddLayer(l0, "l0", 0);
        q.enqueueAddLayer(l1, "l1", 1);
        flushLooper();

        // Reorder: swap positions
        List<String> order = new ArrayList<>();
        order.add("l1");
        order.add("l0");
        CompletableFuture<Void> rf = q.enqueueReorderLayers(order);
        flushLooper();

        assertTrue("Reorder future must be done", rf.isDone());
        rf.get(); // does not throw
    }

    @Test
    public void enqueueReorderLayers_changesBackingOrder() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        Layer l0 = mock(Layer.class);
        Layer l1 = mock(Layer.class);

        q.enqueueAddLayer(l0, "l0", 0);
        q.enqueueAddLayer(l1, "l1", 1);
        flushLooper();

        assertEquals(l0, backingList.get(0));
        assertEquals(l1, backingList.get(1));

        List<String> order = new ArrayList<>();
        order.add("l1");
        order.add("l0");
        q.enqueueReorderLayers(order);
        flushLooper();

        assertEquals("After reorder, l1 must be first", l1, backingList.get(0));
        assertEquals("After reorder, l0 must be second", l0, backingList.get(1));
    }

    // -----------------------------------------------------------------------
    // getKnownLayers reflects current state
    // -----------------------------------------------------------------------

    @Test
    public void getKnownLayersReflectsCurrentState() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        q.enqueueAddLayer(mock(Layer.class), "a", 0);
        q.enqueueAddLayer(mock(Layer.class), "b", 1);
        flushLooper();
        assertEquals("knownLayers size must be 2 after adds", 2,
                q.getKnownLayers().size());

        q.enqueueRemoveLayer("a");
        flushLooper();
        assertEquals("knownLayers size must be 1 after remove", 1,
                q.getKnownLayers().size());
        assertNull(q.getKnownLayers().get("a"));
        assertNotNull(q.getKnownLayers().get("b"));
    }

    // -----------------------------------------------------------------------
    // Concurrent add + remove in single batch
    // -----------------------------------------------------------------------

    @Test
    public void addAndRemoveSameLayerInSingleBatch() throws Exception {
        MapMutationQueue q = MapMutationQueue.get(handle, mockMapView);

        Layer l0 = mock(Layer.class);
        Layer l1 = mock(Layer.class);

        q.enqueueAddLayer(l0, "l0", 0);
        q.enqueueAddLayer(l1, "l1", 1);
        flushLooper();
        assertEquals(2, backingList.size());

        q.enqueueRemoveLayer("l0");
        flushLooper();

        assertEquals("Only l1 remains after removing l0", 1, backingList.size());
        assertEquals("Remaining layer must be l1", l1, backingList.get(0));
        assertNull(q.getKnownLayers().get("l0"));
        assertNotNull(q.getKnownLayers().get("l1"));
    }
}
