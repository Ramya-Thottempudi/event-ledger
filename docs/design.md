# Event Ledger — Design Document

## System Overview

The Event Ledger is a distributed financial transaction processing system built as two microservices. It handles out-of-order event delivery and duplicate submissions while maintaining correct account balances. The system is designed for evaluation/demo purposes with a clear path to production hardening.

## Design Decisions

### 1. Language and Framework: Java 26 + Spring Boot 4.1.0

**Virtual Threads (Project Loom)**
Java 21+ virtual threads allow each request to run on a lightweight thread, enabling high concurrency with simple synchronous blocking code. Tomcat is configured to use virtual threads via `spring.threads.virtual.enabled=true` (default in Spring Boot 4.x). This eliminates the need for complex reactive programming patterns while still achieving excellent throughput.

**Spring Boot 4.1.0 / Spring Framework 7.0.x**
- Provides battle-tested REST framework (`@RestController`, `@Valid`, `ResponseEntity`)
- JPA/Hibernate integration for database access
- Dependency injection and component scanning
- Actuator endpoints for health checks and metrics
- Seamless OpenTelemetry integration via Micrometer

**Why not reactive (WebFlux)?**
Virtual threads make blocking code efficient — there is no need for reactive stacks that complicate debugging, tracing, and error handling. Synchronous code is simpler to reason about and test.

### 2. Database: H2 In-Memory

- Each service gets its own H2 instance (no shared mutable state between services).
- H2's `DB_CLOSE_DELAY=-1` keeps data alive as long as the JVM process runs.
- Schema is auto-created via JPA `ddl-auto: create-drop` — tables are created on startup and dropped on shutdown.
- H2 is suitable for evaluation and development; a production deployment would use PostgreSQL (or another RDBMS) with Flyway migrations.

### 3. Idempotency Strategy

**Event ID as primary key**
The `eventId` field is the database primary key for events in both services. Duplicate inserts with the same primary key are inherently rejected by the database.

**Status tracking**
Events flow through a state machine:
- `RECEIVED` — Event persisted in Gateway database
- `PROCESSED` — Event successfully forwarded to Account Service
- `FAILED` — Event forwarding failed

**Duplicate detection**
The Gateway checks `existsByEventId()` before processing. On duplicate, the original event is returned with HTTP 200 (rather than 201), signaling the client that this was a repeated submission.

### 4. Out-of-Order Event Handling

**Balance computation is commutative**
CREDIT adds to the balance, DEBIT subtracts. The net result is the same regardless of arrival order. A CREDIT of $100 followed by a DEBIT of $30 always yields $70 net — whether they arrive as (CREDIT, DEBIT) or (DEBIT, CREDIT).

**Chronological event listing**
Events are queried using `findByAccountIdOrderByEventTimestampAsc()` to ensure listing is always time-ordered regardless of processing order.

**No strict ordering guarantee needed**
Since financial events carry their own client-supplied timestamps (`eventTimestamp`), balance calculation is order-independent. This avoids the complexity of distributed sequence numbers or consensus protocols.

### 5. Resiliency Pattern: Circuit Breaker

**Why Circuit Breaker over alternatives?**

| Pattern | Limitation |
|---|---|
| **Retry** alone | Amplifies load on a failing service, worsening the problem |
| **Bulkhead** | Isolates thread pools but does not prevent repeated calls to a failing service |
| **Circuit Breaker** | Protects the caller from a failing downstream — stops wasted calls, gives recovery time |

**Behavior**
The Resilience4j circuit breaker:
- Trips **OPEN** after 50% failure rate in a 10-call sliding window (minimum 5 calls evaluated)
- Stays OPEN for 10 seconds, allowing the downstream service to recover
- Transitions to **HALF-OPEN** after the wait duration, permitting 3 test calls
- Closes again if all 3 probes succeed; returns to OPEN if any probe fails

**Graceful degradation**
Read operations (`GET /events/{id}`, `GET /events?account=...`) never depend on the Account Service — they query the Gateway's local H2 database directly. Only `POST /events` requires Account Service availability.

### 6. Distributed Tracing

**Trace ID generation**
A trace ID (UUID) is generated at the Gateway ingress point if not already present in the incoming request.

**MDC injection**
The `TracingFilter` (a servlet filter) extracts or injects trace IDs into SLF4J's MDC (`trace-id` key) on every request. The filter also adds the trace ID to the HTTP response headers.

**Header propagation**
When the Gateway calls the Account Service via `RestClient`, the trace ID is forwarded as the `trace-id` HTTP header. The Account Service's `TracingFilter` reads this header and injects it into its own MDC.

**Structured logging**
Logstash-logback-encoder produces JSON log entries with fields: `trace-id`, `service`, `timestamp`, `level`, `logger`, `message`. This enables log aggregation tools (ELK, Grafana Loki) to correlate logs across services.

**OpenTelemetry**
The OpenTelemetry SDK is configured with a logging span exporter that outputs span events to the logs. The configuration is extensible to OTLP exporter for Jaeger or Zipkin in production.

### 7. API Contract

The contract between Event Gateway and Account Service is defined by the `shared-models` Maven module, which both services depend on:

**TransactionRequest** — Input contract for event processing:
- `eventId` (String, required) — Unique event identifier; doubles as idempotency key
- `accountId` (String, required) — Target account
- `type` (EventType: `CREDIT` or `DEBIT`) — Transaction direction
- `amount` (BigDecimal, positive) — Transaction amount
- `currency` (String, required) — ISO currency code (e.g., "USD")
- `eventTimestamp` (Instant, required) — Client-supplied event timestamp
- `metadata` (Map<String, String>, optional) — Arbitrary key-value metadata

**TransactionResponse** — Output contract:
- `transactionId` — Account Service generated transaction ID
- `accountId` — The account affected
- `newBalance` — Balance after applying the transaction
- `currency` — Currency of the balance
- `status` — `SUCCESS` or `ERROR`
- `message` — Error details (if status is ERROR)
- `processedAt` — Timestamp of processing

Both records are serialized via Jackson 3.x (`tools.jackson.*`) and validated using Jakarta Validation annotations.

### 8. Architecture Summary

```
Client → Event Gateway (port 8081) → Account Service (port 8082)
            │                              │
            ▼                              ▼
      H2 (events)                    H2 (accounts)
```

- No service discovery — Account Service URL is configured via `account-service.url` property
- No API gateway — Event Gateway IS the public-facing API
- No message broker — synchronous REST between services (circuit breaker provides resiliency)
