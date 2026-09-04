# Development report — Phase 5 contextual AI mentor

Date: 2026-09-03

API branch: `feature/ai-mentor`

Web branch: `feature/ai-mentor`

## Outcome

Phase 5 adds a lesson-scoped AI mentor without weakening the existing modular-monolith boundaries. An authenticated learner with an active or completed enrollment can review persisted conversation history and receive a streamed answer beside the lesson player. The existing catalog, enrollment, lesson, and media API contracts remain unchanged.

## Architecture and module boundaries

- Added the `mentoring` Spring Modulith bounded context.
- Exposed a narrow, learning-owned `MentoringLessonContextLookup` contract instead of reading learning repositories or JPA entities from mentoring.
- Kept domain objects independent of Spring, JPA, HTTP, Redis, and OpenAI types.
- Kept orchestration in an application service and expressed persistence, quota, AI, and telemetry as outbound ports.
- Kept provider, Redis, Micrometer, JPA, and MVC concerns in their respective adapters.
- Used UUID values across bounded contexts; no cross-module entity association was introduced.
- Recorded the decision and extraction seams in `ADR-002-ai-mentor-bounded-context.md`.

## HTTP and streaming contract

- `GET /api/v1/me/courses/{courseSlug}/lessons/{lessonId}/mentor/messages` returns lesson conversation history.
- `POST /api/v1/me/courses/{courseSlug}/lessons/{lessonId}/mentor/messages` returns `text/event-stream`.
- Named SSE events are `message`, `delta`, `complete`, and `error`.
- The browser stream helper supports CRLF/LF framing and chunks split across network reads, including a single refresh-token retry before the response stream starts.

## Persistence and database behavior

- Flyway V7 creates `mentor_conversations` and `mentor_messages`.
- One conversation is allowed for each learner/lesson pair.
- Foreign keys preserve user, lesson, and conversation integrity.
- Check constraints and indexes bound content and support ordered history reads.
- Provider model and token usage are optional assistant-message metadata for future cost reporting.
- No existing table or migration was modified.

## Security, privacy, and resilience

- Server-side learning authorization verifies ownership plus active/completed enrollment for every history read and question.
- Lesson context, learner identity, provider model, quota, and prompt instructions are never accepted from the browser.
- User questions and lesson context remain untrusted user-role input and are not concatenated into developer instructions.
- Only a bounded recent-history window and minimal lesson metadata are sent upstream.
- OpenAI requests set `store=false`; secrets are read only from `OPENAI_API_KEY`.
- Redis uses an atomic Lua fixed-window quota of 20 accepted turns per learner per hour by default.
- Quota storage fails closed with a safe 503 response; exhausted quota uses a safe 429 response.
- Question length, provider output, executor capacity, request timeouts, SSE lifetime, and local history are bounded.
- Upstream response bodies, prompts, tokens, API keys, and stack traces are not returned to clients.
- Micrometer counters use low-cardinality status tags only.

## Runtime configuration

Required outside tests:

```text
OPENAI_API_KEY=<server-side secret>
```

Optional environment overrides:

```text
OPENAI_MENTOR_MODEL=gpt-5-mini
MENTOR_QUOTA_LIMIT=20
MENTOR_QUOTA_WINDOW=PT1H
MENTOR_SSE_TIMEOUT=PT2M
```

When the provider key is absent, mentor requests fail safely with service unavailable instead of generating a fabricated answer. A live provider call was intentionally not made from the development gate because no production credential is stored in the repository; the request and SSE parser are covered through a local HTTP contract server.

## Frontend experience

- Added a mentor panel beside the existing lesson player.
- Loads persisted history and renders user/assistant turns distinctly.
- Streams answer deltas into an accessible live region and prevents duplicate submission while a turn is active.
- Retains the learner question when streaming fails so it can be retried.
- Shows remaining quota after completion.
- Uses the existing design language and becomes a single-column layout on smaller screens.
- Verified at 320 px, 768 px, and 1440 px browser widths without horizontal overflow.

## Verification evidence

Backend command:

```text
mvn -Dmaven.repo.local=E:\ai-learning-api\.m2\repository clean verify
```

Result:

- 80 tests run; 0 failures; 0 errors; 0 skipped.
- Spring Modulith module verification passed.
- ArchUnit dependency rules passed.
- Testcontainers PostgreSQL, Redis, and MinIO tests passed.
- Flyway V1 through V7 ran against clean PostgreSQL containers.
- JaCoCo analyzed 142 classes and all configured coverage checks passed.

Frontend gates:

```text
npm run lint
npm test -- --run
npm run build
npm run test:e2e
```

Result:

- ESLint passed.
- 11 test files and 14 tests passed.
- TypeScript production build passed.
- Existing authenticated responsive Selenium smoke test passed at 320 px, 768 px, and 1440 px.

## Cohesive commits

API:

- `4ef2aeb docs: define contextual AI mentor architecture`
- `1c54137 feat: expose authorized lesson mentor context`
- `a78bb65 feat: add contextual AI mentor streaming`
- `a2deabf style: normalize AI mentor source formatting`

Web:

- `6abcdc7 feat: add responsive lesson AI mentor`

Merge commits:

- API `c0c08bb merge: deliver contextual AI mentor`
- Web `c73f16a merge: deliver responsive lesson AI mentor`

GitHub Actions verification:

- API feature: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33776711650
- Web feature: https://github.com/ban2909-personally/ai-learning-web/actions/runs/33776713875
- API main: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33777282731
- Web main: https://github.com/ban2909-personally/ai-learning-web/actions/runs/33777284332

All four workflows completed successfully.

## Provider references

Implementation choices were checked against the official OpenAI streaming and data-control documentation:

- https://developers.openai.com/api/docs/guides/streaming-responses
- https://developers.openai.com/api/docs/guides/your-data

## Deferred intentionally

- Transcript/file retrieval and embeddings until authoritative learning content sources exist.
- Code execution and tool calling; this slice is text-only.
- Shared conversations, instructor moderation, billing, and plan-based quotas.
- Kafka analytics and WebSocket notifications, which remain Phase 6 scope.
