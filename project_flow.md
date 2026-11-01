# 🧭 AI Job Tracker — Project Flow

## 🏗️ High-Level Architecture

```mermaid
graph TD
    Browser["🌐 Browser (Thymeleaf UI)"]
    Client["📱 API clients (Postman, Angular, mobile)"]
    SEC["🔐 Spring Security\nForm login · Google OIDC · JWT"]
    JS["job-service :8081\nThymeleaf UI + REST Controllers\nSpring Data JPA + Spring Mail"]
    AI["ai-agent-service :8082\nGroq API → gpt-oss-120b\nJD Parsing + Resume Scoring"]
    DB["PostgreSQL :5432\njobtracker DB"]
    KAFKA["Kafka\nemail topic + dead-letter topic"]
    GMAIL["Gmail SMTP\n(Outbound Email)"]
    GROQ["Groq Cloud API\nopenai/gpt-oss-120b"]
    GOOGLE["Google OAuth\n(Sign in with Google)"]

    Browser -->|"Session cookie + CSRF token"| SEC
    Client -->|"Authorization: Bearer JWT"| SEC
    SEC --> JS
    SEC <-->|"OIDC login"| GOOGLE
    JS -->|"WebClient POST /api/ai/analyze"| AI
    JS -->|"JPA / Hibernate"| DB
    JS -->|"Publish email events"| KAFKA
    KAFKA -->|"Consume"| JS
    JS -->|"JavaMail / SMTP"| GMAIL
    AI -->|"REST API Call"| GROQ
```

---

## 📦 Microservices Overview

| Service | Port | Role |
|---|---|---|
| `job-service` | 8081 | Main app — UI, auth, CRUD, email, tracking, Kafka producer + consumer |
| `ai-agent-service` | 8082 | AI proxy — JD parsing & resume scoring. **No login — keep internal** |
| PostgreSQL | 5432 | Persistent storage (one shared database, rows scoped by `user_id`) |
| Kafka | 9092 | Async email pipeline: `email` topic + dead-letter topic |
| Redis | 6379 | Defined in Docker (not used yet — planned AI result cache) |

---

## 🔐 Authentication & Per-User Data

### Ways to sign in

| Client | How | Result |
|---|---|---|
| Browser (Thymeleaf pages) | Email + password at `/login`, or **Continue with Google** | Session cookie (JSESSIONID) |
| API client (Postman, Angular, mobile) | `POST /api/auth/login` | JWT, sent as `Authorization: Bearer <token>` |

All three paths end up with the same identity: **the user's email**. That is what the app uses to find "the current user".

### Password login (web)

```mermaid
sequenceDiagram
    actor User
    participant UI as login.html
    participant SEC as Spring Security
    participant UDS as AppUserDetailsService
    participant DB as PostgreSQL

    User->>UI: GET /login
    UI-->>User: Login form (with CSRF token)
    User->>SEC: POST /login (email, password)
    SEC->>UDS: loadUserByUsername(email)
    UDS->>DB: SELECT user by email
    DB-->>UDS: User + BCrypt hash
    SEC->>SEC: Compare password with hash
    SEC-->>User: Session cookie, redirect to /jobs
```

### Google sign-in / sign-up

```mermaid
sequenceDiagram
    actor User
    participant JS as job-service
    participant G as Google
    participant GS as GoogleOidcUserService
    participant DB as PostgreSQL

    User->>JS: GET /oauth2/authorization/google
    JS-->>User: Redirect to Google
    User->>G: Sign in and consent
    G-->>User: Redirect to /login/oauth2/code/google with a code
    User->>JS: Follow the redirect
    JS->>G: Exchange code for ID token
    G-->>JS: ID token (email, name, email_verified)
    JS->>GS: loadUser(...)
    GS->>DB: Find user by email
    alt No user yet
        GS->>DB: INSERT user (provider = GOOGLE, random unusable password)
    else Email already has a password account
        GS-->>User: Rejected - redirect to /login with an error
    end
    JS-->>User: Session cookie, redirect to /jobs
```

Google users never have a usable password. A Google email that already belongs to a password account is rejected on purpose, so accounts are never merged silently.

### JWT for API clients

```mermaid
sequenceDiagram
    participant C as API client
    participant API as AuthApiController
    participant F as JwtAuthFilter
    participant S as Any /api/** endpoint

    C->>API: POST /api/auth/login (email, password)
    API-->>C: { token, name, email }
    C->>F: Request with Authorization: Bearer token
    F->>F: Verify signature + expiry, read email
    F->>S: Request continues as that user
    S-->>C: JSON response
    Note over C,S: No token or a bad token gives 401. Tokens last 24 hours.
```

