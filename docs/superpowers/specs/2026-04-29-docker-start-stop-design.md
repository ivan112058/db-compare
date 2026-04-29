# Docker Container Start/Stop and Status Monitoring

## Overview

Implement Docker container start/stop buttons and status monitoring for the env configuration page using Datastar's reactive approach.

## Data Flow (Datastar's Tao)

**Core principle**: Backend is the source of truth for container status.

```
Button Click → @post(form data) → Backend parses form → docker-compose → check status → SSE patch signals → UI updates
```

- No optimistic updates — button waits for backend confirmation
- Signals `$target.running` / `$source.running` control button text/color
- Backend patches signals via SSE after docker-compose completes

## Frontend Changes (env.html)

### Target Button (line 66-70)

Already has `data-on:click`. Add loading indicator:
- `data-indicator:target.loading` — signal true while request in flight
- `data-attr-disabled="$target.loading"` — prevent double-clicks

### Source Button (line 133-136)

Currently missing `data-on:click`. Add:
```html
data-on:click="$source.running ? @post('/api/docker/stop?type=source', {contentType: 'form'}) : @post('/api/docker/start?type=source', {contentType: 'form'})"
```

Also add loading indicator:
- `data-indicator:source.loading`
- `data-attr-disabled="$source.loading"`

## Backend Changes (envRoutes.kt)

### Routes to Implement

**POST /api/env/docker/start?type=target|source**
1. Parse form data via `call.receiveParameters()`
2. Call `toSaveEnvRequest()` to get EnvConfig
3. Extract relevant side from query parameter (`envConfig.target` or `envConfig.source`)
4. Call `toDockerParams()` to convert to DockerParams
5. If gitref set, call `prepareIsolatedEnvironment(params)`
6. Call `runDockerCompose(params, "up", "-d", "--force-recreate", "--remove-orphans")`
7. Check actual running status via `checkContainerStatus()`
8. Patch signals: `{"target": {"running": true/false}}`
9. Toast success/error

**POST /api/env/docker/stop?type=target|source**
Same flow as start, but:
- Call `runDockerCompose(params, "down")`
- Patch signals: `{"target": {"running": false}}`

### Fix toDockerParams()

Uncomment and update field names:
```kotlin
private fun EnvDbInfo.toDockerParams(): DockerParams =
    DockerParams(
        codePath = codePath,
        composePath = composePath,
        prefix = prefix,           // was containerPrefix
        serviceName = service,     // was serviceName
        port = port,
        excludeInitSql = excludeInitSql,
        gitRef = gitref            // was gitRef
    )
```

### Remove DockerCommandPayload

No longer needed — form data replaces JSON payload.

### Add Docker-Specific Validation

Create `toDockerEnvRequest()` that validates only docker-related fields:
- composePath (required)
- prefix (required)
- service (required)
- port (required)

Skip database credential validation (username, password, database) since they're not needed for docker-compose operations.

## Status Monitoring

### On Config Load

When user selects an env config (POST /api/env), after `fillEnvForm()`:
1. Call `checkContainerStatus()` for both target and source
2. Patch signals: `{"target": {"running": true/false}, "source": {"running": true/false}}`

### On Page Refresh

Status resets to default (false). Re-checks on each config load.

## Files to Modify

1. `dbc/src/main/resources/static/env.html` — Add Source button click handler, loading indicators
2. `dbc/src/main/kotlin/com/zxqj/dbcompare/routes/envRoutes.kt` — Uncomment/fix docker routes, add form parsing

## Existing Code to Reuse

- `toSaveEnvRequest()` — form parsing (or create lighter `toDockerEnvRequest()`)
- `runDockerCompose()` — docker-compose execution
- `checkContainerStatus()` — container status check
- `prepareIsolatedEnvironment()` — git-based compose file extraction
- `resolveRunning()` — convenience wrapper
- `otToast()` — success/error notifications

## Existing Code to Remove

- `DockerCommandPayload` data class — replaced by form data parsing

## Testing

1. Fill in Target docker fields (composePath, codePath, prefix, service, port)
2. Click Target Start button
3. Verify button shows loading state, then changes to "Stop" with danger color
4. Click Target Stop button
5. Verify button changes back to "Start" with success color
6. Repeat for Source
7. Select a saved config — verify status buttons reflect actual container state
