/**
 * Tests for useNativeLayerLifecycle's race handling: out-of-order create
 * resolutions must not clobber the tracked uuid (the zombie-entry bug), and
 * unmount must always clean up the newest native resource.
 *
 * React 19 has no react-test-renderer, so the tests run the hook against a
 * minimal React emulation: per-hook slots for useState/useRef/useCallback/
 * useEffect with render-scheduled flushes and mount-order effect cleanups —
 * enough semantics to drive the hook's state machine deterministically.
 */

import useNativeLayerLifecycle, {
	type CreateFlags,
	type RemoveFlags,
} from '../useNativeLayerLifecycle';

// ── Minimal React emulation ─────────────────────────────────────────────

type StateSlot = { value: unknown; setter: (next: unknown) => void };
type RefSlot = { current: unknown };
type CallbackSlot = { fn: any; deps: unknown[] };
type EffectSlot = {
	fn: () => void | (() => void);
	deps: unknown[];
	cleanup: (() => void) | null;
	ran: boolean;
};

interface Harness {
	useState<T>(initial: T): [T, (next: T) => void];
	useRef<T>(initial: T): { current: T };
	useCallback<T>(fn: T, deps: unknown[]): T;
	useEffect(fn: () => void | (() => void), deps: unknown[]): void;
}

let mockActive: Harness | null = null;

jest.mock('react', () => ({
	useState: (initial: unknown) => mockActive!.useState(initial),
	useRef: (initial: unknown) => mockActive!.useRef(initial),
	useCallback: (fn: any, deps: unknown[]) =>
		mockActive!.useCallback(fn, deps),
	useEffect: (fn: any, deps: unknown[]) => mockActive!.useEffect(fn, deps),
}));

type Lifecycle = {
	enabled: boolean;
	create: (flags: CreateFlags) => Promise<string>;
	remove: (uuid: string, flags: RemoveFlags) => Promise<boolean>;
	onError?: null | ((err: unknown) => void);
};

type Rendered = {
	uuid: () => null | false | string;
	triggerCreate: (flags?: CreateFlags) => void;
	triggerRemove: (flags?: RemoveFlags) => Promise<boolean>;
	rerender: () => void;
	unmount: () => void;
};

const depsChanged = (prev: unknown[], next: unknown[]): boolean =>
	prev.length !== next.length ||
	prev.some((value, index) => !Object.is(value, next[index]));

const renderLifecycle = (lifecycle: Lifecycle): Rendered => {
	const states: StateSlot[] = [];
	const refs: RefSlot[] = [];
	const callbacks: CallbackSlot[] = [];
	const effects: EffectSlot[] = [];

	let cursor = 0;
	let mounted = true;
	let renderScheduled = false;
	let latest: {
		uuid: null | false | string;
		triggerCreate: (flags?: CreateFlags) => void;
		triggerRemove: (flags?: RemoveFlags) => Promise<boolean>;
	} | null = null;

	const harness: Harness = {
		useState<T>(initial: T): [T, (next: T) => void] {
			const index = cursor++;
			if (states[index] === undefined) {
				states[index] = {
					value: initial,
					setter: (next: unknown) => {
						if (Object.is(states[index]!.value, next)) {
							return;
						}
						states[index]!.value = next;
						scheduleRender();
					},
				};
			}
			return [
				states[index]!.value as T,
				states[index]!.setter as (next: T) => void,
			];
		},
		useRef<T>(initial: T): { current: T } {
			const index = cursor++;
			if (refs[index] === undefined) {
				refs[index] = { current: initial };
			}
			return refs[index] as { current: T };
		},
		useCallback<T>(fn: T, deps: unknown[]): T {
			const index = cursor++;
			if (
				callbacks[index] !== undefined &&
				!depsChanged(callbacks[index]!.deps, deps)
			) {
				return callbacks[index]!.fn as T;
			}
			callbacks[index] = { fn, deps };
			return fn;
		},
		useEffect(fn: () => void | (() => void), deps: unknown[]): void {
			const index = cursor++;
			if (effects[index] === undefined) {
				effects[index] = { fn, deps, cleanup: null, ran: false };
				return;
			}
			if (!depsChanged(effects[index]!.deps, deps)) {
				return;
			}
			effects[index] = {
				fn,
				deps,
				cleanup: effects[index]!.cleanup,
				ran: false,
			};
		},
	};

	const flushEffects = () => {
		for (let i = 0; i < effects.length; i++) {
			const effect = effects[i];
			if (effect === undefined) {
				continue;
			}
			if (!effect.ran) {
				if (effect.cleanup !== null) {
					effect.cleanup();
					effect.cleanup = null;
				}
				effect.ran = true;
				effect.cleanup = effect.fn() ?? null;
			}
		}
	};

	const render = () => {
		cursor = 0;
		// The harness deliberately drives the hook from a plain render
		// function instead of a React component (no renderer available on
		// React 19) — the rules-of-hooks lint is not applicable here.
		// eslint-disable-next-line react-hooks/rules-of-hooks
		latest = useNativeLayerLifecycle(lifecycle);
		flushEffects();
	};

	const scheduleRender = () => {
		if (renderScheduled || !mounted) {
			return;
		}
		renderScheduled = true;
		Promise.resolve().then(() => {
			renderScheduled = false;
			if (mounted) {
				render();
			}
		});
	};

	mockActive = harness;
	render();

	return {
		uuid: () => latest!.uuid,
		triggerCreate: (flags?: CreateFlags) => latest!.triggerCreate(flags),
		triggerRemove: (flags?: RemoveFlags) => latest!.triggerRemove(flags),
		rerender: render,
		unmount: () => {
			mounted = false;
			for (let i = 0; i < effects.length; i++) {
				const effect = effects[i];
				if (effect !== undefined && effect.cleanup !== null) {
					effect.cleanup();
					effect.cleanup = null;
				}
			}
		},
	};
};

