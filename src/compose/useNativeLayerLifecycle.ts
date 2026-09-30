/**
 * External dependencies
 */
import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Internal dependencies
 */
import type { ErrorBase } from '../types';
import reportNativeError from '../reportNativeError';

export type CreateFlags = {
	triggerOnCreate: boolean;
	triggerOnChange: boolean;
};

export type RemoveFlags = {
	triggerOnRemove: boolean;
};

/**
 * Shared create/remove lifecycle for a native-side resource identified by a uuid (a map layer, or
 * an item like Marker living inside one). Owns the null -> false -> uuid state machine, creates on
 * mount and removes on unmount, and centralizes native error reporting -- callers only ever supply
 * how to actually create/remove the resource and how to turn the create response into whatever
 * shape their own onCreate/onChange/onRemove props expect.
 *
 * `enabled` gates whether `create` is even attempted (e.g. nativeNodeHandle being set, plus
 * whatever else a given resource additionally requires, like LayerPath needing non-empty
 * coordinates). Until a first attempt succeeds, uuid stays `null` whenever `enabled` is false, so
 * flipping `enabled` to true later (coordinates becoming non-empty, say) retries creation instead
 * of leaving uuid stuck.
 */
const useNativeLayerLifecycle = <TUuid extends string = string>({
	enabled,
	create,
	remove,
	onError,
}: {
	enabled: boolean;
	create: (flags: CreateFlags) => Promise<TUuid>;
	remove: (uuid: TUuid, flags: RemoveFlags) => Promise<boolean>;
	onError?: null | ((err: ErrorBase) => void);
}) => {
	const [uuid, setUuid] = useState<null | false | TUuid>(null);

	// Always point at the latest closure/value, so triggerCreate/triggerRemove never need
	// `create`/`remove`/`uuid` themselves in their own deps. This matters for `uuid` in
	// particular: it's read here rather than closed over via deps so that triggerRemove's
	// identity stays stable across the null -> false -> uuid transitions, instead of changing on
	// every one of them -- callers (like LayerBitmapTile's recreate-on-prop-change effect) depend
	// on triggerRemove/triggerCreate identity to know when to actually re-run, and a uuid-churned
	// identity would make that fire on every creation, not just on a real prop change.
	const createRef = useRef(create);
	createRef.current = create;
	const removeRef = useRef(remove);
	removeRef.current = remove;
	const uuidRef = useRef(uuid);
	uuidRef.current = uuid;
	// Mirror `enabled` so triggerCreate's guard always reflects the current
	// eligibility, matching the always-latest `createRef.current` it invokes.
	// Without this, a stale `enabled` captured in the closure can authorize a
	// create built in a different render (S11).
	const enabledRef = useRef(enabled);
	enabledRef.current = enabled;

	// Tracks whether the component is still mounted so in-flight create
	// resolutions can detect that the component unmounted before the
	// native resource was ready, and clean it up.
	const mountedRef = useRef(true);

	// Monotonic create generation. Every triggerCreate bumps it; a create
	// resolution whose generation is no longer current (a newer create was
	// issued while this one was in flight — e.g. a rapid re-key of the
	// owning type-run fragment) must NOT write uuidRef/setUuid. Without
	// this guard, out-of-order resolutions clobber the tracked uuid with a
	// stale one: the newest native resource becomes untracked, unmount
	// removes the stale uuid only, and the newest entry orphans as a
	// zombie. Stale resolutions instead remove the resource they just
	// created, so the native side never keeps an untracked duplicate.
	const createGenRef = useRef(0);

	const triggerCreate = useCallback(
		(
			flags: CreateFlags = {
				triggerOnCreate: true,
				triggerOnChange: false,
			}
		) => {
			if (!enabledRef.current) {
				return;
			}
			const gen = ++createGenRef.current;
			setUuid(false);
			createRef
				.current(flags)
				.then((newUuid) => {
					if (gen !== createGenRef.current || !mountedRef.current) {
						// Superseded by a newer create cycle, or the
						// component unmounted while this create was
						// in-flight. Either way this native resource is
						// untracked — remove it so it can't orphan.
						// (Balanced with triggerOnRemove like the legacy
						// unmount branch: create() already fired onCreate
						// for this uuid when triggerOnCreate was set.)
						removeRef
							.current(newUuid, {
								triggerOnRemove: true,
							})
							.catch((err: ErrorBase) => {
								reportNativeError(err, onError);
							});
						return;
					}
					// Update uuidRef IMMEDIATELY so the unmount
					// cleanup (which fires before React's re-render)
					// can see the real uuid and call removeLayer.
					// Without this, there is a race window where:
					// 1. Promise resolves → sees mountedRef true
					// 2. Component unmounts before React re-renders
					// 3. Unmount cleanup sees uuidRef=false → skips
					// 4. Native resource is orphaned (zombie).
					uuidRef.current = newUuid;
					setUuid(newUuid);
				})
				.catch((err: ErrorBase) => {
					reportNativeError(err, onError);
				});
		},
		[
			onError,
		]
	);

	const triggerRemove = useCallback(
		(flags: RemoveFlags = { triggerOnRemove: true }): Promise<boolean> => {
			const currentUuid = uuidRef.current;
			if (!currentUuid) {
				return Promise.resolve(false);
			}
			return new Promise<boolean>((resolve) => {
				removeRef
					.current(currentUuid, flags)
					.then((success) => {
						if (success) {
							setUuid(null);
						}
						resolve(success);
					})
					.catch((err: ErrorBase) => {
						reportNativeError(err, onError);
						resolve(false);
					});
			});
		},
		[
			onError,
		]
	);

	// Create whenever we become eligible to. This effect legitimately needs to re-run on every
	// `uuid` transition (that's how it notices "nothing exists yet, and we can now create it"),
	// but it has no cleanup of its own -- removal is handled by the separate, mount/unmount-only
	// effect below.
	useEffect(() => {
		if (uuid === null && enabled) {
			triggerCreate({ triggerOnCreate: true, triggerOnChange: false });
		}
	}, [
		enabled,
		uuid,
		triggerCreate,
	]);

	// Track mounted state so in-flight create resolutions can detect
	// that the component unmounted before the native resource was
	// ready. This MUST be separate from the unmount-remove effect
	// below: that effect needs a real uuid to enqueue a remove, but
	// this one runs unconditionally so triggerCreate can check
	// mountedRef.current regardless of uuid state.
	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
		};
	}, []);

	// Remove on true unmount only. Deliberately *not* depending on `uuid`/`triggerRemove`'s
	// per-render identity: if this effect re-ran on every uuid transition like the one above, its
	// cleanup -- meant for unmount -- would fire on every transition too, and since
	// triggerRemoveRef.current always reads the *latest* uuid, it would end up removing a
	// just-created resource instead of only ever removing on a real unmount.
	const triggerRemoveRef = useRef(triggerRemove);
	triggerRemoveRef.current = triggerRemove;
	useEffect(() => {
		return () => {
			triggerRemoveRef.current({ triggerOnRemove: true });
		};
	}, []);

	return { uuid, triggerCreate, triggerRemove };
};

export default useNativeLayerLifecycle;
