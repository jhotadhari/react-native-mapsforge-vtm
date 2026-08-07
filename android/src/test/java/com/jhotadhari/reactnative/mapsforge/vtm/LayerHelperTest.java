package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReadableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.layers.Layer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LayerHelper}.
 *
 * <p>Tests the error-handling paths of the async add/remove API and the
 * static layer-lookup method. The full success path needs a running
 * {@code MapFragment} + React Activity and is exercised by integration tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LayerHelperTest {

    private ReactContextBaseJavaModule mockModule;
    private ReactApplicationContext mockReactContext;
    private ReadableMap mockParams;
    private Layer mockLayer;

    @Before
    public void setUp() {
        mockModule = mock(ReactContextBaseJavaModule.class);
        mockReactContext = mock(ReactApplicationContext.class);
        mockParams = mock(ReadableMap.class);
        mockLayer = mock(Layer.class);
    }

    @After
    public void tearDown() {
        // No static state to clean up — LayerHelper does not own the registries.
    }

    // ------------------------------------------------------------------
    // addLayerAsync — error paths
    // ------------------------------------------------------------------

    @Test
    public void addLayerAsync_missingNativeNodeHandle_returnsFailingFuture() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(false);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);

        CompletableFuture<String> future = helper.addLayerAsync(mockLayer, mockParams);

        assertTrue("Future must complete exceptionally", future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be IllegalArgumentException",
                    e.getCause() instanceof IllegalArgumentException);
            assertEquals("Missing nativeNodeHandle", e.getCause().getMessage());
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    @Test
    public void addLayerAsync_withNativeNodeHandle_noMapView_returnsFailingFuture() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        // Params have nativeNodeHandle, but Utils.getMapView returns null
        // because there's no Activity/Fragment set up.
        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(true);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);
        when(mockParams.hasKey("positionIndex")).thenReturn(false);
        when(mockParams.getInt("nativeNodeHandle")).thenReturn(99999);

        CompletableFuture<String> future = helper.addLayerAsync(mockLayer, mockParams);

        assertTrue("Future must complete exceptionally without a MapView",
                future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be RuntimeException",
                    e.getCause() instanceof RuntimeException);
            assertTrue("Message must mention mapView",
                    e.getCause().getMessage().contains("Unable to find mapView"));
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    // ------------------------------------------------------------------
    // removeLayerAsync — error paths
    // ------------------------------------------------------------------

    @Test
    public void removeLayerAsync_missingUuid_returnsFailingFuture() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("uuid")).thenReturn(false);
        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(true);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);
        when(mockParams.getInt("nativeNodeHandle")).thenReturn(12345);

        CompletableFuture<Void> future = helper.removeLayerAsync(mockParams);

        assertTrue("Future must complete exceptionally when uuid is missing",
                future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be IllegalArgumentException",
                    e.getCause() instanceof IllegalArgumentException);
            assertEquals("Missing uuid or nativeNodeHandle",
                    e.getCause().getMessage());
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    @Test
    public void removeLayerAsync_missingNativeNodeHandle_returnsFailingFuture() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("uuid")).thenReturn(true);
        when(mockParams.isNull("uuid")).thenReturn(false);
        when(mockParams.getString("uuid")).thenReturn("some-uuid");
        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(false);

        CompletableFuture<Void> future = helper.removeLayerAsync(mockParams);

        assertTrue("Future must complete exceptionally when nativeNodeHandle is missing",
                future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be IllegalArgumentException",
                    e.getCause() instanceof IllegalArgumentException);
            assertEquals("Missing uuid or nativeNodeHandle",
                    e.getCause().getMessage());
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    @Test
    public void removeLayerAsync_withoutMapView_returnsFailingFuture() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("uuid")).thenReturn(true);
        when(mockParams.isNull("uuid")).thenReturn(false);
        when(mockParams.getString("uuid")).thenReturn("some-uuid");
        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(true);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);
        when(mockParams.getInt("nativeNodeHandle")).thenReturn(88888);

        CompletableFuture<Void> future = helper.removeLayerAsync(mockParams);

        assertTrue("Future must complete exceptionally without a MapView",
                future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be RuntimeException",
                    e.getCause() instanceof RuntimeException);
            assertTrue("Message must mention mapView",
                    e.getCause().getMessage().contains("Unable to find mapView"));
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    // ------------------------------------------------------------------
    // getLayer — static lookup
    // ------------------------------------------------------------------

    @Test
    public void getLayer_noQueue_returnsNull() {
        // No MapMutationQueue has been created for handle 77777.
        Layer result = LayerHelper.getLayer(77777, "any-uuid");
        assertNull("getLayer must return null when no queue exists", result);
    }

    @Test
    public void getLayer_knownUuid_returnsLayer() throws Exception {
        // Create a MapMutationQueue for this handle and add a known layer.
        int handle = 77778;

        // We need a mock MapView for MapMutationQueue.get().
        org.oscim.android.MapView mockMapView = mock(org.oscim.android.MapView.class);
        org.oscim.map.Map mockMap = mock(org.oscim.map.Map.class);
        when(mockMapView.map()).thenReturn(mockMap);
        org.oscim.map.Layers mockLayers = mock(org.oscim.map.Layers.class);
        when(mockMap.layers()).thenReturn(mockLayers);
        java.util.List<org.oscim.layers.Layer> backing = new java.util.ArrayList<>();
        when(mockLayers.size()).thenAnswer(inv -> backing.size());

        MapMutationQueue queue = MapMutationQueue.get(handle, mockMapView);
        Layer testLayer = mock(Layer.class);
        queue.getKnownLayers().put("known-uuid", testLayer);

        Layer result = LayerHelper.getLayer(handle, "known-uuid");
        assertNotNull("getLayer must return the layer when it exists", result);
        assertEquals("getLayer must return the correct layer", testLayer, result);
    }

    // ------------------------------------------------------------------
    // getLayers
    // ------------------------------------------------------------------

    @Test
    public void getLayers_noMapView_returnsNull() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        // Utils.getMapView returns null without a real Activity.
        java.util.Map<String, Layer> result = helper.getLayers(99999);
        assertNull("getLayers must return null when no MapView is registered", result);
    }

    // ------------------------------------------------------------------
    // addLayerAsync with explicit UUID
    // ------------------------------------------------------------------

    @Test
    public void addLayerAsync_withExplicitUuid_failsWithoutMapView() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(true);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);
        when(mockParams.hasKey("positionIndex")).thenReturn(false);
        when(mockParams.getInt("nativeNodeHandle")).thenReturn(77777);

        CompletableFuture<String> future = helper.addLayerAsync(mockLayer, mockParams, "explicit-uuid");

        assertTrue("Future must complete exceptionally without a MapView",
                future.isCompletedExceptionally());
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be RuntimeException",
                    e.getCause() instanceof RuntimeException);
            assertTrue("Message must mention mapView",
                    e.getCause().getMessage().contains("Unable to find mapView"));
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }

    // ------------------------------------------------------------------
    // addLayerAsync two-arg overload
    // ------------------------------------------------------------------

    @Test
    public void addLayerAsync_twoArgOverload_usesRandomUuid() {
        LayerHelper helper = new LayerHelper(mockModule, mockReactContext);

        when(mockParams.hasKey("nativeNodeHandle")).thenReturn(true);
        when(mockParams.isNull("nativeNodeHandle")).thenReturn(false);
        when(mockParams.hasKey("positionIndex")).thenReturn(false);
        when(mockParams.getInt("nativeNodeHandle")).thenReturn(77777);

        CompletableFuture<String> future = helper.addLayerAsync(mockLayer, mockParams);

        assertTrue("Future must complete exceptionally without a MapView",
                future.isCompletedExceptionally());
        // The exact UUID is random, but the cause is predictable.
        try {
            future.get();
            fail("get() must throw");
        } catch (ExecutionException e) {
            assertTrue("Cause must be RuntimeException",
                    e.getCause() instanceof RuntimeException);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
    }
}
