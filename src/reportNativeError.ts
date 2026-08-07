import type { ErrorBase } from './types';

const reportNativeError = (
	err: ErrorBase,
	onError?: null | ((err: ErrorBase) => void)
) => {
	console.error('ERROR', err?.userInfo?.errorMsg);
	onError ? onError(err) : null;
};

export default reportNativeError;
