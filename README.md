# Event Ledger System

A two-microservice financial transaction event processing system built with Java 26 and Spring Boot 4.1.0.

## Architecture Overview

The Event Ledger system consists of two independently runnable services:

1. **Event Gateway** (port `8081`) — Public-facing API that receives transaction events, validates input, enforces idempotency, and forwards to the Account Service.
2. **Account Service** (port `8082`) — Internal service managing account balances, transaction history, and account-level queries.

![Architecture Diagram](docs/architecture.md)

Both services use H2 in-memory databases (no shared state) and communicate via synchronous REST calls. OpenTelemetry provides distributed tracing across service boundaries.

## Tech Stack

- Java 26 with Virtual Threads (Project Loom)
- Spring Boot 4.1.0 / Spring Framework 7.0.x
- Jackson 3.x (`tools.jackson.*`)
- H2 In-Memory Database
- JPA / Hibernate
- Resilience4j 2.4.0 (Circuit Breaker + Retry + Rate Limiter)
- OpenTelemetry 1.62
- Prometheus Metrics
- JSON Structured Logging (Logstash)
- Maven Multi-Module Build
- Docker Compose

## Prerequisites

- JDK 26+
- Apache Maven 3.9+
- Docker & Docker Compose (optional, for containerized setup)

## Quick Start

### Docker Compose (preferred)

```bash
# Build images and start both services
# Use 'docker compose' (v2) or 'docker-compose' (v1) depending on your setup
docker compose up --build

# Verify health
curl http://localhost:8081/health
curl http://localhost:8082/health
```

For Jaeger tracing visualization, Docker Compose includes an optional Jaeger service.

### Manual (Terminals)

```bash
# Build all modules
mvn clean package -DskipTests

# Terminal 1: Start Account Service
java --enable-preview -jar account-service/target/account-service-1.0.0.jar

# Terminal 2: Start Event Gateway
java --enable-preview -jar event-gateway/target/event-gateway-1.0.0.jar
```

### Manual with Maven (no build step needed)

```bash
# Terminal 1: Start Account Service
mvn spring-boot:run -pl account-service

# Terminal 2: Start Event Gateway
mvn spring-boot:run -pl event-gateway
```

## API Usage

### Submit an Event

```bash
curl -X POST http://localhost:8081/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {
      "source": "mainframe-batch",
      "batchId": "B-9042"
    }
  }'
```

The response contains the transaction result from the Account Service:

```json
{
  "transactionId": "txn-...",
  "accountId": "acct-123",
  "newBalance": 150.00,
  "currency": "USD",
  "status": "SUCCESS",
  "message": null,
  "processedAt": "2026-05-15T14:02:12Z"
}
```

Submitting the same `eventId` again is idempotent — returns HTTP 200 with event summary (instead of 201 for new events):

### Get Event by ID

```bash
curl http://localhost:8081/events/evt-001
```

Returns the `EventRecord` with status (`RECEIVED`, `PROCESSED`, or `FAILED`), event details, and optional error message.

### List Events for Account

```bash
curl "http://localhost:8081/events?account=acct-123"
```

Returns a chronological list of events (sorted by `eventTimestamp`) for the specified account.

### Get Account Balance

```bash
curl http://localhost:8082/accounts/acct-123/balance
```

Response:

```json
{
  "accountId": "acct-123",
  "balance": 150.00,
  "currency": "USD"
}
```

### Get Account Details

```bash
curl http://localhost:8082/accounts/acct-123
```

Response includes full `AccountDetails` with `transactionCount` and `lastUpdated`:

```json
{
  "accountId": "acct-123",
  "balance": 150.00,
  "currency": "USD",
  "lastUpdated": "2026-05-15T14:02:12Z",
  "transactionCount": 1
}
```

### Health Checks

```bash
curl http://localhost:8081/health
curl http://localhost:8082/health
```

## Running Tests

```bash
# Run all unit tests
mvn test -pl account-service,event-gateway -am

# Run unit + integration tests with JaCoCo coverage report
mvn verify -pl event-gateway -am
# Unit coverage: event-gateway/target/site/jacoco/index.html
# Account svc coverage: account-service/target/site/jacoco/index.html
```

## Resiliency Patterns

The Event Gateway uses three complementary resiliency patterns when calling the Account Service, composed as a decorator chain: **Circuit Breaker → Retry → RateLimiter**.

### Circuit Breaker
Prevents cascading failures when the downstream service is unavailable.
- **Sliding window**: Count-based, last 10 calls
- **Minimum calls**: 5 before evaluating state
- **Failure threshold**: 50% failure rate trips the breaker
- **Open state duration**: 10 seconds (wait before probing recovery)
- **Half-open calls**: 3 test calls permitted to verify recovery

### Retry with Exponential Backoff + Jitter
Handles transient failures by retrying with increasing delays.
- **Max attempts**: 3
- **Initial wait**: 500ms, doubling each attempt
- **Backoff**: Multiplier 2× (500ms → 1s → 2s)
- **Jitter**: ±50% randomization to prevent thundering herd

### Rate Limiting
Protects the Account Service from request spikes.
- **Limit**: 100 requests per second
- **Timeout**: 100ms if capacity is exceeded

### Graceful Degradation

When the Account Service is unavailable or the circuit breaker is open:

| Endpoint | Behavior |
|---|---|
| `POST /events` | Returns **503 Service Unavailable** with error detail and trace ID |
| `GET /events/{id}` | Still works — reads from Gateway-local H2 database |
| `GET /events?account=...` | Still works — reads from Gateway-local H2 database |
| `GET /health` | Reports Account Service status (UP/DOWN) |

This ensures that **read operations never depend on the Account Service** — the Gateway persists all submitted events locally, so event history is always available for querying.

## Distributed Tracing

Each request generates a trace ID propagated via the `trace-id` HTTP header. Both services log structured JSON with the trace ID, enabling end-to-end request tracing.

### Jaeger Visualization (Optional)

Docker Compose includes a Jaeger container for trace visualization:

```bash
docker compose up -d
# Access Jaeger UI at http://localhost:16686
```

Activate the `jaeger` Spring profile on both services to send traces to Jaeger:

```bash
# When running manually, add the profile:
SPRING_PROFILES_ACTIVE=jaeger mvn spring-boot:run -pl account-service
SPRING_PROFILES_ACTIVE=jaeger mvn spring-boot:run -pl event-gateway
```

## Prometheus Metrics

Both services expose Prometheus metrics at the Actuator endpoint:
```bash
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/prometheus
```

Custom metrics include:
- `gateway.events.submitted` — total events received
- `gateway.events.success` — events processed successfully
- `gateway.events.failure` — events that failed

Standard JVM, system, and Hikari connection pool metrics are also available.

## Design Document

See [docs/design.md](docs/design.md) for detailed design decisions and [docs/architecture.md](docs/architecture.md) for architecture diagrams with Mermaid diagrams.
