# DevPulse

## Workspace Navigation and UI

- `/` and `/home` are public landing pages. Signed-in visits redirect to `/dashboard`, which is also the default destination after login.
- The sidebar contains Dashboard and Teams & projects. Boards belong to projects: open a team, then a project. The legacy `/board` address redirects to Dashboard instead of unexpectedly opening Teams.
- Team and project lists have search and expandable creation forms. Project search includes descriptions; active and archived views remain separate.
- Boards have title/description search plus combined assignee and priority filters, an assigned-to-me option, a reset control, and completion counts. Filters only affect display, not saved data.
- Login/signup use the shared brand and password visibility controls. Protected project links and invitation fragments survive authentication.
- Fonts are bundled locally. The public product image is a screenshot of the real UI with explicitly labeled example data, not live user data.

### UI Regression Checks

```sh
npx playwright install chromium
npm run test:ui
```

The Playwright suite starts an isolated Vite server on port 5175 and intercepts all API requests with test fixtures. It does not use the development database. Coverage includes routing, signup/invitation return paths, creation and search, task filtering/writes, member controls, archived read-only boards, retry states, and desktop/tablet/mobile layouts. These checks do not replace backend integration tests.

Screenshots are saved under the ignored `test-results` directory. To regenerate the public example workspace image after a design change:

```sh
CAPTURE_PREVIEW=1 npm run test:ui -- --grep 'public landing'
```

## DevPulse: Projects and Dashboard

Open `/team`, select a team's Projects link, then open a project board. Team owners can edit a project's name/description and archive or restore it from its settings button. Active projects are shown by default; the project-state selector also exposes archived projects.

Archived projects and their task history remain readable. Project edits and task creation, editing, and status changes return HTTP 409 until the owner restores the project. Archive and task writes use the same transactional team lock to serialize concurrent requests. Membership removal still clears that member's assignments, including archived work, while preserving the tasks.

The `/dashboard` tab uses `GET /api/teams/{teamId}/dashboard`, optionally filtered by `projectId`. It checks current team membership and derives personal identity from the authenticated JWT, not a supplied user ID. Responses are not cached and are computed from a consistent database snapshot.

- **My work:** tasks currently assigned to the signed-in user within the selected team/project.
- **Team overview:** tasks for everyone, including unassigned tasks, within the same scope.
- **Open:** To Do, In Progress, or In Review. Completed means current status Done, not completion during a time period.
- **Completion:** Done divided by all tasks in scope. An empty scope has no completion percentage.
- **Active projects only:** archived projects are excluded from all dashboard task counts and queues. Restoring a project includes it again.
- **Project progress and team workload:** always team totals, explicitly labeled even when the metric scope is My work.
- **My open task queue:** at most 20 assigned open tasks, ordered by priority then oldest update. The displayed total still counts all matching tasks. Links open the corresponding board.
- **Work signals:** deterministic counts for high-priority open work, open tasks without updates for more than seven days, unassigned team work, and personal tasks in review. They are not AI-generated conclusions, overdue indicators, or employee performance scores.

### AI Insight Direction

No LLM, external AI calls, vector database, or RAG pipeline is enabled. Structured SQL aggregates are sufficient for the current metrics. A future AI summary should consume authorized metrics and cite its source scope and timestamp. RAG would be useful if project documents, discussions, or other unstructured context are added; retrieval must enforce the same team permissions. API credentials must stay on the backend.

### Run and Verify

Use `.env.example` as a template and add its database entries to the ignored root `.env` without overwriting your existing Supabase values. For an existing database, the three database password values must match the `devpulse`, `devpulse_app`, and `devpulse_migrator` roles. Spring Boot and Docker Compose read this root `.env`; keep real credentials out of source control.

Start the backend and its local PostgreSQL service with:

```sh
./scripts/run-backend.sh
```

The app connects to `localhost:5432/devpulse` as `devpulse_app`; Flyway connects to the same database as `devpulse_migrator`. The migrator owns schema `devpulse` and creates application objects there. The runtime role receives only the schema/table/column grants declared by migrations; it does not need database or schema creation rights.

For a brand-new PostgreSQL volume, initialize the roles and schema once as the Compose admin before starting the backend. Run `docker compose exec postgres psql -U devpulse -d devpulse`, then enter these commands in `psql`; `\password` prompts without echoing credentials:

```sql
CREATE ROLE devpulse_migrator LOGIN;
\password devpulse_migrator
CREATE ROLE devpulse_app LOGIN;
\password devpulse_app
CREATE SCHEMA devpulse AUTHORIZATION devpulse_migrator;
```

Do not reset an existing schema to resolve a Flyway validation error. This database has an applied V8 `pending_signups` migration whose original script is unavailable in the workspace. Flyway's missing-versioned-migration ignore setting is broader than V8, so keep every available migration file in source control and never delete or edit an applied migration. New changes must use a new version.

Run the frontend with `npm run dev` and open the URL Vite reports. Frontend checks: `npm run lint` and `npm run build`. Backend checks: `./backend/mvnw -f backend/pom.xml test` against a disposable PostgreSQL instance configured through the database environment variables. Do not point integration tests at valuable development or production data.

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
