# Product Requirements Document (PRD)
## DevPulse — Real-Time Developer Productivity & Incident Tracker

**Version:** 1.0
**Owner:** Dev Patel
**Status:** Draft
**Last Updated:** August 2026

---

## 1. Overview

### 1.1 Problem Statement
Small engineering teams typically juggle two disconnected tools: a task tracker (Jira, Trello) for planning work, and a separate status/monitoring tool for visibility into what's happening right now. Neither gives a real-time, unified view of team activity and productivity trends without heavy configuration or paid tooling.

### 1.2 Solution
DevPulse is a lightweight, self-hosted web application that combines a Kanban-style task tracker with a live team activity feed and a productivity analytics dashboard. Any update to a task is broadcast instantly to all connected team members, and the system continuously computes metrics (completion rate, average time-to-close, per-member load) without manual reporting.

### 1.3 Goals
- Give a small team (5–20 engineers) one place to plan, track, and observe work in real time.
- Demonstrate production-grade backend patterns: authentication/authorization, caching, real-time messaging, scheduled jobs, and horizontal-scale-ready design.
- Serve as a portfolio project that maps directly to SDE interview topics (system design, API design, data modeling, scalability trade-offs).

### 1.4 Non-Goals (v1)
- Not a full replacement for Jira/Linear (no custom workflows, no third-party integrations in v1).
- Not multi-tenant SaaS — v1 supports one deployed instance per team, not a hosted product serving many orgs.
- No mobile app — responsive web only.

---

## 2. Target Users & Personas

| Persona | Description | Key Needs |
|---|---|---|
| **Admin** | Team lead / engineering manager | Create team, invite members, view analytics, manage task states |
| **Member** | Individual engineer | Create/update tasks, see live feed, view own workload |
| **Viewer** (stretch) | Stakeholder with read-only access | View dashboard only, no edit rights |

---

## 3. User Stories

### Must-have (MVP)
1. As an Admin, I can create a team and invite members via email/link.
2. As a user, I can sign up and log in securely (JWT-based auth).
3. As a Member, I can create a task with title, description, priority, and assignee.
4. As a Member, I can move a task across states: `To Do → In Progress → In Review → Done`.
5. As any team member, I see task updates from others appear in real time without refreshing.
6. As an Admin, I can view a dashboard showing tasks completed per day/week, average time-to-close, and per-member task load.
7. As a user, I can only see and act on data belonging to my own team (data isolation).

### Should-have (Stretch)
8. As an Admin, I can see cached dashboard data that refreshes quickly even under load.
9. As a system, I automatically compute and store daily summary stats via a scheduled job.
10. As an API consumer, I am rate-limited to prevent abuse.
11. As a user, I receive a notification (in-app) when a task assigned to me changes state.

### Out of scope (v1)
- File attachments on tasks
- Third-party integrations (Slack, GitHub)
- Custom workflow/state configuration

---

## 4. Functional Requirements

### 4.1 Authentication & Authorization
- Email/password signup and login.
- Passwords hashed with BCrypt.
- JWT issued on login, short-lived access token + refresh token.
- Role-based access control: `ADMIN`, `MEMBER` (and `VIEWER` if built).
- All API endpoints (except auth) require a valid JWT scoped to a team.

### 4.2 Team Management
- Admin creates a team (name, description).
- Admin invites members via a shareable invite link or email (email can be simulated/logged in v1, not sent via real SMTP).
- Members can belong to exactly one team in v1 (simplifies data model).

### 4.3 Task Management
- CRUD operations on tasks: title, description, priority (`LOW/MEDIUM/HIGH`), status, assignee, created/updated timestamps.
- Status transitions follow a fixed pipeline: `TODO → IN_PROGRESS → IN_REVIEW → DONE`.
- Tasks are scoped to a team; users can only access their own team's tasks.

### 4.4 Real-Time Activity Feed
- Any task create/update/status-change event is broadcast via WebSocket (STOMP over SockJS) to all clients subscribed to that team's channel.
- Feed shows: actor, action, task title, timestamp — most recent first.

### 4.5 Analytics Dashboard
- Tasks completed per day/week (chart).
- Average time-to-close per priority level.
- Per-member current task load (open tasks assigned).
- Data computed from live queries in MVP; moved to a scheduled pre-aggregation job in the stretch phase.

### 4.6 Caching (Stretch)
- Dashboard read-queries cached in Redis with a short TTL (e.g., 60s).
- Cache invalidated on relevant task-state changes, or left to expire naturally — document the trade-off chosen.

### 4.7 Scheduled Jobs (Stretch)
- A nightly job (Spring `@Scheduled`) computes and stores a daily summary row per team (tasks completed, avg cycle time) for fast historical charting without recomputing from raw data each time.

### 4.8 Rate Limiting (Stretch)
- Per-user request rate limit on write endpoints (e.g., 100 requests/min) using a token-bucket approach (Bucket4j or Redis-based counter).

---

