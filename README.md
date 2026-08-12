# Async Jobs (Spring Boot)

Deployable multi-module app: **job-api**, **job-worker**, shared **job-common**.

## System architecture

Two deployable services share one database. The API accepts jobs and streams status; the worker claims and processes them in parallel.

```mermaid
flowchart TB
  subgraph Clients
    UI["Web UI<br/>index.html + SSE"]
    API_CLIENT["API clients<br/>curl / dashboard"]
  end

  subgraph JobAPI["job-api :8080"]
    CTRL["JobController<br/>POST/GET /api/jobs"]
    SSE["SSE /api/jobs/stream"]
    SVC["JobService<br/>unique name validation"]
    SSE_SVC["JobSseService<br/>poll DB + push snapshots"]
  end

  subgraph JobWorker["job-worker"]
    DISP["JobDispatcher<br/>fill up to 5 slots"]
    POOL["Thread pool<br/>size = 5"]
    CLAIM["JobClaimService<br/>SKIP LOCKED claim"]
    EXEC["JobExecutionService<br/>process job"]
    LIFE["JobLifecycleService<br/>complete / retry / DLQ"]
    WATCH["StuckJobWatchdog<br/>RUNNING &gt; 1h → DEAD"]
  end

  subgraph Shared["Shared store"]
    DB[(DB / H2 or Postgres<br/>jobs table)]
    DLQ[(dead_letter_jobs)]
  end

  UI -->|POST create job| CTRL
  UI -->|EventSource| SSE
  API_CLIENT --> CTRL
  CTRL --> SVC
  SVC -->|INSERT PENDING| DB
  SSE --> SSE_SVC
  SSE_SVC -->|read jobs| DB
  SVC -.->|publish snapshot| SSE_SVC

  DISP -->|claim| CLAIM
  CLAIM -->|FOR UPDATE SKIP LOCKED| DB
  DISP -->|submit| POOL
  POOL --> EXEC
  EXEC --> LIFE
  LIFE -->|COMPLETED / PENDING retry| DB
  LIFE -->|DEAD after 3 fails| DLQ
  LIFE -->|DEAD| DB
  WATCH -->|timeout sweep| LIFE
  WATCH -->|find stuck RUNNING| DB
```

### Concurrency model

```mermaid
flowchart LR
  subgraph Dispatcher
    A["inFlight &lt; 5?"]
    B["claim next PENDING"]
    C["submit to pool"]
  end

  subgraph Pool["5 worker threads"]
    T1[Job 1]
    T2[Job 2]
    T3[Job 3]
    T4[Job 4]
    T5[Job 5]
  end

  A -->|yes| B
  B -->|SKIP LOCKED| C
  C --> Pool
  T1 -->|done| A
  T2 -->|done| A
```

## Architecture with all APIs

```mermaid
flowchart TB
  subgraph Clients
    UI["Web UI<br/>GET /"]
    BROWSER["Browser / curl / REST client"]
  end

  subgraph JobAPI["job-api :8080"]
    subgraph Static["Static / UI"]
      HOME["GET /<br/>Form + live dashboard"]
      CSS["GET /css/app.css"]
      JS["GET /js/app.js"]
    end

    subgraph JobAPIs["Job REST + SSE"]
      CREATE["POST /api/jobs<br/>Create job body: name, details<br/>201 or 409 duplicate name"]
      LIST["GET /api/jobs<br/>List jobs ?status=PENDING|RUNNING|..."]
      GETONE["GET /api/jobs/{id}<br/>Get job by id"]
      STREAM["GET /api/jobs/stream<br/>SSE live updates ?status="]
    end

    subgraph SupportAPIs["Support"]
      UICONFIG["GET /api/ui-config<br/>jobsBasePath, jobsStreamPath"]
      HEALTH["GET /actuator/health"]
      INFO["GET /actuator/info"]
      H2["GET /h2-console<br/>local only"]
    end

    CTRL["JobController"]
    UISVC["UiConfigController"]
    SSE_SVC["JobSseService"]
    JOB_SVC["JobService"]
  end

  subgraph JobWorker["job-worker — no public HTTP APIs"]
    DISP["JobDispatcher — pool 5"]
    WATCH["StuckJobWatchdog — RUNNING > 1h"]
    CLAIM["Claim SKIP LOCKED"]
    EXEC["Execute / retry / DLQ"]
  end

  subgraph DB["Shared DB"]
    JOBS[(jobs)]
    DLQ[(dead_letter_jobs)]
  end

  UI --> HOME
  UI --> CSS
  UI --> JS
  UI -->|load paths| UICONFIG
  UI -->|submit| CREATE
  UI -->|EventSource| STREAM

  BROWSER --> CREATE
  BROWSER --> LIST
  BROWSER --> GETONE
  BROWSER --> STREAM
  BROWSER --> UICONFIG
  BROWSER --> HEALTH

  CREATE --> CTRL --> JOB_SVC --> JOBS
  LIST --> CTRL --> JOB_SVC
  GETONE --> CTRL --> JOB_SVC
  STREAM --> CTRL --> SSE_SVC --> JOBS
  UICONFIG --> UISVC
  CREATE -.->|publish snapshot| SSE_SVC

  DISP --> CLAIM --> JOBS
  DISP --> EXEC --> JOBS
  EXEC -->|DEAD| DLQ
  WATCH --> JOBS
  WATCH -->|timeout DEAD| DLQ
```

