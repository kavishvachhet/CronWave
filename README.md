# 🌊 CronWave — Distributed HTTP Job Scheduler

A production-grade, distributed cron-style HTTP job scheduler built with **Spring Boot 3.5**, **MongoDB**, and **Redis**. CronWave lets you schedule recurring HTTP calls (GET, POST, PUT, DELETE, PATCH) using cron expressions, with automatic retries, exponential backoff, crash recovery, and horizontal scaling support.

> Built and load-tested to handle **5,000+ concurrent users** on a single server with sub-100ms response times.

---

## 🏗️ Architecture (CQRS Pattern)

```text
                    ┌─────────────┐
                    │   Clients   │
                    └──────┬──────┘
                           │ (GET, POST, DELETE, PATCH)
                    ┌──────▼──────┐
                    │    Nginx    │
                    │ Load Balancer│
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │                         │
        ┌─────▼─────┐             ┌─────▼─────┐
        │  App #1   │             │  App #2   │
        │ (Spring)  │             │ (Spring)  │
        └─────┬─────┘             └─────┬─────┘
    (Commands)│ (Queries)               │
         ┌────▼────┐              ┌─────▼─────┐
         │  Kafka  │◄─────────────┤  Redis    │
         │ (Topic) │  (Eviction)  │ (Queries) │
         └────┬────┘              └─────┬─────┘
              │                         │
        ┌─────▼─────┐             ┌─────▼─────┐
        │ Kafka     │             │ MongoDB   │
        │ Consumer  ├────────────►│ (Atlas)   │
        └───────────┘  (Writes)   └───────────┘
```

The system uses **Command Query Responsibility Segregation (CQRS)**:
- **Commands (Writes):** POST, PUT, PATCH, and DELETE requests are immediately pushed to an Apache Kafka topic (`job-mutations`). The API instantly responds with `202 Accepted`. A background Kafka Consumer processes these events, writes to MongoDB, and programmatically evicts the user's Redis cache.
- **Queries (Reads):** GET requests fetch data directly from the high-speed Redis cache. If there's a cache miss, data is read from MongoDB and cached.
- **Why?** This prevents slow database writes from blocking API threads during massive traffic spikes. Kafka acts as a shock absorber.

---

## ✨ Features Implemented

### Core Job Scheduling
- **Cron-based scheduling** — Define recurring jobs using standard cron expressions (`0 */5 * * *`)
- **HTTP job execution** — Supports GET, POST, PUT, DELETE, PATCH methods with custom request bodies
- **Async execution** — Jobs run on a dedicated thread pool (`jobExecutorPool`) to prevent blocking the main server

### Reliability & Fault Tolerance
- **Atomic job claiming** — Uses MongoDB's `findAndModify` to prevent duplicate execution across multiple instances. Only one instance can claim a job, making it safe for distributed deployments
- **Exponential backoff retry** — Failed jobs are retried with exponential delays (2s → 4s → 8s → 16s...) up to a configurable `maxRetries` limit
- **Crash recovery** — On startup, the application scans for stuck jobs (status = `RUNNING` or `RETRYING`) and either resumes retries or marks them as `DEAD`
- **Execution logging** — Every job execution (success or failure) is logged with status, response code, response time, and attempt number

### Performance & Caching
- **Redis user caching** — User authentication details are cached in Redis for 5 minutes, eliminating repetitive MongoDB queries. This single optimization improved response times by **410x** (from 24s to 58ms)
- **Redis job caching** — Job listings are cached per-user with a 10-second TTL using Spring Cache abstraction
- **Per-user cache eviction** — Creating or deleting a job evicts only that user's cache entries, preventing thundering herd problems

### Security
- **JWT authentication** — Stateless HMAC-SHA256 signed tokens with 24-hour expiry
- **Rate limiting** — Redis-backed rate limiter using atomic Lua scripts (5 req/min for register, 10 for login, 60 for jobs). Prevents abuse without impacting legitimate users

