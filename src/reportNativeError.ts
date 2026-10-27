import type { ErrorBase } from './types';

const MAP_VIEW_GONE = /Unable to find mapView/;

/**
 * True when a command failed because the map view was torn down while JS
 * was still dispatching (Activity destroy/recreate race).  Not a bug —
 * silently ignored so dev logs and LogBox stay clean and no onError
 * toast fires for it.
 */
export const isMapViewGoneError = (err: any) =>
	err?.code === 'MAP_VIEW_GONE' ||
	MAP_VIEW_GONE.test(String(err?.userInfo?.errorMsg ?? '')) ||
	MAP_VIEW_GONE.test(String(err?.message ?? ''));

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
