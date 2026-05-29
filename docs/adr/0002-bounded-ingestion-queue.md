# ADR-0002: Bounded ingestion queue over fire-and-forget @Async
- Status: Accepted
- Date: 2026-05-30
- Deciders: DASIGConnect team

## Context
Today each media upload fires an `@Async` task that makes ~3 external calls (Claude
classification + 2× Voyage embedding). The headline Capstone 2 scenario is a contributor
dumping **200 assets at once**. With fire-and-forget async, that is ~600 simultaneous
external calls → thread/memory blowup and rate-limit rejections. It also risks the
HikariCP pool, which is capped at **5 connections** for the Supabase Session Pooler and
must not be raised.

## Decision
Replace the unbounded async path with a **bounded queue worker**:
- Upload returns instantly; asset enters as `status = PROCESSING` (browser→Supabase
  upload is already non-blocking).
- A fixed-size `ThreadPoolTaskExecutor` (**~2 workers, bounded queue**) drains at a
  controlled rate. Small on purpose: background workers must not starve the 5 request
  connections.
- A connection is acquired/released **per micro-batch**, never held across an external
  call.
- **Idempotency key per asset** prevents double-processing; **FAILED-after-N-attempts**
  is the dead-letter state.
- Voyage **text** embeddings are batched (multiple inputs per request); image multimodal
  stays 1-per. A `Semaphore`/`RateLimiter` guards Claude/Voyage bursts.
- `EmbeddingReconciliationJob` (already exists) is the idempotent retry safety net.
- Asset flips to `READY` only after metadata + required embeddings are stored.

## Consequences
- This is the measurable "how we handle scale" result (report throughput on D5, zero
  dropped connections).
- Worker count vs. throughput is a tuning trade-off — must compute and report the
  wall-clock for the 200-asset run (don't leave it as "measure later").
- Evolves the existing `PROCESSING/READY/FAILED` status model; not a new subsystem.
