# Pending Activities

This document records the remaining work intentionally deferred after merging the `AI-Integration` branch.

The OpenAI GPT-5 nano integration is functionally implemented and suitable for the current local/integration testing stage. Authentication, entitlement enforcement, quota enforcement, and the related security cleanup are intentionally being postponed until the final authentication/integration phase.

## 1. Restore authentication for protected Cloud AI endpoints

### Current state

The following endpoints are currently allowed without authentication for temporary integration testing:

- `POST /api/v1/ai/intent`
- `POST /api/v1/ai/chat`

`SecurityConfig` currently marks these routes as `permitAll()`. In addition, `JwtAuthenticationFilter.shouldNotFilter(...)` skips the `/api/v1/ai/**` namespace, so bearer tokens are not processed for AI routes.

This behavior was introduced deliberately to simplify local testing between the frontend and backend, but it must not remain in the final production configuration.

### Required work

- Remove the temporary `permitAll()` configuration for protected AI operations.
- Allow the JWT authentication filter to process protected AI routes.
- Keep only endpoints that are intentionally public, such as the model catalogue if that remains a product requirement.
- Ensure missing, malformed, expired, and revoked bearer tokens are handled consistently with the rest of the backend.
- Verify authentication behavior from both the Chrome extension and Postman.

### Acceptance criteria

- `/api/v1/ai/intent` requires a valid Waypoint JWT.
- `/api/v1/ai/chat` requires a valid Waypoint JWT.
- Invalid or expired JWTs receive the expected `401` response.
- Authenticated requests expose the correct `UUID` user principal to the controller/service layer.
- No protected AI endpoint can invoke OpenAI anonymously.

---

## 2. Reconnect Cloud AI entitlement enforcement

### Current state

`AiUsageService` already contains the entitlement rules required to determine whether an account may use Cloud AI. It checks the current subscription, premium status, and whether the plan contains the `AI_SUMMARY` feature.

However, the temporary testing path currently allows `/intent` and `/chat` to call the AI service directly, bypassing this entitlement check.

### Required work

Before any provider request is made, the backend must verify that the authenticated account is entitled to Cloud AI.

The controller/service flow should use the authenticated user's ID and call the appropriate `AiUsageService` logic before invoking OpenAI.

The final implementation must prevent:

- free accounts from using Cloud AI when not entitled;
- expired/inactive subscriptions from using premium Cloud AI features;
- anonymous users from reaching the provider;
- client-side code from deciding whether entitlement checks should run.

### Acceptance criteria

- Entitled premium users can call Cloud AI.
- Trial users can call Cloud AI while their trial quota remains available.
- Accounts without the required feature receive `AI_ACCESS_DENIED` / HTTP `403`.
- The frontend cannot bypass entitlement checks by changing request payloads.

---

## 3. Reconnect the 20-request Cloud AI trial quota to actual AI requests

### Current state

The persistent quota implementation already exists:

- `users.ai_trial_requests_used` stores usage.
- Flyway migration `V16__add_ai_trial_usage.sql` creates the field and its non-negative database constraint.
- `AiUsageService.consume(...)` locks the user row using `findByIdForUpdate(...)`.
- Trial requests are capped at `20`.
- The service returns `AI_TRIAL_LIMIT_REACHED` / HTTP `429` after the quota is exhausted.
- Paid premium accounts do not consume the trial counter.

The missing connection is that `/api/v1/ai/intent` and `/api/v1/ai/chat` currently do not call `AiUsageService.consume(...)` before executing the provider request.

### Required work

- Obtain the authenticated user's UUID in both AI execution endpoints.
- Invoke quota/entitlement enforcement for each chargeable Cloud AI request.
- Decide and document precisely what counts as one Cloud AI request.
- Ensure quota consumption cannot be bypassed by directly choosing `/intent` versus `/chat`.
- Preserve concurrency safety so simultaneous requests cannot exceed the 20-request limit.

### Request-counting decision to confirm during final integration

A chat request may internally make more than one OpenAI provider call because page-grounded answering can fall back to general knowledge. The product quota currently represents user-level Cloud AI requests, not raw provider calls.

During final integration, confirm that one user action should consume one quota unit even if the backend performs an internal fallback/retry. If provider-call-level billing is desired instead, update the quota model explicitly rather than allowing implementation details to change usage semantics accidentally.

### Acceptance criteria

- The first 20 eligible trial requests succeed.
- The 21st request is rejected before an OpenAI call is made.
- Concurrent requests cannot push the persisted trial count beyond 20.
- Paid premium users remain unrestricted by the trial counter.
- Usage returned from `/api/v1/ai/usage` stays consistent with actual AI usage.

---

## 4. Fix authentication behavior for `GET /api/v1/ai/usage`

### Current state

`GET /api/v1/ai/usage` expects an authenticated `UUID` through `@AuthenticationPrincipal` and calls `AiUsageService.current(userId)`.

At the same time, the JWT filter currently skips the entire `/api/v1/ai/**` path family. This means the endpoint's controller contract and the current temporary security configuration are inconsistent.

### Required work

- Ensure the JWT filter runs for `/api/v1/ai/usage`.
- Require authentication through Spring Security.
- Confirm the principal is populated correctly before `AiUsageService.current(...)` is called.
- Add explicit security coverage for anonymous and authenticated access.