### Job lifecycle

```mermaid
stateDiagram-v2
  [*] --> PENDING: POST /api/jobs
  PENDING --> RUNNING: worker claims job
  RUNNING --> COMPLETED: success
  RUNNING --> PENDING: fail, attempts < 3
  RUNNING --> DEAD: fail, attempts = 3
  RUNNING --> DEAD: stuck > 1 hour
  DEAD --> DLQ: row in dead_letter_jobs
  COMPLETED --> [*]
  DEAD --> [*]
```

### Request flow

```mermaid
sequenceDiagram
  participant UI as Browser UI
  participant API as job-api
  participant DB as jobs DB
  participant W as job-worker

  UI->>API: GET /api/ui-config
  API-->>UI: paths

  UI->>API: GET /api/jobs/stream
  API-->>UI: SSE connected + snapshot

  UI->>API: POST /api/jobs
  API->>DB: INSERT PENDING
  API-->>UI: 201 JobResponse
  API-->>UI: SSE snapshot

  W->>DB: claim PENDING → RUNNING
  API-->>UI: SSE snapshot RUNNING
  W->>DB: COMPLETED / retry PENDING / DEAD+DLQ
  API-->>UI: SSE snapshot

  UI->>API: GET /api/jobs?status=COMPLETED
  API-->>UI: filtered list
  UI->>API: GET /api/jobs/{id}
  API-->>UI: job detail
```

### API catalog (job-api)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/` | Form + dashboard UI |
| `GET` | `/css/app.css` | UI styles |
| `GET` | `/js/app.js` | UI logic |
| `GET` | `/api/ui-config` | Frontend path config |
| `POST` | `/api/jobs` | Create job `{ "name", "details" }` |
| `GET` | `/api/jobs` | List all jobs (optional `?status=`) |
| `GET` | `/api/jobs/{id}` | Get one job |
| `GET` | `/api/jobs/stream` | SSE live job snapshots (optional `?status=`) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/info` | App info |
| `GET` | `/h2-console` | H2 console (local profile only) |

**job-worker** exposes no public HTTP APIs (background processor only).

### Modules

| Module | Role |
|--------|------|
| `job-api` | Create/list/status APIs, SSE live dashboard, UI |
| `job-worker` | Parallel processing, retries, timeout → DLQ |
| `job-common` | Shared `Job`, status, repositories |

## Profiles

| Profile | Config files | Use |
|---------|--------------|-----|
| `local` (default) | `application.properties` + `application-local.properties` | H2 file DB |
| `production` | `application.properties` + `application-production.properties` | Postgres via env vars |

Configurable base path:

```properties
app.api.jobs-base-path=/api/jobs
```

Fixed under that base (do not change):

- `GET/POST /api/jobs`
- `GET /api/jobs/{id}`
- `GET /api/jobs/stream` (SSE)

## Run — local

First time (or after pulling changes), install shared module once from the parent folder:

```bash
cd /workspaces/async-jobs
./mvnw clean install -DskipTests
```

Then start both apps:

```bash
# Terminal 1
cd /workspaces/async-jobs/job-api
../mvnw spring-boot:run

# Terminal 2
cd /workspaces/async-jobs/job-worker
../mvnw spring-boot:run
```

Default profile is `local`.

## Run — production (deployable JARs)

```bash
# 1) Start Postgres
docker compose up -d

# 2) Build
./mvnw clean package -DskipTests

# 3) Export env (see .env.production.example)
export SPRING_PROFILES_ACTIVE=production
export DB_URL=jdbc:postgresql://localhost:5432/jobsdb
export DB_USERNAME=jobs
export DB_PASSWORD=jobs

# 4) Start API then worker
java -jar job-api/target/job-api-0.0.1-SNAPSHOT.jar
java -jar job-worker/target/job-worker-0.0.1-SNAPSHOT.jar
```

## UI (form + live dashboard)

With job-api running, open:

- http://localhost:8080/ — submit jobs + SSE live dashboard
- Status filter uses server-side SSE: `/api/jobs/stream?status=PENDING`

Also start **job-worker** so statuses move `PENDING → RUNNING → COMPLETED`.

### Parallel worker

- Up to **5** jobs in flight (`worker.pool-size`)
- When one finishes, the next `PENDING` job is claimed immediately
- Retries up to **3** attempts (`worker.max-attempts`)
- After max attempts → status `DEAD` + row in `dead_letter_jobs`
- If a job stays `RUNNING` longer than **1 hour** (`worker.running-timeout-ms`) → `DEAD` + DLQ immediately (no retries)

Force retry/DLQ in local tests by putting `forceFail` in details, or use payload containing `forceFail`.

## API examples (default paths)

```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Invoice export","details":"Export March invoices"}'

curl -s http://localhost:8080/api/jobs/{id}
curl -s http://localhost:8080/api/jobs
curl -s -N http://localhost:8080/api/jobs/stream
```

If you change `app.api.jobs-base-path` or `server.servlet.context-path`, the UI reads paths from `/api/ui-config`.
