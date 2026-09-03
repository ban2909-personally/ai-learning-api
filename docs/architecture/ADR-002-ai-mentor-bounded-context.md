# ADR-002: Isolate AI Mentor as a bounded context

- Status: Accepted
- Date: 2026-09-03
- Decision owners: AI Learning Platform team

## Context

The next product capability is an AI mentor that answers questions in the context of a lesson. It has a different lifecycle and operational profile from course delivery: external-provider latency, streaming responses, usage quotas, prompt governance, conversation retention, and cost controls.

Putting provider calls inside `learning` would couple lesson delivery to OpenAI and make a later service extraction expensive. Putting conversation tables or prompt rules in `sharedkernel` would also leak business rules into the common base.

## Decision

Create `com.ailearning.platform.mentoring` as a Spring Modulith application module and DDD bounded context.

The dependency direction is:

```text
mentoring -> learning::mentoring-context -> catalog::learning-content
```

- `learning` owns enrollment authorization and exposes only a narrow, provider-neutral lesson context contract.
- `mentoring` owns conversations, messages, prompt policy, quotas, and AI-provider orchestration.
- OpenAI is an outbound adapter behind an application port.
- Redis quota state is an outbound adapter and fails closed so an infrastructure outage cannot create unbounded provider spend.
- PostgreSQL is the source of truth for local conversation history.
- The browser receives only the platform SSE contract; the OpenAI API key and upstream event schema remain server-side.

The Responses API request sets `store=false`. Conversation continuity is built from a bounded window of locally persisted messages rather than provider-managed state. This makes retention explicit and reduces provider coupling.

## Consequences

Benefits:

- The AI provider can be replaced without changing domain or HTTP contracts.
- Quota, prompt, and persistence policies can evolve independently from lesson playback.
- The module can later be extracted behind the same learning context contract.
- Provider credentials never reach the frontend.

Trade-offs:

- There is explicit mapping between domain, persistence, OpenAI, and HTTP models.
- Streaming completion is not one database transaction; the user message is durable before the provider call and the assistant message is persisted only after a complete upstream response.
- A conversation may contain a final user message without an assistant reply after a provider failure. The UI treats that as a retryable failure rather than inventing an answer.

## References

- OpenAI streaming responses: https://developers.openai.com/api/docs/guides/streaming-responses
- OpenAI data controls: https://developers.openai.com/api/docs/guides/your-data

