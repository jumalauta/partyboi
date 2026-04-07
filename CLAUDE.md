# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Partyboi is a demoparty management system built with Kotlin/Ktor. It handles compo (competition) management, entry
submissions, voting, scheduling, and big-screen displays for demoscene parties.

## Build & Run Commands

```bash
# Dev environment (Docker Compose with PostgreSQL)
docker compose -f docker-compose-dev.yml up

# Build fat JAR
./gradlew buildFatJar

# Run all tests (uses H2 in-memory DB, no external services needed)
./gradlew test

# Run a single test class
./gradlew test --tests "party.jml.LoginTest"

# Run a single test method
./gradlew test --tests "party.jml.LoginTest.testFrontPage"
```

The dev server runs on port 8124 (mapped to container port 8123). Default admin credentials: admin/password.

## Architecture

**Server-side rendered Kotlin/Ktor app** — no frontend framework. HTML is generated using kotlinx.html DSL. Pages are
rendered as `Renderable` objects (see `templates/Renderable.kt`).

### Service layer (`AppServices`)

Central dependency container in `AppServices.kt`. All services receive `AppServices` (not individual dependencies).
Services extend `Service(app)` base class which provides logging and persistent properties.

### Module structure (under `party.jml.partyboi`)

Each feature module typically has:

- `*Repository.kt` — database access using KotliQuery
- `*Page.kt` — HTML rendering with kotlinx.html DSL
- `*Routing.kt` — Ktor route handlers (registered in `plugins/Routing.kt`)
- `admin/` subdirectory — admin-only pages and routes

### Error handling

Uses Arrow's `Either<AppError, T>` throughout (type-aliased as `AppResult<T>`). Errors are typed (`NotFound`,
`Unauthorized`, `ValidationError`, etc.) in `data/AppError.kt`.

### Database

- PostgreSQL in production, H2 for tests
- Flyway migrations in `src/main/resources/db/migrations/`
- KotliQuery for queries (no ORM)
- Connection pool via HikariCP (`db/Database.kt`)
- `DatabasePool` provides Arrow-wrapped query helpers: `session.one()`, `session.many()`, `session.option()`,
  `session.exec()`, `session.updateOne()` — always use these instead of raw KotliQuery `session.run()`. They return
  `AppResult<T>` and `db.transaction {}` auto-rollbacks on `Left`.

### Testing

Tests implement `PartyboiTester` interface which provides:

- `test {}` — sets up Ktor test server with H2 database using `tests.yaml` config
- `setupServices {}` — resets database state before each test
- `TestHtmlClient` — HTTP client with cookie support, HTML assertions via skrape{it}
- Tests are end-to-end: they hit HTTP endpoints and assert on rendered HTML

### Config

YAML-based (`tests.yaml` for tests). Environment variables override config values (pattern: `$ENV_VAR:default`).

### Background services

Started in `Application.kt`: triggers, vote processing, and work queue.

### FFmpeg/Docker integration

FFmpeg runs inside Docker containers for media processing. Files are shared via `DockerFileShare`.