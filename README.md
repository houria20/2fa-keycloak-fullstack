# 2FA Keycloak Fullstack

A complete fullstack demonstration of **Step-Up Authentication (2FA)** using:

- 🔑 **Keycloak 23** — OIDC/OAuth2 server with OTP/TOTP browser flow
- 🌱 **Spring Boot 3.2** — Stateless JWT resource server with Redis-backed one-time action tokens
- 🅰️ **Angular 19** — Frontend with `keycloak-angular`, PKCE, and an HTTP interceptor for the step-up flow
- 🔴 **Redis 7** — Short-TTL, one-time-use action token store
- 🐘 **PostgreSQL 15** — Keycloak database

---

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Angular Frontend  (port 4200)                             │
│  • keycloak-angular — OIDC login (PKCE / S256)            │
│  • StepUpAuthInterceptor — handles 403 + triggers OTP     │
└──────────────────────┬─────────────────────────────────────┘
                       │  Bearer JWT  +  X-Action-Token header
                       ▼
┌────────────────────────────────────────────────────────────┐
│  Spring Boot Backend  (port 8081)                          │
│  • StepUpAuthFilter  — checks amr=otp / acr=gold in JWT   │
│  • ActionTokenService — Redis one-time tokens (5 min TTL) │
│  • CardController    — /api/cards/**  (sensitive data)     │
└──────────────┬───────────────────────┬─────────────────────┘
               │ JWT validation         │ token storage
               ▼                        ▼
 ┌─────────────────────┐   ┌──────────────────────────┐
 │  Keycloak  (8080)   │   │  Redis  (6379)            │
 │  realm: 2fa-demo    │   │  action-token:{token}     │
 │  OTP browser flow   │   │  TTL: 300 s, one-shot     │
 └─────────────────────┘   └──────────────────────────┘
```

## 2FA Step-Up Flow

```
User           Angular               Spring Boot         Redis        Keycloak
 │                │                       │                │               │
 │─ click View ──►│                       │                │               │
 │                │── GET /api/cards/{id} ─────────────────────────────────►
 │                │                       │  JWT: no OTP   │               │
 │                │                       │── store token ─►               │
 │                │◄── 403 {action_token} ─┤                │               │
 │                │  StepUpInterceptor     │                │               │
 │                │── keycloak.login(acr=gold) ─────────────────────────────►
 │◄── OTP prompt ─│                       │                │               │
 │── enter OTP ───────────────────────────────────────────────────────────►│
 │                │◄── new JWT (amr=otp) ──────────────────────────────────│
 │                │── GET /api/cards/{id} (new JWT + X-Action-Token) ──────►
 │                │                       │ verify amr=otp │               │
 │                │                       │── consume ─────►               │
 │                │◄── 200 { card data } ──┤                │               │
 │◄── card data ──│                       │                │               │
```

---

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Node.js 20+ (for local frontend development)
- Java 17+ & Maven (for local backend development)

### Run Everything with Docker Compose

```bash
# 1. Start all services (Keycloak, PostgreSQL, Redis, Backend)
docker-compose up -d

# 2. Wait ~30 s for Keycloak to finish importing the realm
docker-compose logs -f keycloak   # Ctrl+C when you see "Listening on: http://0.0.0.0:8080"

# 3. Start the Angular frontend
cd frontend
npm install
npm start          # → http://localhost:4200
```

### Service URLs

| Service  | URL                              | Credentials |
|----------|----------------------------------|-------------|
| Frontend | http://localhost:4200            | —           |
| Backend  | http://localhost:8081            | —           |
| Keycloak | http://localhost:8080/admin      | admin / admin |
| Redis    | localhost:6379                   | —           |

### Test User

| Username | Password | OTP Setup         |
|----------|----------|-------------------|
| testuser | testuser | Prompted on first login |

> On first login Keycloak will prompt the user to register an OTP authenticator (Google Authenticator, Authy, etc.).

---

## Project Structure

```
2fa-keycloak-fullstack/
├── docker-compose.yml
├── keycloak/
│   └── realm-export.json          # Realm with OTP browser flow, client, test user
├── backend/                       # Spring Boot 3.2 / Java 17
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/twofa/
│       ├── TwoFaApplication.java
│       ├── config/
│       │   ├── SecurityConfig.java     # Stateless JWT + CORS
│       │   └── RedisConfig.java
│       ├── filter/
│       │   └── StepUpAuthFilter.java   # 2FA enforcement (amr/acr check + action token)
│       ├── service/
│       │   └── ActionTokenService.java # Redis one-time token (generate / validate+consume)
│       ├── controller/
│       │   └── CardController.java     # /api/cards/** — protected by step-up filter
│       └── model/
│           └── Card.java
└── frontend/                      # Angular 19
    ├── package.json
    ├── angular.json
    ├── tsconfig.json
    └── src/app/
        ├── app.module.ts              # Keycloak init (PKCE S256), provideHttpClient
        ├── app-routing.module.ts      # Functional authGuard
        ├── interceptors/
        │   └── step-up-auth.interceptor.ts  # 403 → OTP → retry
        ├── services/
        │   ├── card.service.ts        # API calls with X-Action-Token header
        │   └── action-token.service.ts
        └── components/card/           # Card list + 2FA-gated detail view
```

---

## Keycloak Configuration

The realm is imported automatically at startup from `keycloak/realm-export.json`. It contains:

- **Realm**: `2fa-demo`
- **Client**: `2fa-frontend` (public, PKCE S256, redirect to `http://localhost:4200/*`)
- **Browser flow**: username/password → **OTP (REQUIRED)**
- **Roles**: `USER`, `ADMIN`

### ACR to LoA Mapping (optional, for `acr=gold` step-up)

1. Keycloak Admin → `2fa-demo` → **Authentication** → **Policies** → **Authentication level of assurance**
2. Add condition: Level `2` → OTP authenticator
3. In client `2fa-frontend` → **Advanced** → **ACR to LoA Mapping**: `gold` = `2`

---

## Security Notes

| Concern | Mitigation |
|---|---|
| Action token secrecy | Token is never stored in the browser (memory only); transmitted only in the `X-Action-Token` header |
| One-time use | Redis key is deleted atomically on first valid use |
| Short TTL | 300 s by default (configurable via `app.security.action-token-ttl-seconds`) |
| Token binding | Token value is `userId:method:path` — cannot be reused by another user or for another action |
| CSRF | Disabled: stateless API, tokens carried in `Authorization` header (not cookies) |
| PKCE | Frontend uses S256 code challenge — prevents authorization code interception |

## Error Scenarios

| Scenario | Backend Response | Frontend Behavior |
|---|---|---|
| No 2FA in JWT | `403 step_up_required + action_token` | Interceptor triggers Keycloak OTP login |
| JWT has OTP but action token missing | `403 step_up_required + action_token` | Interceptor retriggers OTP |
| Action token expired | `403 step_up_required + action_token` | Interceptor retriggers OTP |
| Action token already used | `403 step_up_required + action_token` | Interceptor retriggers OTP |
| Invalid JWT | `401` | Keycloak re-authentication |

---

## Local Development (without Docker)

```bash
# Start only Redis and Keycloak via Docker
docker-compose up -d redis keycloak postgres

# Backend
cd backend
mvn spring-boot:run

# Frontend (separate terminal)
cd frontend
npm install
npm start
```
