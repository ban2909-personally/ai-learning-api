# Phase 8.2 — Container supply-chain security

- [x] Inspect clean branch, complete diff, Maven lifecycle, Docker and workflow.
- [x] Record scope, scanner provenance, SBOMs, severity policy and exceptions before code.
- [ ] Generate application SBOM during Maven verification.
- [ ] Pin actions/frontend and scan the exact exported image with pinned Trivy.
- [ ] Add source/image secret scans and retain bounded vulnerability/SBOM artifacts.
- [ ] Validate SBOM contents, scan failure behavior and remediation requirements locally.
- [ ] Run full Maven tests/coverage, Docker build/runtime assertions and security scans.
- [ ] Review diff, write operations runbook and development report.
- [ ] Commit cohesive changes, push branch, verify CI, merge main, verify and push main, verify main CI.

## Phase 8.1 delivery evidence

Feature run `34008810017` passed at `4abf67af80f129714f026fe443b7d9fb363f532e`. Merge `2c24cb39b1b2042f3a451b70d5d3554b4a0de538` passed local Maven verification (199 tests, no failures/errors/skips, coverage gate) and Docker runtime assertions before push. Main run `34610323026` passed both `verify` and `container-image` for that merge. The old Phase 8.1 report's pending delivery wording is superseded by this evidence.

## Outstanding roadmap decisions

Payments need an actual gateway/account/sandbox choice. B2B invitations need a delivery mechanism; seats need billing/capacity semantics. Production hosting, signing/registry, backup drills, SLOs and responsive commercial/admin frontend journeys remain outstanding. Phase 8.2 does not declare those complete.
