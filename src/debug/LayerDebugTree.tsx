/**
 * External dependencies
 */
import { useState, type FC } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

/**
 * Internal dependencies
 */
import { useLayerDebugInfo } from './useLayerDebugInfo';
import type { LayerDebugEntry } from './useLayerDebugInfo';

/**
 * Props for {@link LayerDebugTree}.
 */
export interface LayerDebugTreeProps {
	/** Maximum height of the tree in points before scrolling (default 200). */
	maxHeight?: number;
}

// ── Color scheme by layer type ──────────────────────────────────────────

const TYPE_COLORS: Record<string, string> = {
	path: '#4a9eff',
	marker: '#e6c030',
	shape: '#4ec94e',
	bitmap: '#30c0c0',
	mapsforge: '#e09040',
	hillshading: '#b08060',
	scalebar: '#a0a0a0',
	mbtiles: '#b070d0',
	pathjts: '#70b0ff',
};

const FALLBACK_COLOR = '#ffffff';
const FRAGMENT_HEADER_COLOR = '#888888';

/** @visibleForTesting */
export const getTypeColor = (layerType: string | null): string =>
	layerType ? (TYPE_COLORS[layerType] ?? FALLBACK_COLOR) : FALLBACK_COLOR;

// ── Helpers ─────────────────────────────────────────────────────────────

const truncateUuid = (uuid: string | null): string => {
	if (!uuid) {
		return '---';
	}
	return uuid.length <= 8 ? uuid : uuid.slice(0, 8) + '…';
};

// ── Styles ──────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
	container: {
		paddingVertical: 4,
	},
	empty: {
		color: '#888888',
		fontStyle: 'italic',
		paddingVertical: 4,
		fontSize: 11,
	},
	row: {
		flexDirection: 'row',
		alignItems: 'center',
		paddingVertical: 2,
	},
	rowIndented: {
		paddingLeft: 16,
	},
	fragmentHeader: {
		flexDirection: 'row',
		alignItems: 'center',
		paddingVertical: 3,
	},
	fragmentToggle: {
		color: FRAGMENT_HEADER_COLOR,
		fontSize: 11,
		fontWeight: 'bold',
		width: 14,
	},
	fragmentLabel: {
		color: FRAGMENT_HEADER_COLOR,
		fontSize: 10,
		fontWeight: '600',
	},
	badge: {
		fontSize: 9,
		fontWeight: '700',
		paddingHorizontal: 3,
		paddingVertical: 1,
		borderRadius: 3,
		overflow: 'hidden',
		marginRight: 4,
		minWidth: 36,
		textAlign: 'center',
	},
	text: {
		color: '#cccccc',
		fontSize: 10,
		marginRight: 6,
	},
	dim: {
		color: '#777777',
		fontSize: 10,
		marginRight: 6,
	},
	costShared: {
		color: '#4ec94e',
		fontSize: 9,
		marginRight: 6,
	},
	costDedicated: {
		color: '#e09040',
		fontSize: 9,
		marginRight: 6,
	},
	more: {
		color: '#888888',
		fontSize: 10,
		fontStyle: 'italic',
		paddingTop: 4,
	},
});

// ── Badge component ─────────────────────────────────────────────────────

const TypeBadge: FC<{ layerType: string | null }> = ({ layerType }) => {
	const label = layerType ?? '?';
	const color = getTypeColor(layerType);
	return (
		<Text
			style={[
				styles.badge,
				{
					color,
					backgroundColor: color + '22',
					borderWidth: 1,
					borderColor: color + '44',
				},
			]}
		>
			{label}
		</Text>
	);
};

// ── Entry row ───────────────────────────────────────────────────────────

const EntryRow: FC<{ entry: LayerDebugEntry; indent: boolean }> = ({
	entry,
	indent,
}) => (
	<View style={[styles.row, indent && styles.rowIndented]}>
		<TypeBadge layerType={entry.layerType} />
		<Text style={styles.dim}>#{entry.positionIndex}</Text>
		<Text style={entry.isResolved ? styles.text : styles.dim}>
			{truncateUuid(entry.uuid)}
		</Text>
		<Text style={entry.isShared ? styles.costShared : styles.costDedicated}>
			{entry.isShared ? `1/${entry.fragmentMemberCount}` : 'own'}
		</Text>
		<Text style={styles.dim}>
			{entry.isShared ? truncateUuid(entry.fragmentUuid) : ''}
		</Text>
	</View>
);

