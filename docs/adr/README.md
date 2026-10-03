# Architecture Decision Records

The reasoning behind the choices in this codebase that are not obvious from the code itself.
Each record captures a decision that was *made*, the forces that drove it, the alternatives
that were rejected, and what it costs us — because a decision without its costs recorded is
indistinguishable from an accident.

| #     | Decision                                          | Status   | Date       |
|-------|---------------------------------------------------|----------|------------|
| [0001](0001-virtual-threads.md)      | Serve redirects on virtual threads               | Accepted | 2026-10-03 |
| [0002](0002-random-short-codes.md)   | Generate random base-62 codes, not encoded ids    | Accepted | 2026-10-03 |
| [0003](0003-redirect-302-no-store.md) | Redirect with `302` + `Cache-Control: no-store`   | Accepted | 2026-10-03 |
| [0004](0004-timestamps-as-instant.md) | Store timestamps as `timestamptz` / `Instant`     | Accepted | 2026-10-03 |

## Status values

- **Accepted** — in force; the code and the build enforce it.
- **Superseded by [NNNN]** — replaced by a later record; do not follow it.
- **Deprecated** — no longer recommended for new work, but not yet replaced.

## Conventions

Records are immutable once accepted. When a decision changes, add a *new* record and mark the old
one `Superseded by [NNNN]`. Rewriting history destroys the one thing these documents exist to
preserve: the fact that an alternative was considered and why it lost. Git blame on a record is a
map of what we believed and when.

Records are numbered and never renumbered, so a reference from a commit message or a code comment
keeps pointing at the decision it was written against.

## Adding one

1. Take the next unused number. Don't fill gaps.
2. Copy the structure of an existing record: context, decision, consequences, alternatives.
3. Fill in *alternatives* honestly, including the ones that were genuinely close. A record with one
   strawman alternative is marketing, not engineering.
4. Record the costs under *Consequences — negative*. If you cannot name any, you have not
   understood the decision.
5. Link from this table in the same commit.

Most of these decisions are also summarised in the README's "Design notes". That summary is a
convenience; these records are the source of the reasoning.
