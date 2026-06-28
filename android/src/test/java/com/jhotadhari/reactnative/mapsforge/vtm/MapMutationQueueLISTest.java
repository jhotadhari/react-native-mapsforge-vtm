package com.jhotadhari.reactnative.mapsforge.vtm;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the private static method
 * {@code MapMutationQueue.longestIncreasingSubsequenceMask(int[])}
 * via reflection.
 *
 * <p>The LIS algorithm is strictly increasing (duplicate values do not extend
 * the subsequence). The returned boolean[] has {@code true} at each index that
 * belongs to one valid LIS; the remaining indices are {@code false}.
 * Tests verify LIS length by counting {@code true} values without assuming
 * which specific indices were chosen.
 */
public class MapMutationQueueLISTest {

    private Method lisMethod;

    @Before
    public void setUp() throws Exception {
        lisMethod = MapMutationQueue.class
                .getDeclaredMethod("longestIncreasingSubsequenceMask", int[].class);
        lisMethod.setAccessible(true);
    }

    /** Invoke the private method and return its result. */
    private boolean[] lis(int... values) throws Exception {
        return (boolean[]) lisMethod.invoke(null, (Object) values);
    }

    /** Count how many entries are {@code true} in the mask — this equals LIS length. */
    private static int countTrue(boolean[] mask) {
        int count = 0;
        for (boolean b : mask) {
            if (b) count++;
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void emptyArray_returnsEmptyMask() throws Exception {
        boolean[] result = lis();
        assertNotNull(result);
        assertEquals("Empty input must produce empty mask", 0, result.length);
    }

    @Test
    public void singleElement_returnsSingleTrue() throws Exception {
        boolean[] result = lis(42);
        assertEquals(1, result.length);
        assertTrue("Single element must be kept", result[0]);
    }

    // -----------------------------------------------------------------------
    // Fully sorted / fully reversed
    // -----------------------------------------------------------------------

    @Test
    public void alreadySortedAscending_allTrue() throws Exception {
        boolean[] result = lis(1, 2, 3, 4);
        assertEquals(4, result.length);
        for (int i = 0; i < result.length; i++) {
            assertTrue("Index " + i + " should be kept in a sorted array", result[i]);
        }
    }

    @Test
    public void reverseSorted_exactlyOneTrueEntry() throws Exception {
        // Strictly decreasing — no two elements form an increasing pair.
        // LIS length = 1; any single element is a valid LIS.
        boolean[] result = lis(4, 3, 2, 1);
        assertEquals(4, result.length);
        assertEquals("Reverse-sorted array has LIS length 1", 1, countTrue(result));
    }

    @Test
    public void reverseSorted_twoElements_exactlyOneTrueEntry() throws Exception {
        boolean[] result = lis(10, 5);
        assertEquals(2, result.length);
        assertEquals(1, countTrue(result));
    }

    // -----------------------------------------------------------------------
    // Mixed sequences
    // -----------------------------------------------------------------------

    @Test
    public void mixedSequence_lisLengthThree() throws Exception {
        // [0, 3, 1, 2]: LIS is [0, 1, 2] — three elements.
        boolean[] result = lis(0, 3, 1, 2);
        assertEquals(4, result.length);
        assertEquals("LIS of [0,3,1,2] must have length 3", 3, countTrue(result));
        // The chosen LIS must be strictly increasing.
        assertStrictlyIncreasing(result, new int[]{0, 3, 1, 2});
    }

    @Test
    public void tieDuplicateValues_lisLengthTwo() throws Exception {
        // [0, 0, 1]: strictly increasing — duplicates cannot both be in LIS.
        // Best is [0, 1] → length 2.
        boolean[] result = lis(0, 0, 1);
        assertEquals(3, result.length);
        assertEquals("LIS of [0,0,1] must have length 2 (strictly increasing)", 2, countTrue(result));
        assertStrictlyIncreasing(result, new int[]{0, 0, 1});
    }

    @Test
    public void tieDuplicateValuesOnly_lisLengthOne() throws Exception {
        // [5, 5, 5]: all equal — no two elements form a strictly increasing pair.
        boolean[] result = lis(5, 5, 5);
        assertEquals(3, result.length);
        assertEquals("LIS of [5,5,5] must have length 1", 1, countTrue(result));
    }

    @Test
    public void interleaved_lisLengthFive() throws Exception {
        // [0, 2, 4, 6, 1, 3, 5, 7]:
        // Longest strictly increasing subsequence has length 5.
        // Valid examples: [0,2,4,6,7] or [0,1,3,5,7].
        boolean[] result = lis(0, 2, 4, 6, 1, 3, 5, 7);
        assertEquals(8, result.length);
        assertEquals("LIS of interleaved sequence must have length 5", 5, countTrue(result));
        assertStrictlyIncreasing(result, new int[]{0, 2, 4, 6, 1, 3, 5, 7});
    }

    @Test
    public void allSameValueAfterOne_lisLengthOne() throws Exception {
        // [0, 3, 3, 3]: LIS is just [0] or [3] — length 1 or 2?
        // [0, 3] is strictly increasing (length 2), [3, 3] is NOT.
        // Best LIS is [0, 3] → length 2.
        boolean[] result = lis(0, 3, 3, 3);
        assertEquals(4, result.length);
        assertEquals("LIS of [0,3,3,3] must have length 2", 2, countTrue(result));
        assertStrictlyIncreasing(result, new int[]{0, 3, 3, 3});
    }

    @Test
    public void twoElementsInOrder_lisLengthTwo() throws Exception {
        boolean[] result = lis(1, 2);
        assertEquals(2, result.length);
        assertEquals(2, countTrue(result));
        assertTrue(result[0]);
        assertTrue(result[1]);
    }

    @Test
    public void longSortedWithOneOutlier_lisLengthFour() throws Exception {
        // [1, 2, 3, 4, 0]: LIS is [1,2,3,4] — length 4; the trailing 0 is excluded.
        boolean[] result = lis(1, 2, 3, 4, 0);
        assertEquals(5, result.length);
        assertEquals(4, countTrue(result));
        // The last element (0) must NOT be in the LIS since no element after it
        // is larger, and 0 < 1,2,3,4 which all appear earlier.
        assertFalse("Trailing 0 cannot extend the LIS [1,2,3,4]", result[4]);
        assertStrictlyIncreasing(result, new int[]{1, 2, 3, 4, 0});
    }

    // -----------------------------------------------------------------------
    // Helper: verify that the mask actually identifies a strictly increasing
    // subsequence (sanity-checks the algorithm's output independent of length).
    // -----------------------------------------------------------------------

    private static void assertStrictlyIncreasing(boolean[] mask, int[] values) {
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                assertTrue(
                    "Chosen subsequence must be strictly increasing: value " + values[i]
                        + " at index " + i + " is not > previous kept value " + prev,
                    values[i] > prev);
                prev = values[i];
            }
        }
    }
}
