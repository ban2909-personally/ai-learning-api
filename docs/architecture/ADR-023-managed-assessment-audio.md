# ADR-023: Managed Listening audio and private transcripts

Status: Accepted for the next product increment (2026-09-28). Specification only; implementation not yet complete. Not implemented by ADR-022.

## Scope and architecture

Replace the Listening demo's browser TTS with uploaded original/licensed audio. Keep Speaking outside scope. Assessment owns audio asset metadata, author/read permissions and use cases; its outbound MinIO adapter depends only on its own output port and `platform::storage`. Do not import catalog persistence, entities, storage adapters or media contracts.

An audio asset has an opaque UUID, owning exam series, immutable object key, canonical content type, byte length and ETag. The object key is generated server-side, never taken from a filename, URL or request. Do not overwrite an asset referenced by a published/historical revision. Revisions reference asset IDs; cloning reuses immutable audio, replacing audio requires a newly uploaded asset.

## Upload and attachment

- Only an active author managing a DRAFT (or ADMIN) can upload for its series. LEADER approves but cannot bypass private-draft ownership. Reject stale expected versions and non-draft targets.
- Enforce a hard upper bound of **9,999,999 bytes**, including multipart configuration and bounded stream validation; configuration must not silently raise it. Reject empty, oversized, unsupported or inconsistent content. Initial formats: MP3 and WAV; validate signatures/container structure as well as MIME. Signature checks are not a malware scanner or proof of valid decoding.
- Store under an assessment prefix with a generated UUID. Metadata persistence must recheck draft/version/ownership atomically after upload. If persistence fails, remove only that just-created object, preserve the original error and record any cleanup failure. Never reset a bucket or delete existing course objects.
- Saving the draft may attach only assets from the same series. Database FK/check constraints and adapter transaction preserve integrity. Unattached uploads may require a later bounded cleanup policy; do not introduce a destructive global sweep in this increment.
- Uploaded immutable assets are not editable/deletable through the authoring UI once used. An approved draft requires Listening audio and a private transcript. Existing TTS samples must remain explicitly labeled demo-only until migrated, not silently presented as equivalent real-audio exams.

## Read authorization and transcript privacy

- Public discovery/intro must not reveal private draft asset IDs, keys, storage credentials or transcripts. Public current publications may stream their attached audio; draft/pending content follows author/admin and submitted-content leader permissions.
- Archived audio is available only through an owned historical attempt or an authorized staff workspace, not by guessing an asset ID. Authorization happens before opening MinIO, including HEAD and Range requests.
- Do not put bearer tokens in audio URLs or localStorage. Prefer native ranged playback under `/api/v1/media/practice/`, reusing the existing HttpOnly `media_access` cookie accepted only for GET/HEAD media requests. Authoring mutations still require the bearer header. Public playback is permitted only after assessment verifies a current publication; protected draft/archived playback still checks identity, role and ownership server-side.
- Use the existing `getAccessToken()`/single-flight refresh flow before loading protected audio; do not create a second auth system. Keep `preload="none"`, explicit load/retry UI, safe loading/error states and native accessible controls. Do not buffer the full file as a Blob by default merely to inject a bearer header when ranged cookie-authenticated delivery already exists.
- Add managed-audio fields additively to existing contracts. During an in-progress attempt, no transcript/key/explanation is returned. Post-submit review may show the private transcript and explanations. Preserve existing submitted attempts/reviews.
- Implement bounded GET/HEAD, single-byte-range handling, 206/416, ETag, correct Content-Length/Content-Range and safe content headers. No arbitrary external URL/proxy or unbounded read-all operation.

## UI and verification checklist

- [ ] Asset metadata migration and historical revision preservation; no destructive preview reset.
- [ ] Framework-free upload/read policy, meaningful use-case and output ports, configuration wiring.
- [ ] Authorization/IDOR, type/signature/size boundary tests (9,999,999 allowed; 10,000,000 denied).
- [ ] Media-cookie GET/HEAD and expired-session/retry tests; prove the media cookie cannot authorize an upload/save/submit mutation.
- [ ] MinIO Testcontainers store/range/failure-cleanup tests; PostgreSQL attachment/CAS rollback tests.
- [ ] No transcript exposure in public/in-progress payloads; transcript appears only in submitted review.
- [ ] Responsive author audio upload/preview and learner player, loading/failure/retry UX and URL cleanup tests.
- [ ] JUnit/Mockito/MockMvc/security/Modulith/ArchUnit; frontend tests/build/browser checks.
- [ ] Preview backup before migration, API-backed acceptance with self-created audio and documented provenance.
- [ ] Full verify → feature commit/push → exact CI green → merge/push main → main CI evidence.

Server-enforced timing, resume/history, full-length content banks, analytics and any validated official score conversion remain separate increments. Human Writing rubric stays distinct from Listening/Reading raw correctness.

Local acceptance asset prepared in ignored `target/preview-audio/`: original team announcement synthesized locally, 770,104-byte WAV, SHA-256 `D1246DED1856E8E7625281877BAB98C250F51247D31A535FCF3490A7C40814ED`. It has not been uploaded or attached to a DB revision. Its provenance is recorded alongside the file.
