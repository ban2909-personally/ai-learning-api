# Phase 5 — Contextual AI Mentor migration plan

## Outcome

An authenticated learner can open the mentor beside a lesson, review that lesson's conversation, ask a bounded question, and receive the answer incrementally. The feature must preserve existing learning APIs and database behavior.

## API contract

```http
GET  /api/v1/me/courses/{courseSlug}/lessons/{lessonId}/mentor/messages
POST /api/v1/me/courses/{courseSlug}/lessons/{lessonId}/mentor/messages
Accept: text/event-stream
Content-Type: application/json

{"question":"Why is dependency inversion useful here?"}
```

The streaming response uses named SSE events:

- `message`: metadata for the accepted user turn.
- `delta`: an answer fragment.
- `complete`: persisted assistant message and remaining quota.
- `error`: a stable platform error code and safe user-facing detail.

History remains ordinary JSON so refresh/reconnect does not replay an upstream stream.

## Data model

- `mentor_conversations`: one conversation for each `(user_id, lesson_id)` pair.
- `mentor_messages`: immutable ordered `USER` and `ASSISTANT` messages.
- Cross-context identifiers are UUID values; Java persistence entities never reference another module's JPA entities.
- Content limits exist in validation, domain construction, prompt-window selection, and database constraints.
- Provider model and token usage are stored only for assistant messages for future cost reporting.

## Security and resilience

- Reuse JWT authentication and enforce active/completed enrollment in the learning module.
- Never accept lesson context, user identity, prompt instructions, model, or quota from the browser.
- Keep the user question as a user-role input; do not concatenate it into developer instructions.
- Send only a bounded recent-history window and minimal lesson metadata.
- Set OpenAI `store=false`; keep the API key in `OPENAI_API_KEY` only.
- Apply an atomic Redis fixed-window quota per user; fail closed with 503 when quota storage is unavailable.
- Bound question length, history size, provider output, connect/read/call timeouts, and SSE lifetime.
- Return safe errors without upstream bodies, keys, stack traces, or raw prompts.
- Record low-cardinality Micrometer counters for accepted, rejected, completed, and failed turns.

## Migration slices and gates

- [x] Reconfirm clean API/web baselines and original roadmap.
- [x] Record the module-boundary decision before implementation.
- [ ] Add the learning-owned mentoring context contract and authorization tests.
- [ ] Add conversation domain model, Flyway V7, JPA adapter, and repository integration tests.
- [ ] Add Redis quota port/adapter with atomic-script integration tests.
- [ ] Add OpenAI Responses streaming adapter with a local HTTP contract test.
- [ ] Add application orchestration tests with mocked outbound ports.
- [ ] Add authenticated MVC/SSE contract tests and module-isolation test.
- [ ] Add responsive lesson mentor UI and component tests.
- [ ] Run Maven `clean verify` with all Testcontainers.
- [ ] Run frontend typecheck, unit tests, production build, and responsive browser smoke test.
- [ ] Review diff for secrets, PII logging, module leaks, accidental API/schema changes, and generated artifacts.
- [ ] Commit by cohesive slice, push the feature branch, wait for green CI, merge to `main`, push, and verify main CI.

## Explicitly deferred

- Retrieval over transcripts, source files, or embeddings until those content sources exist.
- Code execution or tool calling; the first slice is text-only and cannot execute learner code.
- Shared conversations, instructor review, moderation dashboards, billing, and plan-based quotas.
- Kafka analytics and WebSocket notifications, which belong to Phase 6.