const flushMicrotasks = async (count = 10) => {
	for (let i = 0; i < count; i++) {
		await Promise.resolve();
	}
};

type Deferred = { resolve: (uuid: string) => void; promise: Promise<string> };

const deferred = (): Deferred => {
	let resolve!: (uuid: string) => void;
	const promise = new Promise<string>((r) => {
		resolve = r;
	});
	return { resolve, promise };
};

// ── Tests ───────────────────────────────────────────────────────────────

describe('useNativeLayerLifecycle race handling', () => {
	test('a superseded create resolution self-removes instead of clobbering the tracked uuid', async () => {
		const removes: string[] = [];
		const first = deferred();
		const second = deferred();
		const create = jest
			.fn()
			.mockImplementationOnce(() => first.promise)
			.mockImplementationOnce(() => second.promise);
		const remove = jest.fn(async (uuid: string) => {
			removes.push(uuid);
			return true;
		});

		const rendered = renderLifecycle({ enabled: true, create, remove });
		await flushMicrotasks();
		expect(create).toHaveBeenCalledTimes(1);

		// A second create is issued while the first is still in-flight
		// (the re-key double-create pattern).
		rendered.triggerCreate({
			triggerOnCreate: false,
			triggerOnChange: true,
		});
		expect(create).toHaveBeenCalledTimes(2);

		// Newest resolves first.
		second.resolve('uuid-2');
		await flushMicrotasks();
		expect(rendered.uuid()).toBe('uuid-2');
		expect(remove).not.toHaveBeenCalled();

		// The stale (older) resolution lands afterwards — it must remove
		// its own resource, not overwrite the tracked uuid.
		first.resolve('uuid-1');
		await flushMicrotasks();
		expect(rendered.uuid()).toBe('uuid-2');
		expect(remove).toHaveBeenCalledWith('uuid-1', {
			triggerOnRemove: true,
		});

		// Unmount must remove the NEWEST uuid — proof the stale resolution
		// did not clobber the tracking.
		rendered.unmount();
		await flushMicrotasks();
		expect(removes).toEqual(['uuid-1', 'uuid-2']);
	});

	test('unmount during an in-flight create removes the created resource once it lands', async () => {
		const pending = deferred();
		const create = jest.fn(() => pending.promise);
		const remove = jest.fn(async () => true);

		const rendered = renderLifecycle({ enabled: true, create, remove });
		await flushMicrotasks();
		expect(create).toHaveBeenCalledTimes(1);

		rendered.unmount();
		pending.resolve('uuid-1');
		await flushMicrotasks();

		expect(remove).toHaveBeenCalledWith('uuid-1', {
			triggerOnRemove: true,
		});
		// State stays at the last committed value ('false' — the in-flight
		// marker) since the component is gone.
		expect(rendered.uuid()).toBe(false);
	});

	test('a plain create resolution is tracked without any removal', async () => {
		const pending = deferred();
		const create = jest.fn(() => pending.promise);
		const remove = jest.fn(async () => true);

		const rendered = renderLifecycle({ enabled: true, create, remove });
		await flushMicrotasks();
		pending.resolve('uuid-1');
		await flushMicrotasks();

		expect(rendered.uuid()).toBe('uuid-1');
		expect(remove).not.toHaveBeenCalled();
	});

	test('triggerRemove without a tracked uuid resolves false and never calls remove', async () => {
		const create = jest.fn(() => new Promise<string>(() => {}));
		const remove = jest.fn(async () => true);

		const rendered = renderLifecycle({ enabled: false, create, remove });
		await flushMicrotasks();
		expect(create).not.toHaveBeenCalled();

		const success = await rendered.triggerRemove();
		expect(success).toBe(false);
		expect(remove).not.toHaveBeenCalled();
	});

	test('triggerRemove removes the tracked uuid and clears it on success', async () => {
		const pending = deferred();
		const create = jest.fn(() => pending.promise);
		const remove = jest.fn(async () => true);

		const rendered = renderLifecycle({ enabled: true, create, remove });
		await flushMicrotasks();
		pending.resolve('uuid-1');
		await flushMicrotasks();

		const success = await rendered.triggerRemove();
		expect(success).toBe(true);
		expect(remove).toHaveBeenCalledWith('uuid-1', {
			triggerOnRemove: true,
		});
		expect(rendered.uuid()).toBeNull();

		// A second remove with nothing tracked is a no-op.
		const second = await rendered.triggerRemove();
		expect(second).toBe(false);
		expect(remove).toHaveBeenCalledTimes(1);
	});
});
