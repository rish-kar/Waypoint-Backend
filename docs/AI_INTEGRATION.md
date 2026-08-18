# Waypoint self-hosted AI integration

The backend exposes an authenticated AI routing API for the Chrome extension. The extension never receives the model server URL or API key and cannot choose an arbitrary upstream server.

## Architecture

```text
Waypoint extension
    -> POST /api/v1/ai/intent
Waypoint backend
    -> configured OpenAI-compatible model server
Self-hosted model
    -> strict structured intent JSON
Waypoint backend
    -> validates/fails closed
Waypoint extension
    -> deterministic tab matching + safety checks + Chrome API action
```

The model is an intent router only. It never receives or returns Chrome tab IDs.

## Supported model servers

The integration uses the OpenAI-compatible `POST /v1/chat/completions` contract with `response_format=json_schema`. This allows the same backend integration to target local Ollama or a production vLLM deployment by changing environment variables.

## Local development with Ollama

1. Install Ollama on the machine running the model.
2. Pull a suitable instruction model, for example:

```bash
ollama pull qwen3:4b
```

3. Enable the provider:

```text
AI_SELF_HOSTED_ENABLED=true
AI_SELF_HOSTED_BASE_URL=http://localhost:11434/v1
AI_SELF_HOSTED_MODEL=qwen3:4b
AI_SELF_HOSTED_API_KEY=
AI_SELF_HOSTED_REQUEST_TIMEOUT=30s
```

When the backend itself runs in Docker and Ollama runs on the host, use:

```text
AI_SELF_HOSTED_BASE_URL=http://host.docker.internal:11434/v1
```

## Production with vLLM

Run an OpenAI-compatible vLLM server on private infrastructure and point `AI_SELF_HOSTED_BASE_URL` to its `/v1` endpoint. Keep that server private; only Waypoint Backend should be able to reach it. If the model server requires authentication, set `AI_SELF_HOSTED_API_KEY`.

## API

All AI endpoints require the normal Waypoint JWT.

### List available backend models

```http
GET /api/v1/ai/models
Authorization: Bearer <waypoint-jwt>
```

### Route a command

```http
POST /api/v1/ai/intent
Authorization: Bearer <waypoint-jwt>
Content-Type: application/json
```

Example request:

```json
{
  "request": "Group my repository tabs",
  "lastSelectionAvailable": false,
  "lastSelectionTarget": "",
  "currentTime": "2026-08-18T18:00:00+05:30",
  "timeZone": "Asia/Kolkata",
  "locale": "en-IN",
  "model": "self-hosted"
}
```

The response uses the Waypoint intent contract:

```json
{
  "kind": "browser-action",
  "action": "group-tabs",
  "scope": "matching-tabs",
  "target": "repository tabs",
  "matchTerms": ["repository", "repositories"],
  "sites": [],
  "explicitCurrent": false,
  "explicitAll": false,
  "groupTitle": "Repositories",
  "workspaceName": "",
  "wakeAt": "",
  "clarification": "",
  "modelId": "self-hosted"
}
```

The backend deliberately does not accept a model URL from clients. `model` is an allow-listed logical model ID, which creates a safe foundation for adding other providers later.
