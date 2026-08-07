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
	return uuid.length <= 30 ? uuid : uuid.slice(0, 30) + '…';
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
	uuid: {
		flexShrink: 1,
	},
	fragUuid: {
		flexShrink: 1,
		minWidth: 60,
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
		borderWidth: 1,
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
		<Text
			style={[
				entry.isResolved ? styles.text : styles.dim,
				styles.uuid,
			]}
			numberOfLines={1}
		>
			{truncateUuid(entry.uuid)}
		</Text>
		<Text style={entry.isShared ? styles.costShared : styles.costDedicated}>
			{entry.isShared ? `1/${entry.fragmentMemberCount}` : 'own'}
		</Text>
		<Text
			style={[styles.dim, styles.fragUuid]}
			numberOfLines={1}
		>
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
 * Entries that share a native fragment (i.e. {@link LayerDebugEntry.isShared}
 * is true because {@link LayerDebugEntry.fragmentMemberCount} > 1) are grouped
 * into collapsible {@link FragmentGroup} components. Entries with their own
 * dedicated native layer render as individual rows.
 *
 * This works correctly with mixed children — some under {@link SharedLayer}
 * wrappers and some outside — because grouping is decided per-entry, not by
 * a global binary flag. Groups and individual rows are interleaved in JSX
 * document order.
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

	const { layers } = info;

	// Build rendering groups: entries with fragmentMemberCount > 1 are
	// collapsed into a single FragmentGroup (ordered by first appearance);
	// individual-dedicated entries render as standalone rows.
	const groups: Array<
		| { type: 'entry'; entry: LayerDebugEntry }
		| { type: 'fragment'; fragmentUuid: string; entries: LayerDebugEntry[] }
	> = [];

	// Collect shared-fragment entries by fragment UUID, keyed for O(1)
	// lookup during the position-order walk below.
	const fragmentMap = new Map<string, LayerDebugEntry[]>();
	const emittedFragmentUuids = new Set<string>();

	for (const entry of layers) {
		if (entry.isShared && entry.fragmentUuid) {
			// Shared entry: collect into fragment group
			if (!fragmentMap.has(entry.fragmentUuid)) {
				fragmentMap.set(entry.fragmentUuid, []);
			}
			fragmentMap.get(entry.fragmentUuid)!.push(entry);
		}
	}

	// Walk layers in position order, emitting fragment groups at their
	// first member's position and individual entries in place.
	for (const entry of layers) {
		if (entry.isShared && entry.fragmentUuid) {
			if (!emittedFragmentUuids.has(entry.fragmentUuid)) {
				emittedFragmentUuids.add(entry.fragmentUuid);
				groups.push({
					type: 'fragment',
					fragmentUuid: entry.fragmentUuid,
					entries: fragmentMap.get(entry.fragmentUuid)!,
				});
			}
			// else: already emitted this fragment group at first member position
		} else {
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
