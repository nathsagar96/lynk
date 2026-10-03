# 0002 — Random base-62 short codes, not encoded row ids

- **Status**: Accepted
- **Date**: 2026-10-03
- **Applies to**: `ShortCodeGenerator`, `lynk.code.length`, the `short_code` unique constraint

## Context

A URL shortener must invent a short, unique, human-typeable token per link. The obvious
implementation is to let the database assign a sequential id and encode it — base-62 of
`BIGSERIAL` gives short, unique, zero-collision codes with no lookup table and no retry loop.

It is also a security and product problem.

**Enumeration.** Sequential ids are guessable by construction. `lynk.co/1` is the first link ever
created. Anyone can walk `1`, `2`, `3` … and harvest every URL this service has ever shortened,
including ones for internal systems, and read their click counts through
`GET /api/v1/url/stats/{code}`. The README is explicit that there is no authentication, so the
only thing standing between an attacker and the full link namespace is unguessability. Sequential
ids remove it entirely. This is the primary reason for the decision.

**Density.** Encoded ids are 1, 2, 3 … in base 62, so early codes look like `2Xk9`. Random codes
have no structure, which means a code reveals nothing about how many links exist, how old one is,
or how popular it is.

**Legibility as a side effect.** A random 7-character code is easy to read aloud, retype, and put
in a slide. A sequential code is marginally easier to guess but noticeably easier to mistype, and
codes like `1` or `2` invite people to guess low numbers.

## Decision

Generate codes randomly from a 62-character alphabet (`0-9`, `a-z`, `A-Z`) using `SecureRandom`,
at a length of `lynk.code.length` (default 7). Do not derive them from the row id.

Randomness makes collisions possible, so correctness must not depend on the code being free:

- `short_code` carries a `UNIQUE` constraint. This, not the pre-check, is the guarantee.
- On collision, `saveWithGeneratedCode` retries up to `lynk.code.max-attempts` (default 5) with a
  fresh code, then propagates.
- `saveAndFlush` is used so a violation surfaces inside the retry loop rather than at some later
  flush outside it.
- A collision on a *custom alias* is a `409`, not a retry — the user asked for that exact code and
  silently substituting another would be wrong.

## Consequences

**Positive**

- The link namespace is unguessable by enumeration, which is the only protection the service has
  while it has no authentication.
- Codes leak nothing about cardinality, age, or ordering.
- `SecureRandom` is used rather than `Random`: predictability here is an exploit, not a
  statistical curiosity, and a seeded `Random` would make a code sequence reproducible from a
  handful of observations.
- The alphabet is restricted to `A-Za-z0-9_-` by the redirect route pattern and by alias
  validation, so generated codes and custom aliases are indistinguishable to the router — one code
  path, no special cases.

**Negative**

- 7 base-62 characters give 62⁷ ≈ 3.5 × 10¹² codes. Birthday-paradox analysis says collisions
  become non-negligible past roughly 10⁶ links in a single instance (~50% by 10⁶.5). At 10⁵ links
  the probability of any collision is about 1.4 × 10⁻³. This is acceptable today and *will not be*
  at scale — at which point the length needs to grow, and the fix is a configuration change plus a
  new ADR, not a code change.
- Codes are not derived from data, so nothing about a code can be reconstructed. That is the point,
  but it means there is no way to recover a code from an id: a lost code is gone.
- The retry loop costs a wasted `INSERT` attempt plus an exception on collision. Rare enough to be
  invisible, but it is real work, and under a pathological collision rate (see above) the 5-attempt
  ceiling turns a slow path into a `500`.
- Code length and `short_code VARCHAR(32)` are coupled only by convention; nothing validates that
  `lynk.code.length ≤ 32`. Misconfiguration fails at insert time, not at startup.

## Alternatives considered

- **Base-62 of the `BIGSERIAL` id.** Rejected for enumeration and density, above. It is genuinely
  the most efficient scheme on paper (no collisions, no retries, shortest possible codes at low
  volume), which is exactly why it is a trap: the efficiency is not the problem being solved. Nobody
  is asking for maximum code density at 10⁵ links; they are asking for codes that cannot be walked.
- **Hashing the URL (MD5/SHA prefix, or base64url of a digest).** Rejected: deterministic codes make
  shortening the same URL twice return the same link, which changes the data model — it forces URL
  canonicalisation and dedup semantics onto every future caller, and the README lists "one row per
  link" as a deliberate scope decision. It also leaks the length of pre-image space to anyone
  willing to test candidates. If dedup is ever wanted, it is a new ADR that adds an index, not a
  change to this one.
- **A UUID or ULID.** Rejected as too long. Truncated to a comparable entropy they are no better
  than `SecureRandom` over base-62 and produce uglier codes with `-` separators, which the routing
  pattern would have to accommodate.
- **`ThreadLocalRandom` instead of `SecureRandom`.** Close, and cheaper. Rejected because code
  unguessability is a security property here, and a predictable PRNG converts that property into a
  probabilistic one for no measurable gain at this request rate.
- **Client-supplied codes only, no generated ones.** Rejected: it makes the API unusable for
  programmatic callers and pushes the namespace problem onto users.

## Related

- [0003 — 302 with `Cache-Control: no-store`](0003-redirect-302-no-store.md) — the other half of
  keeping a shared link correct once it exists.
- Reserved words (`error`, `api`, `actuator`, `health`) are rejected case-insensitively because
  `error` falls inside the redirect pattern; see the README design notes.
