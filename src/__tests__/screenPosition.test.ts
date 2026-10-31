/**
 * Tests for the screen-anchored position sugar — the JS-side normalization
 * that keeps the TurboModule bridge contract numeric ('fraction' | 'dp')
 * while the components accept ergonomic strings ('50%', '100dp').
 */

/**
 * Internal dependencies
 */
import {
	isScreenPointSugar,
	normalizeMarkerPosition,
	parseScreenPoint,
	splitPathCoordinates,
} from '../utils/screenPosition';

describe('isScreenPointSugar', () => {
	it('detects string tuples', () => {
		expect(isScreenPointSugar(['50%', '50%'])).toBe(true);
		expect(isScreenPointSugar(['100dp', '24dp'])).toBe(true);
	});

	it('detects numeric/string objects', () => {
		expect(isScreenPointSugar({ x: 0.5, y: 0.5 })).toBe(true);
		expect(isScreenPointSugar({ x: '50%', y: 0.5, unit: 'fraction' })).toBe(
			true
		);
	});

	it('rejects geographic tuples and garbage', () => {
		expect(isScreenPointSugar([13.405, 52.52])).toBe(false);
		expect(isScreenPointSugar([0.5, 0.5])).toBe(false);
		expect(isScreenPointSugar('50%')).toBe(false);
		expect(isScreenPointSugar(null)).toBe(false);
		expect(isScreenPointSugar({ x: 0.5 })).toBe(false);
	});
});

describe('parseScreenPoint', () => {
	it('parses percent strings as fractions', () => {
		expect(parseScreenPoint(['50%', '25%'])).toEqual({
			x: 0.5,
			y: 0.25,
			unit: 'fraction',
		});
	});

	it('parses dp strings', () => {
		expect(parseScreenPoint(['100dp', '24dp'])).toEqual({
			x: 100,
			y: 24,
			unit: 'dp',
		});
	});

	it('defaults numeric objects to fractions', () => {
		expect(parseScreenPoint({ x: 0.5, y: 0.5 })).toEqual({
			x: 0.5,
			y: 0.5,
			unit: 'fraction',
		});
	});

	it('honors an explicit unit on numeric objects', () => {
		expect(parseScreenPoint({ x: 120, y: 24, unit: 'dp' })).toEqual({
			x: 120,
			y: 24,
			unit: 'dp',
		});
	});

	it('rejects mixed units between the axes', () => {
		expect(() => parseScreenPoint(['50%', '24dp'])).toThrow(/same unit/);
	});

	it('rejects numeric values outside 0..1 without an explicit unit', () => {
		expect(() => parseScreenPoint({ x: 120, y: 0.5 })).toThrow(/unit/);
	});

	it('rejects unparsable strings', () => {
		expect(() => parseScreenPoint(['50px', '50%'])).toThrow(
			/unable to parse/
		);
	});
});

describe('splitPathCoordinates', () => {
	it('passes through purely geographic coordinates', () => {
		const coordinates = [
			[13.405, 52.52],
			[13.41, 52.53],
		];
		expect(splitPathCoordinates(coordinates)).toEqual({
			geoCoordinates: coordinates,
			screenPoints: [],
		});
	});

	it('splits sugar points out with resolved indices', () => {
		expect(
			splitPathCoordinates([
				[13.405, 52.52],
				['50%', '50%'],
				[13.41, 52.53],
			])
		).toEqual({
			geoCoordinates: [
				[13.405, 52.52],
				[13.41, 52.53],
			],
			screenPoints: [
				{ index: 1, x: 0.5, y: 0.5, unit: 'fraction' },
			],
		});
	});

	it('supports an all-screen path', () => {
		expect(
			splitPathCoordinates([
				['10%', '10%'],
				['90%', '90%'],
			])
		).toEqual({
			geoCoordinates: [],
			screenPoints: [
				{ index: 0, x: 0.1, y: 0.1, unit: 'fraction' },
				{ index: 1, x: 0.9, y: 0.9, unit: 'fraction' },
			],
		});
	});

	it('preserves anchor order for consecutive sugar points', () => {
		expect(
			splitPathCoordinates([
				['50%', '50%'],
				['100dp', '24dp'],
				[13.405, 52.52],
			])
		).toEqual({
			geoCoordinates: [[13.405, 52.52]],
			screenPoints: [
				{ index: 0, x: 0.5, y: 0.5, unit: 'fraction' },
				{ index: 1, x: 100, y: 24, unit: 'dp' },
			],
		});
	});
});

describe('normalizeMarkerPosition', () => {
	it('passes through geographic positions', () => {
		expect(normalizeMarkerPosition([13.405, 52.52])).toEqual({
			position: [13.405, 52.52],
		});
	});

	it('converts sugar into a placeholder geo position + screenPosition', () => {
		expect(normalizeMarkerPosition(['50%', '50%'])).toEqual({
			position: [0, 0],
			screenPosition: { x: 0.5, y: 0.5, unit: 'fraction' },
		});
	});

	it('handles null positions', () => {
		expect(normalizeMarkerPosition(null)).toEqual({});
	});
});
