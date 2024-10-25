package com.jhotadhari.reactnative.mapsforge.vtm;

import android.util.Log;

// Copy of, some small changes: https://www.alibabacloud.com/blog/implementation-of-java-api-throttling_600873

public class FixedWindowRateLimiter {
	// The size of the time window. Unit: milliseconds.
	long windowSize;
	// The number of allowed requests.
	int maxRequestCount;
	// The number of requests that pass through the current window.
	int counter = 0;
	// The right boundary of the window.
	long windowBorder;
	public FixedWindowRateLimiter(long windowSize, int maxRequestCount) {
		this.windowSize = windowSize;
		this.maxRequestCount = maxRequestCount;
		this.windowBorder = System.currentTimeMillis() + windowSize;
	}
	public synchronized boolean tryAcquire() {
		long currentTime = System.currentTimeMillis();
		if ( windowBorder < currentTime ) {
			Log.d( "FixedWindowRateLimiter", "window reset");
			do {
				windowBorder += windowSize;
			} while ( windowBorder < currentTime );
			counter = 0;
		}
		if (counter < maxRequestCount) {
			counter = counter + 1;
			Log.d( "FixedWindowRateLimiter", "tryAcquire success");
			return true;
		} else {
			Log.d( "FixedWindowRateLimiter", "tryAcquire fail");
			return false;
		}
	}
}
