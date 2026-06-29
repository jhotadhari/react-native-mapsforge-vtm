/**
 * External dependencies
 */
import { useMemo, useState, type FC } from 'react';
import { View, Text } from 'react-native';
import {
	LayerBitmapTile,
	LayerShape,
	MapContainer,
	SharedLayer,
	type Position,
	type ShapeDefinition,
	type ShapeStyle,
} from 'react-native-mapsforge-vtm';

import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';

/**
 * Many Shapes — stress-test of the shared-layer architecture for LayerShape.
 *
 * Configurable count of shapes (100, 500, 1000, 3000) with a weighted mix of
 * circle, rectangle, hexagon, and polygon types, plus random positions and
 * colors. Wrapped in <SharedLayer> so all shapes collapse into shared
 * fragment layers — one native draw call regardless of count.
 */

// ── Constants ───────────────────────────────────────────────────────────

const defaultCenter: Position = [-77, -9]; // [ lng, lat ]

const responseInclude = {
	zoomLevel: 2,
	zoom: 2,
	scale: 2,
	zoomScale: 2,
	bearing: 2,
	roll: 2,
	tilt: 2,
	center: 2,
};

const countOptions = [
	100,
	500,
	1000,
	3000,
];

// ── Random generation helpers ────────────────────────────────────────────

type RandShapeType = 'circle' | 'rectangle' | 'hexagon' | 'polygon';

const shapeWeights: Record<RandShapeType, number> = {
	circle: 40,
	rectangle: 30,
	hexagon: 20,
	polygon: 10,
};

function pickWeightedType(): RandShapeType {
	const total = Object.values(shapeWeights).reduce((a, b) => a + b, 0);
	let r = Math.random() * total;
	for (const [type, weight] of Object.entries(shapeWeights)) {
		r -= weight;
		if (r <= 0) return type as RandShapeType;
	}
	return 'circle';
}

const hueColors = [
	'#ff4444',
	'#ff8800',
	'#ffdd00',
	'#44cc44',
	'#4488ff',
	'#8844ff',
	'#cc44cc',
];

function randomColor(): `#${string}` {
	return hueColors[
		Math.floor(Math.random() * hueColors.length)
	] as `#${string}`;
}

function randomShape(): { shape: ShapeDefinition; style: ShapeStyle } {
	const lng = defaultCenter[0]! + (Math.random() - 0.5) * 3;
	const lat = defaultCenter[1]! + (Math.random() - 0.5) * 3;
	const color = randomColor();
	const type = pickWeightedType();

	const style: ShapeStyle = {
		strokeWidth: 1,
		strokeColor: color,
		fillColor: color,
		fillAlpha: 0.3 + Math.random() * 0.3,
	};

	switch (type) {
		case 'circle':
			return {
				shape: {
					type: 'circle',
					center: [lng, lat],
					radiusKm: 5 + Math.random() * 30,
				},
				style,
			};
		case 'rectangle':
			return {
				shape: {
					type: 'rectangle',
					min: [lng, lat],
					max: [lng + Math.random() * 0.3, lat + Math.random() * 0.3],
				},
				style,
			};
		case 'hexagon':
			return {
				shape: {
					type: 'hexagon',
					center: [lng, lat],
					radiusKm: 5 + Math.random() * 25,
				},
				style,
			};
		case 'polygon': {
			const sides = 3 + Math.floor(Math.random() * 4); // 3–6 sides
			const radius = 0.05 + Math.random() * 0.2;
			const rings: Position[] = [];
			for (let s = 0; s < sides; s++) {
				const angle = (2 * Math.PI * s) / sides;
				rings.push([
					lng + Math.cos(angle) * radius,
					lat + Math.sin(angle) * radius,
				]);
			}
			return { shape: { type: 'polygon', rings }, style };
		}
	}
}

// ── Controls ────────────────────────────────────────────────────────────

const Controls: FC<{
	width: number;
	count: number;
	onSetCount: (count: number) => void;
	mountTime: number | null;
}> = ({ width, count, onSetCount, mountTime }) => (
	<ControlPanel width={width}>
		<ControlSection title="Count">
			{countOptions.map((n) => (
				<ControlRow key={n}>
					<Text
						style={[
							sharedStyles.text,
							{ fontWeight: n === count ? 'bold' : 'normal' },
						]}
						onPress={() => onSetCount(n)}
					>
						{n === count ? `▶ ${n}` : `  ${n}`}
					</Text>
				</ControlRow>
			))}
		</ControlSection>

		<ControlSection title="Metrics">
			<StatusLine
				label="Count"
				value={`${count}`}
			/>
			<StatusLine
				label="Mount time"
				value={mountTime != null ? `${mountTime.toFixed(0)} ms` : '-'}
			/>
			<StatusLine
				label="Native layers"
				value="1 fragment (shared)"
			/>
		</ControlSection>

		<ControlSection title="What to look for">
			<Text style={sharedStyles.text}>
				All shapes render via a single shared native VectorLayer (per
				fragment). Performance at 3000 shapes should be noticeably
				better than dedicated-layer mode.{'\n\n'}
				Check that pan/zoom remains smooth and all shapes render without
				flickering.
			</Text>
		</ControlSection>
	</ControlPanel>
);

// ── Example component ───────────────────────────────────────────────────

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [count, setCount] = useState(100);
	const [mountTime, setMountTime] = useState<number | null>(null);

	const shapes = useMemo(() => {
		const start = Date.now();
		const result = Array.from({ length: count }, (_, i) => ({
			key: i,
			...randomShape(),
		}));
		const elapsed = Date.now() - start;
		setTimeout(() => setMountTime(elapsed), 0);
		return result;
	}, [count]);

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				width={width}
				count={count}
				onSetCount={(n) => {
					setCount(n);
				}}
				mountTime={mountTime}
			/>

			<View style={{ height, width }}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					responseInclude={responseInclude}
					zoomLevel={6}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
					<SharedLayer>
						{shapes.map(({ key, shape, style }) => (
							<LayerShape
								key={key}
								shape={shape}
								style={style}
							/>
						))}
					</SharedLayer>
				</MapContainer>

				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'manyShapes',
	label: 'Many Shapes (stress test)',
	category: 'layers',
} as Example;
