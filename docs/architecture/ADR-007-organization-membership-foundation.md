# ADR-007 — Organization and membership authorization foundation

Status: accepted for Phase 7.3a

Date: 2026-09-05

## Context

The B2B journey needs organization ownership and organization-scoped authorization before invitations, paid seats, course assignments, or reporting can be implemented safely. Platform roles such as `STUDENT`, `INSTRUCTOR`, and `ADMIN` do not represent a user's role inside a specific customer organization. Reusing them would mix identity authentication with tenant authorization and make tenant isolation difficult to reason about.

Invitations and seats also carry unresolved business rules: whether membership requires acceptance, who pays, when a seat is consumed, whether one learner can use multiple seats, and how suspension or refunds affect access. Creating those tables or APIs now would encode guesses and leave incomplete flows.

## Decision

Create a top-level `organization` bounded context for Slice 7.3a.

- `organization` depends only on `identity::user-lookup`, `platform::security`, and `sharedkernel::error`.
- Platform identity proves who the caller is and whether a user id exists. Organization owns tenant membership and roles; it never imports identity domain, JPA entities, or repositories.
- Organization roles are `OWNER`, `ADMIN`, and `MEMBER`. They are scoped by `(organization_id, user_id)` and are not added to JWT platform roles.
- Creating an organization creates its initial `OWNER` membership in the same transaction.
- A caller-generated UUID `Idempotency-Key` is unique per creator. A retry returns the original organization without creating another membership.
- Organization slugs are globally unique, lowercase, URL-safe business identifiers. A slug conflict returns a stable conflict error.
- Authenticated users may list only their own bounded memberships.
- Only `OWNER` and `ADMIN` may list an organization's bounded membership roster. A caller without membership receives not-found to avoid confirming tenant existence; a `MEMBER` receives forbidden.
- Responses contain stable user ids and organization-owned fields only. Identity profile data is not copied or joined.
- Persistence uses organization-owned JPA entities without cross-module JPA relationships or a foreign key to `users`.

No invitation, membership mutation, ownership transfer, seat, subscription, course assignment, reporting endpoint, or frontend is introduced in Slice 7.3a.

## API contract

```http
POST /api/v1/me/organizations
Authorization: Bearer <access token>
Idempotency-Key: <UUID>
Content-Type: application/json

{"name":"Acme Academy","slug":"acme-academy"}
```

The winning request returns `201 Created`; an idempotent replay returns `200 OK` with the same organization and owner membership snapshot.

```http
GET /api/v1/me/organizations?limit=20
GET /api/v1/organizations/{organizationId}/members?limit=50
Authorization: Bearer <access token>
```

Both collections use deterministic ordering and hard maximums of 100 rows.

## Role matrix for future mutation work

| Capability | OWNER | ADMIN | MEMBER |
| --- | --- | --- | --- |
| View own organization summary | yes | yes | yes |
| View membership roster | yes | yes | no |
| Invite/remove members | future | future | no |
| Change member/admin role | future | future with restrictions | no |
| Transfer ownership | future | no | no |
| Configure billing/seats | future | future if delegated | no |

Future membership mutations must emit audit records containing organization id, actor id, affected user or invitation id, action, timestamp, and correlation/idempotency reference. Sensitive tokens and invitation email addresses must not be placed in general logs or domain events.

## Persistence invariants

- Organization UUID primary key and unique normalized slug.
- Unique `(created_by, idempotency_key)` for retry-safe creation.
- Membership UUID primary key and unique `(organization_id, user_id)`.
- Membership role database check restricted to `OWNER`, `ADMIN`, and `MEMBER`.
- Internal membership-to-organization foreign key with cascade delete; no foreign key or ORM relationship to the identity module.
- Indexes for bounded membership history and deterministic roster reads.

## Consequences

Slice 7.3b can introduce invitations, seat capacity, assignments, and audit persistence against an explicit tenant boundary. A future service extraction replaces `UserLookup` with RPC or event-backed validation while organization-owned rows and authorization rules remain unchanged.
