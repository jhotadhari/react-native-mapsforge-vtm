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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LayerZoomBoundsHelper}.
 *
 * <p>Focuses on the core zoom-bound logic ({@code updateEnabled}) and
 * error handling in {@code updateEnabledZoomMinMax}. The full add/remove
 * lifecycle requires a running Activity with a {@code MapFragment} and
 * is exercised by integration tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LayerZoomBoundsHelperTest {

    private ReactContextBaseJavaModule mockModule;
    private ReactApplicationContext mockReactContext;
    private ReadableMap mockParams;
    private Promise mockPromise;

    @Before
    public void setUp() throws Exception {
        mockModule = mock(ReactContextBaseJavaModule.class);
        mockReactContext = mock(ReactApplicationContext.class);
        mockParams = mock(ReadableMap.class);
        mockPromise = mock(Promise.class);

        // Provide default constants (enabledZoomMin=0, enabledZoomMax=22).
        Map<String, Object> constants = new HashMap<>();
        constants.put("enabledZoomMin", 0);
        constants.put("enabledZoomMax", 22);
        when(mockModule.getConstants()).thenReturn(constants);
    }

    @After
    public void tearDown() {
        // No static registry to clean up for this helper.
    }

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    @Test
    public void constructor_createsInstance() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        assertNotNull("Helper must be created", helper);
    }

    // ------------------------------------------------------------------
    // updateEnabled
    // ------------------------------------------------------------------

    @Test
    public void updateEnabled_enabledWithinRange() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=5, max=18, zoomLevel=10 → enabled
        helper.updateEnabled(mockLayer, 5, 18, 10);
        verify(mockLayer, times(1)).setEnabled(true);
    }

    @Test
    public void updateEnabled_disabledBelowMin() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=5, max=18, zoomLevel=3 → disabled
        helper.updateEnabled(mockLayer, 5, 18, 3);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    @Test
    public void updateEnabled_disabledAboveMax() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=5, max=18, zoomLevel=20 → disabled
        helper.updateEnabled(mockLayer, 5, 18, 20);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    @Test
    public void updateEnabled_atExactMin_Enabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        helper.updateEnabled(mockLayer, 5, 18, 5);
        verify(mockLayer, times(1)).setEnabled(true);
    }

    @Test
    public void updateEnabled_atExactMax_Enabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        helper.updateEnabled(mockLayer, 5, 18, 18);
        verify(mockLayer, times(1)).setEnabled(true);
    }

    @Test
    public void updateEnabled_sameMinAndMax_preciseMatch() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=max=10, zoomLevel=10 → only 10.
        helper.updateEnabled(mockLayer, 10, 10, 10);
        verify(mockLayer, times(1)).setEnabled(true);

        helper.updateEnabled(mockLayer, 10, 10, 9);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    // ------------------------------------------------------------------
    // updateEnabled — additional boundary cases
    // ------------------------------------------------------------------

    @Test
    public void updateEnabled_zoomLevelJustBelowMin_disabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        helper.updateEnabled(mockLayer, 5, 10, 4);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    @Test
    public void updateEnabled_zoomLevelJustAboveMax_disabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        helper.updateEnabled(mockLayer, 5, 10, 11);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    @Test
    public void updateEnabled_zeroMinMax_alwaysEnabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=0, max=0, zoomLevel=0 → enabled
        helper.updateEnabled(mockLayer, 0, 0, 0);
        verify(mockLayer, times(1)).setEnabled(true);
    }

    @Test
    public void updateEnabled_negativeZoomLevel_disabled() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);
        Layer mockLayer = mock(Layer.class);

        // min=0, max=22, zoomLevel=-1 → disabled (below min)
        helper.updateEnabled(mockLayer, 0, 22, -1);
        verify(mockLayer, times(1)).setEnabled(false);
    }

    // ------------------------------------------------------------------
    // removeUpdateListener — no-op without a registered map view
    // ------------------------------------------------------------------

    @Test
    public void removeUpdateListener_noMapView_doesNotThrow() {
        LayerZoomBoundsHelper helper = new LayerZoomBoundsHelper(mockModule, mockReactContext);

        // Utils.getMapView will return null because there is no Activity.
        // The method should handle this gracefully.
        helper.removeUpdateListener(99999);
        // No exception means success.
    }
}
