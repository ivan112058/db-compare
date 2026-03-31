---
title: "refactor: Replace Spring Boot with Ktor, rewrite frontend with Datastar + Oat"
type: refactor
status: completed
date: 2026-04-01
---

# Refactor: Ktor + Datastar + Oat Migration

## Overview

Complete backend and frontend rewrite: replace Spring Boot with Ktor, clean up all Kotlin code to follow conventions, replace Vue/PrimeVue/Tailwind frontend with a single static `index.html` using Datastar (hypermedia framework with SSE) and Oat (ultra-lightweight CSS/JS UI library). No npm build step.

## Problem Frame

The current app uses Spring Boot + Vue + Vite + PrimeVue + Tailwind — a heavyweight stack for what is essentially a server-rendered DB comparison tool. The goal is minimalism: lighter backend (Ktor), zero-build frontend (static HTML with Datastar + Oat), less code overall.

## Requirements Trace

- R1. Replace Spring Boot with Ktor (Netty engine)
- R2. All Kotlin code follows official Kotlin coding conventions
- R3. Frontend uses Datastar (data-star.dev) for reactivity and SSE
- R4. Frontend uses Oat (oat.ink) for CSS/UI components
- R5. Single static `index.html` served from backend resources — no npm build
- R6. UI does not need to match the previous UI — simpler is better
- R7. Preserve all existing API functionality

## Scope Boundaries

