/**
 * External dependencies
 */
import { useMemo, useState, type FC } from 'react';
import { StyleSheet, View, Text, Switch } from 'react-native';
import {
	LayerBitmapTile,
	LayerPath,
	LayerPathJts,
	LayerScalebar,
	MapContainer,
	type GeometryStyle,
	type GeometryStyleJts,
	type Position,
} from 'react-native-mapsforge-vtm';

import type { Example } from '../../types';
import { handleMapEvent, sharedStyles } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
} from '../../components/ControlPanel';

/**
 * LayerPathJts — Dedicated JTS Path Layer
 *
 * Demonstrates LayerPathJts vs LayerPath. Each LayerPathJts instance owns
 * its own dedicated native org.oscim.layers.vector.PathLayer, giving
 * guaranteed z-order (one native layer per component), built-in
 * Douglas-Peucker generalization, and great-circle arc support.
 *
 * The example draws two routes from Rio to Lisbon across the Atlantic:
 * a straight line (LayerPath) and a great-circle arc (LayerPathJts).
 * Toggle generalization to see Douglas-Peucker simplification.
 */

const defaultCenter: Position = [-25, 17]; // Mid-Atlantic

// ── Route from Rio de Janeiro to Lisbon ─────────────────────────────────

const rioDeJaneiro: Position = [-43.1729, -22.9068];
const lisbon: Position = [-9.1393, 38.7223];

// Straight-line coordinates (LayerPath — rendered as a direct line).
const straightLine: Position[] = [rioDeJaneiro, lisbon];

/**
 * Approximation of a great-circle arc as a polyline, for comparison
 * with LayerPath (which doesn't have great-circle support).
 */
const generateArcApproximation = (numPoints: number): Position[] => {
	const lon1 = rioDeJaneiro[0]!;
	const lat1 = rioDeJaneiro[1]!;
	const lon2 = lisbon[0]!;
	const lat2 = lisbon[1]!;

	const toRad = (deg: number) => (deg * Math.PI) / 180;
	const toDeg = (rad: number) => (rad * 180) / Math.PI;

	const lat1Rad = toRad(lat1);
	const lon1Rad = toRad(lon1);
	const lat2Rad = toRad(lat2);
	const lon2Rad = toRad(lon2);

	const d =
		2 *
		Math.asin(
			Math.sqrt(
				Math.sin((lat2Rad - lat1Rad) / 2) ** 2 +
					Math.cos(lat1Rad) *
						Math.cos(lat2Rad) *
						Math.sin((lon2Rad - lon1Rad) / 2) ** 2
			)
		);

	const result: Position[] = [];
	for (let i = 0; i <= numPoints; i++) {
		const f = i / numPoints;
		const a = Math.sin((1 - f) * d) / Math.sin(d);
		const b = Math.sin(f * d) / Math.sin(d);
		const x =
			a * Math.cos(lat1Rad) * Math.cos(lon1Rad) +
			b * Math.cos(lat2Rad) * Math.cos(lon2Rad);
		const y =
			a * Math.cos(lat1Rad) * Math.sin(lon1Rad) +
			b * Math.cos(lat2Rad) * Math.sin(lon2Rad);
		const z = a * Math.sin(lat1Rad) + b * Math.sin(lat2Rad);
		result.push([
			toDeg(Math.atan2(y, x)),
			toDeg(Math.atan2(z, Math.sqrt(x * x + y * y))),
		]);
	}
	return result;
};

// ── Styles ───────────────────────────────────────────────────────────────

const styleStraight: GeometryStyle = {
	strokeColor: '#0000ff',
	strokeWidth: 3,
};

const styleGreatCircle: GeometryStyleJts = {
	strokeColor: '#ff4444',
	strokeWidth: 4,
	generalization: 1, // SMALL — built-in Douglas-Peucker
};

const styleArcApprox: GeometryStyle = {
	strokeColor: '#ffaa00',
	strokeWidth: 2,
};

// ── Legend text styles ──────────────────────────────────────────────────

const styles = StyleSheet.create({
	legendRed: {
		color: '#ff4444',
		fontWeight: 'bold',
	},
	legendBlue: {
		color: '#0000ff',
		fontWeight: 'bold',
	},
	legendOrange: {
		color: '#ffaa00',
		fontWeight: 'bold',
	},
});

