import type { ErrorBase } from './types';

const MAP_VIEW_GONE = /Unable to find mapView/;

/**
 * True when a command failed because the map view was torn down while JS
 * was still dispatching (Activity destroy/recreate race).  Not a bug —
 * silently ignored so dev logs and LogBox stay clean and no onError
 * toast fires for it.
 *
 * Also covers payload-free rejections: RN's PromiseImpl rejects with
 * the placeholder message "Error not specified." when a rejection
 * carries neither a message nor userInfo (defaults).  Every meaningful
 * rejection in this library carries userInfo.errorMsg or a real
 * message, so nothing of value is swallowed here.
 */
export const isMapViewGoneError = (err: any) =>
	err?.code === 'MAP_VIEW_GONE' ||
	MAP_VIEW_GONE.test(String(err?.userInfo?.errorMsg ?? '')) ||
	MAP_VIEW_GONE.test(String(err?.message ?? '')) ||
	(err != null &&
		err.userInfo?.errorMsg == null &&
		(err.message == null || err.message === 'Error not specified.'));

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
