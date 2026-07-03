import { useEffect, useMemo, useState, type FC } from 'react';
import { NativeModules, View, Button } from 'react-native';
import {
	LayerBitmapTile,
	LayerMarker,
	Marker,
	MapContainer,
	type Position,
	type SymbolParams,
} from 'react-native-mapsforge-vtm';
import Center from '../../components/Center';
import {
	ControlPanel,
	ControlRow,
	ControlSection,
	StatusLine,
} from '../../components/ControlPanel';
import type { Example } from '../../types';
import { handleMapEvent } from '../../sharedDeps';
import MapInfo, { useMapInfo } from '../../components/MapInfo';

// Icons & fonts are bundled in example/android/app/src/main/assets/ and copied to
// the app's files directory at first launch by IconAssetsModule (a tiny native
// module registered in MainApplication.kt).  The base path is exposed as a
// constant so JS can build file:// paths without hardcoding the internals dir.
//
// SVG + PNG asset files were placed in assets/icons/; the Material Design Icons
// TTF (from @react-native-vector-icons/material-design-icons) was placed in
// assets/fonts/.  See example/scripts/icons/ for copies of the icon files.

const IconAssetsModule = NativeModules.IconAssetsModule;
const BASE_PATH: string = IconAssetsModule?.getConstants?.()?.basePath ?? '';

// Glyph map subset from @react-native-vector-icons/material-design-icons
// (glyphmaps/MaterialDesignIcons.json).  Codepoints are decimal Unicode values.
const MDI_GLYPH: Record<string, number> = {
	home: 983772, // U+F02DC
	heart: 983761, // U+F02D1
	star: 984270, // U+F04CE
	'map-marker': 983886, // U+F034E
	information: 983804, // U+F02FC
	flag: 983611, // U+F023B
};

const defaultCenter: Position = [8.5, 47.4]; // Switzerland, zoom 6 shows the Alps nicely

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

// ---------------------------------------------------------------------------
// Marker data families
// ---------------------------------------------------------------------------

// Family 1 — Colored shape markers (demonstrates the `shape` param)
type ShapeMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const shapeMarkers: ShapeMarker[] = [
	{
		key: 'red-circle',
		position: [7.4, 47.5],
		symbol: {
			shape: 'circle',
			fillColor: '#e53935',
			strokeColor: '#b71c1c',
			strokeWidth: 3,
			width: 28,
			height: 28,
		},
	},
	{
		key: 'blue-circle',
		position: [7.9, 47.5],
		symbol: {
			shape: 'circle',
			fillColor: '#1e88e5',
			strokeColor: '#0d47a1',
			strokeWidth: 3,
			width: 28,
			height: 28,
		},
	},
	{
		key: 'green-square',
		position: [8.4, 47.5],
		symbol: {
			shape: 'square',
			fillColor: '#43a047',
			strokeColor: '#1b5e20',
			strokeWidth: 3,
			width: 28,
			height: 28,
		},
	},
	{
		key: 'orange-square',
		position: [8.9, 47.5],
		symbol: {
			shape: 'square',
			fillColor: '#fb8c00',
			strokeColor: '#e65100',
			strokeWidth: 3,
			width: 28,
			height: 28,
		},
	},
	{
		key: 'purple-diamond',
		position: [9.4, 47.5],
		symbol: {
			shape: 'diamond',
			fillColor: '#8e24aa',
			strokeColor: '#4a148c',
			strokeWidth: 3,
			width: 32,
			height: 32,
		},
	},
	{
		key: 'teal-triangle',
		position: [9.9, 47.5],
		symbol: {
			shape: 'triangle',
			fillColor: '#00897b',
			strokeColor: '#004d40',
			strokeWidth: 3,
			width: 32,
			height: 32,
		},
	},
];