// ── Fragment group (collapsible) ────────────────────────────────────────

const FragmentGroup: FC<{
	fragmentUuid: string;
	entries: LayerDebugEntry[];
}> = ({ fragmentUuid, entries }) => {
	const [collapsed, setCollapsed] = useState(true);

	return (
		<View>
			<Pressable
				onPress={() => setCollapsed((c) => !c)}
				style={styles.fragmentHeader}
			>
				<Text style={styles.fragmentToggle}>
					{collapsed ? '▶' : '▼'}
				</Text>
				<Text style={styles.fragmentLabel}>
					{truncateUuid(fragmentUuid)} ({entries.length}{' '}
					{entries.length === 1 ? 'layer' : 'layers'})
				</Text>
			</Pressable>
			{!collapsed &&
				entries.map((entry) => (
					<EntryRow
						key={entry.positionIndex}
						entry={entry}
						indent
					/>
				))}
		</View>
	);
};

// ── Main component ──────────────────────────────────────────────────────

/**
 * Renders a live, color-coded tree of every mounted layer component, showing
 * its type, render-order position, native uuid, and shared-layer fragment
 * assignment.
 *
 * When {@link LayerDebugInfo.groupingDepth} > 0 (i.e. a {@link SharedLayer}
 * wrapper is active), entries are grouped by shared fragment uuid into
 * collapsible groups.
 *
 * The tree is wrapped in a {@link ScrollView} so all layers remain
 * accessible even when the container is shorter than the content. Set
 * {@link LayerDebugTreeProps.maxHeight} to constrain the scrollable area
 * (default 200 points).
 */
const LayerDebugTree: FC<LayerDebugTreeProps> = ({ maxHeight = 200 }) => {
	const info = useLayerDebugInfo();

	if (info.layerCount === 0) {
		return (
			<View style={styles.container}>
				<Text style={styles.empty}>No layers mounted</Text>
			</View>
		);
	}

	const { layers, groupingDepth } = info;

	// Build rendering groups: when groupingDepth > 0, consecutive entries
	// sharing the same fragmentUuid are collapsed into a FragmentGroup.
	// Otherwise (groupingDepth === 0), every entry renders individually.
	const groups: Array<
		| { type: 'entry'; entry: LayerDebugEntry }
		| { type: 'fragment'; fragmentUuid: string; entries: LayerDebugEntry[] }
	> = [];

	if (groupingDepth > 0) {
		let run: LayerDebugEntry[] = [];
		let runFragmentUuid: string | null = null;

		const flushRun = () => {
			if (run.length === 0) {
				return;
			}
			const fragUuid = runFragmentUuid ?? '__dedicated__';
			groups.push({
				type: 'fragment',
				fragmentUuid: fragUuid,
				entries: run,
			});
			run = [];
			runFragmentUuid = null;
		};

		for (const entry of layers) {
			const frag = entry.fragmentUuid ?? '__dedicated__';
			if (frag !== runFragmentUuid) {
				flushRun();
				runFragmentUuid = frag;
			}
			run.push(entry);
		}
		flushRun();
	} else {
		for (const entry of layers) {
			groups.push({ type: 'entry', entry });
		}
	}

	const rendered = groups.map((group) => {
		if (group.type === 'entry') {
			return (
				<EntryRow
					key={group.entry.positionIndex}
					entry={group.entry}
					indent={false}
				/>
			);
		}
		return (
			<FragmentGroup
				key={group.fragmentUuid}
				fragmentUuid={group.fragmentUuid}
				entries={group.entries}
			/>
		);
	});

	return (
		<ScrollView
			style={[styles.container, { maxHeight }]}
			nestedScrollEnabled
		>
			{rendered}
		</ScrollView>
	);
};

export default LayerDebugTree;
