/**
 * Internal dependencies
 */
import NativeMapContainer from './NativeModules/NativeMapContainer';

/**
 * Opt-in: cap the bytes vtm's NativeBufferPool keeps pooled between frames
 * (process-wide, all maps). The pool holds the direct upload buffers of the
 * GL thread; by default it is peak-hold (keeps every buffer at the largest
 * size it ever served, never shrinks). On Android direct buffers are backed
 * by the Java heap, so panning across many regions can pin tens of MB.
 *
 * Trade-off: larger uploads re-allocate more often once the cap is hit.
 * Pass a negative value or 0 to keep nothing pooled.
 */
export const setNativeBufferPoolLimit = (maxBytes: number): Promise<void> =>
	NativeMapContainer.setNativeBufferPoolLimit({
		maxBytes: Math.round(maxBytes),
	});

/**
 * Release all pooled (unused) native buffers at the end of the next rendered
 * frame. No effect until a frame is drawn (e.g. while the map is paused).
 */
export const trimNativeBufferPool = (): Promise<void> =>
	NativeMapContainer.trimNativeBufferPool();
