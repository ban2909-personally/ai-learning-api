# Development report: lesson media delivery

Date: 2026-09-03

Branch: `feature/lesson-media-delivery`

Repositories: `ai-learning-api`, `ai-learning-web`

## Delivered outcome

This slice completes private lesson-video storage and protected browser playback from the unfinished media part
of Phase 2. Instructors can upload MP4/WebM content for lessons in courses they own, administrators can manage
any course, and enrolled learners can play only lessons they are authorized to access. Existing external lesson
URLs remain compatible.

## Backend changes

- Added a catalog media use case, domain ownership/content policy, output ports, JPA metadata adapter, and MinIO
  storage adapter without leaking JPA, MinIO, HTTP, or Spring types into domain code.
- Added Flyway V6 with nullable, all-or-none media metadata and a unique object-key index. Existing rows and
  database behavior are preserved.
- Added private ranged delivery at `GET /api/v1/media/courses/{courseSlug}/lessons/{lessonId}` and owner/admin
  upload at `PUT /api/v1/instructor/courses/{courseSlug}/lessons/{lessonId}/media`.
- Added single-range handling for full, bounded, open-ended, and suffix requests. Invalid or multiple ranges
  return 416; oversized multipart requests return RFC 9457-style problem details with status 413.
- Streams only the requested MinIO range and closes the storage response stream; it does not buffer complete
  video objects in JVM memory.
- Uses immutable server-generated object keys. A failed metadata transaction removes the newly written object;
  replacement objects remain available for a later lifecycle sweep.

## Security decisions

- Upload is protected by Spring Security roles and a domain ownership policy; the UI is not treated as an
  authorization boundary.
- The MinIO bucket remains private and PostgreSQL stores only object metadata/key data.
- Native video requests use a short-lived HttpOnly `media_access` cookie scoped to `/api/v1/media`.
- That cookie is accepted only on `GET` and `HEAD` below the media route. State-changing APIs still require the
  bearer header, limiting the additional CSRF surface.
- Storage credentials, access tokens, client filenames, and object content are not written to logs or object keys.
- Current ingestion verifies declared MIME type and size. Binary signature inspection, antivirus scanning,
  transcoding, HLS/DASH packaging, and CDN delivery are explicitly deferred to the asynchronous media pipeline.

## Frontend changes

- Added a responsive instructor lesson-media manager with native file selection, progress feedback, completion,
  and API error states.
- Added native credentialed HTML5 video playback for platform-managed media, including loading/error/fallback
  states, inline mobile playback, metadata preload, and restored progress.
- Preserved iframe playback for existing externally hosted lesson content.
- Added role-aware navigation while retaining backend enforcement for every management action.

## Verification completed locally

- Backend: `mvn clean verify` — 54 tests, 0 failures, 0 errors, 0 skipped after the final added cases.
- Backend coverage: JaCoCo bundle threshold passed.
- Architecture: four ArchUnit rules and Spring Modulith verification passed.
- Module isolation: the learning module bootstrapped standalone with its catalog/identity contracts mocked.
- Persistence/storage: PostgreSQL/Flyway V1-V6 and real MinIO Testcontainers passed.
- REST/security: owner, administrator, student, anonymous, media-cookie range, and auth cookie behavior covered.
- Frontend: TypeScript check, Vitest (9 files / 11 tests), Vite production build, and responsive Selenium smoke at
  320, 768, and 1440 pixels passed before final review. The final run is repeated before Git delivery.

## Key files

Backend:

- `docs/architecture/phase-2-media-storage-delivery-plan.md`
- `src/main/java/com/ailearning/platform/catalog/application/service/impl/LessonMediaService.java`
- `src/main/java/com/ailearning/platform/catalog/adapter/out/storage/minio/MinioLessonMediaStorage.java`
- `src/main/java/com/ailearning/platform/learning/adapter/in/web/controller/LessonMediaController.java`
- `src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java`
- `src/main/resources/db/migration/V6__add_lesson_media_metadata.sql`
- `src/test/java/com/ailearning/platform/learning/api/LessonMediaApiIntegrationTest.java`

Frontend:

- `src/features/learning/LessonMediaManagerPage.tsx`
- `src/features/learning/LessonContentPlayer.tsx`
- `src/features/auth/AuthContext.tsx`
- `src/lib/api.ts`
- `src/features/learning/LessonMediaManagerPage.test.tsx`
- `src/features/learning/LessonContentPlayer.test.tsx`

## Dependency note

MinIO Java 9.0.3 is used behind the `LessonMediaStorage` port. Its OkHttp 5 transitive artifact does not provide
the JVM implementation classes required at compile time, so `okhttp-jvm` 5.3.2 is declared explicitly. This is
a compatibility dependency, not an additional application abstraction.

## Branch verification

- API feature HEAD `daa7dbf` passed GitHub Actions CI run
  [33732946068](https://github.com/ban2909-personally/ai-learning-api/actions/runs/33732946068).
- Web feature HEAD `914f654` passed GitHub Actions CI run
  [33732954601](https://github.com/ban2909-personally/ai-learning-web/actions/runs/33732954601).
- The API documentation-only completion commit is also required to pass CI before merge.

## Next independent slice

Implement popular-catalog Redis caching with cache-aside behavior, deterministic keys, bounded TTL, graceful
degradation when Redis is unavailable, and explicit invalidation ownership. It must remain separate from media
consistency and receive its own spec, tests, branch, commits, CI run, and merge.
