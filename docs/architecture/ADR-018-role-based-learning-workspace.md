# ADR-018: Role-based learning workspace and local demo data

Status: accepted for implementation, 2026-09-25.

## Problem

The preview used a fresh database with four seeded categories, zero courses and zero lessons.
Backend infrastructure delivery was ahead of the usable product. The web had no course authoring
entry point or administrative workspace. Screenshots supplied by the product owner define the
visual direction: a compact top navigation, contextual sidebar, real statistics and searchable tables.
The existing programming-learning domain remains; reference branding and personal records are not copied.

## Decisions

- Identity owns accounts and global roles: GUEST, STUDENT, LECTURE, LEADER, ADMIN.
  Existing INSTRUCTOR is retained as a compatible lecturer role.
- Anonymous guests and GUEST accounts read published catalog only. Students enroll, study and
  own flashcard decks. Lecturers author their own courses. Leaders review courses; administrators
  manage accounts and all course content. API authorization is authoritative; web navigation mirrors it.
- Account administration rechecks the actor against current database state. Role/status changes
  revoke refresh sessions. The final active administrator cannot be disabled or demoted.
- Catalog owns draft creation, curriculum editing, submission and review. A course can only be
  published with at least one section and lesson. Ownership checks apply to draft reads and writes.
- Flashcards form a separate bounded context with private owner-scoped decks and front/back cards.
  Review UI supports flipping and moving between cards. No simulated persistence or fake totals.
- Media uploads must be strictly less than 10,000,000 bytes (decimal 10 MB). Backend policy,
  servlet limits and frontend validation enforce the boundary. Existing stored objects remain readable.
- Weak demo passwords are inserted only by an explicit local seed script targeting the named preview
  database. Normal registration/account-creation password policy remains in effect.
- Public contracts remain backward compatible. Additive Flyway migrations, framework-free services,
  output ports and persistence adapters preserve the modular monolith conventions.

## Migration and delivery checklist

- [x] Inspect Git changes, pom.xml, frontend routes and preview database counts.
- [x] Preserve unrelated uncommitted performance/ai-mentor.js change outside feature commits.
- [x] Add roles and account administration with authorization tests.
- [x] Enforce upload limit with below/exact/above-boundary tests.
- [x] Add course authoring, curriculum editing and publication review.
- [x] Add private flashcard decks and study UI.
- [x] Seed five demo accounts and programming-course examples into preview only.
- [x] Build responsive role-aware navigation, administration dashboard, accounts and authoring pages.
- [ ] Implement referenced academic management (terms, subjects, classes), question bank and prompt/settings
  as real use cases before presenting those controls as working.
- [x] Verify unit, REST authorization, PostgreSQL/Flyway, Modulith/ArchUnit and frontend tests/build.
- [x] Verify real browser login for each role and mobile viewport.
- [x] Verify course-creation form and flashcard study interactions in a real browser.
- [x] Record implementation and test evidence in the development report.
- [x] Commit/push feature branch, then merge and push main after final review and green CI.

## Preview inventory

At inspection: ai_learning_preview has 1 user, 4 categories, 0 courses and 0 lessons.
The ai_learning database in the same Docker PostgreSQL instance does not contain a users relation.
No claim is made about other PostgreSQL instances or databases without inspecting them.