// Family 2 — Unicode symbol markers (demonstrates `text` with Unicode chars)
type UnicodeMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const unicodeMarkers: UnicodeMarker[] = [
	{
		key: 'star',
		position: [7.4, 47.1],
		symbol: {
			text: '★',
			fillColor: '#ffc107',
			textColor: '#ffffff',
			textSize: 22,
			width: 34,
			height: 34,
		},
	},
	{
		key: 'heart',
		position: [7.9, 47.1],
		symbol: {
			text: '♥',
			fillColor: '#e91e63',
			textColor: '#ffffff',
			textSize: 22,
			width: 34,
			height: 34,
		},
	},
	{
		key: 'diamond-char',
		position: [8.4, 47.1],
		symbol: {
			text: '◆',
			fillColor: '#2196f3',
			textColor: '#ffffff',
			textSize: 22,
			width: 34,
			height: 34,
		},
	},
	{
		key: 'triangle-up',
		position: [8.9, 47.1],
		symbol: {
			text: '▲',
			fillColor: '#4caf50',
			textColor: '#ffffff',
			textSize: 20,
			width: 34,
			height: 34,
		},
	},
	{
		key: 'circle-dot',
		position: [9.4, 47.1],
		symbol: {
			text: '●',
			fillColor: '#ff5722',
			textColor: '#ffffff',
			textSize: 24,
			width: 34,
			height: 34,
		},
	},
	{
		key: 'square-block',
		position: [9.9, 47.1],
		symbol: {
			text: '■',
			fillColor: '#9c27b0',
			textColor: '#ffffff',
			textSize: 22,
			width: 34,
			height: 34,
		},
	},
];

// Family 3 — Large pin-style markers (text + fill + stroke, hotspotPlace BOTTOM_CENTER)
type LargePinMarker = {
	key: string;
	name: string;
	position: Position;
	symbol: SymbolParams;
};
const largePinMarkers: LargePinMarker[] = [
	{
		key: 'zurich',
		name: 'Zürich',
		position: [8.5417, 47.3769],
		symbol: {
			text: 'Z',
			fillColor: '#1565c0',
			strokeColor: '#0d47a1',
			strokeWidth: 3,
			textColor: '#ffffff',
			textSize: 24,
			fontFamily: 'DEFAULT_BOLD',
			width: 48,
			height: 48,
			hotspotPlace: 'BOTTOM_CENTER',
		},
	},
	{
		key: 'bern',
		name: 'Bern',
		position: [7.4474, 46.948],
		symbol: {
			text: 'B',
			fillColor: '#c62828',
			strokeColor: '#b71c1c',
			strokeWidth: 3,
			textColor: '#ffffff',
			textSize: 24,
			fontFamily: 'DEFAULT_BOLD',
			width: 48,
			height: 48,
			hotspotPlace: 'BOTTOM_CENTER',
		},
	},
	{
		key: 'geneva',
		name: 'Geneva',
		position: [6.1432, 46.2044],
		symbol: {
			text: 'G',
			fillColor: '#2e7d32',
			strokeColor: '#1b5e20',
			strokeWidth: 3,
			textColor: '#ffffff',
			textSize: 24,
			fontFamily: 'DEFAULT_BOLD',
			width: 48,
			height: 48,
			hotspotPlace: 'BOTTOM_CENTER',
		},
	},
	{
		key: 'lugano',
		name: 'Lugano',
		position: [8.9511, 46.0037],
		symbol: {
			text: 'L',
			fillColor: '#e65100',
			strokeColor: '#bf360c',
			strokeWidth: 3,
			textColor: '#ffffff',
			textSize: 24,
			fontFamily: 'DEFAULT_BOLD',
			width: 48,
			height: 48,
			hotspotPlace: 'BOTTOM_CENTER',
		},
	},
];

