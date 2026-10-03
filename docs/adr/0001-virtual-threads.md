# 0001 — Serve redirects on virtual threads

- **Status**: Accepted
- **Date**: 2026-10-03
- **Applies to**: `spring.threads.virtual.enabled` in `application.yml`

## Context

The workload is lopsided. `POST /api/v1/url/shorten` is rare and does real work — validate,
insert, occasionally retry. `GET /{shortCode}` is the hot path by a wide margin, and it does very
little: one indexed `SELECT` by unique key, one atomic `UPDATE` of a single column, then a `302`
back to the caller. Both requests spend essentially all of their time parked on a socket waiting
for PostgreSQL, not burning CPU.

That profile is the one thing platform threads are worst at. A platform thread costs roughly 1 MB
of stack and is scheduled by the OS kernel, so the only way to increase throughput is to increase
the number of them. More threads than cores does not help a thread that is blocked on I/O; it adds
context switches and memory. The classic mitigations all have a cost: an async web stack rewrites
the application in `CompletableFuture`, a reactive data layer turns every query into a publisher and
pushes backpressure into application code, and a smaller request-scoped pool just moves the
queueing from Tomcat's accept queue into the pool.

The redirect handler also holds a database transaction open for the whole request (`@Transactional`
on `resolveAndCountClick`, deliberately), and with `open-in-view: false` a connection stays checked
out from Hikari until the response is written. Connection pressure, not CPU pressure, is what
limits this service.

## Decision

Enable virtual threads via `spring.threads.virtual.enabled: true` and keep the entire application
on the ordinary, blocking, synchronous Spring MVC + JPA stack.

No async or reactive code is introduced anywhere. The redirect path stays
`@RestController` → `@Transactional` service → `JpaRepository`.

## Consequences

**Positive**

- Throughput on the redirect path scales with connection availability rather than with
  `cores × concurrency`. The existing Hikari pool (`DB_POOL_SIZE`, default 10) becomes the real
  concurrency limiter, which is the correct place for the limit to live — it is the actual scarce
  resource.
- No reactive or async code anywhere. Blocking JDBC is used as-is, transactions are ordinary
  `@Transactional`, and stack traces stay readable. The codebase remains one thread from entry to
  exit, which is a large part of why the code in it is obvious.
- No `synchronized` pinning risk in practice: our handlers hold no monitors, and Hibernate's use of
  them is brief and not around a blocking JDBC call.
- Graceful shutdown in the `prod` profile (`server.shutdown=graceful`) drains in-flight redirects
  without waiting on a whole platform-thread pool.

**Negative**

- Cheap threads are not free. There is no throughput cliff, but there is a real per-request cost,
  so a flood of redirects still pushes towards the database rather than failing gracefully.
  Virtual threads improve *queueing* behaviour; they provide no backpressure and no rate limiting.
  The "No rate limiting" limitation in the README is unchanged by this decision and is arguably
  more dangerous now, since the service absorbs more concurrent work before it degrades.
- Every blocking call on a request path becomes more expensive under load, because it now holds a
  cheap thread instead of an expensive one and nothing self-limits it. The discipline this imposes:
  **do not add CPU-bound or long-running work to a request handler.** If a future feature needs to
  hash passwords, resize images, or call a slow third-party API inline, it needs a bounded executor
  rather than a virtual thread.
- `ThreadLocal` state is now per-virtual-thread rather than pooled, so anything stashed there is
  created and discarded per request instead of reused. That is *better* — it removes a class of
  cross-request leakage bug — but code assuming a warm pooled thread will behave differently.
- Higher per-request memory than a warm pooled platform thread at low load, and less readable
  `jstack` output while the service is in this state.
- Requires Java 21+. We are on 25, so it is free, but it is a floor on the toolchain.

## Alternatives considered

- **Async Spring MVC / WebFlux with a reactive JPA client.** Rejected. The "correct" answer to an
  I/O-bound workload on Java 17, but it replaces one decision with many others: publisher types
  across every service signature, a different transaction model, and backpressure decisions
  re-litigated at every call site. The win over virtual threads is marginal at this scale; the cost
  to readability is not.
- **A tuned platform-thread pool with a large `maxThreads`.** Rejected as strictly worse: the same
  blocking code with a memory ceiling and a tuning requirement. It fails the way thread pools fail
  — queue, then latency, then collapse.
- **A cache (Caffeine, Redis) in front of the redirect lookup.** Deferred on priority, not
  rejected on merit. It would cut database reads substantially, but click counting is an
  unavoidable write on every hit and is the harder half of the workload; a cache makes counter
  correctness the interesting problem instead of the thread model. Evaluate it when measurement
  shows the database is the bottleneck — as a new ADR, revisiting the pool-sizing argument here.
- **Keep platform threads and do nothing.** Rejected because the default Tomcat pool is a poor fit
  for a service whose entire hot path is one indexed read plus a single-column update, and the fix
  is one property.

## Validation

This decision is justified by the shape of the workload, not by a benchmark, and it is worth being
honest about that: we have not load-tested this service. Revisit this record if redirect latency or
memory under concurrency behaves unexpectedly — particularly if throughput stops improving while
Hikari stays saturated, which would mean the pool really is the bottleneck and the next lever is
the cache above, not more threads.

## Related

- [0003 — 302 with `Cache-Control: no-store`](0003-redirect-302-no-store.md) — the other half of
  keeping the redirect path correct under load.
- [0002 — random short codes](0002-random-short-codes.md) — the unguessability that makes every
  uncached redirect acceptable.