There is no server-side logout for JWTs. The client just deletes the token.

### How a request is secured and scoped

```mermaid
flowchart TD
    REQ["Incoming request"] --> PUB{"Public path?\n/login /register /error\n/track/open/** /api/auth/**"}
    PUB -->|Yes| OK["Allowed without login"]
    PUB -->|No| AUTH{"Logged in?\nSession cookie or Bearer JWT"}
    AUTH -->|No, /api/**| E401["401 Unauthorized"]
    AUTH -->|No, page| REDIR["Redirect to /login"]
    AUTH -->|Yes| CSRF{"POST with session cookie?"}
    CSRF -->|"Yes, no CSRF token"| E403["403 Forbidden"]
    CSRF -->|"No, or token present, or Bearer"| CTRL["Controller"]
    CTRL --> SVC["Service calls CurrentUserService"]
    SVC --> REPO["Repository query filtered by user"]
    REPO -->|"Row belongs to user"| DATA["Return data"]
    REPO -->|"Row belongs to someone else"| E404["404 Not Found"]
```

### Per-user data rule

- `job_applications`, `email_campaigns` and `email_templates` each have a `user_id` owner.
- Every service method works on the logged-in user's rows only (`findByIdAndUser`, `findByUser...`).
- Asking for another user's record returns **404**, so the ID is not even confirmed to exist.
- The owner field is hidden from JSON responses.
- Public exception: `/track/open/{recipientId}` has to work without a login, because it is loaded by the HR's mail client.

---

## 🔄 Core User Flows

### 1. 📊 Add a Job Application

```mermaid
sequenceDiagram
    actor User
    participant UI as Thymeleaf UI (job-service)
    participant SVC as JobApplicationService
    participant DB as PostgreSQL
    participant AI as ai-agent-service

    User->>UI: GET /jobs/new (add form)
    UI-->>User: Renders add.html form
    User->>UI: POST /jobs (with company, role, JD, resume)
    UI->>SVC: save(JobApplicationDTO)
    SVC->>SVC: currentUser.get() - owner of the new row
    SVC->>DB: INSERT JobApplication (status=APPLIED, user_id)
    alt JD text provided
        SVC->>AI: POST /api/ai/analyze {jdText, resumeText}
        AI-->>SVC: {extractedSkills, fitScore, fitReason}
        SVC->>DB: UPDATE with AI results
    end
    SVC-->>UI: Saved JobApplication
    UI-->>User: Redirect to /jobs (dashboard)
```

Edit works the same way: `GET /jobs/{id}/edit` shows a pre-filled form, `POST /jobs/{id}/edit` saves it, and `POST /jobs/{id}/reanalyse` re-runs the AI. All three look the record up by id **and** owner.

---

### 2. 🔍 JD Analyser (Standalone AI Analysis)

```mermaid
sequenceDiagram
    actor User
    participant UI as analyse.html
    participant AC as AnalyseController
    participant AI as ai-agent-service
    participant GROQ as Groq LLM (gpt-oss-120b)

    User->>UI: GET /analyse (must be logged in)
    UI-->>User: Renders form + CSRF token in meta tags
    User->>UI: Click Analyse
    UI->>AC: POST /api/analyse {jdText, resumeText} + X-CSRF-TOKEN header
    AC->>AI: POST /api/ai/analyze
    AI->>GROQ: Structured prompt with JD + resume
    GROQ-->>AI: JSON {skills, fitScore, fitReason}
    AI-->>AC: AnalysisResponseDTO
    AC-->>UI: Result JSON
    UI-->>User: Color-coded fit score (0-100) + skills list
```

From the browser the page sends the session cookie and the CSRF token. From Postman or other API clients you send a Bearer token instead.

---

### 3. 📧 Cold Mail Campaign Flow (async, via Kafka)

```mermaid
sequenceDiagram
    actor User
    participant UI as create.html
    participant EC as EmailCampaignController
    participant ECS as EmailCampaignService
    participant DB as PostgreSQL
    participant K as Kafka
    participant CON as KafkaEmailConsumer
    participant GMAIL as Gmail SMTP

    User->>UI: GET /campaigns/new (optionally pick a template)
    UI-->>User: Renders create form
    User->>UI: POST /campaigns (subject, body, HR names+emails, resume file)
    UI->>EC: createCampaign(...)
    EC->>ECS: createAndSend(dto, file)
    ECS->>DB: INSERT EmailCampaign (owner = current user) + CampaignRecipients
    loop For each recipient (up to 10)
        ECS->>K: Publish EmailEvent (personalised body, tracking pixel URL)
    end
    ECS-->>UI: Saved EmailCampaign (recipients QUEUED)
    UI-->>User: Redirect to /campaigns/{id}

    K->>CON: Deliver EmailEvent
    CON->>GMAIL: Send HTML email with resume attached
    alt Sent
        CON->>DB: recipient sent=true, deliveryStatus=SENT
    else Failed
        CON->>K: Retry (up to 3 attempts)
        CON->>DB: After the last attempt - deliveryStatus=FAILED + failureReason
        Note over K: Event also lands on the dead-letter topic
    end
```

