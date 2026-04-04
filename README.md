# AI Job Application Tracker

A full-stack **microservices-based job application tracker** built with Spring Boot, Thymeleaf, PostgreSQL, and Groq LLM AI. Track every job you apply to, analyse job descriptions instantly, send bulk cold mails to HRs, and monitor open/reply rates — all from one clean dashboard.

> Built by Aditya Singh as a portfolio project during active job search preparation. Every feature solves a real problem I faced while applying to companies.

---

## 🚀 Live Features

### 📊 Application Dashboard
- Track all job applications in one place — company, role, status, source, date applied
- Visual fit score progress bars per application
- Stats overview — total, applied, interview, offers, rejected, average fit score
- Follow-up reminders for pending cold mails

### 🔍 JD Analyser (AI Powered)
- Paste any job description → AI extracts all required skills instantly
- Paste your resume alongside → get a **0–100 fit score** with detailed reasoning
- Colour-coded result (green = strong match, yellow = moderate, red = weak)
- No save required — analyse before deciding to apply

### 📧 Cold Mail Campaigns
- Write one email, send to up to 10 HRs individually (not CC/BCC)
- Use `{hrName}` placeholder — personalised per recipient automatically
- **Open tracking** via invisible pixel — know exactly when HR opens your mail
- **Delivery status** per recipient — Sent / Failed with reason
- **Reply tracking** — manually mark when HR replies
- Campaign history with per-campaign stats

### 🗂️ Application Tracking
- Sources: LinkedIn, Naukri, Cold Mail, Referral, Company Website, Job Portal
- Statuses: Applied → Interview → Offer / Rejected / Withdrawn
- Cold mail specific fields: HR name, HR email, mail sent date, follow-up reminder date
- Notes per application
- AI analysis auto-runs on save if JD text is provided

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2 |
| Frontend | Thymeleaf, Bootstrap 5 |
| Database | PostgreSQL 15 |
| AI / LLM | Groq API (Llama 3.3 70B) |
| Email | Gmail SMTP via Spring Mail |
| HTTP Client | Spring WebFlux (WebClient) |
| Build | Maven |
| Containerisation | Docker + Docker Compose |

---

## 🏗️ Architecture

```
Browser
   │
   ▼
┌─────────────────────────────────────┐
│         job-service  :8081          │
│  Thymeleaf UI + REST Controllers    │
│  Spring Data JPA + Spring Mail      │
└──────────┬──────────────────────────┘
           │  WebClient (internal)
           ▼
┌─────────────────────────────────────┐
│       ai-agent-service  :8082       │
│   Groq API → Llama 3.3 70B         │
│   JD parsing + Resume scoring       │
└──────────┬──────────────────────────┘
           │
           ▼
    ┌──────────────┐       ┌─────────────┐
    │  PostgreSQL  │       │  Gmail SMTP │
    │  :5432       │       │  (outbound) │
    └──────────────┘       └─────────────┘
```

---

## 📁 Project Structure

```
ai-job-tracker/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
│
├── job-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/jobtracker/jobservice/
│       │   ├── controller/
│       │   │   ├── JobApplicationController.java
│       │   │   ├── EmailCampaignController.java
│       │   │   └── AnalyseController.java
│       │   ├── service/
│       │   │   ├── JobApplicationService.java
│       │   │   └── EmailCampaignService.java
│       │   ├── entity/
│       │   │   ├── JobApplication.java
│       │   │   ├── EmailCampaign.java
│       │   │   └── CampaignRecipient.java
│       │   ├── repository/
│       │   ├── dto/
│       │   └── enums/
│       └── resources/
│           ├── application.properties
│           └── templates/
│               ├── analyse.html
│               ├── jobs/
│               │   ├── dashboard.html
│               │   ├── add.html
│               │   └── detail.html
│               └── campaigns/
│                   ├── list.html
│                   ├── create.html
│                   └── detail.html
│
└── ai-agent-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        └── java/com/jobtracker/aiservice/
            ├── controller/AiAgentController.java
            ├── service/GeminiAgentService.java
            └── dto/
```

---

