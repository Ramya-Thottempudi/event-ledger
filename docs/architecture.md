# Architecture Diagrams

## System Architecture

```mermaid
graph TB
    Client[Browser / Client]
    Gateway[Event Gateway<br/>Port 8081]
    Account[Account Service<br/>Port 8082]
    GW_DB[(H2 Database<br/>events)]
    AC_DB[(H2 Database<br/>accounts)]

    Client -- "POST /events" --> Gateway
    Client -- "GET /events/{id}" --> Gateway
    Client -- "GET /events?account=..." --> Gateway
    Client -- "GET /health" --> Gateway

    Gateway -- "POST /accounts/{id}/transactions" --> Account
    Gateway -- "Circuit Breaker" -.-> Account

    Gateway --> GW_DB
    Account --> AC_DB

    subgraph "Observability Stack"
        LOG[JSON Structured Logging<br/>Logstash]
        TRACE[OpenTelemetry Tracing<br/>trace-id header]
        METRICS[Micrometer Metrics<br/>/actuator/metrics]
    end

    Gateway --> LOG
    Gateway --> TRACE
    Gateway --> METRICS
    Account --> LOG
    Account --> TRACE
    Account --> METRICS
```

**Component responsibilities:**

- **Client:** Any HTTP client (curl, browser, application) submitting financial events
- **Event Gateway:** Public API endpoint; validates input, enforces idempotency, persists events locally, forwards to Account Service via RestClient
- **Account Service:** Internal service; manages account lifecycle, applies transactions, maintains balances
- **H2 Databases:** Each service has an isolated in-memory database with no shared state

## Event Flow

### Successful Event Submission

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Event Gateway
    participant GDB as Gateway DB
    participant CB as Circuit Breaker
    participant AS as Account Service
    participant ADB as Account DB

    C->>GW: POST /events
    activate GW

    GW->>GDB: Save event (status=RECEIVED)
    Note over GW,CB: Check circuit breaker state
    CB-->>GW: State = CLOSED

    GW->>AS: POST /accounts/{id}/transactions
    Note over GW,AS: trace-id header propagated
    activate AS

    AS->>ADB: Get or create account
    AS->>ADB: Record transaction
    AS->>ADB: Update balance
    AS-->>GW: TransactionResponse (SUCCESS)
    deactivate AS

    GW->>GDB: Update event status (PROCESSED)
    GW-->>C: 201 Created
    deactivate GW
```

### Duplicate Event Submission

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Event Gateway

    C->>GW: POST /events (same eventId)
    activate GW
    GW->>GDB: existsByEventId() check
    GDB-->>GW: Found existing event
    GW-->>C: 200 OK (idempotent response)
    deactivate GW
```

### Circuit Breaker Open — Read Operations Still Work

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Event Gateway
    participant CB as Circuit Breaker
    participant AS as Account Service

    Note over GW,CB: Circuit is OPEN (AS unhealthy)

    C->>GW: GET /events/{id}
    GW->>GDB: Query local database
    GDB-->>GW: EventRecord
    GW-->>C: 200 OK (from local DB)

    C->>GW: GET /events?account=...
    GW->>GDB: Query local database
    GDB-->>GW: EventRecord[]
    GW-->>C: 200 OK (from local DB)

    C->>GW: POST /events
    CB-->>GW: State = OPEN
    GW-->>C: 503 Service Unavailable
```

## Circuit Breaker States

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: Failure rate > 50%<br/>(in 10-call window)
    OPEN --> HALF_OPEN: After 10s wait
    HALF_OPEN --> CLOSED: 3/3 probes succeed
    HALF_OPEN --> OPEN: Any probe fails
    CLOSED --> CLOSED: Normal operation<br/>(requests forwarded)
```

**State transitions explained:**

- **CLOSED:** Normal operation. Requests flow through to Account Service. Failures are counted.
- **OPEN:** Requests are rejected immediately without calling Account Service. The 10-second wait allows the downstream to recover.
- **HALF_OPEN:** A limited number of test requests (3) are allowed through. If they succeed, the breaker closes; if any fail, it opens again.

## Graceful Degradation Flowchart

```mermaid
flowchart TD
    A[Client Request] --> B{Endpoint type?}

    B -->|POST /events| C{Circuit Breaker state?}
    B -->|GET /events/{id}| D[Query Gateway local DB]
    B -->|GET /events?account=| D
    B -->|GET /health| E[Return aggregated status]

    C -->|CLOSED| F[Forward to Account Service]
    C -->|OPEN| G[Return 503 with error body]

    F -->|Success| H[Return 201 Created<br/>with TransactionResponse]
    F -->|Failure| I[Mark event FAILED in DB<br/>Return 503 with error body]

    D --> J[Return events from gateway local DB]
    E --> K[Include account-service status<br/>in health response]
```

**Design principles illustrated:**

1. **Local-first reads:** All GET endpoints query the Gateway's local H2 database. They never depend on Account Service availability.
2. **Degraded writes:** POST /events requires Account Service. If unavailable (circuit open or call failure), the client receives a clear 503 with trace ID for debugging.
3. **Failure visibility:** The health endpoint reports Account Service status so monitoring tools can detect partial outages.
