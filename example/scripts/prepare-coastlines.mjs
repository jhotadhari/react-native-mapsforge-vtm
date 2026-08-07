#!/usr/bin/env node
// One-time data prep for the `coastlines` example -- not part of the app build.
// Run with: node scripts/prepare-coastlines.mjs
//
// Downloads Natural Earth's 1:50m coastline GeoJSON (1428 features, mixed
// LineString/MultiLineString, ~2.3MB) and trims it down to something an example
// app can reasonably bundle: flattens MultiLineString into individual
// LineStrings, drops lines under MIN_POINTS (removes thousands of tiny-island
// specks), caps any remaining line at MAX_POINTS via uniform downsampling (the
// longest raw line is 9134 points -- LayerPath already supports live
// `simplificationTolerance` for render-time simplification, so this is purely
// about bundle size, not visual quality), and rounds coordinates to 4 decimals
// (~11m precision, far below anything visible on a phone screen).

import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const SOURCE_URL =
	'https://d2ad6b4ur7yvpq.cloudfront.net/naturalearth-3.3.0/ne_50m_coastline.geojson';
const MIN_POINTS = 15;
const MAX_POINTS = 150;
const OUT_PATH = path.join(
	path.dirname(fileURLToPath(import.meta.url)),
	'../src/assets/coastlines.geo.json'
);

const downsample = (line, cap) => {
	if (line.length <= cap) {
		return line;
	}
	const step = (line.length - 1) / (cap - 1);
	return Array.from({ length: cap }, (_, i) => line[Math.round(i * step)]);
};

const roundCoord = ([lng, lat]) => [
	Math.round(lng * 10000) / 10000,
	Math.round(lat * 10000) / 10000,
];

const main = async () => {
	console.log(`Fetching ${SOURCE_URL} ...`);
	const res = await fetch(SOURCE_URL);
	if (!res.ok) {
		throw new Error(`Fetch failed: ${res.status} ${res.statusText}`);
	}
	const source = await res.json();

	const features = source.features
		.flatMap((feature) =>
			feature.geometry.type === 'MultiLineString'
				? feature.geometry.coordinates.map((coordinates) => ({
						type: 'LineString',
						coordinates,
					}))
				: [feature.geometry]
		)
		.filter((geometry) => geometry.coordinates.length >= MIN_POINTS)
		.map((geometry) => ({
			type: 'Feature',
			properties: {},
			geometry: {
				type: 'LineString',
				coordinates: downsample(geometry.coordinates, MAX_POINTS).map(
					roundCoord
				),
			},
		}));

	const out = { type: 'FeatureCollection', features };
	await writeFile(OUT_PATH, JSON.stringify(out));

	const pointCount = features.reduce(
		(sum, f) => sum + f.geometry.coordinates.length,
		0
	);
	console.log(
		`Wrote ${OUT_PATH}: ${features.length} lines, ${pointCount} points`
	);
};

main().catch((err) => {
	console.error(err);
	process.exitCode = 1;
});