## 5. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Dashboard endpoints respond in <300ms at MVP scale (cached: <50ms) |
| **Scalability** | Stateless backend (JWT, no server session) so it can run behind a load balancer with multiple instances |
| **Security** | Passwords hashed, JWT signed and expiring, team-level data isolation enforced at the query layer, HTTPS in production |
| **Reliability** | WebSocket reconnect handled gracefully on the frontend if connection drops |
| **Availability** | Single-region deployment acceptable for v1; no HA requirement |
| **Observability** | Basic structured logging on the backend; optional: expose a `/actuator/health` endpoint via Spring Boot Actuator |

---

## 6. System Architecture

### 6.1 High-Level Components
- **Frontend:** React SPA (task board, live feed, dashboard charts via Recharts)
- **Backend:** Spring Boot REST API + WebSocket (STOMP) endpoint
- **Database:** PostgreSQL (relational — teams, users, tasks, daily_summary)
- **Cache:** Redis (dashboard query cache, optional rate-limit counters)
- **Auth:** Spring Security + JWT
- **Deployment:** Docker Compose locally; AWS (EC2/Elastic Beanstalk for backend, S3+CloudFront for frontend, RDS for Postgres) in production

### 6.2 Data Flow (Task Update Example)
1. User moves a task card on the frontend → PATCH request to `/api/tasks/{id}/status`.
2. Backend validates JWT + team ownership, updates the row in Postgres.
3. Backend publishes an event to the team's WebSocket topic (`/topic/team/{teamId}`).
4. All subscribed clients receive the event and update their local state instantly.
5. Dashboard cache for that team is invalidated (or left to TTL-expire, per chosen strategy).

---

## 7. Data Model (Simplified)

**User**
`id, name, email, password_hash, role, team_id, created_at`

**Team**
`id, name, description, created_by, created_at`

**Task**
`id, team_id, title, description, priority, status, assignee_id, created_by, created_at, updated_at`

**ActivityLog** (optional, powers the feed history)
`id, team_id, actor_id, task_id, action, timestamp`

**DailySummary** (stretch)
`id, team_id, date, tasks_completed, avg_cycle_time_hours`

---

## 8. API Surface (Representative, not exhaustive)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/signup` | Register a user |
| POST | `/api/auth/login` | Login, returns JWT |
| POST | `/api/teams` | Create a team (admin) |
| POST | `/api/teams/{id}/invite` | Invite a member |
| GET | `/api/tasks?teamId=` | List team tasks |
| POST | `/api/tasks` | Create a task |
| PATCH | `/api/tasks/{id}/status` | Update task status |
| GET | `/api/dashboard/{teamId}/summary` | Get aggregated metrics |
| WS | `/ws/team/{teamId}` | WebSocket subscription for live feed |

---

## 9. Milestones & Timeline (Suggested)

| Phase | Duration | Deliverables |
|---|---|---|
| **Phase 0 — Setup** | 2–3 days | Spring Boot + React scaffolding, Postgres schema, Docker Compose local env |
| **Phase 1 — Auth & Teams** | 3–4 days | Signup/login, JWT, team creation, role-based access |
| **Phase 2 — Task CRUD + Board UI** | 4–5 days | Kanban board, task CRUD, status transitions |
| **Phase 3 — Real-Time Feed** | 3–4 days | WebSocket integration, live activity feed |
| **Phase 4 — Dashboard** | 3–4 days | Metrics queries, charts, basic caching |
| **Phase 5 — Stretch: Caching, Jobs, Rate Limiting** | 4–5 days | Redis caching, scheduled summary job, rate limiter |
| **Phase 6 — Testing & Deployment** | 3–4 days | JUnit/Mockito service tests, Dockerize, deploy to AWS |

**Total estimate:** ~3–4 weeks part-time.

---

## 10. Success Metrics (for the project itself, as a portfolio piece)

- Fully functional deployed instance with a live public URL/demo.
- At least one measurable performance claim (e.g., "cached dashboard reads are Xx faster than uncached").
- Test coverage on core service-layer logic (auth, task state transitions).
- A clear, documented answer to "how would this scale to 100 teams / 10K tasks" for interview use.

---

## 11. Risks & Open Questions

| Risk/Question | Notes |
|---|---|
| WebSocket complexity | STOMP/SockJS has a learning curve — budget extra time in Phase 3 if new to it |
| Scope creep | Stretch features are genuinely optional — ship MVP first, stretch only if time allows |
| Real email delivery | Deciding whether to integrate a real email service (e.g., SES) or simulate invites — recommend simulating for v1 to avoid scope creep |
| Hosting cost | AWS free tier should cover this at demo scale; monitor usage to avoid surprise charges |

---

## 12. Appendix: Why This Project (Interview Framing)

This project was deliberately scoped to produce concrete, defensible answers to common SDE interview questions:
- **"Tell me about a project with real-time requirements"** → WebSocket-based live feed
- **"How did you handle authentication/authorization?"** → JWT + role-based access + team data isolation
- **"How would you scale this?"** → Stateless backend, Redis caching, pre-aggregated summary table, rate limiting
- **"How do you ensure code quality?"** → JUnit/Mockito tests on service layer
- **"Have you deployed something to the cloud?"** → Dockerized deployment on AWS
