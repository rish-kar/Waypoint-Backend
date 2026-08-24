# Waypoint OpenAI integration

Waypoint Backend uses OpenAI GPT-5 nano for Cloud AI. The Chrome extension never receives the OpenAI API key and never calls OpenAI directly.

## Architecture

```text
Waypoint extension
    -> POST /api/v1/ai/intent or /api/v1/ai/chat
Waypoint backend
    -> POST https://api.openai.com/v1/chat/completions
OpenAI GPT-5 nano
    -> structured intent JSON or chat response
Waypoint backend
    -> validates/fails closed
Waypoint extension
    -> deterministic tab matching + safety checks + Chrome API action
```

The model is never given Chrome tab IDs for browser-action routing.

## Configuration

Set these environment variables on the backend:

```text
AI_OPENAI_ENABLED=true
AI_OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=<your OpenAI API key>
AI_OPENAI_MODEL=gpt-5-nano
AI_OPENAI_REQUEST_TIMEOUT=30s
```

Keep `OPENAI_API_KEY` server-side only. Do not include it in the Chrome extension, source control, logs, or client responses.

The backend uses GPT-5 nano through `POST /v1/chat/completions`, uses `reasoning_effort=minimal` for low latency/cost, and uses Structured Outputs with `response_format=json_schema` for browser-action intent routing.

## Backend model ID

The logical model ID exposed by Waypoint Backend is:

```text
openai-gpt-5-nano
```

The upstream OpenAI model is configured separately through `AI_OPENAI_MODEL` and defaults to `gpt-5-nano`.

## API

### List models

```http
GET /api/v1/ai/models
```

### Route a browser command

```http
POST /api/v1/ai/intent
Content-Type: application/json
```

Example:

```json
{
  "request": "Group my repository tabs",
  "lastSelectionAvailable": false,
  "lastSelectionTarget": "",
  "currentTime": "2026-08-24T12:00:00+05:30",
  "timeZone": "Asia/Kolkata",
  "locale": "en-IN",
  "model": "openai-gpt-5-nano"
}
```

### Ask Page AI

```http
POST /api/v1/ai/chat
Content-Type: application/json
```

The backend sends the page text to OpenAI as untrusted data and verifies quoted page evidence before returning a page-grounded answer. If the page does not contain the answer and the request allows general answers, the backend performs the existing general-knowledge fallback.

## Failure handling

The OpenAI client:

- retries one failed request once;
- enforces the configured request timeout;
- fails closed on malformed structured intent output;
- reports OpenAI rate-limit responses separately;
- refuses to run when the API key is missing.
