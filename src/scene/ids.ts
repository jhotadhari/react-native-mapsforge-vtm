/**
 * Deterministic native-identity builders shared between the scene model and
 * the React bindings. Both sides MUST produce identical strings for the same
 * inputs — the JS side passes these as `fragmentUuid` to native createLayer,
 * and the scene uses them to build the ordered uuid list.
 */

/**
 * Native uuid for a SharedLayer-hosted fragment:
 * `<SharedLayer instance id>` + layer type.
 */
export const fragmentUuidFor = (
	fragmentId: string,
	layerType: string
): string => `frag:${fragmentId}:${layerType}`;

/**
 * Native uuid for an implicit type-run fragment, keyed by its first member's
 * anchor uid. Removing the first member re-keys the run (a new fragment is
 * created) — acceptable because run membership itself changed.
 */
export const runUuidFor = (firstMemberUid: string): string =>
	`run:${firstMemberUid}`;