The consumer runs in the background with no logged-in user. It works from the recipient id carried in the event.

---

### 4. 📬 Email Open Tracking Flow

```mermaid
sequenceDiagram
    participant HR as HR's Mail Client
    participant JS as job-service :8081
    participant DB as PostgreSQL

    Note over HR: HR opens the email
    HR->>JS: GET /track/open/{recipientId} (loads 1x1 pixel, no login needed)
    JS->>DB: UPDATE CampaignRecipient (opened=true, openedAt=now)
    JS-->>HR: 1x1 transparent GIF (invisible)
    Note over DB: Open event recorded silently
```

Replies are marked by the owner with `POST /campaigns/recipients/{id}/replied`, which checks that the recipient belongs to one of that user's campaigns.

---

## 🧠 AI Agent — Internal Flow

```mermaid
flowchart TD
    IN["Input: jdText + resumeText"] --> PROMPT["Build structured prompt\n(Perceive)"]
    PROMPT --> GROQ["POST to Groq API\nmodel from groq.model setting\n(Reason)"]
    GROQ --> PARSE["Parse JSON response\n(Act)"]
    PARSE --> OUT["Return AnalysisResponseDTO\n{extractedSkills, fitScore, fitReason}"]

    GROQ -->|"Rate limit / error"| RETRY["Retry up to 3x\nBackoff 5s → 10s → 15s"]
    RETRY --> GROQ
```

Groq retires models from time to time (`llama-3.3-70b-versatile` was shut down in August 2026), so the model name is a setting, not hard-coded.

---

## 🗄️ Data Model

```mermaid
erDiagram
    USER {
        Long id PK
        String name
        String email UK
        String passwordHash
        String provider
        LocalDateTime createdAt
    }

    JOB_APPLICATION {
        Long id PK
        Long user_id FK
        String company
        String role
        String jdText
        String resumeText
        String extractedSkills
        Integer fitScore
        String fitReason
        ApplicationStatus status
        ApplicationSource source
        String hrName
        String hrEmail
        LocalDate mailSentDate
        LocalDate followUpDate
        Boolean followUpDone
        String notes
    }

    EMAIL_CAMPAIGN {
        Long id PK
        Long user_id FK
        String name
        String subject
        String body
        LocalDateTime createdAt
    }

    CAMPAIGN_RECIPIENT {
        Long id PK
        Long campaign_id FK
        String hrName
        String hrEmail
        Boolean sent
        LocalDateTime sentAt
        String deliveryStatus
        String failureReason
        Integer retryCount
        Boolean opened
        LocalDateTime openedAt
        Boolean replied
        LocalDateTime repliedAt
    }

    EMAIL_TEMPLATE {
        Long id PK
        Long user_id FK
        String name
        String subject
        String body
        LocalDateTime updatedAt
    }

    USER ||--o{ JOB_APPLICATION : "owns"
    USER ||--o{ EMAIL_CAMPAIGN : "owns"
    USER ||--o{ EMAIL_TEMPLATE : "owns"
    EMAIL_CAMPAIGN ||--o{ CAMPAIGN_RECIPIENT : "has"
```

`provider` is `LOCAL` for password accounts and `GOOGLE` for Google sign-ins. Recipients have no `user_id`: they belong to a campaign, and the campaign has the owner.

---

## 📡 Endpoint Surface

### job-service (`:8081`)