// ── Controls ──────────────────────────────────────────────────────────────

const Controls: FC<{
	width: number;
	showJts: boolean;
	showPath: boolean;
	generalization: number;
	onToggleJts: () => void;
	onTogglePath: () => void;
	onGeneralization: (v: number) => void;
}> = ({
	width,
	showJts,
	showPath,
	generalization,
	onToggleJts,
	onTogglePath,
	onGeneralization,
}) => (
	<ControlPanel width={width}>
		<ControlSection title="LayerPathJts">
			<ControlRow>
				<Text style={sharedStyles.text}>
					Great-circle arc (LayerPathJts)
				</Text>
				<Switch
					value={showJts}
					onValueChange={onToggleJts}
				/>
			</ControlRow>
			<ControlRow>
				<Text style={sharedStyles.text}>Straight line (LayerPath)</Text>
				<Switch
					value={showPath}
					onValueChange={onTogglePath}
				/>
			</ControlRow>
		</ControlSection>
		<ControlSection title="Generalization">
			<ControlRow>
				<Text style={sharedStyles.text}>
					Douglas-Peucker: {generalization}
				</Text>
			</ControlRow>
			<ControlRow>
				<Text
					style={sharedStyles.text}
					onPress={() => onGeneralization(0)}
				>
					Off
				</Text>
				<Switch
					value={generalization > 0}
					onValueChange={(v) => onGeneralization(v ? 1 : 0)}
				/>
				<Text
					style={sharedStyles.text}
					onPress={() => onGeneralization(8)}
				>
					Aggressive
				</Text>
			</ControlRow>
		</ControlSection>

		<ControlSection title="What to look for">
			<Text style={sharedStyles.text}>
				• <Text style={styles.legendRed}>Red arc</Text> = LayerPathJts
				(dedicated layer, guaranteed z-order).{'\n'}•{' '}
				<Text style={styles.legendBlue}>Blue line</Text> = LayerPath
				straight line.{'\n'}•{' '}
				<Text style={styles.legendOrange}>Orange polyline</Text> =
				LayerPath arc approximation.{'\n\n'}• The great-circle arc
				should curve north of the straight line.{'\n'}• With
				generalization on, the arc uses fewer vertices (built-in
				Douglas-Peucker).{'\n'}• LayerPathJts guarantees correct z-order
				(one native layer per component).
			</Text>
		</ControlSection>
	</ControlPanel>
);

// ── Example component ─────────────────────────────────────────────────────

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [showJts, setShowJts] = useState(true);
	const [showPath, setShowPath] = useState(true);
	const [generalization, setGeneralization] = useState(1);

	const arcApprox = useMemo(() => generateArcApproximation(50), []);

	const stylesDynamic = useMemo(
		() => ({
			container: { width, height, gap: 16 },
			containerMap: { height, width },
		}),
		[width, height]
	);

	const stylesGeometry = useMemo(
		() => ({
			jts: {
				...styleGreatCircle,
				generalization,
			},
		}),
		[generalization]
	);

	return (
		<View style={stylesDynamic.container}>
			<Controls
				width={width}
				showJts={showJts}
				showPath={showPath}
				generalization={generalization}
				onToggleJts={() => setShowJts((v) => !v)}
				onTogglePath={() => setShowPath((v) => !v)}
				onGeneralization={setGeneralization}
			/>

			<View style={stylesDynamic.containerMap}>
				<MapContainer
					width={width}
					height={height}
					center={defaultCenter}
					zoomLevel={3}
					zoomMin={2}
					zoomMax={18}
					onMapUpdate={handleMapUpdate}
					onPause={handleMapEvent.onPause}
					onResume={handleMapEvent.onResume}
					onError={handleMapEvent.onError}
				>
					<LayerBitmapTile />
					{showPath && (
						<LayerPath
							coordinates={straightLine}
							style={styleStraight}
						/>
					)}
					{showPath && (
						<LayerPath
							coordinates={arcApprox}
							style={styleArcApprox}
						/>
					)}
					{showJts && (
						<LayerPathJts
							coordinates={arcApprox}
							style={stylesGeometry.jts}
						/>
					)}
					<LayerScalebar />
				</MapContainer>

				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'pathJts',
	label: 'LayerPathJts — Great-Circle Arcs',
	category: 'layers',
} as Example;
