# 🧭 AI Job Tracker — Project Flow

## 🏗️ High-Level Architecture

```mermaid
graph TD
    Browser["🌐 Browser (User)"]
    JS["job-service :8081\nThymeleaf UI + REST Controllers\nSpring Data JPA + Spring Mail"]
    AI["ai-agent-service :8082\nGroq API → Llama 3.3 70B\nJD Parsing + Resume Scoring"]
    DB["PostgreSQL :5432\njobtracker DB"]
    GMAIL["Gmail SMTP\n(Outbound Email)"]
    GROQ["Groq Cloud API\nLlama 3.3 70B"]

    Browser -->|HTTP Requests| JS
    JS -->|WebClient POST /api/ai/analyze| AI
    JS -->|JPA / Hibernate| DB
    JS -->|JavaMail / SMTP| GMAIL
    AI -->|REST API Call| GROQ
```

---

## 📦 Microservices Overview

| Service | Port | Role |
|---|---|---|
| `job-service` | 8081 | Main app — UI, CRUD, email, tracking |
| `ai-agent-service` | 8082 | AI proxy — JD parsing & resume scoring |
| PostgreSQL | 5432 | Persistent storage |
| Redis | 6379 | Defined in Docker (future use) |

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
    SVC->>DB: INSERT JobApplication (status=APPLIED)
    alt JD text provided
        SVC->>AI: POST /api/ai/analyze {jdText, resumeText}
        AI-->>SVC: {extractedSkills, fitScore, fitReason}
        SVC->>DB: UPDATE with AI results
    end
    SVC-->>UI: Saved JobApplication
    UI-->>User: Redirect to /jobs (dashboard)
```

---

### 2. 🔍 JD Analyser (Standalone AI Analysis)

```mermaid
sequenceDiagram
    actor User
    participant UI as analyse.html
    participant AC as AnalyseController
    participant AI as ai-agent-service
    participant GROQ as Groq LLM (Llama 3.3 70B)

    User->>UI: GET /analyse
    UI-->>User: Renders analyse form
    User->>UI: POST /api/analyse {jdText, resumeText}
    UI->>AC: Proxy request
    AC->>AI: POST /api/ai/analyze
    AI->>GROQ: Structured prompt with JD + resume
    GROQ-->>AI: JSON {skills, fitScore, fitReason}
    AI-->>AC: AnalysisResponseDTO
    AC-->>UI: Result JSON
    UI-->>User: Color-coded fit score (0-100) + skills list
```

---

### 3. 📧 Cold Mail Campaign Flow

```mermaid
sequenceDiagram
    actor User
    participant UI as create.html
    participant EC as EmailCampaignController
    participant ECS as EmailCampaignService
    participant DB as PostgreSQL
    participant GMAIL as Gmail SMTP

    User->>UI: GET /campaigns/new
    UI-->>User: Renders create form
    User->>UI: POST /campaigns (subject, body, HR names+emails)
    UI->>EC: createCampaign(CampaignDTO)
    EC->>ECS: createAndSend(dto)
    ECS->>DB: INSERT EmailCampaign + CampaignRecipients
    loop For each recipient (up to 10)
        ECS->>ECS: Personalize body ({hrName} → actual name)
        ECS->>ECS: Inject tracking pixel URL
        ECS->>GMAIL: Send HTML email via SMTP
        ECS->>DB: UPDATE recipient (sent=true, deliveryStatus=SENT)
        ECS->>ECS: Sleep 1.5s (anti-spam delay)
    end
    ECS-->>UI: Saved EmailCampaign
    UI-->>User: Redirect to /campaigns/{id}
```

---

### 4. 📬 Email Open Tracking Flow

```mermaid
sequenceDiagram
    participant HR as HR's Mail Client
    participant JS as job-service :8081
    participant DB as PostgreSQL

    Note over HR: HR opens the email
    HR->>JS: GET /track/open/{recipientId} (loads 1x1 pixel)
    JS->>DB: UPDATE CampaignRecipient (opened=true, openedAt=now)
    JS-->>HR: 1x1 transparent GIF (invisible)
    Note over DB: Open event recorded silently
```

---

## 🧠 AI Agent — Internal Flow

```mermaid
flowchart TD
    IN["Input: jdText + resumeText"] --> PROMPT["Build structured prompt\n(Perceive)"]
    PROMPT --> GROQ["POST to Groq API\nLlama 3.3 70B\n(Reason)"]
    GROQ --> PARSE["Parse JSON response\n(Act)"]
    PARSE --> OUT["Return AnalysisResponseDTO\n{extractedSkills, fitScore, fitReason}"]

    GROQ -->|Rate limit / error| RETRY["Retry up to 3x\nExponential backoff\n5s → 10s → 15s"]
    RETRY --> GROQ
```

---

## 🗄️ Data Model

```mermaid
erDiagram
    JOB_APPLICATION {
        Long id PK
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
        String name
        String subject
        String body
        LocalDateTime createdAt
    }

    CAMPAIGN_RECIPIENT {
        Long id PK
        String hrName
        String hrEmail
        Boolean sent
        LocalDateTime sentAt
        String deliveryStatus
        String failureReason
        Boolean opened
        LocalDateTime openedAt
        Boolean replied
        LocalDateTime repliedAt
    }

    EMAIL_CAMPAIGN ||--o{ CAMPAIGN_RECIPIENT : "has"
```

---

## 📡 REST API Surface

### job-service (`:8081`)

| Method | Endpoint | Handler | Description |
|---|---|---|---|
| `GET` | `/jobs` | `JobApplicationController` | Dashboard with stats |
| `GET` | `/jobs/new` | `JobApplicationController` | Add form |
| `POST` | `/jobs` | `JobApplicationController` | Save + trigger AI |
| `GET` | `/jobs/{id}` | `JobApplicationController` | Detail view |
| `POST` | `/jobs/{id}/status` | `JobApplicationController` | Update status |
| `POST` | `/jobs/{id}/followup` | `JobApplicationController` | Mark follow-up done |
| `POST` | `/jobs/{id}/delete` | `JobApplicationController` | Delete |
| `GET` | `/analyse` | `AnalyseController` | JD Analyser page |
| `POST` | `/api/analyse` | `AnalyseController` | AI proxy endpoint |
| `GET` | `/campaigns` | `EmailCampaignController` | Campaign list |
| `GET` | `/campaigns/new` | `EmailCampaignController` | Create form |
| `POST` | `/campaigns` | `EmailCampaignController` | Create + send |
| `GET` | `/campaigns/{id}` | `EmailCampaignController` | Campaign tracking |
| `POST` | `/campaigns/recipients/{id}/replied` | `EmailCampaignController` | Mark replied |
| `GET` | `/track/open/{id}` | `EmailCampaignController` | Open-tracking pixel |

### ai-agent-service (`:8082`)

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
docker-compose up
│
├── postgres (port 5432)        ← job-service depends on this
├── redis (port 6379)           ← provisioned, future use
├── ai-agent-service (port 8082) ← needs GEMINI_API_KEY env var
└── job-service (port 8081)     ← depends on postgres + ai-agent-service
        │
        └── connects to postgres via JDBC
        └── connects to ai-agent-service via WebClient
```