### Infrastructure
- **Docker Compose** — Full containerized deployment with 3 app replicas + Nginx + Redis
- **Nginx load balancer** — Round-robin distribution across application instances
- **Health checks** — Docker health checks on each instance via `/check` endpoint
- **MongoDB Atlas** — Cloud-hosted database with compound indexes for optimal query performance

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Runtime | Java 21 + Spring Boot 3.5 | Application framework |
| Database | MongoDB Atlas | Persistent storage for jobs, users, execution logs |
| Cache | Redis | User detail caching, job list caching, rate limiting |
| Load Balancer | Nginx | Round-robin across app instances |
| Containers | Docker Compose | Orchestration of app replicas + infra |
| Auth | JWT (HMAC-SHA256) | Stateless authentication |
| Load Testing | k6 (Grafana) | Performance benchmarking |

---

## 📁 Project Structure

```
CronWave/
├── JobScheduler/
│   ├── src/main/java/com/example/JobScheduler/
│   │   ├── auth/
│   │   │   ├── controller/        # REST controllers (Auth, Jobs)
│   │   │   ├── entity/            # MongoDB documents (User, Job, ExecutionLog)
│   │   │   ├── repository/        # Spring Data MongoDB repositories
│   │   │   ├── Services/          # Service interfaces
│   │   │   ├── ServiceImpl/       # Service implementations
│   │   │   │   ├── JobServiceImpl          # CRUD operations for jobs
│   │   │   │   ├── JobClaimServiceImpl     # Atomic job claiming (findAndModify)
│   │   │   │   ├── JobExecutorServiceImpl  # Async HTTP execution
│   │   │   │   ├── RetrybackoffImpl        # Exponential backoff retry
│   │   │   │   ├── RecoveryServiceImpl     # Crash recovery on startup
│   │   │   │   ├── JobServiceCacheImpl     # Redis-cached job queries
│   │   │   │   └── RateLimiterFilter       # Redis-backed rate limiting
│   │   │   ├── Scheduler/         # Polling scheduler (5s interval)
│   │   │   ├── Enum/              # Job status & execution status enums
│   │   │   ├── dto/               # Request/Response DTOs
│   │   │   ├── Utils/             # JWT filter, JWT service
│   │   │   └── Security/          # Password encoder config
│   │   └── config/                # Security, Async, Redis configs
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── nginx.conf
│   └── pom.xml
├── k6/
│   ├── loadtest.js                # k6 load test script (6,000 distinct users)
│   ├── seeder.js                  # Database seeder (6,000 users + JWT tokens)
│   └── tokens.csv                 # Generated JWT tokens for load testing
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- Java 21
- Maven
- Docker & Docker Compose
- MongoDB Atlas account (or local MongoDB)
- Redis

### Run Locally (Single Instance)

```bash
# Clone the repo
git clone https://github.com/kavishvachhet/CronWave.git
cd CronWave

# Set environment variables
cp .env.example .env
# Edit .env with your MongoDB URI and JWT secret

# Start Redis
docker run -d --name redis -p 6379:6379 redis

# Run the application
cd JobScheduler
./mvnw spring-boot:run
```

### Run with Docker Compose (3 Instances + Nginx + Redis)

```bash
cd JobScheduler
docker-compose up --build
```

This starts:
- 3 Spring Boot instances (ports 8080, 8081, 8082)
- Nginx load balancer (port 80)
- Redis (port 6379)

---

## 📡 API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register a new user | No |
| POST | `/auth/login` | Login and receive JWT token | No |
| GET | `/jobs?page=0&size=20` | List your scheduled jobs (paginated) | Yes |
| POST | `/jobs` | Create a new scheduled job | Yes |
| DELETE | `/jobs/{id}` | Delete a job | Yes |
| PATCH | `/jobs/{id}/status` | Pause/resume a job | Yes |
| GET | `/check` | Health check | No |

### Create a Job — Example

```bash
curl -X POST http://localhost:8080/jobs \
  -H "Authorization: Bearer <your-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Health Check Ping",
    "url": "https://httpbin.org/get",
    "method": "GET",
    "cronExpression": "0 */5 * * *"
  }'
