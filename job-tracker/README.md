# 🎯 AI Job Application Tracker

A microservices-based job application tracker built with **Spring Boot**, **Thymeleaf**, **PostgreSQL**, and **Google Gemini AI**. Tracks every job you apply to — including cold mails to HRs — with AI-powered JD parsing and resume fit scoring.

---

## Features

- **Track all applications** — company, role, status, source, date applied
- **Cold mail tracking** — log HR name, email, mail sent date, follow-up reminders
- **AI JD Parsing** — paste a job description and Gemini extracts all required skills automatically
- **AI Resume Scoring** — paste your resume and get a 0–100 fit score with explanation
- **Follow-up alerts** — dashboard highlights cold mails that need a follow-up today
- **Status tracking** — Applied → Interview → Offer / Rejected
- **Dashboard stats** — total applications, interview count, average fit score

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Java 17 |
| Frontend | Thymeleaf + Bootstrap 5 |
| Database | PostgreSQL 15 |
| AI Agent | Google Gemini 1.5 Flash API |
| HTTP Client | Spring WebFlux (WebClient) |
| Containerization | Docker + Docker Compose |
| Build Tool | Maven |

---

## Architecture

```
Browser
   │
   ▼
job-service (port 8081)          ←──────────────────┐
   │  Thymeleaf UI + REST                            │
   │  Spring Data JPA                                │
   │                                                 │
   ▼                                                 │
PostgreSQL (port 5432)           ai-agent-service (port 8082)
                                    │  Google Gemini API
                                    ▼
                                 Gemini 1.5 Flash
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- Google Gemini API key (free at [aistudio.google.com](https://aistudio.google.com))

### Run with Docker Compose

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/ai-job-tracker.git
cd ai-job-tracker

# 2. Set your Gemini API key
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# 3. Start everything
docker-compose up --build

# 4. Open the app
# Visit http://localhost:8081/jobs
```

### Run locally (without Docker)

```bash
# Start PostgreSQL (make sure it's running on port 5432)
# Create a database named: jobtracker

# Terminal 1 — Start AI Agent Service
cd ai-agent-service
export GEMINI_API_KEY=your-key-here
mvn spring-boot:run

# Terminal 2 — Start Job Service
cd job-service
mvn spring-boot:run

# Visit http://localhost:8081/jobs
```

---

## API Endpoints

### Job Service (port 8081)
| Method | URL | Description |
|---|---|---|
| GET | /jobs | Dashboard — all applications |
| GET | /jobs/new | Add new application form |
| POST | /jobs | Save new application (triggers AI analysis) |
| GET | /jobs/{id} | View application detail |
| POST | /jobs/{id}/status | Update application status |
| POST | /jobs/{id}/followup | Mark cold mail follow-up as done |
| POST | /jobs/{id}/delete | Delete application |

### AI Agent Service (port 8082)
| Method | URL | Description |
|---|---|---|
| POST | /api/ai/analyze | Parse JD + score resume via Gemini |
| GET | /api/ai/health | Health check |

---

## How to Use

1. **Find a job** on LinkedIn, Naukri, or anywhere
2. Click **"+ Add Application"** on the dashboard
3. Fill in company, role, and paste the JD text
4. Optionally paste your resume for a fit score
5. If you applied via **cold mail**, select "COLD_MAIL" as source and add HR details + follow-up date
6. Hit **Save** — AI automatically parses the JD and scores your resume
7. Update status as you progress through interviews

---

## Project Structure

```
ai-job-tracker/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── job-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/jobtracker/jobservice/
│       │   ├── entity/JobApplication.java
│       │   ├── enums/{ApplicationStatus, ApplicationSource}.java
│       │   ├── repository/JobApplicationRepository.java
│       │   ├── service/JobApplicationService.java
│       │   ├── controller/JobApplicationController.java
│       │   └── dto/{JobApplicationDTO, AiResponseDTO}.java
│       └── resources/
│           ├── application.properties
│           └── templates/jobs/{dashboard, add, detail}.html
└── ai-agent-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        └── java/com/jobtracker/aiservice/
            ├── service/GeminiAgentService.java
            ├── controller/AiAgentController.java
            └── dto/{AnalyzeRequestDTO, AnalyzeResponseDTO}.java
```

---

## Author

Built by [Your Name] as a portfolio project during job search preparation.