### Acceptance criteria

- Authenticated users receive their own usage data.
- Anonymous requests receive `401`.
- One user's JWT cannot retrieve another user's usage information.
- A null principal cannot reach `AiUsageService.current(...)` during a normal request.

---

## 5. Align Postman collections and API documentation with the final behavior

### Current state

The AI Postman collection already describes the intended final contract: authenticated `/intent` and `/chat` calls that consume trial quota.

Because authentication and quota enforcement were temporarily bypassed for integration testing, the documentation currently represents the target design rather than the exact runtime behavior of this temporary branch state.

### Required work

After authentication and quota enforcement are restored:

- revalidate every AI Postman request;
- verify JWT variables and authentication headers;
- test trial usage before and after calls;
- add negative cases for unauthenticated access;
- add negative cases for exhausted quota;
- add negative cases for accounts without Cloud AI entitlement;
- make sure request descriptions match the implementation exactly;
- update any backend README/API documentation that still mentions temporary test access.

### Acceptance criteria

The importable Postman collection must correctly demonstrate:

1. model catalogue retrieval;
2. authenticated usage retrieval;
3. authenticated AI intent routing;
4. authenticated page/general chat;
5. entitlement rejection;
6. trial quota consumption;
7. trial quota exhaustion;
8. expected request-ID propagation and structured API errors.

---

## 6. Tighten `AiChatRequest` validation after integration testing

### Current state

`AiChatRequest` is intentionally tolerant to make frontend/backend integration testing easier. It normalizes and truncates incoming values instead of enforcing the stricter validation used elsewhere in the API.

The request currently:

- truncates the question to 500 characters;
- truncates title, description, and page text;
- limits history size;
- ignores any client-supplied model choice and leaves provider/model selection to the backend.

This is useful for integration testing, but final request validation should be reviewed before production.

### Required work

- Add appropriate Jakarta validation annotations to required fields, especially the user question.
- Validate nested history entries using `@Valid` if the final request DTO retains `AiChatMessage` records.
- Define the intended response for blank/empty questions.
- Confirm whether silently truncating oversized payloads is desired or whether invalid sizes should return `400`.
- Keep provider/model selection server-controlled.
- Preserve strict size bounds to control latency, token usage, and abuse potential.

### Acceptance criteria

- Blank AI questions cannot reach OpenAI.
- Invalid history entries are rejected consistently.
- Payload-size behavior is documented and tested.
- Client-supplied model/provider values cannot redirect backend traffic.

---

## 7. Add controller/security/end-to-end tests for the restored authentication flow

### Current state

The branch already has useful unit coverage for:

- `OpenAiClient`;
- structured intent responses;
- page evidence verification;
- provider retry/error handling;
- privacy-safe telemetry;
- intent safeguards;
- trial usage and quota rules.

The major missing layer is regression coverage proving that the HTTP/security/controller path correctly connects authentication, entitlement, quota consumption, and AI execution.

### Required work

Add integration tests covering at least:

- anonymous `/intent` request -> `401`;
- anonymous `/chat` request -> `401`;
- anonymous `/usage` request -> `401`;
- valid JWT -> principal resolved correctly;
- free/non-entitled account -> `403 AI_ACCESS_DENIED`;
- trial account under quota -> request succeeds and count increments;
- trial account at quota -> `429 AI_TRIAL_LIMIT_REACHED` and provider is not called;
- paid premium account -> request succeeds without incrementing trial count;
- concurrent trial requests cannot exceed the quota;
- OpenAI failure does not produce an inconsistent usage state according to the final quota-counting policy.

The tests should exercise the real Spring Security/controller wiring rather than testing only individual service classes.

### Acceptance criteria

A future developer should not be able to reintroduce `permitAll()`, skip the JWT filter, or bypass `AiUsageService.consume(...)` without causing automated tests to fail.

---

## 8. Final production security review for the temporary development allowances

Authentication is intentionally being completed last. When that work begins, perform a targeted pass for all temporary integration-testing allowances introduced around Cloud AI.

Review at minimum:

- `SecurityConfig` AI route rules;
- `JwtAuthenticationFilter.shouldNotFilter(...)`;
- development CORS wildcard/origin behavior;
- comments containing words such as `temporary`, `testing`, `bypass`, or equivalent;
- frontend assumptions that AI calls do not require authentication;
- Postman assumptions;
- entitlement and subscription wiring;
- provider API key exposure boundaries;
- rate limiting for expensive AI routes.

The OpenAI key must remain backend-only and must never be sent to the extension/frontend.

---

# Current merge decision

The AI integration is being merged now with the authentication-related items above intentionally deferred.

The following areas are considered implemented enough for the current stage:

- OpenAI GPT-5 nano backend client;
- backend-controlled model selection;
- AI intent routing;
- structured output validation;
- page-context chat;
- automatic general-knowledge fallback;
- page evidence verification;
- prompt-injection boundary for page content;
- retries, timeouts, and provider error handling;
- privacy-safe operational telemetry;
- OpenAI environment/configuration support;
- persistent trial-usage data model;
- concurrency-safe quota service logic;
- AI service/client unit tests;
- AI Postman test assets.

Authentication, entitlement enforcement at the execution endpoints, quota consumption wiring, final validation, and HTTP-level regression coverage remain pending and must be completed before production release.
