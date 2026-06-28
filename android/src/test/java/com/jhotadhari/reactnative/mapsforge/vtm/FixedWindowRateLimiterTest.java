package com.jhotadhari.reactnative.mapsforge.vtm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixedWindowRateLimiterTest {

    // -----------------------------------------------------------------------
    // Basic acceptance: requests within the limit are granted
    // -----------------------------------------------------------------------

    @Test
    public void firstRequestIsAlwaysGranted() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 5);
        assertTrue("First request in a fresh window must be granted", limiter.tryAcquire());
    }

    @Test
    public void allRequestsWithinLimitAreGranted() {
        int max = 3;
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, max);
        for (int i = 0; i < max; i++) {
            assertTrue("Request " + (i + 1) + " of " + max + " should be granted",
                    limiter.tryAcquire());
        }
    }

    // -----------------------------------------------------------------------
    // Over-limit requests are rejected
    // -----------------------------------------------------------------------

    @Test
    public void requestOverLimitIsRejected() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 2);
        limiter.tryAcquire(); // 1st — granted
        limiter.tryAcquire(); // 2nd — granted
        assertFalse("Third request beyond max=2 should be rejected", limiter.tryAcquire());
    }

    @Test
    public void maxRequestCountOfOneAllowsExactlyOneRequest() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 1);
        assertTrue("Only request in window must be granted", limiter.tryAcquire());
        assertFalse("Second request must be rejected when max=1", limiter.tryAcquire());
    }

    @Test
    public void multipleConsecutiveOverLimitRequestsAllRejected() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 1);
        limiter.tryAcquire(); // fills the window
        assertFalse("1st over-limit request rejected", limiter.tryAcquire());
        assertFalse("2nd over-limit request rejected", limiter.tryAcquire());
        assertFalse("3rd over-limit request rejected", limiter.tryAcquire());
    }

    // -----------------------------------------------------------------------
    // Counter resets after the window expires
    // -----------------------------------------------------------------------

    @Test
    public void windowResetAllowsNewRequests() throws InterruptedException {
        // 50ms window, max 2 requests
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(50, 2);

        // Fill the window.
        assertTrue("1st request granted", limiter.tryAcquire());
        assertTrue("2nd request granted", limiter.tryAcquire());
        assertFalse("3rd request rejected (window full)", limiter.tryAcquire());

        // Wait long enough for the window to expire.
        Thread.sleep(100);

        // A new window must have started — requests should be granted again.
        assertTrue("1st request in new window granted", limiter.tryAcquire());
        assertTrue("2nd request in new window granted", limiter.tryAcquire());
        assertFalse("3rd request in new window rejected", limiter.tryAcquire());
    }

    @Test
    public void windowSlidesMultipleTimesWhenLongPauseOccurs() throws InterruptedException {
        // 20ms window, max 1 request
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(20, 1);

        assertTrue("Initial request granted", limiter.tryAcquire());
        assertFalse("Over-limit request rejected", limiter.tryAcquire());

        // Sleep for 5× the window — the do-while in tryAcquire must slide
        // the border forward multiple times.
        Thread.sleep(100);

        assertTrue("Request after multi-window sleep granted", limiter.tryAcquire());
    }

    // -----------------------------------------------------------------------
    // Counter state sanity
    // -----------------------------------------------------------------------

    @Test
    public void counterStartsAtZero() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 10);
        assertEquals("Counter must start at 0", 0, limiter.counter);
    }

    @Test
    public void counterIncrementsOnEachGrantedRequest() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 10);
        limiter.tryAcquire();
        assertEquals(1, limiter.counter);
        limiter.tryAcquire();
        assertEquals(2, limiter.counter);
        limiter.tryAcquire();
        assertEquals(3, limiter.counter);
    }

    @Test
    public void counterDoesNotIncrementOnRejectedRequest() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1000, 2);
        limiter.tryAcquire(); // counter → 1
        limiter.tryAcquire(); // counter → 2
        limiter.tryAcquire(); // rejected, counter must stay at 2
        assertEquals("Counter must not exceed maxRequestCount", 2, limiter.counter);
    }

    @Test
    public void counterResetsToZeroAfterWindowExpires() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(50, 5);
        limiter.tryAcquire(); // counter → 1
        limiter.tryAcquire(); // counter → 2

        Thread.sleep(100); // let the window expire

        // The next tryAcquire() must reset the counter, then increment to 1.
        limiter.tryAcquire();
        assertEquals("Counter must reset to 1 after window slides", 1, limiter.counter);
    }
}