// Family 4 — Hotspot place comparison (same position, different hotspotPlace)
type HotspotMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const hotspotMarkers: HotspotMarker[] = [
	{
		key: 'hotspot-bottom',
		position: [8.56, 46.85],
		symbol: {
			fillColor: '#00bcd4',
			strokeColor: '#006064',
			strokeWidth: 2,
			width: 24,
			height: 32,
			hotspotPlace: 'BOTTOM_CENTER',
		},
	},
	{
		key: 'hotspot-center',
		position: [8.6, 46.85],
		symbol: {
			fillColor: '#ff9800',
			strokeColor: '#e65100',
			strokeWidth: 2,
			width: 24,
			height: 32,
			hotspotPlace: 'CENTER',
		},
	},
	{
		key: 'hotspot-top',
		position: [8.64, 46.85],
		symbol: {
			fillColor: '#e91e63',
			strokeColor: '#880e4f',
			strokeWidth: 2,
			width: 24,
			height: 32,
			hotspotPlace: 'TOP_CENTER',
		},
	},
];

// Family 5 — SVG file icons (demonstrates `filePath` for vector icons)
type SvgMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const buildSvgMarkers = (basePath: string): SvgMarker[] => [
	{
		key: 'svg-restaurant',
		position: [7.2, 46.5],
		symbol: {
			filePath: `${basePath}/icons/restaurant.svg`,
			fillColor: '#e53935',
			width: 36,
			height: 36,
		},
	},
	{
		key: 'svg-hotel',
		position: [7.8, 46.5],
		symbol: {
			filePath: `${basePath}/icons/hotel.svg`,
			fillColor: '#1e88e5',
			width: 36,
			height: 36,
		},
	},
	{
		key: 'svg-gas-station',
		position: [8.4, 46.5],
		symbol: {
			filePath: `${basePath}/icons/gas-station.svg`,
			fillColor: '#ff8f00',
			width: 36,
			height: 36,
		},
	},
	{
		key: 'svg-parking',
		position: [9.0, 46.5],
		symbol: {
			filePath: `${basePath}/icons/parking.svg`,
			fillColor: '#43a047',
			width: 36,
			height: 36,
		},
	},
	{
		key: 'svg-hospital',
		position: [9.6, 46.5],
		symbol: {
			filePath: `${basePath}/icons/hospital.svg`,
			fillColor: '#ffffff',
			strokeColor: '#c62828',
			strokeWidth: 2,
			width: 36,
			height: 36,
		},
	},
];

// Family 6 — PNG file icons (demonstrates `filePath` for raster images)
type PngMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const buildPngMarkers = (basePath: string): PngMarker[] => [
	{
		key: 'png-cafe',
		position: [7.2, 46.1],
		symbol: {
			filePath: `${basePath}/icons/cafe.png`,
			width: 40,
			height: 40,
		},
	},
	{
		key: 'png-airport',
		position: [7.8, 46.1],
		symbol: {
			filePath: `${basePath}/icons/airport.png`,
			width: 40,
			height: 40,
		},
	},
	{
		key: 'png-camping',
		position: [8.4, 46.1],
		symbol: {
			filePath: `${basePath}/icons/camping.png`,
			width: 40,
			height: 40,
		},
	},
];

