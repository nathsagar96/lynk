# 0004 — Timestamps as `timestamptz` mapped to `Instant`

- **Status**: Accepted
- **Date**: 2026-10-03
- **Applies to**: `UrlMapping.createdAt` / `expiresAt`, `V1__create_url_mapping.sql`, the
  `deleteExpired` query

## Context

The service stores three time values per link: `created_at` (never null), `expires_at` (nullable —
null means the link never expires), and `click_count`. Expiry is a business rule evaluated on every
redirect, so the type chosen for these columns is load-bearing in a way that is easy to
underestimate until the first timezone bug report.

The default choice on a JVM is `LocalDateTime`, because that is what `java.sql.Timestamp` becomes
and what JPA's `@Temporal` historically mapped to. It is the wrong choice here, for one specific
reason: **`LocalDateTime` is not a point in time. It is a wall-clock reading with no zone
attached.**

That absence is invisible at the type level and fatal at the storage level. The dangerous
combination is `LocalDateTime` in Java paired with `TIMESTAMP WITHOUT TIME ZONE` in PostgreSQL,
which is the schema you get unless you ask for otherwise. Postgres has no idea what zone the string
`2026-10-03 05:09:39` refers to, so it stores it as-is and hands back the same reading to whatever
asks. The stored value then becomes a property of *whichever zone the writing JVM happened to be
in*, and the correct interpretation is lost the moment the row is written. Anything that reads the
column under a different assumption is wrong, and it is wrong silently.

What makes this concrete for this service:

- **`expires_at` is compared on the read path.** `resolveAndCountClick` computes `Instant.now()`
  and asks `isExpired(now)`. If `expires_at` were a naive local reading, that comparison would be
  between two zone-less wall clocks. It still produces a number, so nothing fails — it just means
  "expired" depends on the timezone of the process doing the comparing.
- **The app and the database may not agree on zone.** The container image and the PostgreSQL
  container each have their own `TZ`. The cleanup job passes `Instant.now()` from the app into a
  bulk `delete ... where expires_at <= :now`. With naive local times that sweep silently misses rows
  near the boundary — hours of error, in whichever direction the mismatch points.
- **A link's expiry must be a fact about the world, not about the deploy.** "Expires in 24 hours"
  has to mean the same instant to a user in Berlin and to a job in a container running `TZ=UTC`. A
  naive timestamp cannot express that, so the same row means two different moments to two readers.

`timestamptz` (with `Instant`) fixes this by storing the actual instant and normalising to UTC;
Postgres applies the session zone only when *rendering* to a client, and SQL comparisons are
timezone-independent.

`OffsetDateTime` was considered and rejected: it also carries a zone, but it preserves the
*offset* rather than the instant's identity, so a value written under one offset and read under
another is a different rendering of the same instant — a distinction with no use here and a real
hazard for anyone doing arithmetic on it. `Instant` is the smallest honest representation of "when
this happened".

## Decision

Store `created_at` and `expires_at` as `TIMESTAMPTZ` and map them to `java.time.Instant` in
`UrlMapping`. Do not use `LocalDateTime` anywhere in the persistence layer.

`created_at` is stamped in `@PrePersist` with `Instant.now()` rather than supplied by the caller,
so no hand-built timestamp reaches the entity. `expires_at` is computed once, in `expiryFor(...)`,
as `Instant.now().plus(hours, HOURS)` — the requested duration is added to a single reading of the
clock, so no wall-clock component can be mixed in.

## Consequences

**Positive**

- "Expires in 24 hours" means the same instant regardless of the JVM's zone, the container's `TZ`,
  or the reader's locale. Timezone bugs are designed out rather than tested for.
- SQL-side comparisons (`deleteExpired`) are zone-independent, so the nightly sweep cannot disagree
  with the read path about whether a link has expired.
- PostgreSQL stores UTC and renders per session zone, so the column stays correct if a future
  consumer is a report, a replica, or a human running `psql`.
- `Instant` is unambiguous about what it does not know: no calendar date, no offset, no zone.
  Nothing to misread.
- The API already serialises these as `Z`-suffixed UTC timestamps (e.g.
  `2026-09-29T05:09:39.484046Z`), so the wire format and the stored format agree and no conversion
  happens at the edge.
- `expires_at IS NULL` remains a clean, indexable statement of "never expires" — nullable
  `timestamptz` plus the `IS NOT NULL` guard in the delete query needs no sentinel date like
  `9999-12-31`, which would be a value that could leak into comparisons and JSON.

**Negative**

- Zone conversion is now somebody's problem, and that somebody has to exist. Any future
  user-facing feature — "expires in 3 days", a digest email, a scheduled report — must format an
  `Instant` into a `ZoneId` deliberately and state whose zone it displays. There is no default,
  which is the point, but the display decision cannot be skipped.
- Debugging is slightly harder: a `timestamptz` read from `psql` is in the session zone, so two
  people looking at the same row can see different strings. A familiarity cost, not a correctness
  one; `SET TIME ZONE 'UTC'` settles ad-hoc queries.
- Internal consistency wants a single clock reading per operation, while `expiryFor` and
  `isExpired` each call `Instant.now()` separately. Acceptable today because each is used within one
  request and the transaction boundary keeps them together, but a feature that must compare across
  a request boundary should take one reading and pass it down.
- `@PrePersist` means `createdAt` is `null` on a freshly constructed, unsaved entity; code that
  reads it before flush sees `null`. Correct place for a server-owned timestamp, but one more
  lifecycle rule to know.

## Alternatives considered

- **`LocalDateTime` with `TIMESTAMP WITHOUT TIME ZONE`.** Rejected for the reasons above. This is
  the default everyone reaches for, and it is the choice that produces a service whose links expire
  at different times depending on which server handles the request.
- **`LocalDateTime` with `TIMESTAMPTZ`.** Strictly worse than the option above: the column carries a
  zone but the type pretends it does not. Postgres applies the session zone on write and read, so
  the value round-trips through an offset without that offset ever being visible in Java. Rejected
  explicitly because it looks like it satisfies this record while violating its entire point.
- **`OffsetDateTime`.** A correct instant, but the wrong mental model: it makes the *offset* look
  load-bearing. Two readers with different offsets see different values, inviting arithmetic on the
  offset. `Instant` plus an explicit `ZoneId` at the formatting boundary is the honest version of
  the same idea.
- **`ZonedDateTime` in the entity.** Rejected as persistence-hostile. Fine in a presentation layer;
  it invites attaching an arbitrary zone at construction, which then has to be discarded or
  persisted inconsistently. Region-based zones also change meaning over time (an offset is a
  function of the instant), which is a subtle way to introduce a bug.
- **`java.util.Date`.** Rejected: mutable, millisecond precision, and the same zone ambiguity with
  none of the clarity.
- **Store expiry as a duration (e.g. `hours_to_expire INT`) and compute the instant on read.**
  Attractive for auditability — it records the user's intent rather than a derived instant. Rejected
  because it makes expiry depend on the reader's clock, defers the decision to every read, and
  cannot be indexed well for the cleanup sweep, which must ask "which links are expired now" across
  all rows. If the intent ever needs recording *as well*, add the column; do not replace the
  resolved instant with it.

## Related

- [0003 — 302 with `Cache-Control: no-store`](0003-redirect-302-no-store.md) — expiry is enforced on
  the read path, which is only meaningful because the stored instant is unambiguous.
