# TODO

## 0. Dependency upgrade: protobuf-java 4.x

The main upgrade plan (eslint, jest, deps) landed in Steps 1–3 (commit `fdeef21`).
ESLint 10 and Jest 30 remain blocked by upstream `@react-native/eslint-config` /
`@react-native/jest-preset` pins — re-attempt once those bump.

The one actionable item:

- **`com.google.protobuf:protobuf-java` 3.25.8 → 4.x** — blocked on verifying that
  vtm's MVT-parsing classes (`vtm-mvt` / `vtm-android-mvt`) are compatible with the
  protobuf 4.x runtime. Needs a dedicated MBTiles/MVT-layer regression test, not just a
  version bump.