**Public (no login):**

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/login` | Login page |
| `POST` | `/login` | Form login (handled by Spring Security) |
| `GET`, `POST` | `/register` | Register page and form |
| `GET` | `/oauth2/authorization/google` | Starts Google sign-in |
| `POST` | `/api/auth/register` | Create account, returns JWT |
| `POST` | `/api/auth/login` | Log in, returns JWT |
| `GET` | `/track/open/{recipientId}` | Open-tracking pixel |

**Needs login** (session cookie, or Bearer JWT for `/api/**`):

| Method | Endpoint | Handler | Description |
|---|---|---|---|
| `POST` | `/logout` | Spring Security | Log out (needs CSRF token) |
| `GET` | `/jobs` | `JobApplicationController` | Dashboard with stats |
| `GET` | `/jobs/new` | `JobApplicationController` | Add form |
| `POST` | `/jobs` | `JobApplicationController` | Save + trigger AI |
| `GET` | `/jobs/{id}` | `JobApplicationController` | Detail view |
| `GET` | `/jobs/{id}/edit` | `JobApplicationController` | Edit form |
| `POST` | `/jobs/{id}/edit` | `JobApplicationController` | Save edit |
| `POST` | `/jobs/{id}/reanalyse` | `JobApplicationController` | Re-run AI analysis |
| `POST` | `/jobs/{id}/status` | `JobApplicationController` | Update status |
| `POST` | `/jobs/{id}/followup` | `JobApplicationController` | Mark follow-up done |
| `POST` | `/jobs/{id}/delete` | `JobApplicationController` | Delete |
| `GET` | `/analyse` | `AnalyseController` | JD Analyser page |
| `POST` | `/api/analyse` | `AnalyseController` | AI proxy endpoint |
| `GET` | `/campaigns` | `EmailCampaignController` | Campaign list |
| `GET` | `/campaigns/new` | `EmailCampaignController` | Create form |
| `POST` | `/campaigns` | `EmailCampaignController` | Create + queue emails |
| `GET` | `/campaigns/{id}` | `EmailCampaignController` | Campaign tracking |
| `POST` | `/campaigns/recipients/{id}/replied` | `EmailCampaignController` | Mark replied |
| `POST` | `/campaigns/{id}/delete` | `EmailCampaignController` | Delete campaign |
| `GET` | `/templates` | `EmailTemplateController` | Template list |
| `GET` | `/templates/new` | `EmailTemplateController` | New template form |
| `POST` | `/templates` | `EmailTemplateController` | Create template |
| `GET`, `POST` | `/templates/{id}/edit` | `EmailTemplateController` | Edit template |
| `POST` | `/templates/{id}/delete` | `EmailTemplateController` | Delete template |
| `GET` | `/api/templates/{id}` | `EmailTemplateController` | Template as JSON (campaign form) |

**REST API for jobs** (`JobApplicationRestController`, Bearer JWT):

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/jobs` | List your applications |
| `GET` | `/api/jobs/{id}` | Get one |
| `POST` | `/api/jobs` | Create |
| `PUT` | `/api/jobs/{id}` | Update |
| `POST` | `/api/jobs/{id}/reanalyse` | Re-run AI |
| `PUT` | `/api/jobs/{id}/status` | Update status |
| `PUT` | `/api/jobs/{id}/followup` | Mark follow-up done |
| `DELETE` | `/api/jobs/{id}` | Delete |
| `GET` | `/api/jobs/pending-followups` | Follow-ups that are due |
| `GET` | `/api/jobs/stats` | Dashboard numbers |

### ai-agent-service (`:8082`, no authentication)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/ai/analyze` | Parse JD + score resume |
| `GET` | `/api/ai/health` | Health check |

---

## 🔁 Status Lifecycle

```mermaid
stateDiagram-v2
    [*] --> APPLIED: Save new application
    APPLIED --> INTERVIEW: Status update
    INTERVIEW --> OFFER: Status update
    INTERVIEW --> REJECTED: Status update
    APPLIED --> REJECTED: Status update
    OFFER --> [*]
    REJECTED --> [*]
    APPLIED --> WITHDRAWN: Status update
    WITHDRAWN --> [*]
```

---

## 🐳 Docker Compose Topology

```
docker compose up
│
├── postgres (port 5432)         ← job-service depends on this
├── redis (port 6379)            ← provisioned, future use
├── kafka (port 9092)            ← email events + dead-letter topic
├── ai-agent-service (port 8082) ← needs GROQ_API_KEY
└── job-service (port 8081)      ← needs JWT_SECRET, DB_PASSWORD, Gmail + Google credentials
        │
        ├── connects to postgres via JDBC
        ├── connects to kafka (produces and consumes email events)
        ├── connects to ai-agent-service via WebClient
        └── connects to Gmail via SMTP
```

### Environment variables

| Variable | Used by |
|---|---|
| `JWT_SECRET` (32+ characters) | job-service |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | job-service (Google sign-in) |
| `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` | job-service (campaign emails) |
| `DB_PASSWORD` | job-service, postgres |
| `GROQ_API_KEY` | ai-agent-service |

Secrets live in `.env` or the IDE run configuration, never in committed files.

---

## 🚧 Current Limitations

- Campaign emails still go out from one shared Gmail account (per-user sending is planned).
- The tracking pixel uses sequential recipient IDs (random tokens planned).
- Kafka retries have no delay between attempts, and the resume travels inside the message (fix planned).
- `ai-agent-service` has no authentication, so it must not be exposed publicly.
- Redis is provisioned but unused.