## ⚙️ Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 15+ running locally
- [Groq API key](https://console.groq.com) — free, no credit card needed
- Gmail App Password — for cold mail sending

---

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_USERNAME/ai-job-tracker.git
cd ai-job-tracker
```

---

### 2. Create PostgreSQL database

```sql
CREATE DATABASE jobtracker;
```

---

### 3. Configure job-service

Open `job-service/src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/jobtracker
spring.datasource.username=postgres
spring.datasource.password=your-db-password

# AI service
ai.service.url=http://localhost:8082

# Gmail SMTP
spring.mail.username=your-gmail@gmail.com
spring.mail.password=your-16-digit-app-password

# App base URL (for email open tracking)
app.base.url=http://localhost:8081
```

> **Gmail App Password**: Google Account → Security → 2-Step Verification → App Passwords → Generate

---

### 4. Configure ai-agent-service

Open `ai-agent-service/src/main/resources/application.properties`:

```properties
groq.api.key=your-groq-api-key-here
groq.api.url=https://api.groq.com/openai/v1/chat/completions
```

> **Groq API key**: Sign up at [console.groq.com](https://console.groq.com) → API Keys → Create

---

### 5. Run both services

**Terminal 1:**
```bash
cd ai-agent-service
mvn spring-boot:run
```

**Terminal 2:**
```bash
cd job-service
mvn spring-boot:run
```

---

### 6. Open the app

```
http://localhost:8081/jobs
```

---

## 🐳 Run with Docker Compose

```bash
cp .env.example .env
# Add your GROQ_API_KEY in .env

docker-compose up --build
```

---

## 🔗 API Reference

### Job Service — `http://localhost:8081`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/jobs` | Dashboard |
| GET | `/jobs/new` | Add application form |
| POST | `/jobs` | Save application + trigger AI |
| GET | `/jobs/{id}` | Application detail |
| POST | `/jobs/{id}/status` | Update status |
| POST | `/jobs/{id}/followup` | Mark follow-up done |
| POST | `/jobs/{id}/delete` | Delete |
| GET | `/analyse` | JD Analyser page |
| POST | `/api/analyse` | AI analyse proxy |
| GET | `/campaigns` | All campaigns |
| GET | `/campaigns/new` | Create campaign form |
| POST | `/campaigns` | Create + send campaign |
| GET | `/campaigns/{id}` | Campaign tracking detail |
| POST | `/campaigns/recipients/{id}/replied` | Mark replied |
| GET | `/track/open/{recipientId}` | Email open tracking pixel |

### AI Agent Service — `http://localhost:8082`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/ai/analyze` | Parse JD + score resume |
| GET | `/api/ai/health` | Health check |

---

## 💡 How Email Open Tracking Works

Each cold mail contains a hidden 1×1 transparent pixel:

```html
<img src="http://localhost:8081/track/open/{recipientId}" width="1" height="1" style="display:none"/>
```

When HR opens the email, their mail client loads the image → server records `openedAt` timestamp → status updates to Opened. Same mechanism used by Mailchimp, HubSpot, and all professional email platforms.

---

## 🧠 AI Agent Design

The `ai-agent-service` follows a perceive → reason → act pattern:

1. **Perceive** — receives JD text and resume text as input
2. **Reason** — builds a structured prompt, calls Groq (Llama 3.3 70B)
3. **Act** — parses JSON response, returns extracted skills + fit score + reasoning
4. **Retry** — auto-retries up to 3 times on rate limiting with exponential backoff (5s → 10s → 15s)

---

## 🔒 Security Notes

- Never commit `application.properties` with real credentials to GitHub
- `.gitignore` already excludes `.env`
- Gmail App Password ≠ your Gmail login password

---



## 👨‍💻 Author

**[Aditya Singh]** — Java Developer | Spring Boot | Microservices

- GitHub: [@AdityaDeveloper-pixel](https://github.com/AdityaDeveloper-pixel)
- LinkedIn: [linkedin.com/in/aditya-singh-234739161](https://www.linkedin.com/in/aditya-singh-234739161)

---

## 📄 License

MIT — free to use, modify, and share.
