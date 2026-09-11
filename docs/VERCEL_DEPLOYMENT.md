# Vercel Docker Deployment

This document describes how the FoodHub Spring Boot backend is deployed to Vercel as a
Docker container, and how that production setup relates to the existing local development
workflow. It does not replace the backend with Vercel Functions — Vercel runs the same
Spring Boot application, packaged as a container image.

## Architecture

**Local development** (unchanged):

```
Spring Boot (./mvnw spring-boot:run / IDE)
        │
        ▼
Docker Compose (compose.yaml)
        │
        ▼
Local PostgreSQL (postgres:17-alpine container)
```

**Production (Vercel)**:

```
GitHub (dev / release branch)
        │
        ▼
Vercel build
        │
        ▼
Dockerfile.vercel  →  Spring Boot application container
        │
        ▼
Externally managed PostgreSQL (Vercel does not run Postgres)
```

Vercel builds and runs only the application container from `Dockerfile.vercel`. It never runs
`compose.yaml` and never runs PostgreSQL itself — the database must be a managed Postgres
instance (e.g. Neon, Supabase, RDS, Vercel Postgres, etc.) reachable over the network.

## Production Branch

Point the Vercel project at whatever branch your team promotes to production from (typically
`main`, after a PR from `dev` is merged). This deployment change itself was implemented on
`feature/vercel-docker-deployment` and goes into `dev` via pull request, same as any other change.

## Files Added

| File | Purpose |
|---|---|
| `Dockerfile.vercel` | Multi-stage production build (Maven Wrapper build → slim JRE runtime). Used only by Vercel. |
| `.dockerignore` | Keeps `.git`, IDE metadata, `target/`, and `.env*` out of the build context. |
| `docs/VERCEL_DEPLOYMENT.md` | This document. |

`compose.yaml` and local `application.properties` defaults are unchanged in behavior — every
new property added for this task follows the existing `${VAR:default}` pattern already used
by `jwt.secret`, `app.admin.bootstrap.*`, etc., so local dev needs zero new environment
variables.

## Manual Vercel Configuration Required

Vercel's zero-config build does not know about `Dockerfile.vercel` by name. In the Vercel
project dashboard:

1. **Build & Development Settings** → Framework Preset: `Other`.
2. Point the project's Docker build at `Dockerfile.vercel` (not the default `Dockerfile` name)
   via your Vercel plan's container/Docker deployment configuration — the exact control lives
   in the dashboard for your account tier at the time of deployment, since Vercel's generic
   Docker/container support is a newer, plan-gated feature rather than a stable `vercel.json`
   key. If your Vercel plan only auto-detects a file literally named `Dockerfile`, either rename
   `Dockerfile.vercel` to `Dockerfile` on the production branch or configure the custom path in
   the dashboard, whichever your plan supports.
3. **Root Directory**: repository root (where `Dockerfile.vercel` and `pom.xml` live).
4. Set every environment variable in the table below under **Project Settings → Environment
   Variables** (Production environment). None of them belong in `vercel.json` or any committed
   file.

No `vercel.json` was added. `Dockerfile.vercel` plus dashboard configuration is enough for this
repository — there is no routing, redirects, or Vercel-specific build step that would justify
one, and the task explicitly asks to prefer the simpler setup when a Dockerfile is enough.

## Required Environment Variables

