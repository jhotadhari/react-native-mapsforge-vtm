# Terminology

This project avoids terminology rooted in colonialism, slavery, and
oppression.  Specifically:

- **sync source / mirror** — used instead of "master" / "slave" for
  synchronisation relationships (e.g. multi-map position sync).  One map is
  the *source of truth* for position; the other *mirrors* it.

- **default / base** — used instead of "master" for branches, configuration
  defaults, or fallback values.  The repository's primary branch is `main`.

These terms are chosen to be descriptive of the mechanism rather than
metaphorical — "sync source" tells you exactly what the variable does,
without relying on a human-ownership analogy that is both inaccurate and
harmful.

## Practical impact

| Avoid | Use | Example context |
|-------|-----|-----------------|
| master/slave | source/mirror, sync source | Multi-map position sync |
| master branch | main branch | Git default branch |
| whitelist/blacklist | allowlist/denylist | Feature flags, permissions |
| master record | primary record, original | Database replication |

## References

- [IETF RFC 9456: Alternatives to master-slave terminology](https://www.rfc-editor.org/rfc/rfc9456)
- [Google Developer Documentation Style Guide: Inclusive language](https://developers.google.com/style/inclusive-documentation)
