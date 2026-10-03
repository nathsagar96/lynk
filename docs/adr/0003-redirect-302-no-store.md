# 0003 — Redirect with 302 and `Cache-Control: no-store`

- **Status**: Accepted
- **Date**: 2026-10-03
- **Applies to**: `RedirectController`, the click-count increment in `UrlShortenerService`

## Context

`GET /{shortCode}` is the only route that does two things at once: it resolves a destination *and*
it records that the link was used. That coupling is what makes its cache headers a correctness
question rather than a performance one.

A link's behaviour is mutable after it is created, in three separate ways:

1. **The destination can change.** Nothing in the API forbids editing `original_url` today, but the
   column is not declared immutable and the entity is not read-only, so a future "change where this
   link points" endpoint changes a link's meaning under a code that has already been distributed.
2. **The link can expire.** `hoursToExpire` makes expiry a normal, expected outcome — the README
   documents `410 Gone` for it. An expired link must stop redirecting, immediately, for every
   holder of the URL.
3. **The click count grows on every hit.** The number is meant to be the truth about how often a
   link is used. Any cache that skips the request skips the count.

All three mean the same thing: the *response* is only true for the instant it was produced, and it
stops being true without notice.

Intermediary and client behaviour makes this dangerous in a way that is easy to underestimate:

- A `301` is, by long-standing convention and by what browsers actually do, cached *permanently*
  and revalidated never. A browser that has seen `lynk.co/abc → https://internal.example/creds`
  keeps sending the user to that target from its own history and skips the request to us entirely —
  so the click is never counted and the expiry is never consulted. The link is frozen in the first
  visitor's browser and we cannot revoke it.
- `302` is treated as cacheable by default unless something says otherwise. Without an explicit
  directive, a shared proxy or CDN in front of this service may serve a stored `302` from its own
  cache, reintroducing exactly what `301` creates.
- Even a browser that honours `302` may cache heuristically. Only an explicit directive closes it.

The failure mode is silent. Nothing errors; the redirect still works, mostly, for most users. Click
counts quietly under-report and expired links quietly keep resolving. That is the worst class of
bug for this service, because it is invisible in the response and only discoverable by comparing
counts against server-side logs.

## Decision

Return `302 Found` and set `Cache-Control: no-store` explicitly, together:

```java
return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(originalUrl))
        .cacheControl(CacheControl.noStore())
        .build();
```

The two are not independent. `302` alone is not enough, because `302` is heuristically cacheable.
`no-store` alone would not be enough either, because clients treat `301` as immutable regardless of
what the origin says. Correctness depends on both being present, which is why they are set in the
same expression and why this record treats them as one decision.

Expiry is also enforced on the read path rather than left to the nightly sweeper:
`resolveAndCountClick` re-checks `expires_at` in the same transaction as the increment, so a link
stops redirecting the moment it expires whether or not the sweeper has run. The sweeper only
reclaims storage.

## Consequences

**Positive**

- Every hit reaches the application, so the click count is the truth. The number in
  `GET /api/v1/url/stats/{code}` is the same number redirects actually produced.
- Expiry takes effect on the first request after the moment it passes, for every holder of the URL,
  with no dependence on a background job, a proxy, or a browser cache entry.
- A link that is later re-pointed or retired cannot be kept alive by a stale `301` in somebody's
  browser. There is no class of "zombie" link we cannot revoke, because we were never permanent.
- Consistent with the rest of the API: an expired link returns `410`, a distinguishable answer
  rather than a silent wrong redirect.

**Negative**

- Every redirect is a cache miss, by design. Under load this service must absorb every click rather
  than serving most of them from a CDN. That is the direct cost of the accurate count — if click
  counting is ever made optional or sampled, this becomes negotiable and needs a new ADR.
- The `Location` target is not reachable while the app is down; a permanently cached redirect would
  have kept working. For a service with no availability SLA this is an accepted trade for
  correctness, and it must be revisited before making any uptime promise.
- Redirect latency is dominated by the PostgreSQL round trip with no edge shortcut available. See
  [0001](0001-virtual-threads.md) for how that path is kept fast.
- `no-store` is a strong directive — but it is the honest one. No `max-age` could be offered that
  would be correct, because the answer expires at a moment the client cannot know.

## Alternatives considered

- **`301 Moved Permanently`.** Rejected, and it is the alternative most likely to be proposed later
  by someone who wants "faster redirects". It caches permanently by definition and by client
  behaviour, breaking click counting, expiry, and any future re-pointing at once, and it cannot be
  undone on the client side. Not a trade-off; a defect.
- **`307 Temporary Redirect`.** Semantically defensible — non-cacheable by default and, unlike
  `303`, it does not change the request method. Rejected only because it invites clients to re-issue
  the body of a non-`GET` request, which is not meaningful for this route. If a non-`GET` route is
  ever added under the code pattern, `307` becomes correct and this record should be revisited.
- **`302` with no cache directive.** Rejected: `302` is heuristically cacheable, so a shared proxy
  can still serve stale destinations and skip click counts. This is the subtle failure, and the
  reason `no-store` sits in the same expression as the status code.
- **`302` with `no-cache, no-store, must-revalidate` and friends.** The full defensive set. Rejected
  as cargo cult: belt-and-braces against caches that `no-store` already forbids, and each extra
  directive is a future question when one misbehaves.
- **Cache the redirect and count clicks out of band** (edge 302 caching with logs shipped back
  asynchronously). This is how commercial shorteners reach high throughput. It is a much larger
  system: an edge tier, at-least-once count delivery, deduplication of retried logs, and a real
  reconciliation story. Rejected as out of scope; if scale ever demands it, it must be adopted
  whole as a proper ADR rather than half-adopted.

## Related

- [0003 — 302 with `Cache-Control: no-store`](0003-redirect-302-no-store.md) — expiry is enforced on
  the read path, which is only meaningful because the stored instant is unambiguous.
- [0002 — random short codes](0002-random-short-codes.md) — codes carry no structure to leak, which
  is a different kind of care about not encoding meaning into an identifier.