| Variable | Required | Secret | Purpose |
|---|---:|---:|---|
| `PORT` | Set by Vercel automatically | No | Runtime listen port; `server.port=${PORT:8080}` binds to it. Do not set manually. |
| `SPRING_DOCKER_COMPOSE_ENABLED` | Yes (`false`) | No | Disables Spring Boot's Docker Compose lifecycle management in the container — there is no `docker` daemon available inside a Vercel container, and no `compose.yaml` service to manage. |
| `SPRING_DATASOURCE_URL` | Yes | No (but points at a private host) | e.g. `jdbc:postgresql://HOST:5432/DATABASE?sslmode=require`. Standard Spring Boot property, auto-bound from this env var. |
| `SPRING_DATASOURCE_USERNAME` | Yes | No | Managed Postgres username. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | **Yes** | Managed Postgres password. |
| `JWT_SECRET` | Yes | **Yes** | Signs/verifies auth JWTs. The compiled-in default is dev-only and must never be used in production. Must be ≥256 bits. |
| `JWT_EXPIRATION` | No | No | Token lifetime in ms. Defaults to `86400000` (24h), same as local dev. |
| `COOKIE_SECURE` | Yes (`true`) | No | Marks the `auth_token` cookie `Secure`. Vercel serves HTTPS, so this should be `true` in production. |
| `COOKIE_SAME_SITE` | Yes if frontend is cross-site | No | `Lax` (default) works only if frontend and backend share a site/subdomain relationship browsers treat as same-site. If the deployed frontend is on a different site, set `None` — browsers require `COOKIE_SECURE=true` whenever this is `None`. |
| `CORS_ALLOWED_ORIGINS` | Yes | No | Comma-separated list of allowed frontend origin(s), e.g. `https://app.foodhub.example`. Defaults to the local-dev origins only; production must override this or the frontend cannot call the API. Never set to `*` — CORS is used with `allowCredentials(true)`. |
| `PASSWORD_RESET_LINK_BASE` | Yes | No | Base URL the reset token is appended to, e.g. `https://app.foodhub.example/reset-password?token=`. Defaults to `http://localhost:5173/...`. |
| `SPRING_MAIL_HOST` | Recommended | No | SMTP host. Standard Spring Boot property, auto-bound — no line needed in `application.properties`. |
| `SPRING_MAIL_PORT` | Recommended | No | SMTP port (e.g. `587`). |
| `SPRING_MAIL_USERNAME` | Recommended | **Yes** (treat as secret alongside password) | SMTP auth username. |
| `SPRING_MAIL_PASSWORD` | Recommended | **Yes** | SMTP auth password / API key. |
| `PLATFORM_SUPPORT_EMAIL` | No | No | Pre-existing; seeds the platform settings row. |
| `PLATFORM_DEFAULT_DELIVERY_FEE` | No | No | Pre-existing. |
| `PLATFORM_DEFAULT_COMMISSION` | No | No | Pre-existing. |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` | No | **Yes** if set | Pre-existing one-time admin seed. Leave both email and password unset unless you specifically want a bootstrap admin created on first boot. |

"Recommended" for mail: if left unset, `LoggingPasswordResetMailer` is used in production too —
password-reset links are written to application logs instead of emailed. That is a functional
fallback, not a crash, but it means reset links only reach whoever can read production logs.

## Local Docker Compose Behavior

Unchanged. `spring.docker.compose.enabled` now reads
`${SPRING_DOCKER_COMPOSE_ENABLED:true}` instead of a hardcoded `true` — with no environment
variable set (the normal local case), it still defaults to `true` and Spring Boot still starts
`compose.yaml`'s `postgres` service automatically. Only an explicit
`SPRING_DOCKER_COMPOSE_ENABLED=false` (set in the Vercel container) changes this.

## PostgreSQL

Production PostgreSQL is never packaged into `Dockerfile.vercel` and never started by
Spring Boot's Docker Compose integration in that container (`SPRING_DOCKER_COMPOSE_ENABLED=false`
prevents that). Provision a managed PostgreSQL instance separately and supply its connection
details via `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`. Use `?sslmode=require` in the JDBC URL for
any managed Postgres provider that mandates TLS.

## Flyway

Unchanged and required. `spring.flyway.enabled=true` runs on every boot, including in
production, applying `src/main/resources/db/migration/V1__baseline_schema.sql` through
`V7__create_password_reset_tokens.sql` in order against whatever database
`SPRING_DATASOURCE_URL` points at. `spring.jpa.hibernate.ddl-auto` stays `validate` — Hibernate
never mutates the schema; only Flyway does. No migrations were added, renamed, or reordered by
this change, and Flyway's baseline configuration was left untouched.

The first production boot against a brand-new database runs every migration from `V1` onward
(no baseline needed — there's nothing to baseline against). If you are instead pointing at a
database that was created some other way, review the existing `baseline-on-migrate` /
`baseline-version=1` comments in `application.properties` before first boot.

## Cookie Configuration

`cookie.secure` and `cookie.same-site` (read by `AuthController` and used when setting/clearing
the `auth_token` cookie) are now `${COOKIE_SECURE:false}` / `${COOKIE_SAME_SITE:Lax}` instead of
hardcoded values. Local dev keeps the old defaults unchanged. Production should set
`COOKIE_SECURE=true` (Vercel is HTTPS-only) and choose `COOKIE_SAME_SITE` based on whether the
deployed frontend shares a site with the API:

- Same site (e.g. both under `*.foodhub.example`): `Lax` is fine, matching current behavior.
- Cross-site (frontend and API on unrelated domains): use `None`, which requires
  `COOKIE_SECURE=true` — browsers reject `SameSite=None` cookies that aren't also `Secure`.

This was made configurable rather than hardcoded to `SameSite=None`, per the actual frontend/backend
domain relationship being unknown at the time of this change — confirm it before setting
`COOKIE_SAME_SITE` in the Vercel dashboard.

## CORS

`common.config.WebConfig` previously hardcoded the allowed origins list (all `localhost`
variants used by local dev tooling). It now reads a comma-separated
`app.cors.allowed-origins` property (`CORS_ALLOWED_ORIGINS` env var), defaulting to the same
local-dev origins so local behavior is unchanged. Production **must** set
`CORS_ALLOWED_ORIGINS` to the real deployed frontend origin(s); it is never widened to `*`
because the CORS config uses `allowCredentials(true)` for the cookie-based auth flow, and
browsers reject `*` combined with credentialed requests.

## Password Reset

`app.password-reset.link-base` now reads `${PASSWORD_RESET_LINK_BASE:http://localhost:5173/reset-password?token=}`
instead of a hardcoded localhost URL. Production must set `PASSWORD_RESET_LINK_BASE` to the
deployed frontend's reset-password route.

## Email

Unchanged in code. `PasswordResetMailerConfig` already conditionally wires a real
`JavaMailSenderPasswordResetMailer` when `spring.mail.host` is present, falling back to the
dev-only `LoggingPasswordResetMailer` otherwise. Because `spring.mail.*` are standard Spring
Boot properties, `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` environment variables are picked up
automatically without any line in `application.properties` — setting them in the Vercel
dashboard is sufficient. Never commit SMTP credentials to any file in this repository.

## Persistent Storage Audit

Searched the codebase (`src/main/java`) for `MultipartFile`, local `File`/`Files` writes,
upload/image/storage handling: **none found**. The backend currently has no file-upload or
local-filesystem-persistence feature, so there is nothing that breaks under Vercel's stateless,
ephemeral container filesystem. If file uploads (e.g. restaurant/menu images) are added later,
they must go to external object storage (S3-compatible bucket, Vercel Blob, etc.) rather than
the local disk — a Vercel container's filesystem is not persisted or shared across instances or
redeploys.

## Background Job Audit

Searched for `@Scheduled`, `TaskScheduler`, `ExecutorService`, message-queue consumers, and
similar always-running processes: **none found**. The application is purely request-driven
(standard Spring MVC controllers), which fits Vercel's container model without modification.
If scheduled/background work is added later, it will need to run outside the request-serving
container (e.g. Vercel Cron hitting a dedicated endpoint, or a separate worker), since a
request-driven container is not guaranteed to stay warm between requests.

## Deployment Steps

1. Merge this PR (or the change it carries) into whatever branch your Vercel project tracks.
2. In the Vercel dashboard, create/configure the project per **Manual Vercel Configuration
   Required** above, pointing it at `Dockerfile.vercel`.
3. Set every variable in **Required Environment Variables** for the Production environment.
4. Provision the managed PostgreSQL instance ahead of the first deploy; Flyway will initialize
   its schema on first boot.
5. Trigger a deploy. Watch the build logs for the Maven build stage, then the runtime logs for
   Flyway migration output and `Started FoodappApplication`.
6. Verify with a request to a public endpoint, e.g. `GET /api/v1/categories` (no auth required),
   which also exercises the database connection end-to-end.

## Rollback Notes

- Vercel keeps previous deployments; rolling back the application container is a normal Vercel
  deployment rollback (no application-specific steps).
- Flyway migrations are forward-only and are **not** automatically reverted by a Vercel
  rollback — rolling back the container to a build with older code does not undo schema changes
  already applied to the shared production database. If a migration must be undone, write and
  apply a new forward migration rather than editing or removing an already-applied one.
- Because there is no local filesystem state and no background jobs, rollback is otherwise
  purely a matter of which container image is serving traffic and which schema version the
  database is at.

## Known Limitations

- No `vercel.json` is committed; the Dockerfile path is a manual, plan-dependent dashboard
  setting (see above) rather than something this repository can pin in version control.
- Vercel's generic Docker/container deployment support is plan-gated and has evolved over time;
  confirm your Vercel plan supports running a persistent Dockerfile-based web server (as opposed
  to short-lived serverless functions) before relying on this setup.
- Mail and admin-bootstrap secrets are optional by design (the app degrades to a logging mailer,
  and skips admin seeding, when unset) — this is intentional, not an oversight.