```

---

## 📊 Load Testing Results

All tests were performed on a **single laptop** (8GB RAM) using [k6](https://k6.io/) to establish baseline performance before horizontal scaling.

### Test Setup
- **Tool**: k6 (Grafana)
- **Database**: MongoDB Atlas (Free Tier M0)
- **Cache**: Redis (local Docker)
- **Queue**: Apache Kafka (local Docker)
- **Application**: Single Spring Boot instance (Java 21)

### The 5,000 Concurrent User Stress Test (CQRS Architecture)

We ran a massive stress test simulating **5,000 concurrent Virtual Users** heavily hammering the API for 2 minutes simultaneously, reading from Redis and pushing writes into Kafka. 

Here are the results of the untuned default server:

| Metric | Result | Notes |
|--------|--------|-------|
| **Total Requests Handled** | `123,822` | Processed in ~110 seconds |
| **Throughput** | `1,093 req/s` | Massive volume for a single un-tuned Tomcat instance |
| **Average Response Time** | `1.14s` | P95 latency was 3.2s under extreme load |
| **GET /jobs Success (Reads)** | `99%` | Redis handled 61,000+ reads flawlessly |
| **POST /jobs Success (Writes)** | `94%` | Kafka ingested 58,000+ jobs gracefully |
| **Overall Failure Rate** | `3.12%` | Errors were purely TCP `EOF` drops because the default Tomcat `max-connections` (8192) was exceeded. |

> **Key finding**: The addition of Kafka acting as a shock-absorber for writes, combined with Redis for sub-millisecond reads, allows a **single un-tuned machine to handle 123,000+ requests at 1,000+ req/s**. The only bottleneck encountered was Tomcat's default TCP connection limits.

---

## 🔬 Optimization Journey

The following optimizations were applied during load testing, each with measurable impact:

### 1. Redis User Caching (410x improvement)
**Before**: Every authenticated request queried MongoDB for user details.  
**After**: User details cached in Redis for 5 minutes. First request hits MongoDB, subsequent requests served from Redis in sub-milliseconds.

| Metric | Before | After |
|--------|--------|-------|
| Avg response (5k users) | 23.84s | **58ms** |
| p95 latency | 39.94s | **82ms** |
| Throughput | 103 req/s | **2,356 req/s** |

### 2. Tomcat Thread Pool Tuning
Increased `max-threads` from 200 → 800, `accept-count` from 100 → 5,000, and `max-connections` from 8,192 → 10,000. Eliminated "connection refused" errors at 10k VUs.

### 3. MongoDB Connection Pool
Tuned `maxPoolSize` from 50 → 100 with `waitQueueTimeoutMS` of 3,000ms. Ensures write operations don't exhaust the connection pool under concurrent load.

### 4. Database Indexes
Added compound and unique indexes on all queried fields:
- `users`: `{ email: 1 }` (unique) — instant user lookup
- `jobs`: `{ status: 1, nextRunAt: 1 }` — polling scheduler
- `jobs`: `{ userId: 1 }` — user's job listing
- `execution_logs`: `{ jobId: 1, executedAt: -1 }` — log retrieval

### 5. Redis Rate Limiting with Atomic Lua Scripts

**Problem**: Without rate limiting, a single bad actor can flood your API with thousands of requests and exhaust server resources for everyone.

**Naive approach (broken)**:
```
count = redis.GET("rate:ip:path")     ← Thread A reads count = 59
count = redis.GET("rate:ip:path")     ← Thread B reads count = 59 (race condition!)
redis.INCR("rate:ip:path")            ← Thread A increments to 60
redis.INCR("rate:ip:path")            ← Thread B increments to 61 (limit bypassed!)
```

**CronWave's approach (atomic Lua script)**:
```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return count
```

This Lua script executes **atomically inside Redis** — no other command can run between `INCR` and `EXPIRE`. This guarantees:
- **No race conditions** — impossible for two threads to read the same count
- **No key leaks** — `EXPIRE` is only set on the first request, preventing orphaned keys
- **Single round-trip** — one network call instead of three (GET + INCR + EXPIRE)

**Per-endpoint limits**:

| Endpoint | Limit | Window | Purpose |
|----------|-------|--------|---------|
| `/auth/register` | 5 req | 1 min | Prevent mass account creation |
| `/auth/login` | 10 req | 1 min | Prevent brute-force attacks |
| `/jobs/*` | 60 req | 1 min | Prevent API abuse |

**Filter chain order**:
```
Request → RateLimiterFilter → JwtFilter → Controller
              ↓ (if over limit)
         429 Too Many Requests (no JWT parsing wasted)
```

The rate limiter runs **before** JWT authentication in the Spring Security filter chain. This means rate-limited requests are rejected immediately without wasting CPU cycles on JWT signature verification.

**Response headers** on every request:
```
X-Rate-Limit-Limit: 60
X-Rate-Limit-Remaining: 45
```

**Fail-open design**: If Redis goes down, the rate limiter skips itself and lets all requests through. This ensures a Redis outage doesn't bring down your entire API.

---

## 📈 Scaling to 10,000+ Concurrent Users

CronWave is architecturally ready for horizontal scaling. Here's the production roadmap:

### Current Limit (Single Server)
- **~5,000 concurrent users** at sub-100ms latency
- Bottleneck: CPU saturation (single machine processes JWT, Redis I/O, JSON serialization)

### Path to 10,000+ Users

```
Current (Single Server)              Production (Multi-Server)
┌─────────────────┐                  ┌─────────────────┐
│  1 App Instance  │                  │     Nginx LB     │
│  8GB RAM         │                  └────────┬─────────┘
│  ~5k users       │                  ┌────────┼────────┐
└─────────────────┘                  │        │        │
                                ┌────▼──┐┌───▼───┐┌───▼───┐
                                │App #1 ││App #2 ││App #3 │
                                │16GB   ││16GB   ││16GB   │
                                └───┬───┘└───┬───┘└───┬───┘
                                    └────────┼────────┘
                                      ┌──────┴──────┐
                                ┌─────▼─────┐ ┌─────▼─────┐
                                │  Redis    │ │ MongoDB   │
                                │  Cluster  │ │ M10/M30   │
                                └───────────┘ └───────────┘
```

| Change | Why | Impact |
|--------|-----|--------|
| **3-4 App Servers** (16GB each) | Split CPU load across machines | 3-4x throughput |
| **MongoDB Atlas M10/M30** | Dedicated CPU, high IOPS, 1500+ connections | Eliminates free-tier throttling |
| **Redis Cluster** | Distributed caching across nodes | Higher cache throughput |
| **Connection pooling** (500+) | More concurrent DB operations | Handles write bursts |

### Why This Scales Without Code Changes

The architecture is already **stateless and distributed**:
- **JWT auth** — No server-side sessions. Any instance can validate any token
- **Redis cache** — Shared across all instances. User cached by App #1 is instantly available to App #2
- **MongoDB `findAndModify`** — Atomic job claiming prevents duplicate execution across instances
- **Nginx round-robin** — Distributes traffic evenly with zero application awareness

> Scaling from 5k to 10k+ requires only **infrastructure changes** (more servers, bigger database tier). Zero code modifications needed.

---

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MONGO_URI` | MongoDB connection string | Required |
| `JWT_SECRET` | HMAC-SHA256 signing key | Built-in default |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `SERVER_PORT` | Application port | `8080` |

### Application Properties

```properties
# Tomcat (tuned for high concurrency)
server.tomcat.threads.max=800
server.tomcat.threads.min-spare=100
server.tomcat.accept-count=5000
server.tomcat.max-connections=10000
server.tomcat.connection-timeout=5000

# MongoDB
spring.data.mongodb.uri=${MONGO_URI}?maxPoolSize=100&minPoolSize=10

# Redis Cache
spring.cache.type=redis
spring.cache.redis.time-to-live=10000
```

---

## 🧪 Running Load Tests

### Prerequisites
- [k6](https://k6.io/) installed
- Node.js 18+ (for the seeder script)

### Seed Test Users

```bash
cd k6
npm install
node seeder.js    # Creates 6,000 users in MongoDB + tokens.csv
```

### Run the Load Test

```bash
k6 run k6/loadtest.js
```

The test ramps to 5,000 concurrent virtual users over 30 seconds, each using a unique JWT token from the pool of 6,000 generated users.

---

## 📝 License

MIT
