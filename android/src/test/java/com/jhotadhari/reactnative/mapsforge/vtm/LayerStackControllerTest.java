package com.jhotadhari.reactnative.mapsforge.vtm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oscim.android.MapView;
import org.oscim.layers.Layer;
import org.oscim.map.Layers;
import org.oscim.map.Map;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Robolectric unit tests for {@link LayerStackController}: registry
 * bookkeeping, absolute-plan application (minimal moves + the
 * vtm-internal-layer guard), and the post-apply self-check.
 *
 * <p>Uses Mockito mocks for vtm's {@code MapView}, {@code Map}, and
 * {@code Layers}, backed by a real {@code ArrayList<Layer>} so
 * add/remove/get/size/contains/indexOf behave as on a real device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LayerStackControllerTest {

	private MapView mockMapView;
	private Map mockMap;
	private Layers mockLayers;
	private List<Layer> backingList;
	private LayerStackController controller;

	@Before
	public void setUp() {
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

		controller = new LayerStackController(mockMapView);
	}

	/** Registers a layer and adds it to the backing list (as the flush does). */
	private void addToStack(String uuid) {
		Layer layer = mock(Layer.class);
		backingList.add(layer);
		controller.register(layer, uuid);
	}

	private void addToMapOnly(Layer layer) {
		backingList.add(layer);
	}

	private List<String> appliedUuids() {
		List<String> uuids = new ArrayList<>();
		for (Layer layer : backingList) {
			String uuid = null;
			for (java.util.Map.Entry<String, Layer> e : controller.getKnownLayers().entrySet()) {
				if (e.getValue() == layer) {
					uuid = e.getKey();
					break;
				}
			}
			if (uuid != null) {
				uuids.add(uuid);
			}
		}
		return uuids;
	}

	// -----------------------------------------------------------------------
	// Registry bookkeeping
	// -----------------------------------------------------------------------

	@Test
	public void registerAndUnregister_updateKnownLayers() {
		Layer layer = mock(Layer.class);
		controller.register(layer, "l1");
		assertEquals(layer, controller.getLayer("l1"));
		controller.unregister("l1");
		assertNull(controller.getLayer("l1"));
	}

	@Test
	public void removeAll_removesOnlyListedUuids() {
		addToStack("l1");
		addToStack("l2");
		addToStack("l3");
		assertEquals(3, backingList.size());

		controller.removeAll(new HashSet<>(Arrays.asList("l1", "l3")));

		assertEquals(1, backingList.size());
		assertNull(controller.getLayer("l1"));
		assertNotNull(controller.getLayer("l2"));
		assertNull(controller.getLayer("l3"));
	}

	@Test
	public void removeLayerSync_removesLayerAndRegistry() {
		addToStack("l1");
		assertEquals(1, backingList.size());
		controller.removeLayerSync("l1");
		assertEquals(0, backingList.size());
		assertNull(controller.getLayer("l1"));
	}

	// -----------------------------------------------------------------------
	// applyPlan
	// -----------------------------------------------------------------------

	@Test
	public void applyPlan_reordersToMatchPlan() {
		addToStack("l1");
		addToStack("l2");
		addToStack("l3");

		controller.applyPlan(Arrays.asList("l3", "l1", "l2"));

		assertEquals(Arrays.asList("l3", "l1", "l2"), appliedUuids());
	}

	@Test
	public void applyPlan_skipsUnknownUuids() {
		addToStack("l1");
		addToStack("l2");

		// "missing" is not registered — must be skipped without error and
		// without disturbing the relative order of the known subset.
		controller.applyPlan(Arrays.asList("missing", "l2", "l1"));

		assertEquals(Arrays.asList("l2", "l1"), appliedUuids());
	}

	@Test
	public void applyPlan_firstElementNeverInsertedBeforeInternalLayers() {
		// A vtm-internal layer (e.g. GestureLayer) sits at index 0 and is
		// NOT in the registry. Moving the first target element must insert
		// it before the first tracked layer, never at index 0.
		Layer gesture = mock(Layer.class);
		addToMapOnly(gesture);
		addToStack("l1");
		addToStack("l2");

		controller.applyPlan(Arrays.asList("l2", "l1"));

		assertEquals("Internal layer must stay at index 0", gesture, backingList.get(0));
		assertEquals(Arrays.asList("l2", "l1"), appliedUuids());
	}

	@Test
	public void applyPlan_emptyOrUnknownOnlyPlan_isNoOp() {
		addToStack("l1");
		controller.applyPlan(new ArrayList<>());
		controller.applyPlan(Arrays.asList("nope"));
		assertEquals(Arrays.asList("l1"), appliedUuids());

		// The empty/stale plan must still run the self-check and report the
		// on-map JS-managed layer as present-but-not-in-plan (M1).
		LayerStackController.VerifyResult result = controller.getLastVerifyResult();
		assertNotNull(result);
		assertTrue(result.matches);
		assertEquals(1, result.notInPlanCount);
	}

	// -----------------------------------------------------------------------
	// verify (self-check)
	// -----------------------------------------------------------------------

	@Test
	public void verify_matchesWhenAppliedEqualsTarget() {
		addToStack("l1");
		addToStack("l2");
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l1", "l2"));
		assertTrue(result.matches);
		assertEquals(Arrays.asList("l1", "l2"), result.expectedUuids);
		assertEquals(Arrays.asList("l1", "l2"), result.appliedUuids);
		assertEquals(result, controller.getLastVerifyResult());
	}

	@Test
	public void verify_detectsMismatch() {
		addToStack("l1");
		addToStack("l2");
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l2", "l1"));
		assertFalse(result.matches);
		assertEquals(Arrays.asList("l1", "l2"), result.appliedUuids);
	}

	@Test
	public void verify_excludesInternalLayersFromApplied() {
		Layer gesture = mock(Layer.class);
		addToMapOnly(gesture);
		addToStack("l1");
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l1"));
		assertTrue("Internal layers must not count against the plan", result.matches);
		assertEquals(Arrays.asList("l1"), result.appliedUuids);
	}

	@Test
	public void verify_ignoresUnknownTargetUuids() {
		// "virtual"/not-yet-registered uuids in the target (e.g. a create
		// whose plan predates registration) must not produce a mismatch.
		addToStack("l1");
		addToStack("l2");
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l1", "virtual", "l2"));
		assertTrue("Unknown uuids must be ignored", result.matches);
		assertEquals(Arrays.asList("l1", "l2"), result.expectedUuids);
		assertEquals(Arrays.asList("l1", "l2"), result.appliedUuids);
	}

	@Test
	public void verify_ignoresAppliedLayersAbsentFromPlan() {
		// A same-batch sibling appended but absent from the plan must not
		// produce a mismatch — only the resolvable relative order counts.
		addToStack("l1");
		addToStack("l2");
		addToStack("sibling");
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l1", "l2"));
		assertTrue("Plan-absent applied layers must be ignored", result.matches);
	}

	@Test
	public void verify_detectsOutOfOrderResolvableLayers() {
		addToStack("l1");
		addToStack("l2");
		addToStack("l3");
		// l3 resolvable but misplaced relative to the others — real desync.
		LayerStackController.VerifyResult result = controller.verify(Arrays.asList("l1", "l3", "l2"));
		assertFalse(result.matches);
	}

	@Test
	public void applyPlan_firesClearEventOnceForNewlyTrackedLayers() {
		Layer listenerLayer = mock(
			Layer.class,
			withSettings().extraInterfaces(org.oscim.map.Map.UpdateListener.class)
		);
		backingList.add(listenerLayer);
		controller.register(listenerLayer, "l1");

		controller.applyPlan(Arrays.asList("l1"));
		org.oscim.map.Map.UpdateListener listener =
			(org.oscim.map.Map.UpdateListener) listenerLayer;
		verify(listener).onMapEvent(eq(org.oscim.map.Map.CLEAR_EVENT), any());

		// Second application of the same plan must NOT re-fire CLEAR_EVENT.
		controller.applyPlan(Arrays.asList("l1"));
		verify(listener, times(1)).onMapEvent(eq(org.oscim.map.Map.CLEAR_EVENT), any());
	}

	// -----------------------------------------------------------------------
	// clear
	// -----------------------------------------------------------------------

	@Test
	public void clear_resetsRegistryAndVerifyState() {
		addToStack("l1");
		controller.verify(Arrays.asList("l1"));
		controller.clear();
		assertTrue(controller.getKnownLayers().isEmpty());
		assertNull(controller.getLastVerifyResult());
	}
}