// Family 7 — Icon font markers (demonstrates `fontPath` for custom TTF, enabling
// react-native-vector-icons and similar icon-font libraries)
type IconFontMarker = {
	key: string;
	position: Position;
	symbol: SymbolParams;
};
const buildIconFontMarkers = (basePath: string): IconFontMarker[] => {
	const fontPath = `${basePath}/fonts/MaterialDesignIcons.ttf`;
	return [
		{
			key: 'mdi-home',
			position: [9.0, 46.1],
			symbol: {
				text: String.fromCodePoint(MDI_GLYPH.home),
				fontPath,
				fillColor: '#1565c0',
				textColor: '#ffffff',
				textSize: 22,
				width: 40,
				height: 40,
			},
		},
		{
			key: 'mdi-heart',
			position: [9.6, 46.1],
			symbol: {
				text: String.fromCodePoint(MDI_GLYPH.heart),
				fontPath,
				fillColor: '#c62828',
				textColor: '#ffffff',
				textSize: 22,
				width: 40,
				height: 40,
			},
		},
		{
			key: 'mdi-map-marker',
			position: [10.2, 46.1],
			symbol: {
				text: String.fromCodePoint(MDI_GLYPH['map-marker']),
				fontPath,
				fillColor: '#2e7d32',
				textColor: '#ffffff',
				textSize: 22,
				width: 40,
				height: 40,
			},
		},
		{
			key: 'mdi-star',
			position: [10.8, 46.1],
			symbol: {
				text: String.fromCodePoint(MDI_GLYPH.star),
				fontPath,
				fillColor: '#ff8f00',
				textColor: '#ffffff',
				textSize: 22,
				width: 40,
				height: 40,
			},
		},
		{
			key: 'mdi-information',
			position: [7.4, 46.7],
			symbol: {
				text: String.fromCodePoint(MDI_GLYPH.information),
				fontPath,
				fillColor: '#00897b',
				textColor: '#ffffff',
				textSize: 22,
				width: 40,
				height: 40,
			},
		},
	];
};

// ---------------------------------------------------------------------------
// Controls panel
// ---------------------------------------------------------------------------

const Controls: FC<{
	mapWidth: number;
	basePath: string;
	showSvg: boolean;
	showPng: boolean;
	showIconFont: boolean;
	onToggleSvg: () => void;
	onTogglePng: () => void;
	onToggleIconFont: () => void;
}> = ({
	mapWidth,
	basePath,
	showSvg,
	showPng,
	showIconFont,
	onToggleSvg,
	onTogglePng,
	onToggleIconFont,
}) => (
	<ControlPanel width={mapWidth}>
		<ControlSection title="Family 1: Colored shapes (shape param)">
			<StatusLine
				label="Shapes"
				value="circle, square, diamond, triangle"
			/>
			<StatusLine
				label="Count"
				value={`${shapeMarkers.length} markers`}
			/>
		</ControlSection>

		<ControlSection title="Family 2: Unicode symbols (text param)">
			<StatusLine
				label="Glyphs"
				value="★ ♥ ◆ ▲ ● ■"
			/>
			<StatusLine
				label="Count"
				value={`${unicodeMarkers.length} markers`}
			/>
		</ControlSection>

		<ControlSection title="Family 3: Large pin-style (hotspotPlace)">
			<StatusLine
				label="Cities"
				value="Zürich, Bern, Geneva, Lugano"
			/>
			<StatusLine
				label="Hotspot"
				value="BOTTOM_CENTER"
			/>
		</ControlSection>

		<ControlSection title="Family 4: Hotspot comparison">
			<StatusLine
				label="Values"
				value="BOTTOM_CENTER, CENTER, TOP_CENTER"
			/>
			<StatusLine
				label="Same position, slightly offset"
				value=""
			/>
		</ControlSection>

		<ControlSection title="Family 5: SVG file icons (filePath)">
			<ControlRow>
				<Button
					title={showSvg ? 'Hide SVG icons' : 'Show SVG icons'}
					onPress={onToggleSvg}
				/>
			</ControlRow>
			<StatusLine
				label="POIs"
				value="Restaurant, Hotel, Gas, Parking, Hospital"
			/>
		</ControlSection>

		<ControlSection title="Family 6: PNG file icons (filePath)">
			<ControlRow>
				<Button
					title={showPng ? 'Hide PNG icons' : 'Show PNG icons'}
					onPress={onTogglePng}
				/>
			</ControlRow>
			<StatusLine
				label="POIs"
				value="Cafe, Airport, Camping"
			/>
		</ControlSection>

		<ControlSection title="Family 7: Icon font (fontPath + TTF)">
			<ControlRow>
				<Button
					title={
						showIconFont
							? 'Hide icon-font icons'
							: 'Show icon-font icons'
					}
					onPress={onToggleIconFont}
				/>
			</ControlRow>
			<StatusLine
				label="Font"
				value="MaterialDesignIcons.ttf (MDI glyph map)"
			/>
			<StatusLine
				label="Icons"
				value="home, heart, map-marker, star, info"
			/>
		</ControlSection>

		<ControlSection title="IconAssetsModule">
			<StatusLine
				label="basePath"
				value={basePath || '(not available)'}
			/>
		</ControlSection>
	</ControlPanel>
);

