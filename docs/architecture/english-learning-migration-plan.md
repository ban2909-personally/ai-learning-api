# English learning and assessment migration checklist

Product direction: English learning, practice and community. Preserve the modular monolith and existing course, enrollment, flashcard and community behavior. Speaking is explicitly deferred.

## 1. Product and data correction

- [x] Record English product scope and assessment boundaries in ADR-020.
- [x] Correct active learner-facing programming/Korean copy and isolated seed content.
- [x] Preserve accounts, enrollments, discussions and stored media; use a guarded idempotent preview correction, not deletion.
- [ ] Decide how to retire legacy built-in programming categories with a separate, reversible migration.

## 2. Taking and reviewing a practice exam

- [x] Independent assessment module; published discovery for guests and owned attempts for active members.
- [x] Listening/Reading answer validation, save/submit locking, objective grading and post-submit explanations.
- [x] Writing submission, role-restricted human rubric, self-review prevention and one-time atomic review.
- [x] Lazy responsive discovery, exam workspace, result review and reviewer queue.
- [x] Unit, Mockito port, PostgreSQL/Flyway, MockMvc/security, Modulith/ArchUnit and frontend/browser checks.
- [x] Push feature branches, verify exact remote CI revisions, then merge and verify main (foundation and human review; report dated 2026-09-27).

## 3. Next: content authoring and media

- [ ] Define author ownership, reviewer assignment and publication permissions before implementation.
- [ ] Draft/edit/publish exam workflow, section order and question bank for Listening/Reading/Writing.
- [ ] Preserve an immutable exam revision for each started attempt; edits must not change existing results.
- [ ] Managed MinIO audio references with sub-10 MB limits, validated media types and authenticated author permissions.
- [ ] Original or properly licensed English material; no copied PREP questions or branding.

## 4. Exam integrity and feedback

- [ ] Server-enforced time policy, expiry and recovery; browser timer is display-only.
- [ ] Resume/history screens, save-race and submit-race tests, clear network failure/retry UX.
- [ ] Assigned review queues and audited score-correction policy; initial global reviewer queue is not a multi-tenant assignment model.
- [ ] Skill/part progress analytics and actionable review; do not mix Writing rubric into Listening/Reading raw score.
- [ ] Validated, licensed scoring specification before official-looking TOEIC or IELTS conversion. Current sample is not a full-format official mock exam.

## Delivery rule

For each coherent feature: feature branch → implementation/docs → full relevant tests → commit → push branch → exact CI green → merge main → push main → main CI evidence. Do not stash or include unrelated work. Record changed files, security checks, limitations and delivery hashes in a report. This checklist is a roadmap, not a claim that the unfinished platform is complete.
