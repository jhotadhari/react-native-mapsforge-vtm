/**
 * Shared types for the layer scene model.
 *
 * The scene model is the single source of truth for layer-stack ordering.
 * It is deliberately free of any React dependency: order is derived from
 * committed data (anchor walk sequence, entry declarations, resolved native
 * uuids) via pure functions, so every ordering rule is unit-testable in
 * isolation.
 */

export type AnchorKind = 'layer' | 'fragment' | 'scope';

/**
 * One entry in the ordered anchor sequence produced by the committed-tree
 * walk. The walk runs over the committed view hierarchy, so its order IS the
 * React tree order — including bailouts, partial re-renders and async mounts.
 */
export type AnchorDescriptor = {
	/** Stable uid of the rendering component (scope / fragment / layer). */
	uid: string;
	/** What the anchor represents in the layer stack. */
	kind: AnchorKind;
	/**
	 * Layer type ('path', 'marker', 'shape', extension types). Absent for
	 * dedicated-only types (mapsforge, bitmap tile, ...) and for the
	 * SharedLayer wrapper (kind 'fragment' without its own native layer).
	 */
	layerType?: string;
	/**
	 * Owner id for kind 'fragment': the SharedLayer wrapper instance id or a
	 * LayerMarker component uid. Entries declare themselves against this id.
	 */
	fragmentId?: string;
	/**
	 * True when the layer type is hosted by a shared fragment manager
	 * (LayerPath / LayerShape / Marker outside a SharedLayer wrapper).
	 * Consecutive same-type shared anchors form a type-run fragment.
	 */
	shared?: boolean;
	/** Owning ReindexScope uid (from React context), if any. */
	scopeUid?: string;
	/** The owning scope's explicit order prop, carried for convenience. */
	scopeOrder?: number;
};

/**
 * Declaration of one entry (drawable) hosted by a shared fragment. Entries
 * do not render anchors — the fragment owner (SharedLayer / LayerMarker)
 * provides their order via sortIndex.
 */
export type EntryDeclaration = {
	/** Stable uid of the entry component (e.g. one LayerPath). */
	uid: string;
	/** Owner id — matches an anchor's fragmentId. */
	fragmentId: string;
	layerType: string;
	/**
	 * Owner-injected position among sibling entries (SharedLayer clones its
	 * children with this prop). Absent for wrapped children — those sort by
	 * declaration sequence after indexed entries.
	 */
	sortIndex?: number;
	/** Monotonic sequence assigned by the scene on declaration, for stable
	 * ordering of entries without sortIndex. */
	declarationSeq?: number;
};

/**
 * One native layer in the bottom→top plan. Its `uuid` is the value sent to
 * the native side: the dedicated layer's resolved uuid, or the deterministic
 * fragment uuid computed by {@link fragmentUuidFor}/{@link runUuidFor}.
 */
export type PlannedLayer = {
	uuid: string;
	kind: 'layer' | 'fragment';
	layerType?: string;
	/** Anchor uid for dedicated layers; first member for type-run fragments. */
	anchorUid?: string;
	/** Owner id for SharedLayer/LayerMarker fragments. */
	fragmentId?: string;
};

/**
 * Per-fragment entry ordering. `entryUids` holds ALL declared entries in
 * order; `resolvedEntryUids` only those whose native create resolved (the
 * subset eligible for priority assignment).
 */
export type FragmentPlan = {
	/** Native uuid of the fragment layer (deterministic key or resolved uuid). */
	uuid: string;
	layerType: string;
	entryUids: string[];
	resolvedEntryUids: string[];
};

/** Immutable snapshot of the desired layer stack (bottom → top). */
export type LayerPlan = {
	layers: PlannedLayer[];
	fragments: FragmentPlan[];
	scopes: ScopeInfo[];
	/**
	 * Resolved fragment uuid per type-run member anchor. The scene is the
	 * single authority: members must create their native entry under this
	 * uuid (never self-key), otherwise the collapse is silently lost.
	 */
	runKeysByAnchor: Map<string, string>;
	/**
	 * The set of fragment uuids present in {@link #fragments} — an O(1)
	 * membership check for the React bindings (`useSceneFragmentReady`).
	 */
	fragmentUuids: Set<string>;
};

export type ScopeInfo = {
	uid: string;
	order?: number;
	anchorIndex: number;
};
