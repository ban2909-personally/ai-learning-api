# ADR-004 — Idempotent notification projection and authenticated realtime delivery

Status: Accepted

Date: 2026-09-04

## Context

Slice 6.1 publishes `lesson.completed` at least once. Slice 6.2 must turn that event into a user-visible notification without showing duplicates, losing notifications during disconnected periods, leaking bearer tokens, or allowing one user to subscribe to another user's messages.

WebSocket delivery alone is not durable. Browser WebSocket handshakes also cannot carry an arbitrary authorization header, while placing an access token in the URL can expose it through logs and browser/network tooling. The application is stateless and authenticates HTTP requests with short-lived JWT access tokens.

The notification capability is independently cohesive and a likely future microservice. It must therefore remain outside learning persistence and consume only the published learning event contract.

## Decision

Create a top-level `notification` Spring Modulith bounded context. It may depend on `learning::events`, `platform::security`, and `sharedkernel::error`; its domain and application layers do not depend on those frameworks or platform packages.

### Durable projection

1. A Kafka inbound adapter consumes `LessonCompletedEventV1` as a string record.
2. The adapter rejects records whose topic, key, type, schema-version, event-id header, or JSON payload disagree.
3. An application use case creates a generic notification whose identifier equals the source `eventId`.
4. A PostgreSQL outbound adapter uses that primary key as the idempotency boundary. Duplicate Kafka delivery returns the existing outcome without producing another realtime message.
5. The Kafka listener runs in a database transaction. It returns successfully only after the notification projection commits, so the container does not acknowledge the record before durable storage.
6. Invalid or transiently failing records retry with a bounded delay. Dead-letter administration and controlled replay remain slice 6.3 work.

The projection does not copy course titles, lesson titles, email addresses, or other mutable/personal content into the integration event. The first notification uses a stable generic Vietnamese title/body and links to `/my-learning`.

### HTTP history and catch-up

- `GET /api/v1/me/notifications` returns only the JWT subject's notifications.
- Pagination uses a bounded `limit` and opaque notification-id cursor backed by the `(user_id, created_at, id)` index; no unbounded list or offset scan is introduced.
- `PATCH /api/v1/me/notifications/{notificationId}/read` can update only a row owned by the JWT subject.
- Responses are REST DTOs in the web adapter; application services expose transport-neutral contracts.
- The frontend loads HTTP history initially and after every STOMP reconnect. Realtime delivery is an optimization, not the source of truth.

### STOMP/WebSocket authentication and authorization

- Endpoint: `/ws/notifications` using native WebSocket and STOMP; SockJS is not required.
- The endpoint accepts only the configured exact frontend origin.
- The HTTP upgrade endpoint is public because browsers cannot set the bearer header there.
- The frontend sends `Authorization: Bearer <access-token>` only in the STOMP `CONNECT` frame, never in the URL.
- A highest-precedence inbound interceptor decodes the JWT with the existing issuer/signature/expiry validator and sets the authenticated user on the STOMP session.
- Spring Security messaging authorization permits authenticated lifecycle frames and subscription only to `/user/queue/notifications`.
- All client `SEND` frames, broker destinations, arbitrary subscriptions, and unmatched messages are denied by default.
- A server-side session registry closes the underlying WebSocket at JWT expiry. Reconnect obtains a fresh access token through the existing refresh-cookie flow.
- Inbound STOMP and WebSocket transport sizes, send time, first-message time, and heartbeat intervals are bounded.

CSRF on STOMP `CONNECT` is not enabled because this application does not authenticate the socket from ambient cookies. Cross-site protection is instead enforced by the exact Origin allowlist, while authentication requires a bearer token in the STOMP frame. The refresh token remains an HttpOnly cookie and is never copied into WebSocket data.

### User-specific delivery

After the projection transaction commits, an outbound adapter uses Spring's user destination support to publish to `/user/queue/notifications` for the JWT subject name (the user UUID). Multiple active sessions for that user may receive the same event; browser state deduplicates by notification id.

If realtime delivery fails after commit, the failure is measured but does not roll back the durable projection or force Kafka redelivery. The next HTTP catch-up retrieves the notification.

## Consequences

Benefits:

- Kafka redelivery cannot create duplicate notification rows or duplicate application-triggered pushes.
- Offline and reconnecting clients do not lose notification history.
- The browser never places JWTs in URLs or local storage.
- Message authorization is explicit and deny-by-default.
- Notification persistence and delivery can move behind a Kafka boundary into a separate service later.

Trade-offs:

- A local simple STOMP broker targets sessions attached to the current application instance. Multi-instance unresolved-user routing requires broker relay or a dedicated notification gateway before horizontal realtime fan-out.
- There can be a short delay between Kafka publication, projection, and WebSocket delivery.
- A permanently malformed record blocks its partition until slice 6.3 introduces a reviewed dead-letter/replay procedure.
- The generic first message sacrifices title enrichment to keep the producer contract small and stable.

## References

- Spring Framework token authentication: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html
- Spring Security WebSocket authorization: https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html
- Spring Framework user destinations: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html
- Spring Framework channel interception: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/interceptors.html
- StompJS reconnect and fresh-token guidance: https://stomp-js.github.io/faqs/faqs.html
