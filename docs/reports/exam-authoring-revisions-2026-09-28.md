# Exam authoring and immutable revisions · 2026-09-28

Status: specification recorded; implementation in progress on `feature/exam-authoring-revisions` in both repositories.

## Intended outcome

Staff create/edit drafts, submit for review, publish and clone new revisions. A candidate's old attempt/result stays bound to its original immutable revision. Roles, ownership and optimistic concurrency are enforced server-side, and the responsive frontend exposes the complete workflow.

## Source decisions and delivery baseline

- ADR-021 defines schema, contracts, lifecycle, publication rules and verification checklist before code changes.
- Based on CI-green backend main `c05c901` and frontend main `f78a48c`. The previous English assessment delivery report records all feature/main CI runs.
- Only pending pre-existing backend change: `performance/ai-mentor.js`; it must remain untouched and unstaged.
- No tests or acceptance for this new feature are claimed yet. Implementation, changed files, migration evidence and exact delivery hashes will be added as each gate completes.
