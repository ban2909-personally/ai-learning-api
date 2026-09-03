# Phase 2 completion: lesson media storage and delivery

## Objective

Complete the media portion of the original Phase 2 without changing the existing lesson JSON contract. An
instructor can attach a video to a lesson they own, enrolled students can play it through a protected URL, and
the API streams only the requested byte range from private MinIO storage.

## Architectural slice

```text
Instructor upload HTTP adapter
    -> ManageLessonMediaUseCase
    -> ownership and media policy
    -> LessonMediaStorage port -> MinIO adapter
    -> LessonMediaCatalog port -> JPA adapter

Browser media request
    -> media-scoped HttpOnly access cookie
    -> StreamLessonMediaUseCase
    -> existing lesson/enrollment authorization
    -> catalog public media contract
    -> MinIO ranged stream
```

The MinIO bucket remains private. The access-token cookie is restricted to `/api/v1/media`, is HttpOnly, and
is only accepted as authentication for `GET`/`HEAD` media requests. Other APIs continue to require the bearer
header, so introducing browser-native video playback does not turn state-changing endpoints into cookie-authenticated
CSRF targets.

## API and database compatibility

- Keep the existing lesson player response and `contentUrl` field.
- Add `PUT /api/v1/instructor/courses/{courseSlug}/lessons/{lessonId}/media` as multipart upload.
- Add `GET /api/v1/media/courses/{courseSlug}/lessons/{lessonId}` with single-range HTTP semantics.
- Add nullable media metadata columns through Flyway V6; existing external lesson URLs continue to work.
- Store object keys, never MinIO credentials or public bucket URLs, in PostgreSQL.

## Security and performance rules

- Only `INSTRUCTOR` and `ADMIN` may call the upload endpoint.
- A non-admin instructor must own the course containing the lesson.
- Accept an explicit content-type allow-list and enforce a configurable maximum upload size.
- Generate server-side object keys; never trust a client filename as a storage path.
- Stream from MinIO without buffering the complete object in JVM memory.
- Support one RFC 9110 byte range; reject malformed, unsatisfied, and multiple ranges with `416`.
- Return `Accept-Ranges`, `Content-Range`, `Content-Length`, `Content-Type`, and `ETag` where applicable.
- Close every MinIO response stream and avoid logging credentials, tokens, object bodies, or query secrets.

## Verification checklist

- [x] Domain policy tests cover owner, admin, and unauthorized instructor.
- [x] Application tests cover upload validation, storage failure, metadata persistence, and failed-persistence cleanup.
- [x] Range parser tests cover full, bounded, open-ended, suffix, malformed, and unsatisfied ranges.
- [x] REST security tests cover anonymous, student, owner instructor, and admin behavior.
- [x] Testcontainers validates Flyway V6 and the MinIO adapter against a real MinIO container.
- [x] Spring Modulith and ArchUnit checks remain green.
- [x] Existing 29 backend tests remain green and JaCoCo stays above the configured threshold.
- [x] Frontend uses a native responsive video element with loading, error, and unsupported-media states.
- [x] Vitest, TypeScript build, and Selenium checks pass at 320, 768, and 1440 pixels.
- [x] Feature branches are pushed and CI is green before merging to `main`.

## Deferred, explicitly separate work

- Resumable/multipart browser uploads and background transcoding require an upload-session model and Kafka worker;
  they belong to the asynchronous phase.
- HLS/DASH packaging and CDN delivery belong to the production media pipeline.
- Redis popular-course caching is the next independent slice because its invalidation and availability behavior
  are unrelated to media consistency.
- Replaced objects use immutable keys and are retained for a later lifecycle sweep. This avoids deleting working
  content before the database points at the replacement and avoids pretending PostgreSQL and object storage share
  an atomic transaction.
