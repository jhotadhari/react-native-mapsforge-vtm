import type { ErrorBase } from './types';

const MAP_VIEW_GONE = /Unable to find mapView/;

/**
 * True when a command failed because the map view was torn down while JS
 * was still dispatching (Activity destroy/recreate race).  Not a bug —
 * silently ignored so dev logs and LogBox stay clean and no onError
 * toast fires for it.
 *
 * Also covers shapeless rejections (no userInfo AND no message) that
 * occur when a @ReactMethod throws an exception without a message while
 * the map is gone — TurboModule interop rejects with the raw exception
 * and the JS err carries neither field.  Every meaningful rejection in
 * this library carries userInfo.errorMsg or a message, so nothing of
 * value is swallowed here.
 */
export const isMapViewGoneError = (err: any) =>
	err?.code === 'MAP_VIEW_GONE' ||
	MAP_VIEW_GONE.test(String(err?.userInfo?.errorMsg ?? '')) ||
	MAP_VIEW_GONE.test(String(err?.message ?? '')) ||
	(err != null && err.userInfo == null && err.message == null);

const reportNativeError = (
	err: ErrorBase,
	onError?: null | ((err: ErrorBase) => void)
) => {
	if (isMapViewGoneError(err)) {
		console.debug('[map] ignored command during teardown', err);
		return;
	}
	console.error('ERROR', err?.userInfo?.errorMsg);
	onError ? onError(err) : null;
};

export default reportNativeError;