- Database service logic (JDBC, MySQL) stays essentially the same
- Config/YAML file management stays the same
- Docker management (EnvController) stays the same
- No new features — this is a pure refactoring
- The frontend can be simpler than before (user explicitly said UI doesn't need to be the same)

## Context & Research

### Relevant Code and Patterns

- **Backend services (keep logic, change DI):**
  - `backend/src/main/kotlin/com/zxqj/dbcompare/service/CompareService.kt` — core comparison logic
  - `backend/src/main/kotlin/com/zxqj/dbcompare/service/DatabaseService.kt` — JDBC operations
  - `backend/src/main/kotlin/com/zxqj/dbcompare/service/ResultCacheService.kt` — in-memory cache
  - `backend/src/main/kotlin/com/zxqj/dbcompare/service/SqlGenerationService.kt` — SQL script generation
- **Controllers (rewrite as Ktor routes):**
  - `backend/src/main/kotlin/com/zxqj/dbcompare/controller/CompareController.kt`
  - `backend/src/main/kotlin/com/zxqj/dbcompare/controller/ConfigController.kt`
  - `backend/src/main/kotlin/com/zxqj/dbcompare/controller/EnvController.kt`
  - `backend/src/main/kotlin/com/zxqj/dbcompare/controller/FsController.kt`
  - `backend/src/main/kotlin/com/zxqj/dbcompare/controller/GitController.kt`
- **Models (simplify):**
  - `backend/src/main/kotlin/com/zxqj/dbcompare/model/` — all data classes
  - `backend/src/main/kotlin/com/zxqj/dbcompare/model/structure/` — structure comparison types

### External References

- **Datastar Kotlin SDK:** `dev.data-star.kotlin:kotlin-sdk:1.0.0-RC3` — framework-agnostic, provides `ServerSentEventGenerator` and `readSignals`
- **Datastar HTML attributes:** `data-on:click`, `data-bind`, `data-attr:*`, `@get()`, `@post()` for reactive hypermedia
- **Oat CSS/JS:** CDN links — `https://unpkg.com/@knadh/oat/oat.min.css` and `oat.min.js` — semantic HTML styling, ~8KB total
- **Ktor:** Netty engine, content negotiation (Jackson), static resources, SSE support

## Key Technical Decisions

- **Ktor + Netty:** Lightweight, idiomatic Kotlin, no annotation scanning or reflection-heavy DI
- **Manual dependency wiring:** Services are simple — instantiate them directly in `Application.module()`, no DI framework needed
- **Datastar for frontend reactivity:** Server sends HTML fragments via SSE. Frontend uses `data-*` attributes for bindings and events. No client-side JS framework.
- **Oat for UI:** CDN-loaded CSS+JS, semantic HTML, no build step. Components: cards, buttons, tables, inputs, dialogs, tabs, badges.
- **Single `index.html`:** All frontend logic in one HTML file with inline `<script>` for Datastar-enhanced interactions. API calls via `data-on:click="@post('/api/...')"` pattern.
- **Keep Jackson for YAML config:** `jackson-dataformat-yaml` stays for config file read/write
- **Keep JDBC for database:** No change to database access layer

## Open Questions

### Resolved During Planning

- Q: Should we use a DI framework (Koin, Kodein)?
  A: No. Services are simple enough to wire manually in the Ktor application module.
- Q: Should the frontend be multiple HTML pages or one SPA-like page?
  A: One `index.html` with Datastar-driven view switching. The app has 3 views (config, results, env management) — Datastar can swap content via SSE.
- Q: Should we keep the `frontend/` directory at all?
  A: No. Delete it entirely. The static HTML lives in `backend/src/main/resources/static/index.html`.

### Deferred to Implementation

- Exact Datastar attribute patterns for complex UI (file selector, SSE status streaming) — will be discovered during implementation
- Whether to keep the Docker management (EnvController) feature in this refactor or defer it — it's complex and may slow down the refactor

## Implementation Units

- [ ] **Unit 1: Set up Ktor project structure**

**Goal:** Replace Spring Boot build configuration with Ktor, establish the application entry point.

**Requirements:** R1

**Dependencies:** None

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/settings.gradle.kts`
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/DbCompareApplication.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/Application.kt`

**Approach:**
- Replace Spring Boot plugins with Ktor plugin + shadowJar or application plugin
- Dependencies: `ktor-server-netty`, `ktor-server-content-negotiation`, `ktor-serialization-jackson`, `ktor-server-static-resources`, `ktor-server-sse`
- Keep: `mysql-connector-j`, `jackson-dataformat-yaml`, `commons-text`, `guava`
- Remove: all `spring-boot-*`, `spring-boot-starter-*`, `kotlin-reflect`, `kotlin-stdlib`, `spring-boot-plugin`, `kotlin-spring`
- Application entry: `fun main() { embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true) }`
- Install plugins: ContentNegotiation (Jackson), routing, SSE, static resources

**Test scenarios:**
- Build succeeds with `./gradlew build`
- Application starts on port 8080 and responds to `GET /`

**Verification:**
- `./gradlew build` succeeds
- `./gradlew run` starts server, curl to localhost:8080 returns response

---

- [ ] **Unit 2: Migrate API routes — Compare & Config**

**Goal:** Convert CompareController and ConfigController to Ktor routing.

**Requirements:** R1, R2, R7

**Dependencies:** Unit 1

**Files:**
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/controller/CompareController.kt`
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/controller/ConfigController.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/routes/compareRoutes.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/routes/configRoutes.kt`

**Approach:**
- `Route.compareRoutes(compareService, dbService, resultCacheService, sqlGenerationService)`
- `Route.configRoutes()`
- Use `call.receive<DbConfig>()` instead of `@RequestBody`
- Use `call.parameters["id"]` instead of `@PathVariable`
- Use `call.request.queryParameters["filename"]` instead of `@RequestParam`
- For SQL download: `call.respondOutputStream(ContentType.Application.Sql)` with `Content-Disposition` header
- All endpoints keep same path structure: `/api/connect/check`, `/api/compare`, `/api/compare/{id}/tables`, etc.

**Test scenarios:**
- POST `/api/connect/check` with valid DbConfig returns `{success: true}`
- POST `/api/connect/check` with invalid host returns `{error: "..."}`
- POST `/api/compare` with valid request returns `{success: true, id: "..."}`
- GET `/api/compare/{id}/tables` returns table summaries
- GET `/api/compare/{id}/table/{tableName}` returns diff detail
- GET `/api/compare/{id}/table/{tableName}/sql` returns upgrade/rollback SQL
- GET `/api/config/list` returns YAML file list
- GET `/api/config/load?filename=x.yml` returns parsed config
- POST `/api/config/save?filename=x.yml` saves config

**Verification:**
- All compare API endpoints return correct JSON responses matching previous contract

---

- [ ] **Unit 3: Migrate API routes — Env, Fs, Git**

**Goal:** Convert EnvController, FsController, and GitController to Ktor routing.

**Requirements:** R1, R2, R7

**Dependencies:** Unit 1

**Files:**
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/controller/EnvController.kt`
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/controller/FsController.kt`
- Delete: `backend/src/main/kotlin/com/zxqj/dbcompare/controller/GitController.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/routes/envRoutes.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/routes/fsRoutes.kt`
- Create: `backend/src/main/kotlin/com/zxqj/dbcompare/routes/gitRoutes.kt`

**Approach:**
- EnvController: Docker params data class moves to the route file or a shared models file
- SSE streaming for docker status: use Ktor's `respondSse()` with `SSESession.send()`
- FsController: straightforward GET routes with `File` operations
- GitController: straightforward GET routes with `ProcessBuilder` calls
- Remove `@CrossOrigin` — configure CORS at application level if needed

**Test scenarios:**
- GET `/api/env/list` returns environment config file list
- GET `/api/fs/list?path=/some/path` returns file listing
- GET `/api/git/status?path=/some/repo` returns git status
- POST `/api/env/docker/start` starts docker container
- GET `/api/env/docker/status/stream` streams SSE status events

**Verification:**
- All env/fs/git endpoints respond correctly

---

- [ ] **Unit 4: Clean up models and services — Kotlin conventions**

**Goal:** Refactor all model and service classes to follow Kotlin coding conventions.

**Requirements:** R2

**Dependencies:** Unit 1

**Files:**
- All files in `backend/src/main/kotlin/com/zxqj/dbcompare/model/`
- All files in `backend/src/main/kotlin/com/zxqj/dbcompare/service/`
- Delete: `backend/src/main/resources/application.properties`

**Approach:**
- Remove `@Service` annotations — services are plain classes
- Remove `@Suppress("UNCHECKED_CAST")` where possible by using proper types
- Use `val` over `var` where possible in data classes
- Use `buildList {}` instead of `ArrayList` constructors
- Use `sequence {}` for chains of transformations
- Replace `String.format` with string templates
- Replace `System.err.println` with proper logging (SLF4J simple or just `println` for simplicity)
- Use Kotlin idioms: `?.let {}`, `?:`, `when`, `isNullOrEmpty()`, `orEmpty()`
- Remove redundant `@Throws` annotations
- Data classes: use `val` for immutable fields where the data is read-only after creation
- `TableStructure`: use `LinkedHashMap` with proper Kotlin map syntax
- `StructureDiff`: make fields `val` with constructor params
- Remove explicit types where type inference works
- Use `emptyList()` instead of `emptyList()` calls in default params (already done, verify)
- Clean up `CompareRequest` — many `var` fields with null defaults could be `val` with proper nullability

**Test scenarios:**
- All services behave identically after refactoring
- No Spring annotations remain in the codebase
- Code passes Kotlin conventions check

**Verification:**
- Code is idiomatic Kotlin with no Spring Boot remnants
- All functionality works identically

---

- [ ] **Unit 5: Create static index.html with Datastar + Oat**

**Goal:** Build a single-file frontend that replaces the entire Vue/PrimeVue app.

**Requirements:** R3, R4, R5, R6

**Dependencies:** Units 2, 3

**Files:**
- Create: `backend/src/main/resources/static/index.html`

**Approach:**
- CDN links for Oat CSS/JS and Datastar JS in `<head>`
- Three views managed by Datastar signals: `config`, `result`, `env`
- **Config view:**
  - Form with `data-bind` on source/target DB fields
  - Config file list loaded via `@get('/api/config/list')` on mount
  - Options as comma-separated input fields (simpler than tag inputs)
  - "Compare" button triggers `@post('/api/compare')`, on success switches to result view
- **Result view:**
  - Left panel: table list with struct/data diff badges
  - Right panel: detail view with structure diff tables, data diff tables, SQL scripts
  - Download buttons for upgrade/rollback SQL
- **Env view:**
  - Simplified version — Docker start/stop, status display
  - File/directory input fields (no file browser dialog)
- Use `<template data-if>` for conditional rendering
- Use `data-on:click="@get('/api/...')"` for data fetching
- Use `@patchElements` responses from backend for dynamic content updates
- Inline `<style>` only for layout tweaks beyond what Oat provides

**Technical design:** *(directional guidance, not implementation spec)*

```
<head>
  <link rel="stylesheet" href="https://unpkg.com/@knadh/oat/oat.min.css">
  <script type="module" src="https://cdn.jsdelivr.net/gh/starfederation/datastar@main/packages/library/src/bundles/datastar.js"></script>
  <script src="https://unpkg.com/@knadh/oat/oat.min.js" defer></script>
</head>
<body>
  <nav><!-- view switching via data-on --></nav>
  <main id="app">
    <div data-show="$view === 'config'"><!-- config form --></div>
    <div data-show="$view === 'result'"><!-- result view --></div>
    <div data-show="$view === 'env'"><!-- env management --></div>
  </main>
</body>
```

For complex interactions (table detail loading, SQL generation), backend endpoints return Datastar SSE events (`patchElements`, `patchSignals`) instead of JSON. Alternatively, keep JSON endpoints and use `fetch()` in inline scripts for simplicity.

**Test scenarios:**
- Open http://localhost:8080 — config view loads
- Fill in source/target DB config, click Compare
- Result view shows table list with diff badges
- Click a table — detail view shows structure/data diffs
- Click Upgrade/Rollback download — SQL file downloads
- Env view: start/stop docker containers, see status updates

**Verification:**
- All previous UI functionality is accessible
- No npm build step required
- Page loads with just CDN resources

---

- [ ] **Unit 6: Wire everything together, delete old code**

**Goal:** Complete the application module, delete old files, clean up directory structure.

**Requirements:** R1, R5

**Dependencies:** Units 1-5

**Files:**
- Create/Modify: `backend/src/main/kotlin/com/zxqj/dbcompare/Application.kt`
- Delete: entire `frontend/` directory
- Delete: `backend/src/main/resources/application.properties`

**Approach:**
- In `Application.kt`:
  - Instantiate services: `DatabaseService()`, `CompareService(dbService)`, `ResultCacheService()`, `SqlGenerationService()`
  - Install plugins: ContentNegotiation, routing, SSE, static files (serve `static/` resources)
  - Register all route groups
  - Configure CORS if needed (or remove `@CrossOrigin` equivalent)
- Delete the entire `frontend/` directory (node_modules, package.json, Vue components, Vite config, etc.)
- Delete `application.properties` — Ktor config is in code
- Verify `backend/src/main/resources/static/index.html` is served at `/`

**Test scenarios:**
- `./gradlew build` produces a working fat JAR
- Application serves index.html at root
- All API endpoints work
- No Spring Boot or Vue artifacts remain

**Verification:**
- Clean project structure: only `backend/` directory with Ktor
- Single `index.html` in resources
- Full functionality preserved

---

## System-Wide Impact

- **Build system:** Gradle plugins change entirely (Spring Boot → Ktor)
- **Entry point:** `DbCompareApplication` (Spring) → `Application.kt` (Ktor `embeddedServer`)
- **API contract:** Same REST endpoints, same JSON shapes — frontend and any external consumers unaffected
- **Frontend delivery:** From Vite dev server / built assets → static file from Ktor resources
- **Dependencies:** Dropping Spring Boot, Vue, Vite, PrimeVue, Tailwind, Axios, diff2html. Adding Ktor, Datastar CDN, Oat CDN.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Datastar Kotlin SDK is RC (1.0.0-RC3) — may have breaking changes | Use CDN JS directly for frontend; SDK only needed if backend generates SSE events. Can fall back to raw SSE. |
| Oat is sub-v1 — API may change | Only using CSS classes and semantic HTML — unlikely to break. Can pin version. |
| Docker management (EnvController) is complex | Implement last, can be simplified or deferred if time-consuming |
| SSE streaming for docker status needs Ktor SSE support | Ktor has built-in SSE support via `ktor-server-sse` plugin |
| Losing file browser dialog (FileSelector.vue) | Simplify to text input for paths — acceptable tradeoff for less code |

## Sources & References

- **Datastar:** https://data-star.dev
- **Datastar Kotlin SDK:** https://github.com/starfederation/datastar-kotlin
- **Oat UI:** https://oat.ink
- **Ktor documentation:** https://ktor.io
- **Kotlin coding conventions:** https://kotlinlang.org/docs/coding-conventions.html
