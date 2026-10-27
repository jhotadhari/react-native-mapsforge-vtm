/**
 * Tests for reportNativeError — teardown-race rejections must be
 * swallowed silently; everything else still reports and fires onError.
 */

/**
 * Internal dependencies
 */
import reportNativeError, { isMapViewGoneError } from '../reportNativeError';

describe('isMapViewGoneError', () => {
	it('matches the MAP_VIEW_GONE reject code', () => {
		expect(isMapViewGoneError({ code: 'MAP_VIEW_GONE' })).toBe(true);
	});

	it('matches userInfo.errorMsg containing the teardown message', () => {
		expect(
			isMapViewGoneError({
				userInfo: {
					errorMsg:
						'java.lang.RuntimeException: Unable to find mapView',
				},
			})
		).toBe(true);
	});

	it('matches err.message containing the teardown message', () => {
		expect(
			isMapViewGoneError({
				message: 'Unable to find mapView or mapFragment',
			})
		).toBe(true);
	});

	it('matches a shapeless rejection (no userInfo, no message)', () => {
		expect(
			isMapViewGoneError({ code: 'error', nativeStackAndroid: [] })
		).toBe(true);
	});

	it('matches the RN placeholder message without userInfo', () => {
		expect(
			isMapViewGoneError({
				code: 'error',
				message: 'Error not specified.',
				nativeStackAndroid: [],
			})
		).toBe(true);
	});

	it('does not silence the placeholder message when userInfo carries a real error', () => {
		expect(
			isMapViewGoneError({
				code: 'error',
				message: 'Error not specified.',
				userInfo: { errorMsg: 'real failure' },
			})
		).toBe(false);
	});

	it('rejects unrelated errors', () => {
		expect(
			isMapViewGoneError({
				code: 'error',
				userInfo: { errorMsg: 'boom' },
			})
		).toBe(false);
		expect(
			isMapViewGoneError({ code: 'error', message: 'something real' })
		).toBe(false);
		expect(isMapViewGoneError(undefined)).toBe(false);
		expect(isMapViewGoneError(null)).toBe(false);
	});
});

describe('reportNativeError', () => {
	let errorSpy: jest.SpyInstance;
	let debugSpy: jest.SpyInstance;

	beforeEach(() => {
		errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
		debugSpy = jest.spyOn(console, 'debug').mockImplementation(() => {});
	});

	afterEach(() => {
		errorSpy.mockRestore();
		debugSpy.mockRestore();
	});

	it('silences teardown errors and skips onError', () => {
		const onError = jest.fn();
		reportNativeError(
			{ userInfo: { errorMsg: 'Unable to find mapView' } } as any,
			onError
		);
		expect(errorSpy).not.toHaveBeenCalled();
		expect(onError).not.toHaveBeenCalled();
		expect(debugSpy).toHaveBeenCalled();
	});

	it('silences a bare rejection without any payload and skips onError', () => {
		const onError = jest.fn();
		reportNativeError(undefined as any, onError);
		expect(errorSpy).not.toHaveBeenCalled();
		expect(onError).not.toHaveBeenCalled();
		expect(debugSpy).toHaveBeenCalled();
	});

	it('reports real errors and fires onError', () => {
		const onError = jest.fn();
		const err = { userInfo: { errorMsg: 'some real failure' } } as any;
		reportNativeError(err, onError);
		expect(errorSpy).toHaveBeenCalledWith('ERROR', err);
		expect(onError).toHaveBeenCalledWith(err);
	});
});