// ---------------------------------------------------------------------------
// Example component
// ---------------------------------------------------------------------------

const ExampleComponent: FC<{
	height: number;
	width: number;
}> = ({ height, width }) => {
	const { handleMapUpdate, info } = useMapInfo();

	const [showSvg, setShowSvg] = useState(true);
	const [showPng, setShowPng] = useState(true);
	const [showIconFont, setShowIconFont] = useState(true);
	const [basePath, setBasePath] = useState(BASE_PATH);

	// Ensure assets are copied from APK assets → files dir on first launch.
	useEffect(() => {
		if (IconAssetsModule?.ensureAssetsCopied) {
			IconAssetsModule.ensureAssetsCopied();
			// Re-read basePath in case it changed (it shouldn't, but just in case).
			const p = IconAssetsModule.getConstants?.()?.basePath;
			if (p && p !== basePath) {
				setBasePath(p);
			}
		}
	}, [basePath]);

	// Build file-based marker arrays (depend on basePath).
	const svgMarkers = useMemo(() => buildSvgMarkers(basePath), [basePath]);
	const pngMarkers = useMemo(() => buildPngMarkers(basePath), [basePath]);
	const iconFontMarkers = useMemo(
		() => buildIconFontMarkers(basePath),
		[basePath]
	);

	return (
		<View style={{ width, height, gap: 16 }}>
			<Controls
				mapWidth={width}
				basePath={basePath}
				showSvg={showSvg}
				showPng={showPng}
				showIconFont={showIconFont}
				onToggleSvg={() => setShowSvg((v) => !v)}
				onTogglePng={() => setShowPng((v) => !v)}
				onToggleIconFont={() => setShowIconFont((v) => !v)}
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

					<LayerMarker>
						{/* Family 1: shape markers */}
						{shapeMarkers.map((m) => (
							<Marker
								key={m.key}
								position={m.position}
								symbol={m.symbol}
							/>
						))}

						{/* Family 2: Unicode symbol markers */}
						{unicodeMarkers.map((m) => (
							<Marker
								key={m.key}
								position={m.position}
								symbol={m.symbol}
							/>
						))}

						{/* Family 3: Large pin-style markers */}
						{largePinMarkers.map((m) => (
							<Marker
								key={m.key}
								position={m.position}
								title={m.name}
								symbol={m.symbol}
							/>
						))}

						{/* Family 4: Hotspot comparison */}
						{hotspotMarkers.map((m) => (
							<Marker
								key={m.key}
								position={m.position}
								symbol={m.symbol}
							/>
						))}

						{/* Family 5: SVG file icons */}
						{showSvg &&
							svgMarkers.map((m) => (
								<Marker
									key={m.key}
									position={m.position}
									symbol={m.symbol}
								/>
							))}

						{/* Family 6: PNG file icons */}
						{showPng &&
							pngMarkers.map((m) => (
								<Marker
									key={m.key}
									position={m.position}
									symbol={m.symbol}
								/>
							))}

						{/* Family 7: Icon font markers */}
						{showIconFont &&
							iconFontMarkers.map((m) => (
								<Marker
									key={m.key}
									position={m.position}
									symbol={m.symbol}
								/>
							))}
					</LayerMarker>
				</MapContainer>

				<Center
					height={height}
					width={width}
				/>
				<MapInfo info={info} />
			</View>
		</View>
	);
};

export default {
	ExampleComponent,
	key: 'icons',
	label: 'Icons',
	category: 'layers',
} as Example;
