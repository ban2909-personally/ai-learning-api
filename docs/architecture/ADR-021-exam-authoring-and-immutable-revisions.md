# ADR-021: Exam authoring and immutable published revisions

Status: Accepted for implementation (2026-09-28).

## Context and scope

The English assessment foundation supports taking and reviewing a seeded exam. A real learning platform needs staff to create content, approve it and publish revisions without changing what previous candidates answered. This increment delivers the backend and responsive authoring UI together. Managed audio upload follows as a separate explicitly tracked increment; Speaking remains out of scope.

## Decisions

- Keep authoring inside the assessment bounded context. Expose a meaningful authoring use-case boundary and output store port; do not access catalog entities or another module's persistence/storage adapters.
- Model a stable exam series (slug and author) and immutable exam revisions. Existing V16 exams become revision 1 of their own series with the same exam, section, question and attempt IDs. The seeded system exam has no personal author; only admins can edit/clone it.
- Lifecycle: `DRAFT → PENDING_REVIEW → PUBLISHED → ARCHIVED`. An owner can withdraw pending content back to draft. A published revision is never edited in place: cloning creates a new draft and fresh section/question IDs. There is at most one editable revision and one published revision per series.
- Active LECTURE/INSTRUCTOR accounts create and edit their own drafts; ADMIN can manage all. LEADER/ADMIN inspect submitted content and publish. STUDENT/GUEST cannot view private drafts or answer keys. Check roles and ownership in the use case, not only HTTP.
- New authored exams scope Writing review to their author by default; LEADER/ADMIN may review across authors. The legacy system practice sample retains its existing staff review behavior. Self-review remains forbidden. Explicit additional reviewer assignments and audited score corrections are later work, not an implicit global permission for all lecturers.
- Save and transition commands include the expected revision version. Atomic compare-and-set writes return a conflict for stale editors. Publication locks the series, archives only the previous publication state and publishes the approved revision in one transaction.
- Existing attempts retain their revision FK. Attempt/result reads may load PUBLISHED or ARCHIVED revisions; public discovery and new starts use only the current PUBLISHED revision. No historical prompts, keys, answers or Writing reviews are rewritten.
- Domain rules validate the content and publication lifecycle without Spring/JPA/HTTP dependencies. Use domain skill/question/status enums where they remove ambiguous string states. Input commands/contracts are framework-free; bean validation stays in the inbound web adapter.
- Drafts can be incomplete, but publication requires at least one nonempty section, compatible skill/question kinds, prompts, distinct choices with a valid key, objective explanations and valid Writing prompts. Bounds: 20 sections, 250 questions total, 1–240 minutes; enforce string/list limits server-side. No official score conversion is introduced.
- Public exam-list retrieval loads summaries, not every question/key. Keep existing public/take/result JSON contracts; authoring endpoints are separate under `/api/v1/practice/authoring/exams`.
- The editor uses lazy routes, section-based editing, explicit save state/version-conflict feedback and responsive controls. Staff content navigation should group course authoring, exams and Writing review without removing existing access options.

## Migration and API workflow

Flyway V18 introduces series ownership, revision/status/version and publication metadata. Backfill legacy revisions before replacing the old slug/published representation; preserve the stable public slug. Partial unique indexes and FK constraints enforce lifecycle integrity. Do not edit applied V16/V17 migrations.

Authoring use cases: paged list, owned/private workspace, create draft, save draft, submit/withdraw review, publish and clone current publication into a new revision. HTTP returns private keys only to authorized staff. Empty drafts cannot be published. Reject a duplicate draft, stale version, non-owner write or illegal transition with an explicit business error.

Before preview migration, back up the existing preview database and verify the dump inventory. A migration upgrade test must submit an attempt and Writing review on V17, migrate forward, publish a replacement and prove the old result is unchanged. Never reset a shared database or Docker volume.

## Implementation / verification checklist

- [x] V18 migration/backfill and old-result preservation test.
- [x] Framework-free content/lifecycle/authorization policy tests.
- [x] Authoring input contracts, use case, Mockito port tests and configuration.
- [x] Transactional JDBC authoring adapter, optimistic concurrency and publication constraints.
- [x] HTTP roles/ownership/private-key tests; taking and archived-result regression tests.
- [x] Role-scoped Writing queue and atomic review authorization for new series.
- [x] Lazy responsive studio/editor, save/conflict/publish/clone flows and unit tests.
- [x] Full backend verify, frontend build/tests and phone/tablet/desktop browser acceptance.
- [ ] Feature commits → push branch → exact CI green → main merge/push → main CI evidence.

The next increments remain managed MinIO audio (<10 MB), server-enforced timing/resume/history, fuller original/licensed practice banks and analytics. This feature does not redefine those unfinished requirements as complete.
