import { useState, type FC, type ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { sharedStyles } from '../sharedDeps';

// Shared shell for an example screen's floating controls -- every example used to either
// re-declare an identical `controls`/`section`/`flexRow` style block, or grow an always-visible
// `sharedStyles.info` block tall enough to cover most of the map (e.g. the `mapsforge` example's
// render-style/overlay picker). `ControlPanel` now renders as a collapsed toggle button by
// default and only takes up screen space -- as a scrollable sidebar drawer -- once opened, so the
// map underneath is unobstructed until the user actually wants the controls.

const DRAWER_WIDTH_RATIO = 0.85;
const DRAWER_MAX_WIDTH = 300;

export const ControlPanel: FC<{
	width: number;
	children: ReactNode;
	/** Constrain the drawer height (points) so it doesn't overlap bottom overlays. */
	maxHeight?: number;
}> = ({ width, children, maxHeight }) => {
	const [open, setOpen] = useState(false);

	if (!open) {
		return (
			<Pressable
				style={styles.toggle}
				onPress={() => setOpen(true)}
			>
				<Text style={styles.toggleText}>{'☰ Controls'}</Text>
			</Pressable>
		);
	}

	const drawerWidth = Math.min(width * DRAWER_WIDTH_RATIO, DRAWER_MAX_WIDTH);

	return (
		<View
			style={[
				maxHeight != null ? styles.drawerCapped : styles.drawer,
				{ width: drawerWidth },
				maxHeight != null ? { maxHeight } : null,
			]}
		>
			<View style={styles.drawerHeader}>
				<Text style={styles.toggleText}>{'Controls'}</Text>
				<Pressable onPress={() => setOpen(false)}>
					<Text style={styles.toggleIcon}>{'✕'}</Text>
				</Pressable>
			</View>
			<ScrollView contentContainerStyle={styles.drawerContent}>
				{children}
			</ScrollView>
		</View>
	);
};

export const ControlSection: FC<{
	title?: string;
	children: ReactNode;
}> = ({ title, children }) => (
	<View style={styles.section}>
		{!!title && (
			<View style={styles.flexRow}>
				<Text style={sharedStyles.text}>{title}</Text>
			</View>
		)}
		{children}
	</View>
);

export const ControlRow: FC<{ children: ReactNode }> = ({ children }) => (
	<View style={styles.flexRow}>{children}</View>
);

// Replaces the repeated "Last action" / "Status" row -- pass `busy` while an async useMap()
// action is in flight to show `busyValue` instead of the resolved `value`.
export const StatusLine: FC<{
	label: string;
	value: string;
	busy?: boolean;
	busyValue?: string;
}> = ({ label, value, busy, busyValue = 'working...' }) => (
	<ControlRow>
		<Text style={sharedStyles.text}>{label}</Text>
		<Text style={sharedStyles.text}>{busy ? busyValue : value}</Text>
	</ControlRow>
);

const styles = StyleSheet.create({
	toggle: {
		position: 'absolute',
		top: 12,
		left: 12,
		zIndex: 9,
		backgroundColor: '#000000',
		paddingVertical: 8,
		paddingHorizontal: 12,
		borderRadius: 8,
	},
	toggleText: {
		color: '#fff',
		fontWeight: 'bold',
	},
	toggleIcon: {
		color: '#fff',
		fontWeight: 'bold',
		fontSize: 20,
		paddingVertical: 4,
		paddingHorizontal: 10,
		borderWidth: 1,
		borderColor: '#fff',
	},
	drawer: {
		position: 'absolute',
		top: 0,
		left: 0,
		bottom: 0,
		zIndex: 9,
		backgroundColor: '#000000',
	},
	// When maxHeight is passed, skip `bottom: 0` so the drawer height is
	// determined by the content capped at maxHeight — it won't stretch
	// over bottom-positioned overlays like the debug tree.
	drawerCapped: {
		position: 'absolute',
		top: 0,
		left: 0,
		zIndex: 9,
		backgroundColor: '#000000',
	},
	drawerHeader: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'space-between',
		padding: 12,
	},
	drawerContent: {
		padding: 16,
		gap: 16,
	},
	section: {
		alignItems: 'center',
		justifyContent: 'space-evenly',
	},
	// `flexWrap` matters here in a way it didn't for the old full-width panel -- rows with 3-4
	// buttons (e.g. fly-ease's easing comparisons) need to wrap onto multiple lines now that
	// they're laid out inside a much narrower sidebar drawer instead of the full screen width.
	flexRow: {
		flexDirection: 'row',
		flexWrap: 'wrap',
		justifyContent: 'space-evenly',
		gap: 16,
	},
});
